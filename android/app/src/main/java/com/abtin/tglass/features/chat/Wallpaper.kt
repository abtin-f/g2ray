package com.abtin.tglass.features.chat

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.design.UserBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Port of Telegram-iOS `SoftwareGradientBackground.swift`: 4 colors placed on 8 base positions,
 * a slight swirl, and cubic inverse-distance blending. Rendered into a tiny bitmap and scaled up,
 * exactly like Telegram does. Every sent message advances [phase] by one step (animated).
 */
private val BasePositions = listOf(
    Offset(0.80f, 0.10f), Offset(0.60f, 0.20f), Offset(0.35f, 0.25f), Offset(0.25f, 0.60f),
    Offset(0.20f, 0.90f), Offset(0.40f, 0.80f), Offset(0.65f, 0.75f), Offset(0.75f, 0.40f),
)

private fun positions(phase: Int): List<Offset> {
    val p = ((phase % 8) + 8) % 8
    val shifted = BasePositions.drop(p) + BasePositions.take(p)
    return List(4) { shifted[it * 2] }
}

private const val GW = 48
private const val GH = 84

private fun renderGradient(pixels: IntArray, colors: List<Color>, pos: List<Offset>) {
    val rgb = colors.map { floatArrayOf(it.red, it.green, it.blue) }
    for (y in 0 until GH) {
        val dy = y.toFloat() / GH - 0.5f
        val dy2 = dy * dy
        for (x in 0 until GW) {
            val dx = x.toFloat() / GW - 0.5f
            val center = sqrt(dx * dx + dy2)
            val swirl = 0.35f * center
            val theta = swirl * swirl * 0.8f * 8f
            val s = sin(theta)
            val c = cos(theta)
            val px = max(0f, min(1f, 0.5f + dx * c - dy * s))
            val py = max(0f, min(1f, 0.5f + dx * s + dy * c))
            var sum = 0f
            var r = 0f
            var g = 0f
            var b = 0f
            for (i in pos.indices) {
                val ddx = px - pos[i].x
                val ddy = py - pos[i].y
                var d = max(0f, 0.92f - sqrt(ddx * ddx + ddy * ddy))
                d = d * d * d
                sum += d
                r += d * rgb[i][0]; g += d * rgb[i][1]; b += d * rgb[i][2]
            }
            if (sum < 0.00001f) sum = 0.00001f
            val ri = min(255f, r / sum * 255f).toInt()
            val gi = min(255f, g / sum * 255f).toInt()
            val bi = min(255f, b / sum * 255f).toInt()
            pixels[y * GW + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
    }
}


private class Doodle(val x: Float, val y: Float, val kind: Int, val size: Float, val angle: Float)

private val Doodles: List<Doodle> by lazy {
    val r = Random(7)
    List(110) { Doodle(r.nextFloat(), r.nextFloat(), r.nextInt(6), 0.55f + r.nextFloat() * 0.55f, r.nextFloat() * 360f) }
}

/** The wallpaper of the current theme: the user's own background for this day/night side, else the animated default. */
@Composable
fun ChatWallpaper(modifier: Modifier = Modifier, phase: Int = 0) {
    val dark = TgTheme.colors.isDark
    val bg = LocalAppSettings.current.backgroundFor(dark)
    WallpaperBackground(bg, modifier, phase)
}

/** Draws [bg]: photo, color/gradient, or (when neither) the theme's animated gradient with doodles. */
@Composable
fun WallpaperBackground(bg: UserBackground, modifier: Modifier = Modifier, phase: Int = 0) {
    val photo = bg.photo
    when {
        photo != null -> PhotoWallpaper(photo, bg.blur, bg.dim, modifier)
        bg.colors.isNotEmpty() -> ColorWallpaper(bg.colors, bg.rotation, bg.dim, modifier)
        else -> DefaultWallpaper(modifier, phase)
    }
}

@Composable
private fun PhotoWallpaper(photo: String, blur: Boolean, dim: Float, modifier: Modifier) {
    val context = LocalContext.current
    val fallback = TgTheme.colors.wallpaper.first()
    val image by produceState<ImageBitmap?>(null, photo, blur) { value = WallpaperStore.load(context, photo, blur) }
    Box(
        modifier.fillMaxSize().drawBehind {
            val img = image
            if (img == null) {
                drawRect(fallback)
            } else {
                // ContentScale.Crop by hand: one drawImage of the centered source window.
                val s = max(size.width / img.width, size.height / img.height)
                val sw = (size.width / s).roundToInt().coerceIn(1, img.width)
                val sh = (size.height / s).roundToInt().coerceIn(1, img.height)
                drawImage(
                    img,
                    srcOffset = IntOffset((img.width - sw) / 2, (img.height - sh) / 2),
                    srcSize = IntSize(sw, sh),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
        },
    )
}

@Composable
private fun ColorWallpaper(colors: List<Int>, rotation: Int, dim: Float, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().drawWithCache {
            val list = colors.map { Color(it) }
            val brush: Brush = if (list.size < 2 || list[0] == list[1]) SolidColor(list[0]) else {
                // Rotation 0 = top to bottom, then clockwise.
                val rad = Math.toRadians((rotation + 90).toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                val half = (abs(dx) * size.width + abs(dy) * size.height) / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                Brush.linearGradient(list, Offset(cx - dx * half, cy - dy * half), Offset(cx + dx * half, cy + dy * half))
            }
            onDrawBehind {
                drawRect(brush)
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
        },
    )
}

/**
 * The default Telegram gradient + doodles. The static doodles are rendered once per size into a bitmap; the gradient
 * (a 48x84 bitmap) is regenerated on a background thread while it animates. A frame is two drawImage calls.
 */
@Composable
private fun DefaultWallpaper(modifier: Modifier, phase: Int) {
    val c = TgTheme.colors
    val animations = LocalAppSettings.current.animations
    val anim = remember { Animatable(phase.toFloat()) }
    LaunchedEffect(phase) {
        if (animations) anim.animateTo(phase.toFloat(), tween(500, easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)))
        else anim.snapTo(phase.toFloat())
    }
    val colors = c.wallpaper
    var gradient by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(colors) {
        snapshotFlow { anim.value }.conflate().collect { f ->
            gradient = withContext(Dispatchers.Default) { renderGradientBitmap(colors, f) }
        }
    }
    val patternColor = c.wallpaperPattern
    val fallback = colors.first()
    Box(
        modifier.fillMaxSize().drawWithCache {
            val w = size.width.roundToInt()
            val h = size.height.roundToInt()
            val pattern = if (patternColor.alpha > 0f && w > 0 && h > 0) renderPattern(this, layoutDirection, w, h, patternColor) else null
            onDrawBehind {
                val g = gradient
                if (g != null) drawImage(g, srcSize = IntSize(GW, GH), dstSize = IntSize(w, h), filterQuality = FilterQuality.High)
                else drawRect(fallback)
                if (pattern != null) drawImage(pattern)
            }
        },
    )
}

private fun renderGradientBitmap(colors: List<Color>, f: Float): ImageBitmap {
    val a = floor(f).toInt()
    val t = f - a
    val p0 = positions(a)
    val p1 = positions(a + 1)
    val pos = List(4) { i -> Offset(p0[i].x + (p1[i].x - p0[i].x) * t, p0[i].y + (p1[i].y - p0[i].y) * t) }
    val pixels = IntArray(GW * GH)
    renderGradient(pixels, colors, pos)
    return Bitmap.createBitmap(pixels, GW, GH, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun renderPattern(density: Density, direction: LayoutDirection, w: Int, h: Int, color: Color): ImageBitmap {
    val bmp = ImageBitmap(w, h)
    CanvasDrawScope().draw(density, direction, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
        val unit = 24f * this.density
        Doodles.forEach { d ->
            translate(d.x * w, d.y * h) {
                rotate(d.angle, Offset.Zero) { drawDoodle(d.kind, unit * d.size, color) }
            }
        }
    }
    return bmp
}

private fun DrawScope.drawDoodle(kind: Int, s: Float, color: Color) {
    val stroke = Stroke(width = s * 0.085f)
    when (kind) {
        0 -> drawCircle(color, s * 0.42f, Offset.Zero, style = stroke)
        1 -> {
            val p = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) s * 0.5f else s * 0.22f
                val a = Math.toRadians((i * 36 - 90).toDouble())
                val x = (r * cos(a)).toFloat()
                val y = (r * sin(a)).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, color, style = stroke)
        }
        2 -> {
            val p = Path().apply {
                moveTo(0f, s * 0.35f)
                cubicTo(-s * 0.6f, -s * 0.05f, -s * 0.3f, -s * 0.55f, 0f, -s * 0.2f)
                cubicTo(s * 0.3f, -s * 0.55f, s * 0.6f, -s * 0.05f, 0f, s * 0.35f)
            }
            drawPath(p, color, style = stroke)
        }
        3 -> {
            val p = Path().apply {
                moveTo(-s * 0.5f, 0f); lineTo(s * 0.5f, -s * 0.35f); lineTo(s * 0.1f, s * 0.4f); lineTo(-s * 0.05f, s * 0.08f); close()
            }
            drawPath(p, color, style = stroke)
        }
        4 -> drawRoundRect(color, Offset(-s * 0.45f, -s * 0.3f), Size(s * 0.9f, s * 0.55f), CornerRadius(s * 0.2f), style = stroke)
        else -> {
            drawCircle(color, s * 0.14f, Offset(-s * 0.15f, s * 0.3f), style = stroke)
            drawLine(color, Offset(-s * 0.01f, s * 0.3f), Offset(-s * 0.01f, -s * 0.35f), strokeWidth = s * 0.085f)
            drawLine(color, Offset(-s * 0.01f, -s * 0.35f), Offset(s * 0.3f, -s * 0.2f), strokeWidth = s * 0.085f)
        }
    }
}
