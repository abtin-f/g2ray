package com.abtin.tglass.core.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.abtin.tglass.core.design.GlassLevel
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangularShape

/**
 * The backdrop that glass surfaces on the current screen refract/blur.
 * A screen marks its scrolling content with `Modifier.layerBackdrop(backdrop)` and provides it here.
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Liquid Glass surface (spec §5). Honors Settings → Power Saving → Interface Effects:
 * FULL = blur + vibrancy + lens refraction, MEDIUM = blur + vibrancy, LOW = translucent, OFF = opaque.
 */
@Composable
fun Modifier.glass(
    shape: RoundedRectangularShape = Capsule(),
    backdrop: Backdrop? = LocalBackdrop.current,
    surface: Color = TgTheme.colors.glassSurface,
    blurRadius: Dp = 8.dp,
    /**
     * Lens refraction. Left unspecified, refraction is only applied to small controls (buttons, small pills): the
     * AGSL lens shader runs on every pixel of the element on every frame the backdrop changes, so large surfaces
     * (composer, search fields, bars, cards) get blur + vibrancy only. Pass explicit values to force refraction
     * (menus, alerts, banners: hero surfaces that sit over a static backdrop).
     */
    lensHeight: Dp = Dp.Unspecified,
    lensAmount: Dp = Dp.Unspecified,
    shadow: Boolean = true,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val level = LocalAppSettings.current.glassLevel
    val colors = TgTheme.colors
    if (backdrop == null || level == GlassLevel.Low || level == GlassLevel.Off) {
        val fill = if (level == GlassLevel.Off || backdrop == null) colors.glassOpaque
        else colors.glassOpaque.copy(alpha = 0.88f)
        return this
            .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
            .then(if (shadow) Modifier.shadow(6.dp, shape, clip = false, ambientColor = Color.Black.copy(0.08f), spotColor = Color.Black.copy(0.12f)) else Modifier)
            .clip(shape)
            .background(fill)
    }
    val full = level == GlassLevel.Full
    val forceLens = lensHeight.isSpecified
    val lensH = if (lensHeight.isSpecified) lensHeight else 16.dp
    val lensA = if (lensAmount.isSpecified) lensAmount else 24.dp
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((if (full) blurRadius else blurRadius * 1.5f).toPx())
            // `size` is in px; the lens is only worth its per-pixel cost on small controls (<= ~8000 dp^2).
            if (full && (forceLens || size.width * size.height <= SmallGlassAreaDp2 * density * density)) {
                lens(lensH.toPx(), lensA.toPx())
            }
        },
        highlight = { Highlight.Default },
        shadow = if (shadow) ({ Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.08f)) }) else null,
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(surface) },
    )
}

/** Largest element area (dp^2) that still gets lens refraction when the caller did not ask for it explicitly. */
private const val SmallGlassAreaDp2 = 8000f

/** Circular liquid-glass button with the press "gel" highlight used across Telegram iOS 26. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    tint: Color = TgTheme.colors.text,
    contentDescription: String? = null,
) {
    GlassBox(onClick = onClick, modifier = modifier.size(size), shape = Capsule()) {
        Image(
            imageVector = icon,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Circular glass button with a drawable icon (e.g. [com.abtin.tglass.ui.components.TgIcons]). */
@Composable
fun GlassIconButton(
    @androidx.annotation.DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 26.dp,
    tint: Color = TgTheme.colors.text,
    contentDescription: String? = null,
) {
    GlassBox(onClick = onClick, modifier = modifier.size(size), shape = Capsule()) {
        Image(
            painter = androidx.compose.ui.res.painterResource(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Generic interactive glass container. */
@Composable
fun GlassBox(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    shape: RoundedRectangularShape = Capsule(),
    surface: Color = TgTheme.colors.glassSurface,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(animationScope = scope) }
    val interactive = onClick != null
    Box(
        modifier
            .glass(
                shape = shape,
                surface = surface,
                layerBlock = if (interactive) {
                    {
                        // Telegram-iOS TouchEffect: pressedSizeIncrease = 20pt on each axis.
                        val grow = 20.dp.toPx() * highlight.pressProgress
                        scaleX = 1f + grow / size.width.coerceAtLeast(1f)
                        scaleY = 1f + grow / size.height.coerceAtLeast(1f)
                    }
                } else null,
            )
            .then(
                if (interactive) Modifier
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick!!)
                    .then(highlight.modifier)
                    .then(highlight.gestureModifier)
                else Modifier
            ),
        contentAlignment = contentAlignment,
        content = content,
    )
}
