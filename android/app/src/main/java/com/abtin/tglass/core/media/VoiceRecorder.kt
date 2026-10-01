package com.abtin.tglass.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** A finished recording: local file, whole seconds and a 0..1 waveform. */
data class RecordedVoice(val path: String, val seconds: Int, val waveform: List<Float>)

/**
 * Records voice notes the way Telegram expects them: Opus in an Ogg container (Android 10+),
 * AAC otherwise. [sample] is polled by the UI to build the waveform.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val levels = ArrayList<Float>()

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        stop(discard = true)
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val ogg = Build.VERSION.SDK_INT >= 29
        val out = File(dir, "voice_${System.currentTimeMillis()}.${if (ogg) "ogg" else "m4a"}")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (ogg) {
                r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            r.setAudioSamplingRate(48_000)
            r.setAudioEncodingBitRate(32_000)
            r.setAudioChannels(1)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = System.currentTimeMillis()
            levels.clear()
            true
        } catch (e: Exception) {
            r.release()
            out.delete()
            false
        }
    }

    /** Current input level 0..1; also stored for the waveform. Call every ~60 ms while recording. */
    fun sample(): Float {
        val r = recorder ?: return 0f
        val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
        // Loudness on a perceptual (dB) scale: about -50 dBFS (silence) .. 0 dBFS → 0..1.
        val level = if (amp <= 0) 0f else ((20f * kotlin.math.log10(amp / 32767f) + 50f) / 50f).coerceIn(0f, 1f)
        levels += level
        return level
    }

    /** Stops recording; returns the result unless [discard] or the recording failed. */
    fun stop(discard: Boolean): RecordedVoice? {
        val r = recorder ?: return null
        recorder = null
        val out = file
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
        if (discard || !ok || out == null || seconds < 1) {
            out?.delete()
            return null
        }
        return RecordedVoice(out.absolutePath, seconds, resample(levels, 40))
    }

    private fun resample(src: List<Float>, n: Int): List<Float> {
        if (src.isEmpty()) return List(n) { 0.2f }
        return List(n) { i ->
            val from = i * src.size / n
            val to = maxOf(from + 1, (i + 1) * src.size / n).coerceAtMost(src.size)
            src.subList(from, to).maxOrNull() ?: 0f
        }.map { (it * it * 1.3f).coerceIn(0.08f, 1f) }
    }
}
