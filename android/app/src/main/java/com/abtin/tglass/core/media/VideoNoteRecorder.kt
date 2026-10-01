package com.abtin.tglass.core.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Rational
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.MessageContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** A finished round video message: square MP4 [path] of [length]×[length] px, and a square JPEG cover of [thumbSize] px. */
data class RecordedVideoNote(val path: String, val seconds: Int, val length: Int, val thumbPath: String?, val thumbSize: Int)

/** The recording as a message to send (local files, fileId 0). */
fun RecordedVideoNote.toContent(): MessageContent.VideoNote = MessageContent.VideoNote(
    seconds = seconds,
    video = ImageRef(0, path, null, length, length),
    thumb = thumbPath?.let { ImageRef(0, it, null, thumbSize, thumbSize) },
)

/**
 * Records Telegram video messages with CameraX: front camera, ~480p, cropped to a square through a 1:1 [ViewPort]
 * (so the preview circle shows exactly what is recorded), mirrored like the preview, audio on, into the app cache.
 *
 * Flow: [open] shows the camera overlay ([active]) and registers the result callback; the overlay's PreviewView calls
 * [bind], which opens the camera and starts recording; [stop] finishes (send) or discards (cancel). The callback gets
 * the finished note (or null) exactly once per [open] — also when the recording ends by itself (60 s limit, camera
 * lost). [active] stays true until the file is finalized, so a new recording can't start on top of the old one.
 * All calls on the main thread.
 */
class VideoNoteRecorder(context: Context) {
    private val app = context.applicationContext
    private val main = ContextCompat.getMainExecutor(app)
    private val scope = MainScope()

    /** True from [open] until the recording is finished, finalized and reported. */
    var active by mutableStateOf(false)
        private set

    /** [SystemClock.elapsedRealtime] when frames started being recorded; 0 while the camera is still starting. */
    var startedAt by mutableLongStateOf(0L)
        private set

    /** Duration actually written to the file so far (from CameraX status events). */
    private var recordedMs by mutableLongStateOf(0L)

    private var provider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var session = 0
    private var stopRequested = false
    private var sendRequested = false
    private var onResult: ((RecordedVideoNote?) -> Unit)? = null

    /** Milliseconds recorded so far. */
    fun elapsedMs(): Long {
        if (startedAt <= 0) return 0L
        return maxOf(recordedMs, SystemClock.elapsedRealtime() - startedAt)
    }

    /** Starts a recording session; [result] gets the finished note, or null when it was cancelled or failed. */
    fun open(result: (RecordedVideoNote?) -> Unit) {
        if (active) return
        session++
        active = true
        startedAt = 0
        recordedMs = 0
        stopRequested = false
        sendRequested = false
        recording = null
        onResult = result
    }

    /** Opens the camera for the overlay's preview and starts recording. [rotation] is the display's Surface.ROTATION_*. */
    fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, rotation: Int = Surface.ROTATION_0) {
        if (!active || stopRequested || provider != null && recording != null) return
        val s = session
        val future = ProcessCameraProvider.getInstance(app)
        future.addListener({
            if (s != session || !active || stopRequested) return@addListener
            val p = runCatching { future.get() }.getOrNull()
            if (p == null) {
                finish(null)
                return@addListener
            }
            provider = p
            val preview = Preview.Builder().setTargetRotation(rotation).build()
            preview.setSurfaceProvider(surfaceProvider)
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            val capture = VideoCapture.Builder(recorder)
                .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                .setTargetRotation(rotation)
                .build()
            val group = UseCaseGroup.Builder()
                .setViewPort(ViewPort.Builder(Rational(1, 1), rotation).build())
                .addUseCase(preview)
                .addUseCase(capture)
                .build()
            val bound = runCatching {
                p.unbindAll()
                val front = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
                p.bindToLifecycle(owner, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, group)
            }.isSuccess
            if (!bound) {
                finish(null)
                return@addListener
            }
            startRecording(recorder, s)
        }, main)
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(recorder: Recorder, s: Int) {
        val dir = File(app.cacheDir, "video_notes").apply { mkdirs() }
        val out = File(dir, "note_${System.currentTimeMillis()}.mp4")
        // Safety net in case the UI misses the limit; the file then still gets sent.
        val options = FileOutputOptions.Builder(out).setDurationLimitMillis(MaxMs + 500).build()
        var pending = recorder.prepareRecording(app, options)
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        val started = runCatching { pending.start(main) { event -> onEvent(event, s, out) } }.getOrNull()
        if (started == null) {
            out.delete()
            finish(null)
            return
        }
        recording = started
    }

    private fun onEvent(event: VideoRecordEvent, s: Int, out: File) {
        if (s != session) {
            // A stale session (should not happen, [open] waits for finalization): just clean up.
            if (event is VideoRecordEvent.Finalize) out.delete()
            return
        }
        when (event) {
            is VideoRecordEvent.Start -> startedAt = SystemClock.elapsedRealtime()
            is VideoRecordEvent.Status -> recordedMs = event.recordingStats.recordedDurationNanos / 1_000_000L
            is VideoRecordEvent.Finalize -> {
                val error = event.error
                val usable = error == VideoRecordEvent.Finalize.ERROR_NONE ||
                    error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ||
                    error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
                    error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
                // Stopped by the user: their choice. Stopped by the time limit: send, like Telegram.
                val wanted = if (stopRequested) sendRequested else error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
                recording = null
                closeCamera()
                if (!(wanted && usable && out.length() > 0)) {
                    out.delete()
                    finish(null)
                    return
                }
                scope.launch {
                    val result = withContext(Dispatchers.IO) { describe(out) }
                    if (result == null) out.delete()
                    finish(result)
                }
            }
            else -> {}
        }
    }

    /** Finishes the recording: [send] keeps and reports it, otherwise it is deleted (and null reported). */
    fun stop(send: Boolean) {
        if (!active || stopRequested) return
        stopRequested = true
        sendRequested = send
        val r = recording
        if (r == null) {
            // The camera had not started recording yet: nothing was recorded.
            closeCamera()
            finish(null)
            return
        }
        r.stop()
    }

    fun cancel() = stop(false)

    private fun closeCamera() {
        runCatching { provider?.unbindAll() }
        provider = null
        startedAt = 0
    }

    /** Reports the result once and ends the session. */
    private fun finish(result: RecordedVideoNote?) {
        closeCamera()
        recording = null
        val cb = onResult
        onResult = null
        active = false
        cb?.invoke(result)
    }

    /** Duration, square side and a square JPEG cover of a finished recording (background thread). */
    private fun describe(out: File): RecordedVideoNote? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(out.absolutePath)
            var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (ms < 700) return null
            val seconds = ((ms + 500) / 1000).toInt().coerceIn(1, MaxSeconds)
            val side = minOf(w, h).takeIf { it > 0 } ?: 384
            // A frame a little into the video (the very first one is often dark while the sensor adjusts).
            val at = minOf(400_000L, ms * 1000 / 2)
            val frame = runCatching { r.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }.getOrNull()
                ?: runCatching { r.getFrameAtTime(0) }.getOrNull()
            val thumb = frame?.let { squareThumb(it, out) }
            return RecordedVideoNote(out.absolutePath, seconds, side.coerceIn(1, 640), thumb?.first, thumb?.second ?: 0)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun squareThumb(frame: Bitmap, out: File): Pair<String, Int>? = runCatching {
        val side = minOf(frame.width, frame.height)
        val square = Bitmap.createBitmap(frame, (frame.width - side) / 2, (frame.height - side) / 2, side, side)
        val size = minOf(side, 240)
        val scaled = if (side > size) Bitmap.createScaledBitmap(square, size, size, true) else square
        val file = File(out.parentFile, out.nameWithoutExtension + "_thumb.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        if (scaled !== square) scaled.recycle()
        if (square !== frame) square.recycle()
        frame.recycle()
        file.absolutePath to size
    }.getOrNull()

    companion object {
        /** Telegram's limit for a video message. */
        const val MaxSeconds = 60
        const val MaxMs = MaxSeconds * 1000L
    }
}

/** A [VideoNoteRecorder] tied to the current screen; an unfinished recording is discarded when the screen goes away. */
@Composable
fun rememberVideoNoteRecorder(): VideoNoteRecorder {
    val context = LocalContext.current
    val recorder = remember { VideoNoteRecorder(context.applicationContext) }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}
