package com.abtin.tglass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.kyant.shapes.RoundedRectangle

/** Scroll progress (0 = header fully visible, 1 = collapsed) for a list whose first item is a [ProfileHero]. */
@Composable
fun rememberHeroCollapse(state: LazyListState, heroHeightDp: Float = 250f): Float {
    val px = with(LocalDensity.current) { heroHeightDp.dp.toPx() }
    val p by remember(state, px) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / px).coerceIn(0f, 1f)
        }
    }
    return p
}

/**
 * iOS-style profile header: peer-colored gradient with a soft radial glow, a large avatar,
 * name and status. The avatar shrinks and fades as [collapse] grows (the title moves into the nav bar).
 */
@Composable
fun ProfileHero(
    name: String,
    seed: Long,
    subtitle: String?,
    collapse: Float,
    modifier: Modifier = Modifier,
    saved: Boolean = false,
    subtitleAccent: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    photoPeer: Long = seed,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = TgTheme.colors
    val (top, bottom) = avatarColors(seed)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // Peer-colored backdrop that melts into the page background.
                drawRect(Brush.verticalGradient(listOf(bottom.copy(alpha = if (c.isDark) 0.55f else 0.42f), top.copy(alpha = 0.18f), c.groupedBackground)))
                drawCircle(
                    Brush.radialGradient(listOf(top.copy(alpha = 0.55f), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.34f), radius = size.width * 0.55f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width / 2f, size.height * 0.34f),
                )
            }
            .padding(top = statusTop + 56.dp, bottom = 18.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.graphicsLayer {
                    val s = 1f - 0.45f * collapse
                    scaleX = s; scaleY = s
                    alpha = 1f - collapse * 1.2f
                    translationY = collapse * 40.dp.toPx()
                }
            ) {
                Box(Modifier.shadow(24.dp, CircleShape, clip = false, ambientColor = bottom, spotColor = bottom)) {
                    Avatar(name, seed, 112.dp, saved = saved, photoPeer = photoPeer)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.graphicsLayer { alpha = 1f - collapse * 1.4f },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(name, TgTheme.type.title2.copy(fontSize = 26.sp, lineHeight = 30.sp), c.text, weight = FontWeight.SemiBold, maxLines = 1, align = TextAlign.Center)
                if (badge != null) {
                    Spacer(Modifier.width(6.dp))
                    badge()
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                T(subtitle, TgTheme.type.subheadline, if (subtitleAccent) c.accent else c.secondaryText, maxLines = 1,
                    modifier = Modifier.graphicsLayer { alpha = 1f - collapse * 1.4f })
            }
            if (actions != null) {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            }
        }
    }
}

/** Action tile under a [ProfileHero] (Message / Mute / Call / Video / More). */
@Composable
fun RowScope.HeroAction(icon: Int, label: String, onClick: () -> Unit) {
    val c = TgTheme.colors
    Column(
        Modifier
            .weight(1f)
            .height(62.dp)
            .clip(RoundedRectangle(18.dp))
            .background(if (c.isDark) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.72f))
            .bounceClickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, c.accent, 26.dp)
        Spacer(Modifier.height(2.dp))
        T(label, TgTheme.type.caption1, c.accent, maxLines = 1, weight = FontWeight.Medium)
    }
}

/** Compact title shown in the nav bar once the hero has collapsed. */
@Composable
fun CollapsedTitle(name: String, seed: Long, collapse: Float, saved: Boolean = false, photoPeer: Long = seed) {
    val a = ((collapse - 0.55f) / 0.45f).coerceIn(0f, 1f)
    Row(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 12.dp.toPx() }, verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, seed, 26.dp, saved = saved, photoPeer = photoPeer)
        Spacer(Modifier.width(8.dp))
        com.abtin.tglass.core.emoji.EmojiText(name, TgTheme.type.headline, TgTheme.colors.text, maxLines = 1)
    }
}

@Suppress("unused")
private val keepSize = Modifier.size(0.dp)
