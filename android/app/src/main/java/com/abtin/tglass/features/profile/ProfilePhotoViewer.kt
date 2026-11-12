package com.abtin.tglass.features.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.ProfilePhotoItem
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.formatDay
import com.abtin.tglass.ui.components.initials
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen profile photo viewer: swipe between the photos, tap to hide the controls, swipe down to close.
 * With no photos it shows the big initials avatar (the demo, or people without a photo).
 */
@Composable
internal fun ProfilePhotoViewer(
    name: String,
    seed: Long,
    photos: List<ProfilePhotoItem>,
    startIndex: Int,
    onIndex: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val count = photos.size.coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, count - 1)) { count }
    val dismiss = remember { Animatable(0f) }
    var chrome by remember { mutableStateOf(true) }
    BackHandler { onDismiss() }
    androidx.compose.runtime.LaunchedEffect(pager.currentPage) { onIndex(pager.currentPage) }
    val bg = (1f - abs(dismiss.value) / 1400f).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bg))
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = dismiss.value == 0f,
            key = { page -> photos.getOrNull(page)?.id ?: page.toLong() },
        ) { page ->
            val item = photos.getOrNull(page)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { chrome = !chrome } }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (abs(dismiss.value) > 220f) onDismiss() else scope.launch { dismiss.animateTo(0f) }
                            },
                            onDragCancel = { scope.launch { dismiss.animateTo(0f) } },
                        ) { change, dy ->
                            change.consume()
                            scope.launch { dismiss.snapTo(dismiss.value + dy) }
                        }
                    }
                    .graphicsLayer {
                        translationY = dismiss.value
                        val s = 1f - (abs(dismiss.value) / 3000f).coerceAtMost(0.15f)
                        scaleX = s; scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                val (a, b) = avatarColors(seed)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).background(Brush.verticalGradient(listOf(a, b))),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item?.thumb == null && item?.image == null) {
                        T(initials(name), TgTheme.type.body.copy(fontSize = 120.sp, lineHeight = 130.sp), Color.White, weight = FontWeight.SemiBold)
                    }
                    item?.thumb?.let { TgImage(it, Modifier.fillMaxSize(), maxPx = 320) }
                    item?.image?.let { TgImage(it, Modifier.fillMaxSize(), maxPx = 2048, contentScale = ContentScale.Fit) }
                }
            }
        }
        if (chrome && dismiss.value == 0f) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)) {
                Box(
                    Modifier.size(44.dp).clip(Capsule()).background(Color.White.copy(alpha = 0.16f))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(IosIcons.Close, Color.White, 22.dp) }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    com.abtin.tglass.core.emoji.EmojiText(name, TgTheme.type.headline, Color.White, maxLines = 1)
                    if (count > 1) T("${pager.currentPage + 1} of $count", TgTheme.type.footnote, Color.White.copy(alpha = 0.7f))
                }
            }
            val date = photos.getOrNull(pager.currentPage)?.date ?: 0L
            if (date > 0L) {
                Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("Set on ${formatDay(date * 1000L)}", TgTheme.type.footnote, Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
