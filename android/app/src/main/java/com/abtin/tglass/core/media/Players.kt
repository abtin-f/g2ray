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
import androidx.media3.common.VideoSize
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

/** Players already released by [releaseSafely]; touching them (surfaces, seeks, polling) is skipped. Main thread only. */
private val releasedPlayers: MutableSet<Player> = java.util.Collections.newSetFromMap(java.util.WeakHashMap())

/** True once this player has been released by [releaseSafely]. */
val Player.isReleasedSafely: Boolean get() = this in releasedPlayers

/** Releases the player once; later calls (and calls on other players' behalf) are no-ops. */
fun ExoPlayer.releaseSafely() {
    if (!releasedPlayers.add(this)) return
    runCatching { clearVideoSurface() }
    runCatching { release() }
}

/**
 * An ExoPlayer for one video file, released when it leaves the composition (or when [path]/[loop] change).
 * Silent players ([muted], e.g. GIF loops) don't take audio focus and don't stop a playing voice note.
 */
@Composable
fun rememberVideoPlayer(path: String, loop: Boolean, muted: Boolean = false, autoPlay: Boolean = true): ExoPlayer =
    rememberVideoPlayer(Uri.fromFile(File(path)), loop, muted, autoPlay)

/** Same as the path version, for any playable [uri] (e.g. a gallery content:// item in the photo/video editor). */
@Composable
fun rememberVideoPlayer(uri: Uri, loop: Boolean, muted: Boolean = false, autoPlay: Boolean = true): ExoPlayer {
    val context = LocalContext.current
    val player = remember(uri, loop) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            prepare()
            playWhenReady = autoPlay
        }
    }
    LaunchedEffect(player, muted) {
        if (player.isReleasedSafely) return@LaunchedEffect
        player.volume = if (muted) 0f else 1f
        runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ !muted,
            )
        }
        if (!muted) VoicePlayer.stop()
    }
    DisposableEffect(player) {
        onDispose { player.releaseSafely() }
    }
    return player
}

/**
 * The video picture. Attaches the TextureView to [player] and detaches it when the view goes away or the player
 * changes, so a surface is never left on (or handed to) a released player.
 */
@Composable
fun VideoSurface(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { TextureView(it) },
        modifier = modifier,
        update = { tv ->
            val old = tv.tag as? ExoPlayer
            if (old !== player) {
                if (old != null && !old.isReleasedSafely) runCatching { old.clearVideoTextureView(tv) }
                if (!player.isReleasedSafely) runCatching { player.setVideoTextureView(tv) }
                tv.tag = player
            }
        },
        onRelease = { tv ->
            val old = tv.tag as? ExoPlayer
            if (old != null && !old.isReleasedSafely) runCatching { old.clearVideoTextureView(tv) }
            tv.tag = null
        },
    )
}

/** Observable playback state of a video [ExoPlayer] for custom controls. */
class VideoState {
    var playing by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    /** Waiting for data (show a spinner). */
    var buffering by mutableStateOf(false)
    /** Played to the end (non-looping). */
    var ended by mutableStateOf(false)
    /** Display aspect ratio of the decoded video, 0 until known. */
    var videoAspect by mutableFloatStateOf(0f)
    /** The first frame has been drawn (hide the cover image). */
    var firstFrame by mutableStateOf(false)
}

@Composable
fun rememberVideoState(player: ExoPlayer): VideoState {
    val state = remember(player) { VideoState() }
    DisposableEffect(player) {
        fun aspectOf(size: VideoSize) {
            if (size.width > 0 && size.height > 0) state.videoAspect = size.width * size.pixelWidthHeightRatio / size.height
        }
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.playing = isPlaying || player.playWhenReady && player.playbackState == Player.STATE_BUFFERING
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                state.buffering = playbackState == Player.STATE_BUFFERING
                state.ended = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_ENDED && player.repeatMode == Player.REPEAT_MODE_OFF) state.playing = false
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) = aspectOf(videoSize)
            override fun onRenderedFirstFrame() { state.firstFrame = true }
        }
        if (!player.isReleasedSafely) {
            aspectOf(player.videoSize)
            state.buffering = player.playbackState == Player.STATE_BUFFERING
            player.addListener(l)
        }
        onDispose { if (!player.isReleasedSafely) player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (isActive && !player.isReleasedSafely) {
            state.positionMs = player.currentPosition.coerceAtLeast(0)
            state.durationMs = player.duration.coerceAtLeast(0)
            delay(40)
        }
    }
    return state
}

fun ExoPlayer.togglePlay() {
    if (isReleasedSafely) return
    if (isPlaying || playWhenReady && playbackState == Player.STATE_BUFFERING) pause()
    else {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        play()
    }
}
