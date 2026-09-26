package com.abtin.tglass.features.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.MessageStatus
import com.abtin.tglass.data.Reaction
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatCount
import com.abtin.tglass.ui.components.formatDuration
import com.abtin.tglass.ui.components.formatTime
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

val TailWidth = 6.dp

/**
 * iOS message bubble: continuous corners, smaller radius on the grouped side, and a curved tail on the
 * last bubble of a group (spec §13). The tail column is always reserved so grouped bubbles align.
 */
class BubbleShape(
    private val outgoing: Boolean,
    private val tail: Boolean,
    private val radius: Dp,
    private val smallRadius: Dp,
    private val groupedTop: Boolean,
    private val groupedBottom: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val w = size.width
        val h = size.height
        val tw = TailWidth.toPx()
        val bR = w - tw
        val maxR = minOf(h, bR) / 2f
        val big = radius.toPx().coerceAtMost(maxR)
        val small = smallRadius.toPx().coerceAtMost(maxR)
        val tl = big
        val bl = big
        val tr = if (groupedTop) small else big
        val br = if (groupedBottom && !tail) small else big
        fun x(v: Float) = if (outgoing) v else w - v
        val p = Path()
        var cx = x(tl)
        var cy = 0f
        p.moveTo(cx, cy)
        fun line(nx: Float, ny: Float) { p.lineTo(nx, ny); cx = nx; cy = ny }
        fun corner(kx: Float, ky: Float, ex: Float, ey: Float) {
            val k = 0.55f
            p.cubicTo(cx + (kx - cx) * k, cy + (ky - cy) * k, ex + (kx - ex) * k, ey + (ky - ey) * k, ex, ey)
            cx = ex; cy = ey
        }
        line(x(bR - tr), 0f)
        corner(x(bR), 0f, x(bR), tr)
        if (tail) {
            val tailTop = (h - 17.dp.toPx()).coerceAtLeast(tr)
            line(x(bR), tailTop)
            p.cubicTo(x(bR), h - 6.dp.toPx(), x(bR + 1.5.dp.toPx()), h - 1.dp.toPx(), x(w), h)
            cx = x(w); cy = h
            p.cubicTo(x(w - 3.dp.toPx()), h + 0.3.dp.toPx(), x(bR - 4.dp.toPx()), h - 0.4.dp.toPx(), x(bR - 9.dp.toPx()), h - 0.8.dp.toPx())
            cx = x(bR - 9.dp.toPx()); cy = h - 0.8.dp.toPx()
            p.quadraticTo(x(bR - 12.dp.toPx()), h, x(bR - 16.dp.toPx()), h)
            cx = x(bR - 16.dp.toPx()); cy = h
        } else {
            line(x(bR), h - br)
            corner(x(bR), h, x(bR - br), h)
        }
        line(x(bl), h)
        corner(x(0f), h, x(0f), h - bl)
        line(x(0f), tl)
        corner(x(0f), 0f, x(tl), 0f)
        p.close()
        Outline.Generic(p)
    }
}

@Immutable
data class BubbleGroup(val groupedTop: Boolean, val groupedBottom: Boolean, val showName: Boolean, val showAvatar: Boolean)

/** Palette for one bubble side. */
@Immutable
data class BubbleColors(val fill: Color, val text: Color, val meta: Color, val accent: Color, val link: Color, val gradient: List<Color>? = null) {
    /** Content drawn on top of an [accent]-filled control (play button, file icon, chosen reaction). */
    val onAccent: Color
        get() = when {
            accent == Color.White -> Color(0xFF0088FF)   // night outgoing: white controls, blue glyph
            fill == Color(0xFFE1FFC7) -> fill            // day outgoing: glyph uses the bubble green
            else -> Color.White
        }
}

@Composable
fun bubbleColors(outgoing: Boolean): BubbleColors {
    val c = TgTheme.colors
    return if (outgoing) BubbleColors(c.bubbleOut, c.bubbleOutText, c.bubbleOutMeta, c.bubbleOutAccent, if (c.isDark) Color.White else Color(0xFF004BAD), c.bubbleOutGradient)
    else BubbleColors(c.bubbleIn, c.bubbleInText, c.bubbleInMeta, c.accent, if (c.isDark) c.accent else Color(0xFF004BAD))
}

/**
 * Spec §55 component tree: header → reply/forward → content → reactions → metadata.
 * Each content type has its own renderer.
 */
@Composable
fun MessageBubble(
    m: Message,
    group: BubbleGroup,
    senderName: String?,
    senderSeed: Long,
    replyTo: Message?,
    replyName: String?,
    isChannel: Boolean,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    onReplyClick: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onVote: (Int) -> Unit = {},
    onMediaClick: () -> Unit = {},
) {
    val c = TgTheme.colors
    val content = m.content
    if (content is MessageContent.Sticker) {
        StickerMessage(m, content, modifier)
        return
    }
    val radius = LocalAppSettings.current.bubbleRadius.dp
    val shape = remember(m.outgoing, group, radius) {
        BubbleShape(m.outgoing, !group.groupedBottom, radius, (radius.value / 2f).coerceAtLeast(4f).dp, group.groupedTop, group.groupedBottom)
    }
    val colors = bubbleColors(m.outgoing)
    val tailPad = TailWidth
    val isMediaOnly = content is MessageContent.Photo && content.caption == null && replyTo == null && senderName == null
    Column(
        modifier
            .widthIn(max = maxWidth)
            .width(IntrinsicSize.Max)
            .defaultMinSize(minWidth = 40.dp + TailWidth, minHeight = 35.dp)
            .clip(shape)
            .then(if (colors.gradient != null) Modifier.background(Brush.verticalGradient(colors.gradient)) else Modifier.background(colors.fill))
            .padding(start = if (m.outgoing) 0.dp else tailPad, end = if (m.outgoing) tailPad else 0.dp)
            .padding(if (isMediaOnly) 2.dp else 0.dp)
    ) {
        val inner = Modifier.padding(horizontal = 11.dp)
        if (senderName != null) {
            T(
                senderName, TgTheme.type.subheadline.copy(fontSize = 14.sp), if (isChannel) c.accent else avatarColors(senderSeed).second,
                weight = FontWeight.SemiBold, maxLines = 1, modifier = inner.padding(top = 6.dp),
            )
        }
        if (m.forwardedFrom != null) {
            T("Forwarded from", TgTheme.type.footnote, colors.accent, modifier = inner.padding(top = 6.dp))
            T(m.forwardedFrom, TgTheme.type.footnote, colors.accent, weight = FontWeight.SemiBold, modifier = inner)
        }
        if (replyTo != null) {
            ReplyHeader(replyName ?: "", replyTo.preview, colors, Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp).fadeClickable(onClick = onReplyClick))
        }
        when (content) {
            is MessageContent.Text -> TextBody(content.text, m, colors, inner.padding(top = if (senderName != null || replyTo != null) 2.dp else 6.dp, bottom = 6.dp))
            is MessageContent.Photo -> PhotoBody(m, content, colors, isMediaOnly, onMediaClick)
            is MessageContent.Voice -> VoiceBody(m, content, colors)
            is MessageContent.File -> FileBody(m, content, colors)
            is MessageContent.Location -> LocationBody(m, content, colors)
            is MessageContent.Contact -> ContactBody(m, content, colors)
            is MessageContent.Poll -> PollBody(m, content, colors, onVote)
            is MessageContent.Link -> LinkBody(m, content, colors)
            else -> {}
        }
        if (m.reactions.isNotEmpty()) {
            ReactionsRow(m.reactions, colors, Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp), onReact)
        }
    }
}

@Composable
private fun ReplyHeader(name: String, text: String, colors: BubbleColors, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedRectangle(6.dp))
            .background(colors.accent.copy(alpha = 0.12f))
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(colors.accent))
        Column(Modifier.padding(horizontal = 7.dp, vertical = 4.dp)) {
            T(name, TgTheme.type.footnote.copy(fontSize = 14.sp), colors.accent, weight = FontWeight.SemiBold, maxLines = 1)
            T(text, TgTheme.type.footnote.copy(fontSize = 14.sp), colors.text, maxLines = 1)
        }
    }
}

/** Time + edited + delivery state (spec §14). */
@Composable
fun MetaRow(m: Message, color: Color, overlay: Boolean = false) {
    val c = TgTheme.colors
    val row = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.pinned) {
                Icon(Icons.Rounded.PushPin, color, 11.dp)
                Spacer(Modifier.width(2.dp))
            }
            if (m.views != null) {
                Icon(Icons.Rounded.Visibility, color, 13.dp)
                Spacer(Modifier.width(2.dp))
                T(formatCount(m.views), TgTheme.type.caption1, color, maxLines = 1)
                Spacer(Modifier.width(5.dp))
            }
            if (m.edited) {
                T("edited ", TgTheme.type.caption2, color, maxLines = 1)
            }
            T(formatTime(m.date), TgTheme.type.caption2, color, maxLines = 1)
            if (m.outgoing) {
                Spacer(Modifier.width(2.dp))
                when (m.status) {
                    MessageStatus.Sending -> Icon(Icons.Rounded.Schedule, color, 14.dp)
                    MessageStatus.Sent -> Icon(Icons.Rounded.Check, color, 16.dp)
                    MessageStatus.Read -> Icon(Icons.Rounded.DoneAll, color, 16.dp)
                    MessageStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, c.destructive, 16.dp)
                }
            }
        }
    }
    if (overlay) {
        Box(Modifier.clip(Capsule()).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 6.dp, vertical = 2.dp)) { row() }
    } else row()
}

/**
 * Text followed by metadata that sits on the last line when it fits, otherwise on its own line —
 * exactly how Telegram lays out timestamps.
 */
@Composable
fun TextWithMeta(text: String, style: TextStyle, meta: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val holder = remember { arrayOfNulls<TextLayoutResult>(1) }
    Layout(
        modifier = modifier,
        content = {
            BasicText(text, style = style, onTextLayout = { holder[0] = it })
            meta()
        },
    ) { ms, cons ->
        val maxW = if (cons.hasBoundedWidth) cons.maxWidth else Constraints.Infinity
        val tp = ms[0].measure(Constraints(maxWidth = maxW))
        val mp = ms[1].measure(Constraints())
        val layout = holder[0]
        var textW = tp.width
        var lastRight = tp.width.toFloat()
        if (layout != null && layout.lineCount > 0) {
            var widest = 0f
            for (i in 0 until layout.lineCount) widest = max(widest, layout.getLineRight(i))
            textW = ceil(widest).toInt().coerceAtMost(tp.width)
            lastRight = layout.getLineRight(layout.lineCount - 1)
        }
        val gap = 8.dp.roundToPx()
        val inline = lastRight + gap + mp.width <= maxW
        val width: Int
        val height: Int
        if (inline) {
            width = max(textW, ceil(lastRight).toInt() + gap + mp.width).coerceAtLeast(cons.minWidth)
            height = max(tp.height, mp.height)
        } else {
            width = max(textW, mp.width).coerceAtLeast(cons.minWidth)
            height = tp.height + mp.height
        }
        layout(width, height) {
            tp.place(0, 0)
            mp.place(width - mp.width, height - mp.height + if (inline) 2.dp.roundToPx() else 0)
        }
    }
}

@Composable
private fun TextBody(text: String, m: Message, colors: BubbleColors, modifier: Modifier) {
    val emojiOnly = text.length <= 8 && text.none { it.isLetterOrDigit() } && text.isNotBlank()
    val style = if (emojiOnly) TgTheme.type.body.copy(fontSize = 34.sp, lineHeight = 40.sp) else TgTheme.type.body
    TextWithMeta(
        text,
        style.copy(color = colors.text),
        meta = { MetaRow(m, colors.meta) },
        modifier = modifier,
    )
}

@Composable
private fun PhotoBody(m: Message, p: MessageContent.Photo, colors: BubbleColors, mediaOnly: Boolean, onClick: () -> Unit) {
    val (a, b) = avatarColors(p.seed.toLong())
    val r = LocalAppSettings.current.bubbleRadius
    Box(
        Modifier
            .width(250.dp)
            .aspectRatio(p.aspect.coerceIn(0.6f, 1.8f))
            .clip(RoundedRectangle((r - 2f).coerceAtLeast(4f).dp))
            .background(Brush.linearGradient(listOf(a, b)))
            .iosClickable(highlight = Color.Black.copy(0.1f), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        T(p.emoji, TgTheme.type.body.copy(fontSize = 64.sp, lineHeight = 72.sp))
        if (mediaOnly || p.caption == null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { MetaRow(m, Color.White, overlay = true) }
        }
    }
    if (p.caption != null) {
        TextWithMeta(p.caption, TgTheme.type.body.copy(color = colors.text), { MetaRow(m, colors.meta) }, Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun StickerMessage(m: Message, s: MessageContent.Sticker, modifier: Modifier) {
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        BasicText(s.emoji, style = TextStyle(fontSize = 110.sp, lineHeight = 124.sp))
        MetaRow(m, Color.White, overlay = true)
    }
}

@Composable
private fun VoiceBody(m: Message, v: MessageContent.Voice, colors: BubbleColors) {
    Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(colors.accent).bounceClickable { }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayArrow, colors.onAccent, 30.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Canvas(Modifier.width(150.dp).height(22.dp)) {
                val n = v.waveform.size
                val step = size.width / n
                v.waveform.forEachIndexed { i, amp ->
                    val bh = (amp * size.height).coerceAtLeast(3f)
                    drawRoundRect(
                        colors.accent.copy(alpha = if (i < n / 3) 1f else 0.4f),
                        topLeft = Offset(i * step, size.height - bh),
                        size = Size(step * 0.55f, bh),
                        cornerRadius = CornerRadius(step),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(formatDuration(v.seconds), TgTheme.type.caption1, colors.meta)
                Spacer(Modifier.width(4.dp))
                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accent))
                Spacer(Modifier.width(24.dp))
                MetaRow(m, colors.meta)
            }
        }
    }
}

@Composable
private fun FileBody(m: Message, f: MessageContent.File, colors: BubbleColors) {
    Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedRectangle(10.dp)).background(colors.accent), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, colors.onAccent, 26.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 190.dp)) {
            T(f.name, TgTheme.type.subheadline, colors.text, weight = FontWeight.SemiBold, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(f.size, TgTheme.type.footnote, colors.meta)
                Spacer(Modifier.width(16.dp))
                MetaRow(m, colors.meta)
            }
        }
    }
}

@Composable
private fun LocationBody(m: Message, l: MessageContent.Location, colors: BubbleColors) {
    val dark = TgTheme.colors.isDark
    Box(
        Modifier
            .padding(2.dp)
            .width(250.dp)
            .height(140.dp)
            .clip(RoundedRectangle((LocalAppSettings.current.bubbleRadius - 2f).coerceAtLeast(4f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(if (dark) Color(0xFF2B3A2F) else Color(0xFFE8EEDB))
            val road = if (dark) Color(0xFF45524A) else Color.White
            for (i in 0..6) {
                drawLine(road, Offset(0f, i * size.height / 6f + 10f), Offset(size.width, i * size.height / 6f - 20f), strokeWidth = 6f)
                drawLine(road, Offset(i * size.width / 6f, 0f), Offset(i * size.width / 6f + 30f, size.height), strokeWidth = 4f)
            }
            drawCircle(if (dark) Color(0xFF3B6C8C) else Color(0xFFA8D5F2), 40f, Offset(size.width * 0.8f, size.height * 0.25f))
        }
        Icon(Icons.Rounded.LocationOn, TgTheme.colors.destructive, 40.dp)
    }
    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        T(l.title, TgTheme.type.subheadline, colors.text, weight = FontWeight.SemiBold)
        TextWithMeta(l.address, TgTheme.type.footnote.copy(color = colors.meta), { MetaRow(m, colors.meta) })
    }
}

@Composable
private fun ContactBody(m: Message, ct: MessageContent.Contact, colors: BubbleColors) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(ct.name, ct.phone.hashCode().toLong(), 44.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                T(ct.name, TgTheme.type.subheadline, colors.accent, weight = FontWeight.SemiBold)
                T(ct.phone, TgTheme.type.footnote, colors.text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.padding(horizontal = 10.dp)) { Separator() }
        Box(Modifier.fillMaxWidth().height(36.dp).fadeClickable { }, contentAlignment = Alignment.Center) {
            T("View Contact", TgTheme.type.subheadline, colors.accent, weight = FontWeight.SemiBold)
        }
        Box(Modifier.align(Alignment.End).padding(end = 10.dp, bottom = 6.dp)) { MetaRow(m, colors.meta) }
    }
}

@Composable
private fun PollBody(m: Message, p: MessageContent.Poll, colors: BubbleColors, onVote: (Int) -> Unit) {
    val total = p.votes.sum().coerceAtLeast(1)
    Column(Modifier.width(260.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
        T(p.question, TgTheme.type.body, colors.text, weight = FontWeight.SemiBold)
        T(if (p.quiz) "Quiz" else "Anonymous Poll", TgTheme.type.footnote, colors.meta)
        Spacer(Modifier.height(8.dp))
        p.options.forEachIndexed { i, opt ->
            val pct = p.votes[i] * 100 / total
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).then(if (p.voted == null) Modifier.fadeClickable { onVote(i) } else Modifier),
                verticalAlignment = Alignment.Top,
            ) {
                if (p.voted == null) {
                    Box(Modifier.size(22.dp).border(1.5.dp, colors.meta.copy(alpha = 0.6f), CircleShape))
                } else {
                    T("$pct%", TgTheme.type.footnote, colors.text, weight = FontWeight.Bold, modifier = Modifier.width(36.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    T(opt, TgTheme.type.subheadline, colors.text)
                    if (p.voted != null) {
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.fillMaxWidth(pct / 100f).coerceMinWidth().height(4.dp).clip(Capsule()).background(colors.accent))
                    }
                }
                if (p.voted == i) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.Check, colors.accent, 16.dp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("${formatCount(p.votes.sum())} votes", TgTheme.type.footnote, colors.meta, modifier = Modifier.weight(1f))
            MetaRow(m, colors.meta)
        }
    }
}

private fun Modifier.coerceMinWidth(): Modifier = this.then(Modifier.widthIn(min = 4.dp))

@Composable
private fun LinkBody(m: Message, l: MessageContent.Link, colors: BubbleColors) {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 280.dp)) {
        T(l.text, TgTheme.type.body, colors.link)
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedRectangle(6.dp))
                .background(colors.accent.copy(alpha = 0.12f))
        ) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(colors.accent))
            Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                T(l.site, TgTheme.type.footnote, colors.accent, weight = FontWeight.SemiBold)
                T(l.title, TgTheme.type.footnote, colors.text, weight = FontWeight.SemiBold)
                T(l.description, TgTheme.type.footnote, colors.text, maxLines = 3)
            }
        }
        Box(Modifier.align(Alignment.End).padding(top = 4.dp)) { MetaRow(m, colors.meta) }
    }
}

@Composable
private fun ReactionsRow(reactions: List<Reaction>, colors: BubbleColors, modifier: Modifier, onReact: (String) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.forEach { r ->
            Row(
                Modifier
                    .height(30.dp)
                    .clip(Capsule())
                    .background(if (r.chosen) colors.accent else colors.accent.copy(alpha = 0.14f))
                    .bounceClickable { onReact(r.emoji) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(r.emoji, style = TextStyle(fontSize = 16.sp))
                Spacer(Modifier.width(4.dp))
                T(formatCount(r.count), TgTheme.type.footnote, if (r.chosen) colors.onAccent else colors.accent, weight = FontWeight.SemiBold)
            }
        }
    }
}

/** Centered service capsule (spec §44) — also used for date separators. */
@Composable
fun ServicePill(text: String, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    Box(modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(Capsule()).background(c.serviceBubble).padding(horizontal = 10.dp, vertical = 3.dp)) {
            T(text, TgTheme.type.footnote, Color.White, weight = FontWeight.Medium)
        }
    }
}

@Suppress("unused")
private fun roundPx(v: Float) = v.roundToInt()
