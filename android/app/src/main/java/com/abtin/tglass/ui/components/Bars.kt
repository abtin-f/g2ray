package com.abtin.tglass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.GlassLevel
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.Capsule

/**
 * iOS 26 "scroll edge effect": content under the bars is blurred progressively, strongest at the screen
 * edge and fading to sharp. Falls back to a color fade when glass effects are reduced.
 */
@Composable
fun ScrollEdgeBlur(height: Dp, modifier: Modifier = Modifier, top: Boolean = true, tint: Color = TgTheme.colors.background) {
    val backdrop = LocalBackdrop.current
    val level = LocalAppSettings.current.glassLevel
    val fade = if (top) Brush.verticalGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.6f), Color.Transparent))
    else Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f), Color.Black))
    val tintFade = if (top) Brush.verticalGradient(listOf(tint.copy(alpha = 0.75f), tint.copy(alpha = 0.35f), tint.copy(alpha = 0f)))
    else Brush.verticalGradient(listOf(tint.copy(alpha = 0f), tint.copy(alpha = 0.35f), tint.copy(alpha = 0.75f)))
    if (backdrop == null || level == GlassLevel.Off || level == GlassLevel.Low) {
        Box(modifier.fillMaxWidth().height(height).background(
            if (top) Brush.verticalGradient(listOf(tint, tint.copy(alpha = 0.9f), tint.copy(alpha = 0f)))
            else Brush.verticalGradient(listOf(tint.copy(alpha = 0f), tint.copy(alpha = 0.9f), tint))
        ))
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(14.dp.toPx()) },
                onDrawBackdrop = { draw ->
                    draw()
                    drawRect(tintFade)
                    drawRect(fade, blendMode = BlendMode.DstIn)
                },
            )
    )
}

/**
 * iOS 26 navigation bar: floating glass controls over a progressive blur instead of an opaque bar (spec §11).
 */
@Composable
fun GlassTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    fade: Color = TgTheme.colors.background,
    showBack: Boolean = true,
    onBack: (() -> Unit)? = null,
    left: (@Composable RowScope.() -> Unit)? = null,
    right: (@Composable RowScope.() -> Unit)? = null,
    center: (@Composable BoxScope.() -> Unit)? = null,
) {
    val nav = LocalNavigator.current
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxWidth()) {
        ScrollEdgeBlur(top + 76.dp, tint = fade)
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp)
                .height(52.dp)
                .padding(horizontal = 12.dp)
        ) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                if (left != null) left()
                else if (showBack) BackButton(onBack ?: { nav.pop() })
            }
            Box(Modifier.align(Alignment.Center).padding(horizontal = 72.dp)) {
                if (center != null) center()
                else if (title != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T(title, TgTheme.type.headline, TgTheme.colors.text, maxLines = 1, align = TextAlign.Center)
                    if (subtitle != null) T(subtitle, TgTheme.type.footnote, TgTheme.colors.secondaryText, maxLines = 1)
                }
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                if (right != null) right()
            }
        }
    }
}

/** Glass circle with the iOS chevron. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier, badge: Int = 0) {
    if (badge > 0) {
        GlassBox(onClick = onClick, modifier = modifier.height(44.dp), shape = Capsule()) {
            Row(Modifier.padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(IosIcons.ChevronLeft, TgTheme.colors.text, 24.dp)
                Spacer(Modifier.width(2.dp))
                T(formatCount(badge), TgTheme.type.body, TgTheme.colors.text, weight = FontWeight.Medium, maxLines = 1)
            }
        }
    } else {
        GlassIconButton(IosIcons.ChevronLeft, onClick, modifier, iconSize = 24.dp, tint = TgTheme.colors.text)
    }
}

/** Text pill in glass ("Edit", "Done"). */
@Composable
fun GlassTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = TgTheme.colors.text, bold: Boolean = false) {
    GlassBox(onClick = onClick, modifier = modifier.height(44.dp), shape = Capsule()) {
        T(
            text, TgTheme.type.body, color,
            weight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp), maxLines = 1,
        )
    }
}

/** Several glass icon buttons fused into one capsule (iOS 26 toolbar group). */
@Composable
fun GlassButtonGroup(vararg buttons: Pair<Any, () -> Unit>, modifier: Modifier = Modifier) {
    GlassBox(onClick = null, modifier = modifier.height(44.dp), shape = Capsule()) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            buttons.forEach { (icon, onClick) ->
                Box(
                    Modifier
                        .height(44.dp)
                        .width(44.dp)
                        .fadeClickable(onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    when (icon) {
                        is Int -> Icon(icon, TgTheme.colors.text, 24.dp)
                        is androidx.compose.ui.graphics.vector.ImageVector -> Icon(icon, TgTheme.colors.text, 24.dp)
                    }
                }
            }
        }
    }
}
