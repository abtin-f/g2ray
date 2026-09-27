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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.Entity
import com.abtin.tglass.data.EntityType
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.MessageStatus
import com.abtin.tglass.data.Reaction
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.TgImage
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
import kotlin.math.min
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

/**
 * Palette for one bubble side, taken from Telegram-iOS `DefaultDayPresentationTheme` / `DefaultDarkPresentationTheme`
 * (incoming / outgoing `PresentationThemePartedColors`).
 */
@Immutable
data class BubbleColors(
    val fill: Color,
    val text: Color,
    val meta: Color,
    val accent: Color,
    val link: Color,
    val gradient: List<Color>? = null,
    /** Play button, file icon, radio progress (accentControlColor). */
    val control: Color = accent,
    /** Glyph drawn on [control] (mediaControlInnerBackgroundColor). */
    val controlGlyph: Color = Color.White,
    /** Waveform part not played yet (mediaInactiveControlColor). */
    val inactive: Color = meta,
    val fileTitle: Color = text,
    val reactionBg: Color = accent.copy(alpha = 0.1f),
    val reactionFg: Color = accent,
    val reactionActiveBg: Color = accent,
    val reactionActiveFg: Color = Color.White,
    val pollBar: Color = accent,
    val radio: Color = meta,
    val separator: Color = meta.copy(alpha = 0.3f),
    val underlineLinks: Boolean = false,
) {
    /** Content drawn on top of an accent-filled control (play button, file icon, chosen reaction). */
    val onAccent: Color get() = controlGlyph
}

@Composable
fun bubbleColors(outgoing: Boolean): BubbleColors {
    val c = TgTheme.colors
    val white = Color.White
    // Day theme (Settings → Appearance): accent-colored outgoing bubbles with white content, like night ones.
    val lightStyle = !c.isDark && !(outgoing && c.bubbleOutAccent == white)
    return if (lightStyle) {
        if (outgoing) {
            val green = Color(0xFF3FC33B)
            BubbleColors(
                fill = c.bubbleOut, text = c.bubbleOutText, meta = c.bubbleOutMeta, accent = c.bubbleOutAccent, link = Color(0xFF004BAD),
                control = green, controlGlyph = c.bubbleOut, inactive = Color(0xFF93D987), fileTitle = Color(0xFF3FAA3C),
                reactionBg = green.copy(alpha = 0.14f), reactionFg = Color(0xFF2E9E2B), reactionActiveBg = green, reactionActiveFg = white,
                pollBar = Color(0xFF00A700), radio = Color(0xFF93D987), separator = Color(0xFF93D987).copy(alpha = 0.7f),
            )
        } else {
            BubbleColors(
                fill = c.bubbleIn, text = c.bubbleInText, meta = c.bubbleInMeta, accent = c.accent, link = Color(0xFF004BAD),
                control = c.accent, controlGlyph = white, inactive = Color(0xFFCACACA), fileTitle = Color(0xFF0B8BED),
                reactionBg = c.accent.copy(alpha = 0.1f), reactionFg = c.accent, reactionActiveBg = c.accent, reactionActiveFg = white,
                pollBar = c.accent, radio = Color(0xFFC8C7CC), separator = Color(0xFFC8C7CC).copy(alpha = 0.8f),
            )
        }
    } else {
        if (outgoing) {
            BubbleColors(
                fill = c.bubbleOut, text = white, meta = white.copy(alpha = 0.6f), accent = white, link = white, gradient = c.bubbleOutGradient,
                control = white, controlGlyph = Color(0xFF1E8EF7), inactive = white.copy(alpha = 0.45f), fileTitle = white,
                reactionBg = white.copy(alpha = 0.16f), reactionFg = white, reactionActiveBg = white, reactionActiveFg = Color(0xFF1E8EF7),
                pollBar = white, radio = white.copy(alpha = 0.55f), separator = white.copy(alpha = 0.3f), underlineLinks = true,
            )
        } else {
            val blue = Color(0xFF5AA9F8)
            BubbleColors(
                fill = c.bubbleIn, text = white, meta = white.copy(alpha = 0.5f), accent = blue, link = Color(0xFF71B7FA),
                control = c.accent, controlGlyph = white, inactive = c.accent.copy(alpha = 0.4f), fileTitle = white,
                reactionBg = white.copy(alpha = 0.09f), reactionFg = white, reactionActiveBg = white, reactionActiveFg = c.bubbleIn,
                pollBar = c.accent, radio = Color(0xFF737373), separator = white.copy(alpha = 0.14f),
            )
        }
    }
}

private val NameColorsDay = listOf(
    Color(0xFFD9483F), Color(0xFFE17A1C), Color(0xFF8D5BD6), Color(0xFF3FA535),
    Color(0xFF1E9FB5), Color(0xFF2F87DA), Color(0xFFD24E8C),
)
private val NameColorsNight = listOf(
    Color(0xFFFF7B72), Color(0xFFFFA958), Color(0xFFB794FF), Color(0xFF7ED36A),
    Color(0xFF5CD3E8), Color(0xFF6EB4FF), Color(0xFFFF84BD),
)

/** Telegram peer name color (the same index as the avatar gradient, so name and avatar match). */
@Composable
fun nameColor(seed: Long): Color {
    val list = if (TgTheme.colors.isDark) NameColorsNight else NameColorsDay
    val i = ((seed % list.size + list.size) % list.size).toInt()
    return list[i]
}

/**
 * Bubble background. Night outgoing bubbles use Telegram's screen-wide gradient: each bubble shows the part of
 * the #61BCF9 → #0088FF gradient behind its position, so bubbles darken towards the bottom of the screen.
 * The position is read only while drawing, so scrolling just redraws.
 */
@Composable
private fun Modifier.bubbleFill(shape: Shape, colors: BubbleColors): Modifier {
    val grad = colors.gradient
    if (grad == null || grad.size < 2) return this.background(colors.fill, shape)
    val screenH = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val y = remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { y.floatValue = it.positionInRoot().y }
        .drawBehind {
            val top = y.floatValue
            val brush = Brush.verticalGradient(grad, startY = -top, endY = screenH - top)
            drawOutline(shape.createOutline(size, layoutDirection, this), brush)
        }
}

/** Counts the emoji of an emoji-only text (0 if it has anything else or more than 3). */
private fun bigEmojiCount(text: String): Int {
    val t = text.trim()
    if (t.isEmpty() || t.length > 48) return 0
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(t)
    var count = 0
    var start = it.first()
    var end = it.next()
    while (end != android.icu.text.BreakIterator.DONE) {
        val g = t.substring(start, end)
        if (g.isNotBlank()) {
            if (!isEmojiGrapheme(g)) return 0
            count++
            if (count > 3) return 0
        }
        start = end
        end = it.next()
    }
    return count
}

private fun isEmojiGrapheme(g: String): Boolean {
    val cp = g.codePointAt(0)
    if (g.contains('⃣')) return true // keycaps 1️⃣
    if (Character.isLetterOrDigit(cp)) return false
    return cp >= 0x1F000 || cp in 0x2190..0x2BFF || cp in 0x2300..0x23FF || cp in 0x3030..0x303D ||
        cp == 0x3297 || cp == 0x3299 || cp == 0xA9 || cp == 0xAE || cp == 0x2122 ||
        Character.getType(cp) == Character.OTHER_SYMBOL.toInt()
}

/**
 * One message bubble, Telegram-iOS layout: sender name → forward header → reply header → content →
 * reactions (with the time inline when it fits). Albums ([album] = all messages of the album) become one
 * bubble with a photo grid.
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
    /** Corner radius override (the Appearance preview while its slider is dragged). */
    radiusOverride: Float? = null,
    /** All messages of the media album this bubble shows (empty for a single message). */
    album: List<Message> = emptyList(),
    onAlbumItemClick: (Long) -> Unit = {},
    onSenderClick: (() -> Unit)? = null,
) {
    val content = m.content
    val colors = bubbleColors(m.outgoing)
    if (content is MessageContent.Sticker) {
        StickerMessage(m, content, modifier, onReact)
        return
    }
    if (content is MessageContent.VideoNote) {
        VideoNoteMessage(m, content, replyTo, replyName, onReplyClick, modifier)
        return
    }
    if (content is MessageContent.Text && replyTo == null && m.forwardedFrom == null && album.isEmpty()) {
        val n = remember(content.text) { bigEmojiCount(content.text) }
        if (n > 0 && LocalAppSettings.current.largeEmoji) {
            BigEmojiMessage(m, content.text, n, modifier, onReact)
            return
        }
    }
    val radius = (radiusOverride ?: LocalAppSettings.current.bubbleRadius).dp
    val small = (radius.value / 2f).coerceAtLeast(4f).dp
    val shape = remember(m.outgoing, group, radius) {
        BubbleShape(m.outgoing, !group.groupedBottom, radius, small, group.groupedTop, group.groupedBottom)
    }
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val contentMax = maxWidth - TailWidth
    val textMax = contentMax - 22.dp
    val mediaMax = minOf(contentMax - 4.dp, screenW * 0.68f)
    val reactions = m.reactions
    val photos = album.filter { it.content is MessageContent.Photo }
    val isAlbumGrid = album.size > 1 && photos.size == album.size
    val albumCaption = if (isAlbumGrid) album.firstNotNullOfOrNull { (it.content as MessageContent.Photo).caption?.let { c -> it to c } } else null
    val hasHeader = senderName != null || replyTo != null || m.forwardedFrom != null

    // A photo/video without caption or header: the picture itself takes the bubble shape (tail included).
    if (content is MessageContent.Photo && album.size <= 1 && content.caption == null && !hasHeader && reactions.isEmpty()) {
        val size = mediaSize(content.aspect, mediaMax)
        Box(
            modifier
                .width(size.first + TailWidth)
                .height(size.second)
                .clip(shape)
                .background(colors.fill.copy(alpha = 0.5f)),
        ) {
            MediaTile(content, Modifier.fillMaxSize(), onMediaClick)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (m.outgoing) TailWidth + 7.dp else 7.dp, bottom = 7.dp)
            ) { MetaRow(m, Color.White, overlay = true) }
        }
        return
    }

    val showMeta = reactions.isEmpty()
    Column(
        modifier
            .widthIn(max = maxWidth)
            .width(IntrinsicSize.Max)
            .defaultMinSize(minWidth = 40.dp + TailWidth, minHeight = 35.dp)
            .bubbleFill(shape, colors)
            .padding(start = if (m.outgoing) 0.dp else TailWidth, end = if (m.outgoing) TailWidth else 0.dp)
    ) {
        val inner = Modifier.padding(horizontal = 11.dp)
        if (senderName != null) {
            T(
                senderName,
                TgTheme.type.subheadline.copy(fontSize = 14.sp, lineHeight = 18.sp, textDirection = TextDirection.Content),
                if (isChannel) colors.accent else nameColor(senderSeed),
                weight = FontWeight.SemiBold, maxLines = 1,
                modifier = inner.padding(top = 6.dp).then(if (onSenderClick != null) Modifier.fadeClickable(onClick = onSenderClick) else Modifier),
            )
        }
        if (m.forwardedFrom != null) {
            Column(inner.padding(top = if (senderName != null) 1.dp else 6.dp)) {
                T("Forwarded from", TgTheme.type.footnote.copy(fontSize = 14.sp, lineHeight = 17.sp), colors.accent, maxLines = 1)
                T(m.forwardedFrom, TgTheme.type.footnote.copy(fontSize = 14.sp, lineHeight = 17.sp, textDirection = TextDirection.Content), colors.accent, weight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        if (replyTo != null) {
            val replyColor = if (m.outgoing) colors.accent else if (replyTo.outgoing) colors.accent else nameColor(replyTo.senderId)
            ReplyHeader(
                replyName ?: "", replyTo, replyColor, colors,
                Modifier.padding(start = 8.dp, end = 8.dp, top = if (senderName != null || m.forwardedFrom != null) 4.dp else 7.dp).fadeClickable(onClick = onReplyClick),
            )
        }
        val textTop = if (hasHeader) 3.dp else 6.dp
        when {
            isAlbumGrid -> {
                val w = minOf(contentMax - 4.dp, screenW * 0.76f)
                AlbumGrid(
                    photos, w, radius, small, topFlat = hasHeader, bottomFlat = albumCaption != null || !showMeta,
                    onClick = onAlbumItemClick,
                    overlayMeta = if (albumCaption == null && showMeta) ({ MetaRow(m, Color.White, overlay = true) }) else null,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = if (hasHeader) 4.dp else 2.dp, bottom = if (albumCaption == null && showMeta) 2.dp else 0.dp),
                )
                if (albumCaption != null) {
                    val (cm, text) = albumCaption
                    val cc = cm.content as MessageContent.Photo
                    TextWithMeta(
                        richFor(m, text, cc.captionEntities, colors), bodyStyle(colors),
                        meta = { if (showMeta) MetaRow(m, colors.meta) },
                        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 6.dp),
                        maxTextWidth = w - 20.dp,
                    )
                }
            }
            album.size > 1 -> {
                // Documents / music sent together: stacked rows in one bubble.
                album.forEachIndexed { i, am ->
                    val c = am.content
                    if (c is MessageContent.File) FileBody(am, c, colors, showMeta = showMeta && i == album.lastIndex)
                    else if (c is MessageContent.Photo) PhotoBody(am, c, colors, mediaMax, radius, small, hasHeader || i > 0, showMeta && i == album.lastIndex, onClick = { onAlbumItemClick(am.id) })
                }
            }
            content is MessageContent.Text -> TextWithMeta(
                richFor(m, content.text, content.entities, colors),
                bodyStyle(colors),
                meta = { if (showMeta) MetaRow(m, colors.meta) },
                modifier = Modifier.fillMaxWidth().padding(start = 11.dp, end = 11.dp, top = textTop, bottom = 6.dp),
                maxTextWidth = textMax,
            )
            content is MessageContent.Photo -> PhotoBody(m, content, colors, mediaMax, radius, small, hasHeader, showMeta, onMediaClick)
            content is MessageContent.Voice -> VoiceBody(m, content, colors, showMeta)
            content is MessageContent.File -> FileBody(m, content, colors, showMeta)
            content is MessageContent.Location -> LocationBody(m, content, colors, mediaMax, radius, hasHeader, showMeta)
            content is MessageContent.Contact -> ContactBody(m, content, colors, showMeta)
            content is MessageContent.Poll -> PollBody(m, content, colors, onVote, minOf(contentMax, 300.dp), showMeta)
            content is MessageContent.Link -> LinkBody(m, content, colors, textMax, textTop, showMeta)
            content is MessageContent.Service -> T(content.text, bodyStyle(colors), colors.text, modifier = inner.padding(vertical = 6.dp))
            else -> {}
        }
        if (reactions.isNotEmpty()) {
            ReactionsWithMeta(
                reactions, colors,
                meta = { MetaRow(m, colors.meta) },
                onReact = onReact,
                modifier = Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = 5.dp, bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun bodyStyle(colors: BubbleColors): TextStyle =
    TgTheme.type.body.copy(color = colors.text, textDirection = TextDirection.Content)

/** Fits a picture of [aspect] (width / height) into the media box like Telegram (min 170×90, crop beyond). */
private fun mediaSize(aspect: Float, maxW: Dp): Pair<Dp, Dp> {
    val a = if (aspect.isNaN() || aspect <= 0f) 1f else aspect.coerceIn(0.4f, 3f)
    val maxH = maxW * 1.15f
    var w = maxW
    var h = w / a
    if (h > maxH) {
        h = maxH
        w = h * a
    }
    if (w < 170.dp) w = minOf(170.dp, maxW)
    if (h < 90.dp) h = 90.dp
    return w to h
}

@Composable
private fun ReplyHeader(name: String, replyTo: Message, color: Color, colors: BubbleColors, modifier: Modifier) {
    val thumb = (replyTo.content as? MessageContent.Photo)?.image
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedRectangle(6.dp))
            .background(color.copy(alpha = if (TgTheme.colors.isDark) 0.16f else 0.1f))
            .drawBehind { drawRect(color, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumb != null) {
            Spacer(Modifier.width(6.dp))
            TgImage(thumb, Modifier.size(32.dp).clip(RoundedRectangle(4.dp)), maxPx = 120)
        }
        Column(Modifier.padding(start = 7.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            val style = TgTheme.type.footnote.copy(fontSize = 14.sp, lineHeight = 17.sp, textDirection = TextDirection.Content)
            T(name, style, color, weight = FontWeight.SemiBold, maxLines = 1)
            T(replyTo.preview.replace('\n', ' '), style, colors.text, maxLines = 1)
        }
    }
}

/** Time + edited + views + delivery state; [overlay] puts it in a dark capsule (on media). */
@Composable
fun MetaRow(m: Message, color: Color, overlay: Boolean = false, overlayColor: Color = Color.Black.copy(alpha = 0.32f)) {
    val c = TgTheme.colors
    val style = TgTheme.type.caption2.copy(fontSize = 11.sp, lineHeight = 13.sp)
    val row = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.pinned) {
                Icon(TgIcons.MsgPinned, color, 11.dp)
                Spacer(Modifier.width(3.dp))
            }
            if (m.views != null) {
                Icon(IosIcons.Eye, color, 13.dp)
                Spacer(Modifier.width(2.dp))
                T(formatCount(m.views), style, color, maxLines = 1)
                Spacer(Modifier.width(5.dp))
            }
            if (m.edited) T("edited ", style, color, maxLines = 1)
            T(formatTime(m.date), style, color, maxLines = 1)
            if (m.outgoing) {
                Spacer(Modifier.width(2.dp))
                when (m.status) {
                    MessageStatus.Sending -> Icon(IosIcons.Clock, color, 12.dp)
                    MessageStatus.Sent -> Icon(IosIcons.CheckSingle, color, 14.dp)
                    MessageStatus.Read -> Icon(IosIcons.CheckDouble, color, 14.dp)
                    MessageStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, c.destructive, 15.dp)
                }
            }
        }
    }
    if (overlay) {
        Box(Modifier.clip(Capsule()).background(overlayColor).padding(horizontal = 6.dp, vertical = 2.dp)) { row() }
    } else row()
}

/**
 * Text followed by metadata that sits on the last line when it fits, otherwise on its own line —
 * how Telegram lays out timestamps. [maxTextWidth] lets the bubble ask for its exact width up front
 * (intrinsics), so the bubble is as narrow as Telegram's. Right-to-left text hugs the right edge.
 */
@Composable
fun TextWithMeta(text: String, style: TextStyle, meta: @Composable () -> Unit, modifier: Modifier = Modifier, maxTextWidth: Dp = Dp.Unspecified) =
    TextWithMeta(AnnotatedString(text), style, meta, modifier, maxTextWidth)

@Composable
fun TextWithMeta(text: AnnotatedString, style: TextStyle, meta: @Composable () -> Unit, modifier: Modifier = Modifier, maxTextWidth: Dp = Dp.Unspecified) {
    val holder = remember { arrayOfNulls<TextLayoutResult>(1) }
    val measurer = rememberTextMeasurer()
    val resolved = if (style.textDirection == TextDirection.Unspecified) style.copy(textDirection = TextDirection.Content) else style
    val policy = remember(text, resolved, maxTextWidth, measurer) { TextMetaPolicy(text, resolved, maxTextWidth, measurer, holder) }
    Layout(
        modifier = modifier,
        content = {
            BasicText(text, style = resolved, onTextLayout = { holder[0] = it })
            // Always one child here, even when [meta] draws nothing (0×0 then).
            Box { meta() }
        },
        measurePolicy = policy,
    )
}

private class MetaFit(val width: Int, val height: Int, val inline: Boolean, val textX: Int)

private fun fitMeta(l: TextLayoutResult, boxW: Int, boxH: Int, maxW: Int, minW: Int, metaW: Int, metaH: Int, gap: Int): MetaFit {
    if (l.lineCount == 0) return MetaFit(max(minW, metaW), max(boxH, metaH), true, 0)
    val last = l.lineCount - 1
    val rtl = l.getParagraphDirection(l.getLineStart(last)) == ResolvedTextDirection.Rtl
    if (metaW == 0) {
        val w = max(minW, boxW)
        return MetaFit(w, boxH, true, if (rtl) w - boxW else 0)
    }
    if (!rtl) {
        var widest = 0f
        for (i in 0 until l.lineCount) widest = max(widest, l.getLineRight(i))
        val textW = ceil(widest).toInt().coerceAtMost(boxW)
        val need = ceil(l.getLineRight(last)).toInt() + gap + metaW
        return if (need <= maxW) MetaFit(max(minW, max(textW, need)), max(boxH, metaH), true, 0)
        else MetaFit(max(minW, max(textW, metaW)), boxH + metaH, false, 0)
    }
    // Right-to-left: the lines hug the right edge of the text box; the time goes right of the box when it fits.
    val need = boxW + gap + metaW
    return if (need <= maxW) {
        val w = max(minW, need)
        MetaFit(w, max(boxH, metaH), true, w - need)
    } else {
        val w = max(minW, max(boxW, metaW))
        MetaFit(w, boxH + metaH, false, w - boxW)
    }
}

private class TextMetaPolicy(
    private val text: AnnotatedString,
    private val style: TextStyle,
    private val maxTextWidth: Dp,
    private val measurer: TextMeasurer,
    private val holder: Array<TextLayoutResult?>,
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val limit = if (maxTextWidth != Dp.Unspecified) maxTextWidth.roundToPx() else Constraints.Infinity
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else limit
        val tp = measurables[0].measure(Constraints(maxWidth = maxW.coerceAtLeast(1)))
        val mp = measurables[1].measure(Constraints())
        val gap = 6.dp.roundToPx()
        val l = holder[0]
        val fit = if (l != null) fitMeta(l, tp.width, tp.height, maxW, constraints.minWidth, mp.width, mp.height, gap)
        else MetaFit(max(constraints.minWidth, tp.width), tp.height + mp.height, false, 0)
        val width = fit.width.coerceAtMost(maxW).coerceAtLeast(constraints.minWidth)
        val height = fit.height.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            tp.place(fit.textX.coerceAtLeast(0), 0)
            mp.place(width - mp.width, height - mp.height + if (fit.inline && mp.height < tp.height) 1.dp.roundToPx() else 0)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val metaW = measurables[1].maxIntrinsicWidth(height)
        val gap = 6.dp.roundToPx()
        if (maxTextWidth == Dp.Unspecified) return measurables[0].maxIntrinsicWidth(height) + gap + metaW
        val limit = maxTextWidth.roundToPx().coerceAtLeast(1)
        val l = measurer.measure(text, style, constraints = Constraints(maxWidth = limit), layoutDirection = layoutDirection, density = this)
        val metaH = measurables[1].minIntrinsicHeight(metaW)
        return fitMeta(l, l.size.width, l.size.height, limit, 0, metaW, metaH, gap).width
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        max(measurables[0].minIntrinsicWidth(height), measurables[1].minIntrinsicWidth(height))
}

@Composable
private fun richFor(m: Message, text: String, entities: List<Entity>, colors: BubbleColors): AnnotatedString {
    var revealed by remember(m.id) { mutableStateOf(false) }
    return rememberRichText(
        text, entities,
        link = colors.link,
        codeBackground = colors.accent.copy(alpha = 0.13f),
        spoilerColor = colors.text.copy(alpha = 0.22f),
        spoilersRevealed = revealed,
        onRevealSpoiler = { revealed = true },
        underlineLinks = colors.underlineLinks,
    )
}

/** Picture (or video/GIF cover) with the play badge and duration. */
@Composable
private fun MediaTile(p: MessageContent.Photo, modifier: Modifier, onClick: () -> Unit) {
    val (a, b) = avatarColors(p.seed.toLong())
    val c = TgTheme.colors
    Box(
        modifier
            .background(
                if (p.image != null) Brush.linearGradient(listOf(c.secondaryText.copy(0.25f), c.secondaryText.copy(0.15f)))
                else Brush.linearGradient(listOf(a, b))
            )
            .iosClickable(highlight = Color.Black.copy(0.1f), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (p.image != null) TgImage(p.image, Modifier.matchParentSize(), maxPx = 900)
        else T(p.emoji, TgTheme.type.body.copy(fontSize = 56.sp, lineHeight = 64.sp))
        if (p.video) {
            if (!p.loop) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
                    Icon(IosIcons.Play, Color.White, 24.dp, Modifier.offset(x = 1.dp))
                }
            }
            if (p.duration > 0 || p.loop) {
                Box(Modifier.align(Alignment.TopStart).padding(7.dp).clip(Capsule()).background(Color.Black.copy(0.4f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    T(if (p.loop) "GIF" else formatDuration(p.duration), TgTheme.type.caption2.copy(fontSize = 11.sp), Color.White, weight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun PhotoBody(
    m: Message, p: MessageContent.Photo, colors: BubbleColors, maxMedia: Dp, radius: Dp, small: Dp,
    topFlat: Boolean, showMeta: Boolean, onClick: () -> Unit,
) {
    val size = mediaSize(p.aspect, maxMedia)
    val r = (radius - 2.dp).coerceAtLeast(4.dp)
    val bottomR = if (p.caption != null || !showMeta) (small - 2.dp).coerceAtLeast(3.dp) else r
    val topR = if (topFlat) (small - 2.dp).coerceAtLeast(3.dp) else r
    Box(Modifier.padding(start = 2.dp, end = 2.dp, top = if (topFlat) 4.dp else 2.dp, bottom = if (p.caption == null && showMeta) 2.dp else 0.dp)) {
        MediaTile(
            p,
            Modifier.width(size.first).height(size.second).clip(RoundedCornerShape(topR, topR, bottomR, bottomR)),
            onClick,
        )
        if (p.caption == null && showMeta) {
            Box(Modifier.align(Alignment.BottomEnd).padding(7.dp)) { MetaRow(m, Color.White, overlay = true) }
        }
    }
    if (p.caption != null) {
        TextWithMeta(
            richFor(m, p.caption, p.captionEntities, colors), bodyStyle(colors),
            meta = { if (showMeta) MetaRow(m, colors.meta) },
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 6.dp),
            maxTextWidth = size.first - 16.dp,
        )
    }
}

/** A tile of an album grid, in dp relative to the grid. */
private class Tile(val x: Float, val y: Float, val w: Float, val h: Float)

/** Telegram-like mosaic for 2…10 album items: special layouts for 2–4, rows of 2/3 beyond. */
private fun albumLayout(aspects: List<Float>, width: Float, gap: Float): Pair<List<Tile>, Float> {
    val a = aspects.map { if (it.isNaN() || it <= 0f) 1f else it.coerceIn(0.5f, 2.5f) }
    val tiles = ArrayList<Tile>()
    fun row(items: List<Float>, y: Float): Float {
        val avail = width - gap * (items.size - 1)
        val h = (avail / items.sum()).coerceIn(width * 0.22f, width * 0.8f)
        val total = items.sumOf { (it * h).toDouble() }.toFloat()
        var x = 0f
        items.forEachIndexed { i, it ->
            val w = if (i == items.lastIndex) width - x else it * h * avail / total
            tiles += Tile(x, y, w, h)
            x += w + gap
        }
        return h
    }
    fun column(left: Float, items: List<Float>, leftFrac: Float): Float {
        val leftW = (width - gap) * leftFrac
        val rightW = width - gap - leftW
        val h = (leftW / left).coerceIn(width * 0.55f, width * 1.25f)
        tiles += Tile(0f, 0f, leftW, h)
        val each = (h - gap * (items.size - 1)) / items.size
        items.indices.forEach { i -> tiles += Tile(leftW + gap, i * (each + gap), rightW, each) }
        return h
    }
    val height = when (a.size) {
        0 -> 0f
        1 -> { val h = (width / a[0]).coerceIn(width * 0.5f, width * 1.15f); tiles += Tile(0f, 0f, width, h); h }
        2 -> if (a[0] > 1.2f && a[1] > 1.2f) {
            val h0 = (width / a[0]).coerceIn(width * 0.35f, width * 0.7f)
            val h1 = (width / a[1]).coerceIn(width * 0.35f, width * 0.7f)
            tiles += Tile(0f, 0f, width, h0)
            tiles += Tile(0f, h0 + gap, width, h1)
            h0 + gap + h1
        } else row(a, 0f)
        3 -> if (a[0] > 1.1f) {
            val h0 = (width / a[0]).coerceIn(width * 0.4f, width * 0.72f)
            tiles += Tile(0f, 0f, width, h0)
            h0 + gap + row(a.subList(1, 3), h0 + gap)
        } else column(a[0], a.subList(1, 3), 0.62f)
        4 -> if (a[0] > 1.1f) {
            val h0 = (width / a[0]).coerceIn(width * 0.4f, width * 0.72f)
            tiles += Tile(0f, 0f, width, h0)
            h0 + gap + row(a.subList(1, 4), h0 + gap)
        } else column(a[0], a.subList(1, 4), 0.66f)
        else -> {
            val sizes = when (a.size) {
                5 -> listOf(2, 3)
                6 -> listOf(3, 3)
                7 -> listOf(2, 2, 3)
                8 -> listOf(2, 3, 3)
                9 -> listOf(3, 3, 3)
                else -> listOf(2, 2, 3, 3)
            }
            var y = 0f
            var from = 0
            sizes.forEachIndexed { i, n ->
                val to = min(from + n, a.size)
                if (from < to) {
                    val h = row(a.subList(from, to), y)
                    y += h + if (i < sizes.lastIndex) gap else 0f
                }
                from = to
            }
            y
        }
    }
    return tiles to height
}

@Composable
private fun AlbumGrid(
    items: List<Message>,
    width: Dp,
    radius: Dp,
    small: Dp,
    topFlat: Boolean,
    bottomFlat: Boolean,
    onClick: (Long) -> Unit,
    overlayMeta: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shown = items.take(10)
    val gap = 2f
    val (tiles, height) = remember(shown.map { it.id }, width) {
        albumLayout(shown.map { (it.content as MessageContent.Photo).aspect }, width.value, gap)
    }
    val r = (radius - 2.dp).coerceAtLeast(4.dp)
    val sr = (small - 2.dp).coerceAtLeast(3.dp)
    val top = if (topFlat) sr else r
    val bottom = if (bottomFlat) sr else r
    Box(modifier.width(width).height(height.dp).clip(RoundedCornerShape(top, top, bottom, bottom))) {
        shown.forEachIndexed { i, msg ->
            val t = tiles.getOrNull(i) ?: return@forEachIndexed
            MediaTile(
                msg.content as MessageContent.Photo,
                Modifier.offset(x = t.x.dp, y = t.y.dp).width(t.w.dp).height(t.h.dp),
            ) { onClick(msg.id) }
        }
        if (overlayMeta != null) Box(Modifier.align(Alignment.BottomEnd).padding(7.dp)) { overlayMeta() }
    }
}

@Composable
private fun StickerMessage(m: Message, s: MessageContent.Sticker, modifier: Modifier, onReact: (String) -> Unit) {
    val repo = LocalRepository.current
    val anim = s.animation
    val animPath = anim?.let { repo.filePath(it) }
    LaunchedEffect(anim, animPath) { if (anim != null && animPath == null) repo.requestImage(anim) }
    Column(modifier.padding(horizontal = 4.dp), horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        val still: @Composable () -> Unit = {
            if (s.image != null) TgImage(s.image, Modifier.size(160.dp), maxPx = 512, contentScale = ContentScale.Fit)
            else BasicText(s.emoji, style = TextStyle(fontSize = 110.sp, lineHeight = 124.sp))
        }
        if (anim != null) com.abtin.tglass.ui.components.TgsSticker(animPath, Modifier.size(160.dp), still)
        else still()
        FreeformFooter(m, onReact)
    }
}

/** 1–3 emoji without a bubble, big — like Telegram. */
@Composable
private fun BigEmojiMessage(m: Message, text: String, count: Int, modifier: Modifier, onReact: (String) -> Unit) {
    val size = when (count) { 1 -> 64; 2 -> 50; else -> 42 }
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        BasicText(text.trim(), style = TextStyle(fontSize = size.sp, lineHeight = (size * 1.2f).sp))
        FreeformFooter(m, onReact)
    }
}

/** Time capsule (and reactions) under a sticker / big emoji, on the service background. */
@Composable
private fun FreeformFooter(m: Message, onReact: (String) -> Unit) {
    val c = TgTheme.colors
    if (m.reactions.isNotEmpty()) {
        val colors = BubbleColors(
            fill = c.serviceBubble, text = Color.White, meta = Color.White, accent = Color.White, link = Color.White,
            reactionBg = c.serviceBubble, reactionFg = Color.White, reactionActiveBg = Color.White, reactionActiveFg = Color.Black,
        )
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            m.reactions.forEach { r -> ReactionChip(r, colors, onReact) }
        }
    }
    Box(Modifier.padding(top = 4.dp)) { MetaRow(m, Color.White, overlay = true, overlayColor = c.serviceBubble) }
}

@Composable
private fun VoiceBody(m: Message, v: MessageContent.Voice, colors: BubbleColors, showMeta: Boolean) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val player = com.abtin.tglass.core.media.VoicePlayer
    val key = "${m.chatId}:${m.id}"
    val current = player.currentKey == key
    val playing = current && player.playing
    val progress = if (current) player.progress else 0f
    val media = v.media
    val path = media?.let { repo.filePath(it) }
    // Tapped before the file was downloaded: start playing as soon as it arrives.
    var pending by remember(key) { mutableStateOf(false) }
    LaunchedEffect(pending, path) {
        if (pending && path != null) {
            pending = false
            player.toggle(context, key, path, v.seconds)
        }
    }
    val loading = pending && path == null
    val waveWidth = (100 + v.seconds * 4).coerceIn(120, 190).dp
    Row(Modifier.padding(start = 7.dp, end = 10.dp, top = 7.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(colors.control).bounceClickable {
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
                    Canvas(Modifier.size(36.dp)) {
                        drawArc(colors.controlGlyph, -90f, 360f * p.coerceAtLeast(0.05f), false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Icon(IosIcons.Close, colors.controlGlyph, 14.dp)
                }
                playing -> Icon(IosIcons.Pause, colors.controlGlyph, 20.dp)
                else -> Icon(IosIcons.Play, colors.controlGlyph, 22.dp, Modifier.offset(x = 1.5.dp))
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.width(IntrinsicSize.Max)) {
            Canvas(Modifier.width(waveWidth).height(20.dp)) {
                val barW = 2.dp.toPx()
                val step = 3.dp.toPx()
                val bars = (size.width / step).toInt().coerceAtLeast(1)
                val wave = v.waveform
                val played = progress * bars
                for (i in 0 until bars) {
                    val amp = if (wave.isEmpty()) 0.3f else wave[(i * wave.size / bars).coerceIn(0, wave.lastIndex)]
                    val bh = (amp.coerceIn(0f, 1f) * size.height).coerceAtLeast(2.dp.toPx())
                    val f = (played - i).coerceIn(0f, 1f)
                    val color = if (f >= 1f) colors.control else if (f <= 0f) colors.inactive else androidx.compose.ui.graphics.lerp(colors.inactive, colors.control, f)
                    drawRoundRect(
                        color,
                        topLeft = Offset(i * step, size.height - bh),
                        size = Size(barW, bh),
                        cornerRadius = CornerRadius(barW / 2f),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val secs = if (current) (player.positionMs / 1000).toInt() else v.seconds
                T(formatDuration(secs), TgTheme.type.caption1.copy(fontSize = 12.sp), colors.meta, maxLines = 1)
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    VoiceSpeedButton(player.speed, colors) { player.cycleSpeed() }
                } else if (!m.outgoing && m.content is MessageContent.Voice && media != null && path == null) {
                    // Not listened yet: Telegram's small dot next to the duration.
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(colors.control))
                }
                Spacer(Modifier.weight(1f).widthIn(min = 10.dp))
                if (showMeta) MetaRow(m, colors.meta)
            }
        }
    }
}

/** iOS 1x / 1.5x / 2x speed pill, shown while a voice note is the current one. */
@Composable
private fun VoiceSpeedButton(speed: Float, colors: BubbleColors, onClick: () -> Unit) {
    val label = if (speed == 1.5f) "1.5x" else "${speed.roundToInt()}x"
    Box(
        Modifier
            .height(16.dp)
            .clip(RoundedRectangle(5.dp))
            .border(1.2.dp, colors.control, RoundedRectangle(5.dp))
            .fadeClickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(label, TgTheme.type.caption2.copy(fontSize = 10.sp, lineHeight = 12.sp), colors.control, weight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun FileBody(m: Message, f: MessageContent.File, colors: BubbleColors, showMeta: Boolean = true) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val toast = com.abtin.tglass.ui.components.LocalToast.current
    val player = com.abtin.tglass.core.media.VoicePlayer
    val key = "${m.chatId}:${m.id}"
    val ref = f.file
    val path = ref?.let { repo.filePath(it) }
    // Tapped while not downloaded yet: act (open / play) as soon as the file arrives.
    var pending by remember(key) { mutableStateOf(false) }
    fun act(p: String) {
        if (f.music) player.toggle(context, key, p, f.duration)
        else if (!com.abtin.tglass.core.media.Files.open(context, p, f.mime)) toast.show("No app can open this file")
    }
    LaunchedEffect(pending, path) {
        if (pending && path != null) {
            pending = false
            act(path)
        }
    }
    val downloading = pending && path == null
    val playing = f.music && player.currentKey == key && player.playing
    Column {
        Row(Modifier.padding(start = 8.dp, end = 11.dp, top = 8.dp, bottom = if (f.caption != null) 2.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.control)
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
                            drawArc(colors.controlGlyph, -90f, 360f * progress.coerceAtLeast(0.05f), false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Icon(IosIcons.Close, colors.controlGlyph, 14.dp)
                    }
                    f.music -> Icon(if (playing) IosIcons.Pause else IosIcons.Play, colors.controlGlyph, 22.dp)
                    ref != null && path == null -> Icon(IosIcons.ArrowDown, colors.controlGlyph, 22.dp)
                    else -> Icon(TgIcons.AttFile, colors.controlGlyph, 24.dp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 200.dp)) {
                T(f.name, TgTheme.type.body.copy(fontSize = 16.sp, lineHeight = 20.sp, textDirection = TextDirection.Content), colors.fileTitle, weight = FontWeight.SemiBold, maxLines = 2)
                if (f.music && f.performer != null) T(f.performer, TgTheme.type.footnote, colors.meta, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val info = when {
                        downloading -> "${((ref?.let { repo.fileProgress(it) } ?: 0f) * 100).toInt()}% of ${f.size}"
                        playing -> "${formatDuration((player.positionMs / 1000).toInt())} / ${formatDuration(f.duration)}"
                        f.music && f.duration > 0 -> "${formatDuration(f.duration)} · ${f.size}"
                        else -> f.size
                    }
                    T(info, TgTheme.type.footnote, colors.meta, maxLines = 1)
                    if (f.caption == null && showMeta) {
                        Spacer(Modifier.weight(1f).widthIn(min = 14.dp))
                        MetaRow(m, colors.meta)
                    }
                }
            }
        }
        if (f.caption != null) {
            TextWithMeta(
                richFor(m, f.caption, f.captionEntities, colors), bodyStyle(colors),
                meta = { if (showMeta) MetaRow(m, colors.meta) },
                modifier = Modifier.fillMaxWidth().padding(start = 11.dp, end = 11.dp, bottom = 6.dp),
                maxTextWidth = 264.dp,
            )
        }
    }
}

@Composable
private fun LocationBody(m: Message, l: MessageContent.Location, colors: BubbleColors, maxMedia: Dp, radius: Dp, topFlat: Boolean, showMeta: Boolean) {
    val dark = TgTheme.colors.isDark
    val r = (radius - 2.dp).coerceAtLeast(4.dp)
    Box(
        Modifier
            .padding(start = 2.dp, end = 2.dp, top = if (topFlat) 4.dp else 2.dp)
            .width(minOf(maxMedia, 260.dp))
            .height(140.dp)
            .clip(RoundedCornerShape(if (topFlat) 4.dp else r, if (topFlat) 4.dp else r, 4.dp, 4.dp)),
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        T(l.title, TgTheme.type.subheadline.copy(textDirection = TextDirection.Content), colors.text, weight = FontWeight.SemiBold)
        TextWithMeta(l.address, TgTheme.type.footnote.copy(color = colors.meta), { if (showMeta) MetaRow(m, colors.meta) })
    }
}

@Composable
private fun ContactBody(m: Message, ct: MessageContent.Contact, colors: BubbleColors, showMeta: Boolean) {
    val repo = LocalRepository.current
    val nav = com.abtin.tglass.core.navigation.LocalNavigator.current
    // A contact that is a Telegram user opens the chat with them.
    val openChat: () -> Unit = {
        if (ct.userId != 0L) nav.push(com.abtin.tglass.core.navigation.Route.Chat(repo.privateChatWith(ct.userId)))
    }
    Column(Modifier.padding(top = 9.dp).widthIn(min = 220.dp)) {
        Row(
            Modifier.padding(horizontal = 10.dp).then(if (ct.userId != 0L) Modifier.fadeClickable(onClick = openChat) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(ct.name, if (ct.userId != 0L) ct.userId else ct.phone.hashCode().toLong(), 44.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                T(ct.name, TgTheme.type.body.copy(fontSize = 16.sp, textDirection = TextDirection.Content), colors.fileTitle, weight = FontWeight.SemiBold, maxLines = 1)
                T(ct.phone, TgTheme.type.footnote, colors.meta, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(0.6.dp).background(colors.separator))
        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).fillMaxHeight().fadeClickable(onClick = openChat), contentAlignment = Alignment.Center) {
                T(if (ct.userId != 0L) "Message" else "View Contact", TgTheme.type.subheadline, colors.accent, weight = FontWeight.SemiBold)
            }
        }
        if (showMeta) Box(Modifier.align(Alignment.End).padding(end = 10.dp, bottom = 6.dp)) { MetaRow(m, colors.meta) }
    }
}

@Composable
private fun PollBody(m: Message, p: MessageContent.Poll, colors: BubbleColors, onVote: (Int) -> Unit, width: Dp, showMeta: Boolean) {
    val total = p.votes.sum()
    val voted = p.voted != null
    val maxVotes = p.votes.maxOrNull()?.coerceAtLeast(1) ?: 1
    val textStyle = TgTheme.type.body.copy(fontSize = 16.sp, lineHeight = 21.sp, textDirection = TextDirection.Content)
    Column(Modifier.width(width).padding(start = 11.dp, end = 11.dp, top = 7.dp, bottom = 6.dp)) {
        T(p.question, textStyle, colors.text, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        T(
            when {
                p.quiz -> if (p.anonymous) "Anonymous Quiz" else "Quiz"
                else -> if (p.anonymous) "Anonymous Poll" else "Public Poll"
            },
            TgTheme.type.footnote, colors.meta,
        )
        Spacer(Modifier.height(6.dp))
        p.options.forEachIndexed { i, opt ->
            val count = p.votes.getOrElse(i) { 0 }
            val pct = if (total > 0) (count * 100f / total).roundToInt() else 0
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).then(if (!voted) Modifier.fadeClickable { onVote(i) } else Modifier),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.width(34.dp).fillMaxHeight().padding(top = 9.dp, bottom = 2.dp)) {
                    if (!voted) Box(Modifier.size(21.dp).border(1.2.dp, colors.radio, CircleShape))
                    else {
                        T("$pct%", TgTheme.type.footnote.copy(fontSize = 13.sp), colors.text, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.fillMaxWidth(), align = TextAlign.End)
                        if (p.voted == i) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(15.dp).clip(CircleShape).background(colors.pollBar),
                                contentAlignment = Alignment.Center,
                            ) { Icon(IosIcons.Checkmark, colors.fill, 10.dp) }
                        }
                    }
                }
                Spacer(Modifier.width(if (voted) 8.dp else 4.dp))
                Column(Modifier.weight(1f)) {
                    Spacer(Modifier.height(9.dp))
                    T(opt, textStyle, colors.text)
                    Spacer(Modifier.height(7.dp))
                    if (voted) {
                        val frac = (count.toFloat() / maxVotes).coerceIn(0f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth(frac.coerceAtLeast(0.03f))
                                .height(4.dp)
                                .clip(Capsule())
                                .background(if (p.voted == i) colors.pollBar else colors.pollBar.copy(alpha = 0.6f))
                        )
                        Spacer(Modifier.height(2.dp))
                    } else {
                        Box(Modifier.fillMaxWidth().height(0.6.dp).background(colors.separator))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth()) {
            T(
                if (total == 0) "No votes" else "${formatCount(total)} ${if (total == 1) "vote" else "votes"}",
                TgTheme.type.footnote, colors.meta, modifier = Modifier.align(Alignment.Center),
            )
            if (showMeta) Box(Modifier.align(Alignment.CenterEnd)) { MetaRow(m, colors.meta) }
        }
    }
}

@Composable
private fun LinkBody(m: Message, l: MessageContent.Link, colors: BubbleColors, textMax: Dp, top: Dp, showMeta: Boolean) {
    val handler = LocalLinkHandler.current
    val entities = l.entities.ifEmpty { detectEntities(l.text) }
    val url = entities.firstOrNull { it.type == EntityType.TextUrl }?.url
        ?: entities.firstOrNull { it.type == EntityType.Url }?.let { e -> l.text.substring(e.start.coerceIn(0, l.text.length), e.end.coerceIn(0, l.text.length)) }
    Column(Modifier.padding(start = 11.dp, end = 11.dp, top = top, bottom = 6.dp).widthIn(max = textMax)) {
        BasicText(richFor(m, l.text, l.entities, colors), style = bodyStyle(colors))
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedRectangle(6.dp))
                .background(colors.accent.copy(alpha = if (TgTheme.colors.isDark) 0.16f else 0.1f))
                .drawBehind { drawRect(colors.accent, size = Size(3.dp.toPx(), size.height)) }
                .then(if (url != null) Modifier.fadeClickable { handler(Entity(0, url.length, EntityType.Url), url) } else Modifier)
                .padding(start = 11.dp, end = 8.dp, top = 5.dp, bottom = 6.dp)
        ) {
            val style = TgTheme.type.footnote.copy(fontSize = 14.sp, lineHeight = 18.sp, textDirection = TextDirection.Content)
            if (l.site.isNotBlank()) T(l.site, style, colors.accent, weight = FontWeight.SemiBold, maxLines = 1)
            if (l.title.isNotBlank()) T(l.title, style, colors.text, weight = FontWeight.SemiBold, maxLines = 2)
            if (l.description.isNotBlank()) T(l.description, style, colors.text, maxLines = 4)
        }
        if (showMeta) Box(Modifier.align(Alignment.End).padding(top = 4.dp)) { MetaRow(m, colors.meta) }
    }
}

@Composable
private fun ReactionChip(r: Reaction, colors: BubbleColors, onReact: (String) -> Unit) {
    Row(
        Modifier
            .height(28.dp)
            .clip(Capsule())
            .background(if (r.chosen) colors.reactionActiveBg else colors.reactionBg)
            .bounceClickable { onReact(r.emoji) }
            .padding(start = 7.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(r.emoji, style = TextStyle(fontSize = 15.sp, lineHeight = 18.sp))
        Spacer(Modifier.width(4.dp))
        T(formatCount(r.count), TgTheme.type.footnote.copy(fontSize = 13.sp), if (r.chosen) colors.reactionActiveFg else colors.reactionFg, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** iOS reactions: small capsules wrapping inside the bubble, with the time on the last row when it fits. */
@Composable
private fun ReactionsWithMeta(
    reactions: List<Reaction>,
    colors: BubbleColors,
    meta: @Composable () -> Unit,
    onReact: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            reactions.forEach { r -> ReactionChip(r, colors, onReact) }
            meta()
        },
        measurePolicy = ReactionsPolicy,
    )
}

private object ReactionsPolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val spacing = 6.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val chips = measurables.dropLast(1).map { it.measure(Constraints(maxWidth = maxW)) }
        val mp = measurables.last().measure(Constraints())
        val positions = ArrayList<Pair<Int, Int>>()
        var x = 0
        var y = 0
        var rowH = 0
        var widest = 0
        chips.forEach { p ->
            if (x > 0 && x + p.width > maxW) {
                y += rowH + spacing
                x = 0
                rowH = 0
            }
            positions += x to y
            x += p.width + spacing
            rowH = max(rowH, p.height)
            widest = max(widest, x - spacing)
        }
        val lastRowW = (x - spacing).coerceAtLeast(0)
        val inline = lastRowW + gap + mp.width <= maxW
        val width = max(constraints.minWidth, if (inline) max(widest, lastRowW + gap + mp.width) else max(widest, mp.width)).coerceAtMost(maxW)
        val height = if (inline) y + max(rowH, mp.height) else y + rowH + 4.dp.roundToPx() + mp.height
        return layout(width, height) {
            chips.forEachIndexed { i, p -> p.place(positions[i].first, positions[i].second) }
            if (inline) mp.place(width - mp.width, y + rowH - mp.height - 1.dp.roundToPx())
            else mp.place(width - mp.width, height - mp.height)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val spacing = 6.dp.roundToPx()
        val chips = measurables.dropLast(1).sumOf { it.maxIntrinsicWidth(height) + spacing }
        return chips - spacing + 8.dp.roundToPx() + measurables.last().maxIntrinsicWidth(height)
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0
}

/** Centered service capsule (joins, pins, date separators). Multi-line texts wrap centered. */
@Composable
fun ServicePill(text: String, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    Box(modifier.fillMaxWidth().padding(vertical = 5.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedRectangle(11.dp))
                .background(c.serviceBubble)
                .padding(horizontal = 9.dp, vertical = 3.dp)
        ) {
            T(
                text,
                TgTheme.type.footnote.copy(fontSize = 13.sp, lineHeight = 17.sp, textDirection = TextDirection.Content),
                Color.White, weight = FontWeight.Medium, align = TextAlign.Center,
            )
        }
    }
}
