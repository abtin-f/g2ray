package com.abtin.tglass.core.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.abtin.tglass.core.design.GlassLevel
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangularShape

/**
 * The backdrop that glass surfaces on the current screen refract/blur.
 * A screen marks its scrolling content with `Modifier.layerBackdrop(backdrop)` and provides it here.
 */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * Material of a glass surface, chosen per surface like Telegram iOS 26:
 *  - [Liquid]: floating controls (header pills, avatar button, button groups, tab bar, composer buttons,
 *    scroll-to-bottom, floating chips, context menu / reaction bar, alerts): blur + vibrancy + lens refraction on the
 *    edges + specular rim + faint inner shadow.
 *  - [Blur]: larger panels over content (composer field, pinned / translate / now-playing bars, search fields, action
 *    sheets and menu bodies, banners, emoji panel): frosted blur + vibrancy + rim, no lens.
 *  - [Translucent]: no backdrop sampling, a translucent fill (folder-tab strip, things inside recorded list layers).
 * Settings → Power Saving → Interface Effects maps on top: Medium turns Liquid into Blur, Low makes everything
 * Translucent, Off makes everything opaque.
 */
enum class GlassStyle { Liquid, Blur, Translucent }

/**
 * Glass surface (spec §5) of the given [style]: the backdrop blurred and saturated, the tinted veil
 * ([TgColors.glassSurface]), a rim highlight and a soft drop shadow; Liquid adds lens refraction and an inner shadow.
 *
 * Without a [backdrop] (the element lives inside content that is itself recorded for glass) Liquid / Blur fall back
 * to a frosted fill + rim, so the surface still reads as glass; screens should provide a backdrop wherever iOS shows
 * real glass. The lens band scales with the element ([lensHeight] / [lensAmount] override it); the shader returns
 * early inside the band, so its cost is mostly the rim.
 */
@Composable
fun Modifier.glass(
    shape: RoundedRectangularShape = Capsule(),
    style: GlassStyle = GlassStyle.Liquid,
    backdrop: Backdrop? = LocalBackdrop.current,
    surface: Color = TgTheme.colors.glassSurface,
    blurRadius: Dp = 8.dp,
    lensHeight: Dp = Dp.Unspecified,
    lensAmount: Dp = Dp.Unspecified,
    shadow: Boolean = true,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val level = LocalAppSettings.current.glassLevel
    val colors = TgTheme.colors
    val effective = when (level) {
        GlassLevel.Full -> style
        GlassLevel.Medium -> if (style == GlassStyle.Liquid) GlassStyle.Blur else style
        GlassLevel.Low, GlassLevel.Off -> GlassStyle.Translucent
    }
    if (backdrop == null || effective == GlassStyle.Translucent) {
        val fill = when {
            level == GlassLevel.Off -> colors.glassOpaque
            effective == GlassStyle.Translucent -> colors.glassOpaque.copy(alpha = 0.86f)
            // Wanted blur but there is nothing to sample: frosted (nearly opaque) instead of see-through.
            else -> colors.glassOpaque.copy(alpha = 0.94f)
        }
        val rim = level != GlassLevel.Off
        val dark = colors.isDark
        return this
            .then(if (layerBlock != null) Modifier.graphicsLayer(layerBlock) else Modifier)
            .then(if (shadow) Modifier.shadow(6.dp, shape, clip = false, ambientColor = Color.Black.copy(0.08f), spotColor = Color.Black.copy(0.12f)) else Modifier)
            .clip(shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                // Rim: bright on the top-left and bottom-right edges, like the Highlight shader of the real glass.
                val a = if (dark) 0.26f else 0.7f
                val rimBrush = Brush.linearGradient(
                    0f to Color.White.copy(alpha = a),
                    0.35f to Color.White.copy(alpha = a * 0.25f),
                    0.65f to Color.White.copy(alpha = a * 0.25f),
                    1f to Color.White.copy(alpha = a * 0.7f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val stroke = Stroke(width = 1.5.dp.toPx())
                onDrawWithContent {
                    drawOutline(outline, fill)
                    drawContent()
                    if (rim) drawOutline(outline, rimBrush, style = stroke)
                }
            }
    }
    val liquid = effective == GlassStyle.Liquid
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((if (level == GlassLevel.Full) blurRadius else blurRadius * 1.5f).toPx())
            if (liquid) {
                // Lens along the edges: the band is a third of the smaller side (max 16 pt), the bend up to 24 pt,
                // so small buttons bulge like iOS 26 controls and bigger floating shapes only bend their rims.
                val minSide = size.minDimension
                val h = if (lensHeight.isSpecified) lensHeight.toPx() else (minSide * 0.33f).coerceAtMost(16.dp.toPx())
                val amt = if (lensAmount.isSpecified) lensAmount.toPx() else (h * 2f).coerceAtMost(24.dp.toPx())
                if (h > 0f && amt > 0f) lens(h, amt)
            }
        },
        highlight = if (liquid) ({ GlassRim }) else ({ BlurRim }),
        shadow = if (shadow) ({ GlassShadow }) else null,
        innerShadow = if (liquid) ({ GlassInnerShadow }) else null,
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(surface) },
    )
}

/** Specular rim of liquid glass (Kyant's default highlight: 0.5 pt, white, strongest at 45°). */
private val GlassRim = Highlight.Default

/** Softer rim of the frosted (blur-only) panels. */
private val BlurRim = Highlight.Default.copy(alpha = 0.6f)

/** Soft drop shadow under floating glass. */
private val GlassShadow = Shadow(radius = 16.dp, color = Color.Black.copy(alpha = 0.08f))

/** Very faint inner shadow that gives the liquid slab its thickness. */
private val GlassInnerShadow = InnerShadow(radius = 4.dp, offset = DpOffset(0.dp, 1.dp), color = Color.Black.copy(alpha = 0.12f))

/** Circular liquid-glass button with the press "gel" highlight used across Telegram iOS 26. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
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
    size: Dp = 40.dp,
    iconSize: Dp = 24.dp,
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
    style: GlassStyle = GlassStyle.Liquid,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(animationScope = scope) }
    val interactive = onClick != null
    Box(
        modifier
            .glass(
                shape = shape,
                style = style,
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

/**
 * A screen whose [content] is recorded as the glass backdrop of the [chrome] drawn over it (top bars, buttons), for
 * screens that have no scrolling list of their own to record. Glass inside [content] gets no backdrop (never glass
 * inside the layer it samples: that recurses in the RenderThread), so it falls back to the frosted look.
 */
@Composable
fun BackdropScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    chrome: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier) {
        CompositionLocalProvider(LocalBackdrop provides null) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop), content = content)
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) { chrome() }
    }
}
