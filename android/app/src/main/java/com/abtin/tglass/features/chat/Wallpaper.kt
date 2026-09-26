package com.abtin.tglass.features.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import com.abtin.tglass.core.design.TgTheme
import kotlin.random.Random

private class Doodle(val x: Float, val y: Float, val kind: Int, val size: Float, val angle: Float)

/** Telegram-like 4-color gradient wallpaper with a subtle doodle pattern. */
@Composable
fun ChatWallpaper(modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    val doodles = remember {
        val r = Random(7)
        List(90) { Doodle(r.nextFloat(), r.nextFloat(), r.nextInt(6), 0.6f + r.nextFloat() * 0.6f, r.nextFloat() * 360f) }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cols = c.wallpaper
        drawRect(cols[0])
        val radius = maxOf(w, h) * 0.75f
        listOf(Offset(0f, 0f), Offset(w, h * 0.35f), Offset(0f, h * 0.7f), Offset(w, h)).forEachIndexed { i, o ->
            drawRect(Brush.radialGradient(listOf(cols[i % cols.size], cols[i % cols.size].copy(alpha = 0f)), center = o, radius = radius))
        }
        val unit = 22f * density
        doodles.forEach { d ->
            translate(d.x * w, d.y * h) {
                rotate(d.angle, Offset.Zero) {
                    drawDoodle(d.kind, unit * d.size, c.wallpaperPattern)
                }
            }
        }
    }
}

private fun DrawScope.drawDoodle(kind: Int, s: Float, color: Color) {
    val stroke = Stroke(width = s * 0.09f)
    when (kind) {
        0 -> drawCircle(color, s * 0.45f, Offset.Zero, style = stroke)
        1 -> { // star
            val p = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) s * 0.5f else s * 0.22f
                val a = Math.toRadians((i * 36 - 90).toDouble())
                val x = (r * kotlin.math.cos(a)).toFloat()
                val y = (r * kotlin.math.sin(a)).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, color, style = stroke)
        }
        2 -> { // heart
            val p = Path().apply {
                moveTo(0f, s * 0.35f)
                cubicTo(-s * 0.6f, -s * 0.05f, -s * 0.3f, -s * 0.55f, 0f, -s * 0.2f)
                cubicTo(s * 0.3f, -s * 0.55f, s * 0.6f, -s * 0.05f, 0f, s * 0.35f)
            }
            drawPath(p, color, style = stroke)
        }
        3 -> { // paper plane
            val p = Path().apply {
                moveTo(-s * 0.5f, 0f); lineTo(s * 0.5f, -s * 0.35f); lineTo(s * 0.1f, s * 0.4f); lineTo(-s * 0.05f, s * 0.08f); close()
            }
            drawPath(p, color, style = stroke)
        }
        4 -> { // chat bubble
            drawRoundRect(color, Offset(-s * 0.45f, -s * 0.3f), androidx.compose.ui.geometry.Size(s * 0.9f, s * 0.55f), androidx.compose.ui.geometry.CornerRadius(s * 0.2f), style = stroke)
        }
        else -> { // music note
            drawCircle(color, s * 0.14f, Offset(-s * 0.15f, s * 0.3f), style = stroke)
            drawLine(color, Offset(-s * 0.01f, s * 0.3f), Offset(-s * 0.01f, -s * 0.35f), strokeWidth = s * 0.09f)
            drawLine(color, Offset(-s * 0.01f, -s * 0.35f), Offset(s * 0.3f, -s * 0.2f), strokeWidth = s * 0.09f)
        }
    }
}
