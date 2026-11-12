package com.abtin.tglass.features.media

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** SF Symbols–style glyphs for the media viewer and the photo/video editor (24×24 grid, tint when drawing). */
object MediaIcons {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList(), width: Float = 2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        fills.forEach { d -> b.addPath(addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black)) }
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

    /** crop */
    val Crop = icon("crop", "M6.5 2.5v14a1 1 0 0 0 1 1h14", "M2.5 6.5h14a1 1 0 0 1 1 1v14")
    /** rotate.left */
    val Rotate = icon(
        "rotate",
        "M5 11h9a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 14 21H5a1.5 1.5 0 0 1-1.5-1.5v-7A1.5 1.5 0 0 1 5 11z",
        "M9.5 5.5h4.5a5 5 0 0 1 5 5v1.5",
        "M11.5 3.2 9.2 5.5l2.3 2.3",
    )
    /** pencil.tip */
    val Brush = icon("brush", "M4.2 19.8l1-4.2L16.3 4.5a2.1 2.1 0 0 1 3 3L8.2 18.6z", "M14.3 6.5l3.2 3.2")
    /** textformat */
    val Text = icon("text", "M5 7V5h14v2", "M12 5v14", "M9.3 19h5.4", width = 2.2f)
    /** slider.horizontal.3 */
    val Adjust = icon(
        "adjust",
        "M3.5 7h7.3M15.2 7h5.3M3.5 17h3.3M11.2 17h9.3",
        "M13 4.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0-4.4z",
        "M9 14.8a2.2 2.2 0 1 0 0 4.4 2.2 2.2 0 0 0 0-4.4z",
    )
    /** arrow.uturn.backward */
    val Undo = icon("undo", "M8.5 13.5 4 9l4.5-4.5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    /** speaker.slash.fill */
    val SpeakerOff = icon("speakerOff", "M16 9.5l5 5M21 9.5l-5 5", fills = listOf("M3 9h3.5L11 5v14l-4.5-4H3z"))
    /** speaker.wave.2.fill */
    val SpeakerOn = icon("speakerOn", "M15 8.5a5 5 0 0 1 0 7M17.8 5.8a9 9 0 0 1 0 12.4", fills = listOf("M3 9h3.5L11 5v14l-4.5-4H3z"))
    /** flip.horizontal */
    val Flip = icon("flip", "M12 3v18", "M9 6.5 3.5 17.5H9z", "M15 6.5l5.5 11H15z", width = 1.8f)
    /** gobackward / goforward style chevron used by the double-tap seek feedback. */
    val SeekBack = icon("seekBack", fills = listOf("M11 6.2v11.6a.7.7 0 0 1-1.1.6L2.6 12.6a.7.7 0 0 1 0-1.2l7.3-5.8a.7.7 0 0 1 1.1.6zM21 6.2v11.6a.7.7 0 0 1-1.1.6l-7.3-5.8a.7.7 0 0 1 0-1.2l7.3-5.8a.7.7 0 0 1 1.1.6z"))
    val SeekForward = icon("seekForward", fills = listOf("M13 6.2v11.6a.7.7 0 0 0 1.1.6l7.3-5.8a.7.7 0 0 0 0-1.2l-7.3-5.8a.7.7 0 0 0-1.1.6zM3 6.2v11.6a.7.7 0 0 0 1.1.6l7.3-5.8a.7.7 0 0 0 0-1.2L4.1 5.6A.7.7 0 0 0 3 6.2z"))
    /** trash */
    val Trash = icon("trash", "M4 6.5h16", "M9.5 6.5V4.8a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1.7", "M6 6.5l1 12.7a1.5 1.5 0 0 0 1.5 1.3h7a1.5 1.5 0 0 0 1.5-1.3l1-12.7", width = 1.8f)
}
