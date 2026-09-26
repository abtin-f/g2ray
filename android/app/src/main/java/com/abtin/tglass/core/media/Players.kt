package com.abtin.tglass.core.media

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * App-wide voice note player (one voice plays at a time, like Telegram).
 * [key] identifies the message; items without a file (demo data) are "played" by a timer so the UI still animates.
 */
object VoicePlayer {
    var currentKey by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    /** 0..1 of the current item. */
    var progress by mutableFloatStateOf(0f)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    /** Playback speed of voice notes (1x / 1.5x / 2x), kept for the next ones like Telegram. */
    var speed by mutableFloatStateOf(1f)
        private set

    /** 1x → 1.5x → 2x → 1x. */
    fun cycleSpeed() {
        speed = when (speed) {
            1f -> 1.5f
            1.5f -> 2f
            else -> 1f
        }
        player?.setPlaybackSpeed(speed)
    }

    private val scope = MainScope()
    private var player: ExoPlayer? = null
    private var ticker: Job? = null
    private var simulatedMs = 0L

    fun toggle(context: Context, key: String, path: String?, seconds: Int) {
        if (key == currentKey) {
            if (playing) pause() else resume()
            return
        }
        stop()
        currentKey = key
        if (path == null) {
            simulatedMs = seconds * 1000L
            playing = true
            startTicker()
            return
        }
        simulatedMs = 0
        val p = player ?: ExoPlayer.Builder(context.applicationContext).build().also { p ->
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (currentKey != null && simulatedMs == 0L) playing = isPlaying
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) stop()
                }
            })
            player = p
        }
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
        p.setPlaybackSpeed(speed)
        p.prepare()
        p.play()
        playing = true
        startTicker()
    }

    fun pause() {
        player?.pause()
        playing = false
    }

    private fun resume() {
        if (simulatedMs == 0L) player?.play()
        playing = true
    }

    fun stop() {
        ticker?.cancel()
        player?.stop()
        currentKey = null
        playing = false
        progress = 0f
        positionMs = 0
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(50)
                if (simulatedMs > 0) {
                    if (playing) positionMs += (50 * speed).toLong()
                    progress = (positionMs.toFloat() / simulatedMs).coerceIn(0f, 1f)
                    if (positionMs >= simulatedMs) { stop(); return@launch }
                } else {
                    val p = player ?: continue
                    val d = p.duration.takeIf { it > 0 } ?: continue
                    positionMs = p.currentPosition
                    progress = (positionMs.toFloat() / d).coerceIn(0f, 1f)
                }
            }
        }
    }
}

/** An ExoPlayer for one video file, released when it leaves the composition. */
@Composable
fun rememberVideoPlayer(path: String, loop: Boolean, muted: Boolean = false): ExoPlayer {
    val context = LocalContext.current
    val player = remember(path) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        VoicePlayer.stop()
        onDispose { player.release() }
    }
    return player
}

/** The video picture. */
@Composable
fun VideoSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(factory = { TextureView(it).also { tv -> player.setVideoTextureView(tv) } }, modifier = modifier)
}

/** Observable playback state of a video [ExoPlayer] for custom controls. */
class VideoState {
    var playing by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
}

@Composable
fun rememberVideoState(player: ExoPlayer): VideoState {
    val state = remember(player) { VideoState() }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { state.playing = isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && player.repeatMode == Player.REPEAT_MODE_OFF) state.playing = false
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (true) {
            state.positionMs = player.currentPosition
            state.durationMs = player.duration.coerceAtLeast(0)
            delay(100)
        }
    }
    return state
}

fun ExoPlayer.togglePlay() {
    if (isPlaying) pause()
    else {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        play()
    }
}
