package com.abtin.tglass.features.media

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.abtin.tglass.core.media.togglePlay
import com.abtin.tglass.ui.components.formatDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.runtime.CompositionLocalProvider
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.senderName
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.formatDay
import com.abtin.tglass.ui.components.formatTime
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Spec §26: full-screen media viewer with pinch/double-tap zoom and swipe-down dismiss. */
@Composable
fun MediaViewer(chatId: Long, messageId: Long) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val m = repo.findMessage(chatId, messageId) ?: return
    val photo = m.content as? MessageContent.Photo ?: return
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val dismiss = remember { Animatable(0f) }
    var chrome by remember { mutableStateOf(true) }
    val (a, b) = avatarColors(photo.seed.toLong())
    // Videos: download (with progress) and then play with ExoPlayer.
    val videoFile = photo.videoFile?.takeIf { photo.video }
    val videoPath = videoFile?.let { repo.filePath(it) }
    LaunchedEffect(videoFile, videoPath) { if (videoFile != null && videoPath == null) repo.requestImage(videoFile) }
    val player = videoPath?.let { com.abtin.tglass.core.media.rememberVideoPlayer(it, loop = photo.loop, muted = photo.loop) }
    val video = player?.let { com.abtin.tglass.core.media.rememberVideoState(it) }
    val bgAlpha = (1f - abs(dismiss.value) / 1200f).coerceIn(0f, 1f)

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgAlpha))) {
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { chrome = !chrome },
                        onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
                .pointerInput(scale) {
                    if (scale <= 1f) detectVerticalDragGestures(
                        onDragEnd = {
                            if (abs(dismiss.value) > 250f) nav.pop() else scope.launch { dismiss.animateTo(0f) }
                        },
                    ) { _, dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(photo.aspect.coerceIn(0.5f, 2f))
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x
                        translationY = offset.y + dismiss.value
                    }
                    .then(if (photo.image == null) Modifier.background(Brush.linearGradient(listOf(a, b))) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (photo.image != null) com.abtin.tglass.ui.components.TgImage(photo.image, Modifier.matchParentSize(), maxPx = 2048, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                else if (videoFile == null) T(photo.emoji, TgTheme.type.body.copy(fontSize = 120.sp, lineHeight = 140.sp))
                if (player != null) {
                    com.abtin.tglass.core.media.VideoSurface(player, Modifier.matchParentSize())
                    if (video?.playing == false) {
                        GlassIconButton(IosIcons.Play, { player.play() }, size = 64.dp, iconSize = 30.dp, tint = Color.White)
                    }
                } else if (videoFile != null) {
                    // Downloading: ring with the percentage, like Telegram's media overlay.
                    val progress = repo.fileProgress(videoFile)
                    Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(Modifier.size(52.dp)) {
                            drawArc(
                                Color.White, -90f, 360f * progress.coerceAtLeast(0.04f), false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                            )
                        }
                        T("${(progress * 100).toInt()}%", TgTheme.type.caption1, Color.White, weight = FontWeight.SemiBold)
                    }
                }
            }
        }
        if (chrome) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(IosIcons.Close, { nav.pop() }, iconSize = 20.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(repo.senderName(m), TgTheme.type.headline, Color.White, weight = FontWeight.SemiBold)
                    T("${formatDay(m.date)} at ${formatTime(m.date)}", TgTheme.type.footnote, Color.White.copy(0.7f))
                }
                GlassIconButton(TgIcons.PiMore, { toast.show("Saved to gallery") })
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                photo.caption?.let { T(it, TgTheme.type.body, Color.White) }
                if (player != null && video != null && !photo.loop) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(if (video.playing) IosIcons.Pause else IosIcons.Play, { player.togglePlay() }, size = 40.dp, iconSize = 20.dp, tint = Color.White)
                        Spacer(Modifier.width(10.dp))
                        T(formatDuration((video.positionMs / 1000).toInt()), TgTheme.type.caption1, Color.White)
                        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            com.abtin.tglass.ui.components.IOSSlider(
                                value = if (video.durationMs > 0) video.positionMs.toFloat() / video.durationMs else 0f,
                                onValueChange = { f -> if (video.durationMs > 0) player.seekTo((f * video.durationMs).toLong()) },
                                range = 0f..1f,
                            )
                        }
                        T(formatDuration((video.durationMs / 1000).toInt()), TgTheme.type.caption1, Color.White.copy(0.7f))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    GlassIconButton(TgIcons.IcNavShare, { toast.show("Shared") })
                }
            }
        }
    }
    }
}
