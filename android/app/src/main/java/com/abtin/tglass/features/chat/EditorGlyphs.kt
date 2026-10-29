package com.abtin.tglass.features.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Extra SF Symbols-like glyphs of the photo editor's crop bar (24×24 grid, tint when drawing). */
internal object EditorGlyphs {
    private fun glyph(name: String, vararg strokes: String, width: Float = 1.9f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        strokes.forEach { d ->
            b.addPath(
                addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    /** aspectratio: a rounded frame with two inner corner brackets. */
    val AspectRatio = glyph(
        "aspectratio",
        "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3z",
        "M7 12V9.5a.5.5 0 0 1 .5-.5H10",
        "M17 12v2.5a.5.5 0 0 1-.5.5H14",
    )
}
