package com.abtin.tglass.features.chat

import android.net.Uri
import android.os.SystemClock
import android.view.TextureView
import androidx.compose.ui.platform.LocalView
import android.view.Surface
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.abtin.tglass.core.design.GlassLevel
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.media.VideoNoteRecorder
import com.abtin.tglass.core.media.VoicePlayer
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatDuration
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import java.io.File

/** Diameter of a round video message in the chat (Telegram-iOS). */
private val NoteSize = 220.dp

/** Diameter while it plays with sound: Telegram-iOS enlarges the circle. */
private val NoteSizePlaying = 276.dp

private val NoteAudio = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()

/**
 * The video message currently playing with sound ("chatId:messageId"); every other one on screen loops muted.
 * Only one plays at a time, like Telegram.
 */
object VideoNotePlayback {
    var activeKey by mutableStateOf<String?>(null)
}

/** Observable state of one round video's player. */
private class NoteState(val player: ExoPlayer) {
    var playWhenReady by mutableStateOf(true)
    var progress by mutableFloatStateOf(0f)
    var positionMs by mutableLongStateOf(0L)
}

/**
 * Round video message (Telegram-iOS `ChatMessageInstantVideoItemNode`): no bubble, a 220pt circle that shows the cover,
 * then loops the video muted once downloaded. Tap plays it from the start with sound and a progress ring; tap again pauses.
 */
@Composable
internal fun VideoNoteMessage(
    m: Message,
    v: MessageContent.VideoNote,
    replyTo: Message?,
    replyName: String?,
    onReplyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalRepository.current
    val colors = bubbleColors(m.outgoing)
    val key = "${m.chatId}:${m.id}"
    val video = v.video
    val path = video?.let { repo.filePath(it) }
    LaunchedEffect(video, path) { if (video != null && path == null) repo.requestImage(video) }
    val active = VideoNotePlayback.activeKey == key
    // Scrolled away (the list item is disposed) or deleted: stop the sound.
    DisposableEffect(key) { onDispose { if (VideoNotePlayback.activeKey == key) VideoNotePlayback.activeKey = null } }
    val note = if (path != null) rememberNotePlayer(path, key) else null

    if (note != null) {
        LaunchedEffect(note, active) {
            val p = note.player
            if (active) {
                VoicePlayer.stop()
                // Take audio focus only while it plays with sound (the muted loops must not pause other apps).
                p.setAudioAttributes(NoteAudio, true)
                p.volume = 1f
                p.repeatMode = Player.REPEAT_MODE_OFF
                p.seekTo(0)
                p.play()
                while (true) {
                    val d = p.duration
                    note.positionMs = p.currentPosition
                    note.progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
                    withFrameMillis { }
                }
            } else {
                note.progress = 0f
                note.positionMs = 0
                p.setAudioAttributes(NoteAudio, false)
                p.volume = 0f
                p.repeatMode = Player.REPEAT_MODE_ONE
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                p.play()
            }
        }
    }
    // A voice note started elsewhere silences this one.
    val voicePlaying = VoicePlayer.playing
    LaunchedEffect(voicePlaying) { if (voicePlaying && VideoNotePlayback.activeKey == key) VideoNotePlayback.activeKey = null }

    fun onTap() {
        when {
            video == null -> {}
            note == null -> repo.requestImage(video)
            !active -> {
                VideoNotePlayback.activeKey = key
                if (!m.outgoing && !v.viewed) repo.openMessageContent(m.chatId, m.id)
            }
            note.player.playWhenReady -> note.player.pause()
            else -> note.player.play()
        }
    }

    val diameter by animateDpAsState(if (active) NoteSizePlaying else NoteSize, spring(dampingRatio = 0.75f, stiffness = 380f), label = "noteSize")
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        if (m.forwardedFrom != null || replyTo != null) {
            Column(
                Modifier
                    .padding(bottom = 4.dp)
                    .widthIn(max = diameter)
                    .clip(RoundedRectangle(14.dp))
                    .background(colors.fill)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                if (m.forwardedFrom != null) {
                    T("Forwarded from", TgTheme.type.footnote, colors.accent, maxLines = 1)
                    T(m.forwardedFrom, TgTheme.type.footnote, colors.accent, weight = FontWeight.SemiBold, maxLines = 1)
                }
                if (replyTo != null) {
                    Row(Modifier.height(IntrinsicSize.Min).fadeClickable(onClick = onReplyClick)) {
                        Box(Modifier.width(3.dp).fillMaxHeight().clip(Capsule()).background(colors.accent))
                        Column(Modifier.padding(start = 6.dp)) {
                            T(replyName ?: "", TgTheme.type.footnote.copy(fontSize = 14.sp), colors.accent, weight = FontWeight.SemiBold, maxLines = 1)
                            T(replyTo.preview, TgTheme.type.footnote.copy(fontSize = 14.sp), colors.text, maxLines = 1)
                        }
                    }
                }
            }
        }
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(diameter)
                    .clip(CircleShape)
                    .background(colors.meta.copy(alpha = 0.25f))
                    .bounceClickable { onTap() },
                contentAlignment = Alignment.Center,
            ) {
                TgImage(v.thumb, Modifier.size(diameter), maxPx = 480)
                if (note != null) NoteSurface(note.player, Modifier.size(diameter))
            }
            if (active) {
                val progress = note?.progress ?: 0f
                Canvas(Modifier.size(diameter)) {
                    val w = 3.5.dp.toPx()
                    val topLeft = Offset(w / 2, w / 2)
                    val arcSize = Size(size.width - w, size.height - w)
                    drawArc(Color.White.copy(alpha = 0.3f), 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(w))
                    drawArc(Color.White, -90f, 360f * progress, false, topLeft = topLeft, size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            when {
                video != null && note == null -> {
                    // Downloading
                    val p = repo.fileProgress(video)
                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(38.dp)) {
                            drawArc(Color.White, -90f, 360f * p.coerceAtLeast(0.05f), false, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Icon(IosIcons.ArrowDown, Color.White, 18.dp)
                    }
                }
                active && note?.playWhenReady == false -> {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Icon(IosIcons.Play, Color.White, 28.dp)
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 6.dp, bottom = 4.dp)
                    .clip(Capsule())
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val secs = if (active) ((note?.positionMs ?: 0L) / 1000).toInt() else v.seconds
                T(formatDuration(secs), TgTheme.type.caption2, Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                if (!active) {
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.AutoMirrored.Rounded.VolumeOff, Color.White, 12.dp)
                }
                if (!active && !m.outgoing && !v.viewed) {
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 4.dp)) { MetaRow(m, Color.White, overlay = true) }
        }
    }
}

/** A muted, looping player for one video message; paused while the app is in the background, released with the item. */
@Composable
private fun rememberNotePlayer(path: String, key: String): NoteState {
    val context = LocalContext.current
    val note = remember(path) {
        NoteState(
            ExoPlayer.Builder(context.applicationContext).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                prepare()
                playWhenReady = true
            }
        )
    }
    val player = note.player
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                note.playWhenReady = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Played to the end with sound: back to the silent loop.
                if (playbackState == Player.STATE_ENDED && VideoNotePlayback.activeKey == key) VideoNotePlayback.activeKey = null
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player.pause()
            } else if (event == Lifecycle.Event.ON_RESUME && VideoNotePlayback.activeKey != key) {
                player.play()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    return note
}

/** The video picture, center-cropped into the (square) bounds whatever the video's aspect. */
@Composable
private fun NoteSurface(player: ExoPlayer, modifier: Modifier) {
    var aspect by remember(player) { mutableFloatStateOf(1f) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // Keyed by the player: a new file gets a new view attached to its player.
    key(player) {
        AndroidView(
            factory = { TextureView(it).also { tv -> player.setVideoTextureView(tv) } },
            modifier = modifier.graphicsLayer {
                // The TextureView stretches the picture to its bounds; scale the long side back out (clipped by the circle).
                scaleX = if (aspect > 1f) aspect else 1f
                scaleY = if (aspect < 1f) 1f / aspect else 1f
            },
            onRelease = { tv -> player.clearVideoTextureView(tv) },
        )
    }
}

/**
 * Full-screen camera overlay while a video message is being recorded (Telegram-iOS): the chat dims and blurs, and the
 * front camera shows in a big circle that springs in, with a ring filling up to the 60 s limit and the elapsed time
 * under it. The composer stays on top of it.
 */
@Composable
fun VideoNoteRecordingOverlay(recorder: VideoNoteRecorder, modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current
    val level = LocalAppSettings.current.glassLevel
    AnimatedVisibility(recorder.active, modifier = modifier, enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
        val blurred = backdrop != null && (level == GlassLevel.Full || level == GlassLevel.Medium)
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (blurred && backdrop != null) Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(0.dp) },
                        effects = { blur(28.dp.toPx()) },
                        onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.3f)) },
                    ) else Modifier.background(Color.Black.copy(alpha = 0.6f))
                )
                // Swallow touches on the chat behind.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center,
        ) {
            val context = LocalContext.current
            val view = LocalView.current
            val lifecycle = LocalLifecycleOwner.current
            // TextureView-backed (COMPATIBLE) so the circle clip applies to the camera picture; PreviewView mirrors
            // the front camera like a mirror, and the recording is mirrored the same way.
            val previewView = remember {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            }
            val active = recorder.active
            LaunchedEffect(previewView, active) {
                if (active) recorder.bind(lifecycle, previewView.surfaceProvider, view.display?.rotation ?: Surface.ROTATION_0)
            }
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(Unit) {
                while (true) withFrameMillis { now = SystemClock.elapsedRealtime() }
            }
            val started = recorder.startedAt
            val elapsed = if (started > 0) now - started else 0L
            val progress = (elapsed.toFloat() / VideoNoteRecorder.MaxMs).coerceIn(0f, 1f)
            val appear = remember { Animatable(0.6f) }
            LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 320f)) }
            Column(Modifier.padding(bottom = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .graphicsLayer { scaleX = appear.value; scaleY = appear.value }
                        .fillMaxWidth(0.82f)
                        .widthIn(max = 380.dp)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize().padding(7.dp).clip(CircleShape).background(Color.Black),
                    )
                    if (started == 0L) ActivityIndicator(28.dp, Color.White)
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 3.5.dp.toPx()
                        val topLeft = Offset(w / 2, w / 2)
                        val arcSize = Size(size.width - w, size.height - w)
                        drawArc(Color.White.copy(alpha = 0.22f), 0f, 360f, false, topLeft = topLeft, size = arcSize, style = Stroke(w))
                        drawArc(Color.White, -90f, 360f * progress, false, topLeft = topLeft, size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    }
                }
                Spacer(Modifier.height(14.dp))
                val secs = (elapsed / 1000).toInt()
                Row(
                    Modifier.clip(Capsule()).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                    Spacer(Modifier.width(6.dp))
                    T(formatDuration(secs) + " / " + formatDuration(VideoNoteRecorder.MaxSeconds), TgTheme.type.footnote, Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
