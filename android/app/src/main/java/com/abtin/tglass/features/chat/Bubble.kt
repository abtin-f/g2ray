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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
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
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
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
 * Telegram-iOS message bubble (port of `ChatMessageBubbleImages.messageBubbleImage`):
 * rounded body, and for the last bubble of a group the tail = body ∪ corner rect ∪ half-ellipse lobe
 * minus a cut-out ellipse — which gives the characteristic notch before the tail tip.
 * The tail column ([TailWidth] = 6) is always reserved so grouped bubbles align.
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
        val u = 1.dp.toPx() // Telegram geometry is in points
        val tw = TailWidth.toPx()
        val bR = w - tw
        val maxR = minOf(h, bR) / 2f
        val big = radius.toPx().coerceAtMost(maxR)
        val small = smallRadius.toPx().coerceAtMost(maxR)
        // Radii for the tail side ("right" for outgoing) and the far side.
        val nearTop = if (groupedTop) small else big
        val nearBottom = if (tail) big else if (groupedBottom) small else big
        fun x(v: Float) = if (outgoing) v else w - v
        fun cr(r: Float) = CornerRadius(r, r)
        val bodyLeft = if (outgoing) 0f else tw
        val bodyRight = if (outgoing) bR else w
        val body = Path().apply {
            addRoundRect(
                RoundRect(
                    left = bodyLeft, top = 0f, right = bodyRight, bottom = h,
                    topLeftCornerRadius = cr(if (outgoing) big else nearTop),
                    topRightCornerRadius = cr(if (outgoing) nearTop else big),
                    bottomRightCornerRadius = cr(if (outgoing) nearBottom else big),
                    bottomLeftCornerRadius = cr(if (outgoing) big else nearBottom),
                )
            )
        }
        if (!tail) return Outline.Generic(body)
        // Reference square is 33pt with its bottom-right at (bR, h).
        val ox = bR - 33f * u
        val oy = h - 33f * u
        fun px(v: Float) = x(ox + v * u)
        fun py(v: Float) = oy + v * u
        val corner = Path().apply {
            val l = px(16.5f); val r = px(33f)
            addRect(Rect(minOf(l, r), py(16f), maxOf(l, r), py(24.5f)))
        }
        val lobe = Path().apply {
            if (radius.toPx() >= 14f * u) {
                moveTo(px(24f), py(24.5f))
                quadraticTo(px(24f), py(33f), px(37.5f), py(33f))
                quadraticTo(px(51f), py(33f), px(51f), py(24.5f))
                close()
            } else {
                val l = px(22f); val r = px(51f)
                addRect(Rect(minOf(l, r), py(24.5f), maxOf(l, r), py(33f)))
            }
        }
        val cut = Path().apply {
            val l = px(33f); val r = px(56f)
            addOval(Rect(minOf(l, r), py(14f), maxOf(l, r), py(35f)))
        }
        val tailArea = Path().apply { op(corner, lobe, PathOperation.Union) }
        val carved = Path().apply { op(tailArea, cut, PathOperation.Difference) }
        val result = Path().apply { op(body, carved, PathOperation.Union) }
        Outline.Generic(result)
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
    if (content is MessageContent.VideoNote) {
        VideoNoteMessage(m, content, replyTo, replyName, onReplyClick, modifier)
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
            is MessageContent.Text -> TextBody(content, m, colors, inner.padding(top = if (senderName != null || replyTo != null) 2.dp else 6.dp, bottom = 6.dp))
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
                Icon(TgIcons.MsgPinned, color, 11.dp)
                Spacer(Modifier.width(2.dp))
            }
            if (m.views != null) {
                Icon(IosIcons.Eye, color, 13.dp)
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
                    MessageStatus.Sending -> Icon(IosIcons.Clock, color, 13.dp)
                    MessageStatus.Sent -> Icon(IosIcons.CheckSingle, color, 15.dp)
                    MessageStatus.Read -> Icon(IosIcons.CheckDouble, color, 15.dp)
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
fun TextWithMeta(text: String, style: TextStyle, meta: @Composable () -> Unit, modifier: Modifier = Modifier) =
    TextWithMeta(AnnotatedString(text), style, meta, modifier)

@Composable
fun TextWithMeta(text: AnnotatedString, style: TextStyle, meta: @Composable () -> Unit, modifier: Modifier = Modifier) {
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
private fun richFor(m: Message, text: String, entities: List<com.abtin.tglass.data.Entity>, colors: BubbleColors): AnnotatedString {
    var revealed by remember(m.id) { androidx.compose.runtime.mutableStateOf(false) }
    return rememberRichText(
        text, entities,
        link = colors.link,
        codeBackground = colors.accent.copy(alpha = 0.13f),
        spoilerColor = colors.text.copy(alpha = 0.22f),
        spoilersRevealed = revealed,
        onRevealSpoiler = { revealed = true },
    )
}

@Composable
private fun TextBody(content: MessageContent.Text, m: Message, colors: BubbleColors, modifier: Modifier) {
    val text = content.text
    val emojiOnly = text.length <= 8 && text.none { it.isLetterOrDigit() } && text.isNotBlank()
    val style = if (emojiOnly) TgTheme.type.body.copy(fontSize = 34.sp, lineHeight = 40.sp) else TgTheme.type.body
    TextWithMeta(
        richFor(m, text, content.entities, colors),
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
            .background(if (p.image != null) Brush.linearGradient(listOf(colors.meta.copy(0.25f), colors.meta.copy(0.15f))) else Brush.linearGradient(listOf(a, b)))
            .iosClickable(highlight = Color.Black.copy(0.1f), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (p.image != null) TgImage(p.image, Modifier.matchParentSize(), maxPx = 900)
        else T(p.emoji, TgTheme.type.body.copy(fontSize = 64.sp, lineHeight = 72.sp))
        if (p.video) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
                Icon(IosIcons.Play, Color.White, 26.dp)
            }
            if (p.duration > 0 || p.loop) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(Capsule()).background(Color.Black.copy(0.45f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    T(if (p.loop) "GIF" else formatDuration(p.duration), TgTheme.type.caption2, Color.White, weight = FontWeight.SemiBold)
                }
            }
        }
        if (mediaOnly || p.caption == null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(6.dp)) { MetaRow(m, Color.White, overlay = true) }
        }
    }
    if (p.caption != null) {
        TextWithMeta(richFor(m, p.caption, p.captionEntities, colors), TgTheme.type.body.copy(color = colors.text), { MetaRow(m, colors.meta) }, Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun StickerMessage(m: Message, s: MessageContent.Sticker, modifier: Modifier) {
    val repo = com.abtin.tglass.features.main.LocalRepository.current
    val anim = s.animation
    val animPath = anim?.let { repo.filePath(it) }
    androidx.compose.runtime.LaunchedEffect(anim, animPath) { if (anim != null && animPath == null) repo.requestImage(anim) }
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        val still: @Composable () -> Unit = {
            if (s.image != null) TgImage(s.image, Modifier.size(160.dp), maxPx = 512, contentScale = ContentScale.Fit)
            else BasicText(s.emoji, style = TextStyle(fontSize = 110.sp, lineHeight = 124.sp))
        }
        if (anim != null) com.abtin.tglass.ui.components.TgsSticker(animPath, Modifier.size(160.dp), still)
        else still()
        MetaRow(m, Color.White, overlay = true)
    }
}

@Composable
private fun VoiceBody(m: Message, v: MessageContent.Voice, colors: BubbleColors) {
    val repo = com.abtin.tglass.features.main.LocalRepository.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = com.abtin.tglass.core.media.VoicePlayer
    val key = "${m.chatId}:${m.id}"
    val current = player.currentKey == key
    val playing = current && player.playing
    val progress = if (current) player.progress else 0f
    val media = v.media
    val path = media?.let { repo.filePath(it) }
    // Tapped before the file was downloaded: start playing as soon as it arrives.
    var pending by androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(pending, path) {
        if (pending && path != null) {
            pending = false
            player.toggle(context, key, path, v.seconds)
        }
    }
    val loading = pending && path == null
    Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(colors.accent).bounceClickable {
                when {
                    media == null -> player.toggle(context, key, null, v.seconds)
                    path != null -> player.toggle(context, key, path, v.seconds)
                    else -> { pending = !pending; if (pending) repo.requestImage(media) }
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> {
                    val p = media?.let { repo.fileProgress(it) } ?: 0f
                    Canvas(Modifier.size(34.dp)) {
                        drawArc(colors.onAccent, -90f, 360f * p.coerceAtLeast(0.05f), false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Icon(IosIcons.Close, colors.onAccent, 14.dp)
                }
                playing -> Icon(IosIcons.Pause, colors.onAccent, 22.dp)
                else -> Icon(IosIcons.Play, colors.onAccent, 24.dp)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Canvas(Modifier.width(150.dp).height(22.dp)) {
                val n = v.waveform.size
                val step = size.width / n
                val played = progress * n
                v.waveform.forEachIndexed { i, amp ->
                    val bh = (amp * size.height).coerceAtLeast(3f)
                    // While playing, the part already played is solid and the rest faded (Telegram).
                    val alpha = if (current) 0.4f + 0.6f * (played - i).coerceIn(0f, 1f) else 1f
                    drawRoundRect(
                        colors.accent.copy(alpha = alpha),
                        topLeft = Offset(i * step, size.height - bh),
                        size = Size(step * 0.55f, bh),
                        cornerRadius = CornerRadius(step),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(formatDuration(if (current) (player.positionMs / 1000).toInt() else v.seconds), TgTheme.type.caption1, colors.meta)
                Spacer(Modifier.width(4.dp))
                if (!current) Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accent))
                else VoiceSpeedButton(player.speed, colors) { player.cycleSpeed() }
                Spacer(Modifier.width(if (current) 12.dp else 24.dp))
                MetaRow(m, colors.meta)
            }
        }
    }
}

/** 1x / 1.5x / 2x toggle shown while a voice note is the current one. */
@Composable
private fun VoiceSpeedButton(speed: Float, colors: BubbleColors, onClick: () -> Unit) {
    val label = if (speed == 1.5f) "1.5x" else "${speed.roundToInt()}x"
    Box(
        Modifier
            .clip(Capsule())
            .background(colors.accent.copy(alpha = 0.16f))
            .fadeClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(label, TgTheme.type.caption2, colors.accent, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun FileBody(m: Message, f: MessageContent.File, colors: BubbleColors) {
    val repo = com.abtin.tglass.features.main.LocalRepository.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = com.abtin.tglass.ui.components.LocalToast.current
    val player = com.abtin.tglass.core.media.VoicePlayer
    val key = "${m.chatId}:${m.id}"
    val ref = f.file
    val path = ref?.let { repo.filePath(it) }
    // Tapped while not downloaded yet: act (open / play) as soon as the file arrives.
    var pending by remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    fun act(p: String) {
        if (f.music) player.toggle(context, key, p, f.duration)
        else if (!com.abtin.tglass.core.media.Files.open(context, p, f.mime)) toast.show("No app can open this file")
    }
    androidx.compose.runtime.LaunchedEffect(pending, path) {
        if (pending && path != null) {
            pending = false
            act(path)
        }
    }
    val downloading = pending && path == null
    val playing = f.music && player.currentKey == key && player.playing
    Column {
        Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(if (f.music) CircleShape else RoundedRectangle(10.dp))
                    .background(colors.accent)
                    .bounceClickable {
                        when {
                            ref == null -> {}
                            path != null -> act(path)
                            else -> { pending = !pending; if (pending) repo.requestImage(ref) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    downloading -> {
                        val progress = ref?.let { repo.fileProgress(it) } ?: 0f
                        Canvas(Modifier.size(38.dp)) {
                            drawArc(colors.onAccent, -90f, 360f * progress.coerceAtLeast(0.05f), false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                        }
                        Icon(IosIcons.Close, colors.onAccent, 14.dp)
                    }
                    f.music -> Icon(if (playing) IosIcons.Pause else IosIcons.Play, colors.onAccent, 24.dp)
                    ref != null && path == null -> Icon(IosIcons.ArrowDown, colors.onAccent, 24.dp)
                    else -> {
                        val ext = f.name.substringAfterLast('.', "").take(4).uppercase()
                        if (ext.isNotEmpty() && ext.length <= 4) T(ext, TgTheme.type.caption1, colors.onAccent, weight = FontWeight.Bold)
                        else Icon(TgIcons.AttFile, colors.onAccent, 26.dp)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 190.dp)) {
                T(f.name, TgTheme.type.subheadline, colors.text, weight = FontWeight.SemiBold, maxLines = 2)
                if (f.music && f.performer != null) T(f.performer, TgTheme.type.footnote, colors.meta, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val info = when {
                        downloading -> "${((ref?.let { repo.fileProgress(it) } ?: 0f) * 100).toInt()}% of ${f.size}"
                        playing -> "${formatDuration((player.positionMs / 1000).toInt())} / ${formatDuration(f.duration)}"
                        f.music && f.duration > 0 -> "${formatDuration(f.duration)} · ${f.size}"
                        else -> f.size
                    }
                    T(info, TgTheme.type.footnote, colors.meta)
                    if (f.caption == null) {
                        Spacer(Modifier.width(16.dp))
                        MetaRow(m, colors.meta)
                    }
                }
            }
        }
        if (f.caption != null) {
            TextWithMeta(
                richFor(m, f.caption, f.captionEntities, colors),
                TgTheme.type.body.copy(color = colors.text),
                { MetaRow(m, colors.meta) },
                Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
            )
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
        Icon(TgIcons.AttLocation, TgTheme.colors.destructive, 40.dp)
    }
    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        T(l.title, TgTheme.type.subheadline, colors.text, weight = FontWeight.SemiBold)
        TextWithMeta(l.address, TgTheme.type.footnote.copy(color = colors.meta), { MetaRow(m, colors.meta) })
    }
}

@Composable
private fun ContactBody(m: Message, ct: MessageContent.Contact, colors: BubbleColors) {
    val repo = com.abtin.tglass.features.main.LocalRepository.current
    val nav = com.abtin.tglass.core.navigation.LocalNavigator.current
    // A contact that is a Telegram user opens the chat with them.
    val openChat: () -> Unit = {
        if (ct.userId != 0L) nav.push(com.abtin.tglass.core.navigation.Route.Chat(repo.privateChatWith(ct.userId)))
    }
    Column(Modifier.padding(top = 8.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp).then(if (ct.userId != 0L) Modifier.fadeClickable(onClick = openChat) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(ct.name, if (ct.userId != 0L) ct.userId else ct.phone.hashCode().toLong(), 44.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                T(ct.name, TgTheme.type.subheadline, colors.accent, weight = FontWeight.SemiBold)
                T(ct.phone, TgTheme.type.footnote, colors.text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.padding(horizontal = 10.dp)) { Separator() }
        Box(Modifier.fillMaxWidth().height(36.dp).fadeClickable(onClick = openChat), contentAlignment = Alignment.Center) {
            T(if (ct.userId != 0L) "Message" else "View Contact", TgTheme.type.subheadline, colors.accent, weight = FontWeight.SemiBold)
        }
        Box(Modifier.align(Alignment.End).padding(end = 10.dp, bottom = 6.dp)) { MetaRow(m, colors.meta) }
    }
}

@Composable
private fun PollBody(m: Message, p: MessageContent.Poll, colors: BubbleColors, onVote: (Int) -> Unit) {
    val total = p.votes.sum().coerceAtLeast(1)
    Column(Modifier.width(260.dp).padding(horizontal = 10.dp, vertical = 8.dp)) {
        T(p.question, TgTheme.type.body, colors.text, weight = FontWeight.SemiBold)
        T(
            when {
                p.quiz -> if (p.anonymous) "Anonymous Quiz" else "Quiz"
                else -> if (p.anonymous) "Anonymous Poll" else "Public Poll"
            },
            TgTheme.type.footnote, colors.meta,
        )
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
                    Icon(IosIcons.Checkmark, colors.accent, 16.dp)
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
        BasicText(richFor(m, l.text, l.entities, colors), style = TgTheme.type.body.copy(color = colors.text))
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
