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
 * (so the preview circle shows exactly what is recorded), audio on, into the app cache.
 *
 * Flow: [open] shows the camera overlay ([active]); the overlay's PreviewView calls [bind], which opens the camera and
 * starts recording; [stop] finishes (send) or discards (cancel) and reports the result once the file is finalized.
 * All calls on the main thread.
 */
class VideoNoteRecorder(context: Context) {
    private val app = context.applicationContext
    private val main = ContextCompat.getMainExecutor(app)
    private val scope = MainScope()

    /** True while the camera overlay should be shown (from [open] until the recording is finished or cancelled). */
    var active by mutableStateOf(false)
        private set

    /** [SystemClock.elapsedRealtime] when frames started being recorded; 0 while the camera is still starting. */
    var startedAt by mutableLongStateOf(0L)
        private set

    private var provider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private var session = 0
    private var stopRequested = false
    private var sendRequested = false
    private var onDone: ((RecordedVideoNote?) -> Unit)? = null

    /** Milliseconds recorded so far. */
    fun elapsedMs(): Long = if (startedAt > 0) SystemClock.elapsedRealtime() - startedAt else 0L

    fun open() {
        if (active) return
        session++
        active = true
        startedAt = 0
        stopRequested = false
        sendRequested = false
        onDone = null
    }

    /** Opens the camera for the overlay's preview and starts recording. */
    fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        if (!active) return
        val s = session
        val future = ProcessCameraProvider.getInstance(app)
        future.addListener({
            if (s != session || !active) return@addListener
            val p = runCatching { future.get() }.getOrNull()
            if (p == null) {
                closeCamera()
                deliver(null)
                return@addListener
            }
            provider = p
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(surfaceProvider)
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            val capture = VideoCapture.Builder(recorder)
                .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
                .build()
            val group = UseCaseGroup.Builder()
                .setViewPort(ViewPort.Builder(Rational(1, 1), Surface.ROTATION_0).build())
                .addUseCase(preview)
                .addUseCase(capture)
                .build()
            val bound = runCatching {
                p.unbindAll()
                val front = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
                p.bindToLifecycle(owner, if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, group)
            }.isSuccess
            if (!bound) {
                closeCamera()
                deliver(null)
                return@addListener
            }
            startRecording(recorder, s)
        }, main)
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(recorder: Recorder, s: Int) {
        val dir = File(app.cacheDir, "video_notes").apply { mkdirs() }
        val out = File(dir, "note_${System.currentTimeMillis()}.mp4")
        var pending = recorder.prepareRecording(app, FileOutputOptions.Builder(out).build())
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        val started = runCatching { pending.start(main) { event -> onEvent(event, s, out) } }.getOrNull()
        if (started == null) {
            out.delete()
            closeCamera()
            deliver(null)
            return
        }
        recording = started
    }

    private fun onEvent(event: VideoRecordEvent, s: Int, out: File) {
        if (event is VideoRecordEvent.Start) {
            if (s == session) startedAt = SystemClock.elapsedRealtime()
            return
        }
        if (event !is VideoRecordEvent.Finalize) return
        if (s != session) {
            out.delete()
            return
        }
        val error = event.error
        val usable = error == VideoRecordEvent.Finalize.ERROR_NONE ||
            error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE ||
            error == VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED ||
            error == VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
        val send = stopRequested && sendRequested && usable && out.length() > 0
        recording = null
        closeCamera()
        if (!send) {
            out.delete()
            deliver(null)
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { describe(out) }
            if (result == null) out.delete()
            deliver(result)
        }
    }

    /** Finishes the recording: [send] keeps and reports it through [onResult], otherwise it is deleted (and null reported). */
    fun stop(send: Boolean, onResult: (RecordedVideoNote?) -> Unit) {
        if (!active || stopRequested) return
        stopRequested = true
        sendRequested = send
        onDone = onResult
        val r = recording
        if (r == null) {
            // The camera had not started yet: nothing was recorded.
            closeCamera()
            deliver(null)
            return
        }
        recording = null
        r.stop()
    }

    fun cancel() = stop(false) {}

    private fun closeCamera() {
        runCatching { provider?.unbindAll() }
        active = false
        startedAt = 0
    }

    private fun deliver(result: RecordedVideoNote?) {
        val cb = onDone
        onDone = null
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
            val seconds = ((ms + 500) / 1000).toInt()
            if (seconds < 1) return null
            val side = minOf(w, h).takeIf { it > 0 } ?: 384
            val thumb = runCatching { r.getFrameAtTime(0) }.getOrNull()?.let { squareThumb(it, out) }
            return RecordedVideoNote(out.absolutePath, seconds.coerceAtMost(MaxSeconds), side.coerceIn(1, 640), thumb?.first, thumb?.second ?: 0)
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
