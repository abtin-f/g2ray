package com.abtin.tglass.features.chat

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
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

@Composable
fun ChatWallpaper(modifier: Modifier = Modifier, phase: Int = 0) {
    val c = TgTheme.colors
    val animations = LocalAppSettings.current.animations
    val anim = remember { Animatable(phase.toFloat()) }
    LaunchedEffect(phase) {
        if (animations) anim.animateTo(phase.toFloat(), tween(500, easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)))
        else anim.snapTo(phase.toFloat())
    }
    val bitmap = remember { Bitmap.createBitmap(GW, GH, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val pixels = remember { IntArray(GW * GH) }
    val cache = remember { floatArrayOf(Float.NaN) }
    val cachedColors = remember { arrayOfNulls<List<Color>>(1) }
    val doodles = remember {
        val r = Random(7)
        List(110) { Doodle(r.nextFloat(), r.nextFloat(), r.nextInt(6), 0.55f + r.nextFloat() * 0.55f, r.nextFloat() * 360f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val f = anim.value
        if (f != cache[0] || cachedColors[0] != c.wallpaper) {
            val a = floor(f).toInt()
            val t = f - a
            val p0 = positions(a)
            val p1 = positions(a + 1)
            val pos = List(4) { i -> Offset(p0[i].x + (p1[i].x - p0[i].x) * t, p0[i].y + (p1[i].y - p0[i].y) * t) }
            renderGradient(pixels, c.wallpaper, pos)
            bitmap.setPixels(pixels, 0, GW, 0, 0, GW, GH)
            cache[0] = f
            cachedColors[0] = c.wallpaper
        }
        drawImage(
            image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(GW, GH),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.High,
        )
        val unit = 24f * density
        doodles.forEach { d ->
            translate(d.x * size.width, d.y * size.height) {
                rotate(d.angle, Offset.Zero) { drawDoodle(d.kind, unit * d.size, c.wallpaperPattern) }
            }
        }
    }
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

@Suppress("unused")
private fun Color.argb() = toArgb()
