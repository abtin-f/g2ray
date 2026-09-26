package com.abtin.tglass.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * SF Symbols–style glyphs that Telegram-iOS draws from the system font (so they are not in its asset catalog).
 * Drawn on a 24×24 grid; tint with [Icon].
 */
object IosIcons {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList(), width: Float = 2.2f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        fills.forEach { d ->
            b.addPath(addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        }
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

    /** chevron.left (semibold), used by every back button. */
    val ChevronLeft = icon("chevronLeft", "M15 4.5 7.5 12l7.5 7.5", width = 2.6f)
    val ChevronRight = icon("chevronRight", "M9 4.5 16.5 12 9 19.5", width = 2.4f)
    val ChevronDown = icon("chevronDown", "M5 9l7 7 7-7", width = 2.6f)
    val Close = icon("xmark", "M6.5 6.5l11 11M17.5 6.5l-11 11", width = 2.4f)
    val Plus = icon("plus", "M12 5v14M5 12h14", width = 2.4f)
    val Checkmark = icon("checkmark", "M5 12.5l4.5 4.5L19 7.5", width = 2.6f)

    /** Single / double read checks (Telegram message status). */
    val CheckSingle = icon("check1", "M4 12.8l4.2 4.2L17 8", width = 2f)
    val CheckDouble = icon("check2", "M2.5 12.8l4.2 4.2L15.5 8", "M11 16.4l.6.6L20.5 8", width = 2f)
    val Clock = icon("clock", "M12 7v5l3 2", "M12 3.5a8.5 8.5 0 1 0 0 17 8.5 8.5 0 0 0 0-17z", width = 1.8f)

    /** paperclip (attachment). Path from Feather Icons (MIT). */
    val Paperclip = icon(
        "paperclip",
        "M20.5 11.1l-8.6 8.6a5.6 5.6 0 0 1-7.9-7.9l8.6-8.6a3.7 3.7 0 0 1 5.3 5.3l-8.6 8.6a1.9 1.9 0 0 1-2.6-2.6l7.9-7.9",
        width = 2f,
    )

    /** mic.fill */
    val Mic = icon(
        "mic",
        "M5.5 11.5a6.5 6.5 0 0 0 13 0", "M12 18v3",
        fills = listOf("M12 2.2a3.3 3.3 0 0 0-3.3 3.3v6a3.3 3.3 0 0 0 6.6 0v-6A3.3 3.3 0 0 0 12 2.2z"),
        width = 2f,
    )

    /** arrow.up (send button glyph). */
    val ArrowUp = icon("arrowUp", "M12 19.5V5M5.5 11.5 12 5l6.5 6.5", width = 2.6f)
    val ArrowDown = icon("arrowDown", "M12 4.5V19M5.5 12.5 12 19l6.5-6.5", width = 2.6f)
    val Keyboard = icon(
        "keyboard",
        "M3.5 6.5h17a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5h-17A1.5 1.5 0 0 1 2 16V8a1.5 1.5 0 0 1 1.5-1.5z",
        "M6 10h.01M9.3 10h.01M12.6 10h.01M16 10h.01M18.5 10h.01M6 13.3h.01M18.5 13.3h.01M9 13.8h6",
        width = 1.7f,
    )
    val Lock = icon(
        "lock",
        "M8 10.5V8a4 4 0 0 1 8 0v2.5",
        fills = listOf("M6.5 10.5h11a1.5 1.5 0 0 1 1.5 1.5v7a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19v-7a1.5 1.5 0 0 1 1.5-1.5z"),
        width = 2f,
    )
    val Play = icon("play", fills = listOf("M8 5.2v13.6a.8.8 0 0 0 1.2.7l10.6-6.8a.8.8 0 0 0 0-1.4L9.2 4.5A.8.8 0 0 0 8 5.2z"))
    val Pause = icon("pause", fills = listOf("M7 5h3.2v14H7zM13.8 5H17v14h-3.2z"))
    val Stop = icon("stop", fills = listOf("M7 7h10v10H7z"))
    val Eye = icon("eye", "M2 12s3.6-6.5 10-6.5S22 12 22 12s-3.6 6.5-10 6.5S2 12 2 12z", "M12 9.3a2.7 2.7 0 1 0 0 5.4 2.7 2.7 0 0 0 0-5.4z", width = 1.8f)
    val Heart = icon("heart", "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7a4.3 4.3 0 0 1 7.5 2.8C19.5 15.4 12 20 12 20z", width = 2f)
    val Speaker = icon("speaker", "M16 8.5a5 5 0 0 1 0 7M18.8 5.8a9 9 0 0 1 0 12.4", fills = listOf("M4 9h3.5L12 5v14l-4.5-4H4z"), width = 2f)
    val PhoneDown = icon("phoneDown", fills = listOf("M12 9.5c3.6 0 6.9 1 9.3 2.6.6.4.8 1.2.4 1.8l-1.6 2.2c-.4.5-1.1.7-1.7.4l-2.5-1.2a1.4 1.4 0 0 1-.8-1.3v-1.6a15 15 0 0 0-6.2 0V14c0 .6-.3 1.1-.8 1.3l-2.5 1.2c-.6.3-1.3.1-1.7-.4L2.3 13.9a1.4 1.4 0 0 1 .4-1.8C5.1 10.5 8.4 9.5 12 9.5z"))
    val Video = icon("video", fills = listOf("M4.5 6.5h9A2.5 2.5 0 0 1 16 9v6a2.5 2.5 0 0 1-2.5 2.5h-9A2.5 2.5 0 0 1 2 15V9a2.5 2.5 0 0 1 2.5-2.5zM17 10.2l4-2.4c.5-.3 1 .1 1 .6v7.2c0 .5-.5.9-1 .6l-4-2.4z"))
    val MicSlash = icon("micSlash", "M5.5 11.5a6.5 6.5 0 0 0 11 4.6", "M12 18v3", "M3.5 3.5l17 17", fills = listOf("M12 2.2a3.3 3.3 0 0 0-3.3 3.3v6a3.3 3.3 0 0 0 5.4 2.6L8.7 8.8V5.5"), width = 2f)
    val QrCode = icon("qr", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2", width = 1.9f)
    val Bookmark = icon("bookmark", fills = listOf("M7 3.5h10a1.5 1.5 0 0 1 1.5 1.5v15.2a.6.6 0 0 1-1 .5L12 16.8l-5.5 3.9a.6.6 0 0 1-1-.5V5A1.5 1.5 0 0 1 7 3.5z"))
    val Camera = icon("camera", "M12 9.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z", "M4.5 7h3l1.6-2.2h5.8L16.5 7h3A1.5 1.5 0 0 1 21 8.5v10a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5v-10A1.5 1.5 0 0 1 4.5 7z", width = 1.8f)
    val Sticker = icon("stickerSmall", "M20 12v-4a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v8a4 4 0 0 0 4 4h4", "M12 20a8 8 0 0 0 8-8h-4a4 4 0 0 0-4 4z", width = 1.8f)
}
