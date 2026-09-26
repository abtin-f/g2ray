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
    val m = repo.messages(chatId).firstOrNull { it.id == messageId } ?: return
    val photo = m.content as? MessageContent.Photo ?: return
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val dismiss = remember { Animatable(0f) }
    var chrome by remember { mutableStateOf(true) }
    val (a, b) = avatarColors(photo.seed.toLong())
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
                    .background(Brush.linearGradient(listOf(a, b))),
                contentAlignment = Alignment.Center,
            ) { T(photo.emoji, TgTheme.type.body.copy(fontSize = 120.sp, lineHeight = 140.sp)) }
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
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    GlassIconButton(TgIcons.IcNavShare, { toast.show("Shared") })
                }
            }
        }
    }
    }
}
