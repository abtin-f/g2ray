package com.abtin.tglass.features.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** SF Symbols–style glyphs for the white-on-color settings icons (24×24 grid, tinted by Icon). */
internal object SettingsGlyphs {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList(), width: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        fills.forEach { d -> b.addPath(addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black)) }
        strokes.forEach { d ->
            b.addPath(
                addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    /** at */
    val At = icon("at", "M15.5 12a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0z", "M15.5 9v4.3a2.2 2.2 0 0 0 4.4 0V12A7.9 7.9 0 1 0 16 18.9")
    /** shield (proxy) */
    val Shield = icon("shield", fills = listOf("M12 2.8l7.5 2.8v5.8c0 4.8-3.2 8.5-7.5 9.9-4.3-1.4-7.5-5.1-7.5-9.9V5.6z"))
    /** gift (birthday) */
    val Gift = icon(
        "gift",
        "M12 9v11.5",
        "M12 9c-1.5-3.8-5.6-4.3-5.6-1.8C6.4 8.7 9 9 12 9zM12 9c1.5-3.8 5.6-4.3 5.6-1.8C17.6 8.7 15 9 12 9z",
        fills = listOf("M3.5 9h17v4h-17zM5 13h14v7.5H5z"),
        width = 1.6f,
    )
    /** phone */
    val Phone = icon("phone", fills = listOf("M7.2 3.2l2.6 3.9c.4.6.3 1.4-.2 1.9L8.4 10.2a11 11 0 0 0 5.4 5.4l1.2-1.2c.5-.5 1.3-.6 1.9-.2l3.9 2.6c.6.4.8 1.2.4 1.8l-1 1.6c-.6 1-1.8 1.5-2.9 1.2C10.7 19.8 4.2 13.3 2.6 6.7c-.3-1.1.2-2.3 1.2-2.9l1.6-1c.6-.4 1.4-.2 1.8.4z"))
    /** megaphone (personal channel) */
    val Channel = icon("channel", fills = listOf("M18.5 4.5v15l-6-3.3H9.8L10.9 21H8.2L7 16.2H6A3 3 0 0 1 3 13.2v-2.9a3 3 0 0 1 3-3h6.5z"))
    /** paintpalette (your color) */
    val Palette = icon(
        "palette",
        fills = listOf("M12 3a9 9 0 0 0 0 18c1.2 0 1.9-.8 1.9-1.8 0-.9-.6-1.3-.6-2.1 0-1 .8-1.6 1.8-1.6h2.1A3.8 3.8 0 0 0 21 11.7C21 6.9 17 3 12 3zM7.5 12.3a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm3-3.8a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3zm4.4.1a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3z"),
    )
}
