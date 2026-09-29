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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.Placeholder
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
import com.abtin.tglass.core.emoji.EmojiGlyph
import com.abtin.tglass.core.emoji.appleEmojiInlineContent
import com.abtin.tglass.core.emoji.appleEmojiPlaceholders
import com.abtin.tglass.core.emoji.rememberAppleEmojiText
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
    /** Poll voting / results (multiple answers, View Results); null = single votes through [onVote]. */
    pollActions: PollActions? = null,
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
    // Telegram-iOS media: at most ~70 % of the screen / 280 pt wide, never wider than the bubble allows.
    val mediaMax = minOf(contentMax - 4.dp, screenW * 0.7f, 280.dp)
    val reactions = m.reactions
    val photos = album.filter { it.content is MessageContent.Photo }
    val isAlbumGrid = album.size > 1 && photos.size == album.size
    val albumCaption = if (isAlbumGrid) album.firstNotNullOfOrNull { (it.content as MessageContent.Photo).caption?.let { c -> it to c } } else null
    val hasHeader = senderName != null || replyTo != null || m.forwardedFrom != null
    val text = bubbleText()

    // A photo/video without caption or header: the picture itself takes the bubble shape (tail included).
    if (content is MessageContent.Photo && album.size <= 1 && content.caption == null && !hasHeader && reactions.isEmpty() && m.comments == null) {
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
    // Widest the content itself gets: headers / reactions wrap or truncate inside it instead of widening the bubble.
    val albumW = mediaMax
    val contentW: Dp = when {
        isAlbumGrid -> albumW + 4.dp
        album.size > 1 -> minOf(contentMax, 300.dp)
        content is MessageContent.Photo -> mediaSize(content.aspect, mediaMax).first + 4.dp
        content is MessageContent.Location -> minOf(mediaMax, 260.dp) + 4.dp
        else -> contentMax
    }
    // Headers alone never make a short message's bubble wider than this.
    val headerCap = if (contentW < contentMax) contentW else minOf(contentMax, 250.dp)
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
            CapWidth(headerCap) {
                com.abtin.tglass.core.emoji.EmojiText(
                    senderName,
                    text.header,
                    if (isChannel) colors.accent else nameColor(senderSeed),
                    weight = FontWeight.SemiBold, maxLines = 1,
                    modifier = inner.padding(top = 6.dp).then(if (onSenderClick != null) Modifier.fadeClickable(onClick = onSenderClick) else Modifier),
                )
            }
        }
        if (m.forwardedFrom != null) {
            CapWidth(headerCap) {
                ForwardHeader(m, colors, inner.padding(top = if (senderName != null) 2.dp else 6.dp))
            }
        }
        if (replyTo != null) {
            val replyColor = if (m.outgoing) colors.accent else if (replyTo.outgoing) colors.accent else nameColor(replyTo.senderId)
            CapWidth(headerCap) {
                ReplyHeader(
                    replyName ?: "", replyTo, replyColor, colors,
                    Modifier.padding(start = 8.dp, end = 8.dp, top = if (senderName != null || m.forwardedFrom != null) 4.dp else 7.dp).fadeClickable(onClick = onReplyClick),
                )
            }
        }
        val textTop = if (hasHeader) 3.dp else 6.dp
        when {
            isAlbumGrid -> {
                val w = albumW
                AlbumGrid(
                    photos, w, radius, small, topFlat = hasHeader, bottomFlat = albumCaption != null || !showMeta,
                    onClick = onAlbumItemClick,
                    overlayMeta = if (albumCaption == null && showMeta) ({ MetaRow(m, Color.White, overlay = true) }) else null,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = if (hasHeader) 4.dp else 2.dp, bottom = if (albumCaption == null && showMeta) 2.dp else 0.dp),
                )
                if (albumCaption != null) {
                    val (cm, captionText) = albumCaption
                    val cc = cm.content as MessageContent.Photo
                    TextWithMeta(
                        richFor(m, captionText, cc.captionEntities, colors), bodyStyle(colors),
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
            content is MessageContent.Voice -> VoiceMessageBody(m, content, colors, showMeta)
            content is MessageContent.File -> FileBody(m, content, colors, showMeta)
            content is MessageContent.Location -> LocationBody(m, content, colors, mediaMax, radius, hasHeader, showMeta)
            content is MessageContent.Contact -> ContactBody(m, content, colors, showMeta)
            content is MessageContent.Poll -> PollMessageBody(
                m, content, colors,
                pollActions ?: PollActions(vote = { ids -> ids.firstOrNull()?.let(onVote) }, viewResults = {}),
                minOf(contentMax, 300.dp), showMeta,
            )
            content is MessageContent.Link -> LinkBody(m, content, colors, textMax, textTop, showMeta)
            content is MessageContent.Service -> T(content.text, bodyStyle(colors), colors.text, modifier = inner.padding(vertical = 6.dp))
            else -> {}
        }
        if (reactions.isNotEmpty()) {
            // Reactions wrap into rows inside the content's width (Telegram iOS) — they never stretch the bubble.
            ReactionsWithMeta(
                reactions, colors,
                meta = { MetaRow(m, colors.meta) },
                onReact = onReact,
                limit = contentW - 20.dp,
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 7.dp),
            )
        }
        if (m.comments != null) CommentsBar(m, colors)
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
            TgImage(thumb, Modifier.size(30.dp).clip(RoundedRectangle(4.dp)), maxPx = 120)
        }
        Column(Modifier.padding(start = 7.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            val style = bubbleText().header
            com.abtin.tglass.core.emoji.EmojiText(name, style, color, weight = FontWeight.SemiBold, maxLines = 1)
            T(replyTo.preview.replace('\n', ' '), style, colors.text, maxLines = 1)
        }
    }
}

/** Time + edited + views + delivery state; [overlay] puts it in a dark capsule (on media). */
@Composable
fun MetaRow(m: Message, color: Color, overlay: Boolean = false, overlayColor: Color = Color.Black.copy(alpha = 0.32f)) {
    val c = TgTheme.colors
    val style = bubbleText().meta
    val scale = LocalAppSettings.current.textScale
    // Telegram-iOS ChatMessageDateAndStatusNode: 11 pt date, status glyphs ~13 pt, all scaled with the text size.
    fun sz(v: Float) = (v * scale).dp
    val row = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (m.pinned) {
                Icon(TgIcons.MsgPinned, color, sz(10f))
                Spacer(Modifier.width(3.dp))
            }
            if (m.views != null) {
                Icon(IosIcons.Eye, color, sz(12f))
                Spacer(Modifier.width(2.dp))
                T(formatCount(m.views), style, color, maxLines = 1)
                Spacer(Modifier.width(5.dp))
            }
            if (m.edited) T("edited ", style, color, maxLines = 1)
            T(formatTime(m.date), style, color, maxLines = 1)
            if (m.outgoing) {
                Spacer(Modifier.width(2.dp))
                when (m.status) {
                    MessageStatus.Sending -> Icon(IosIcons.Clock, color, sz(11f))
                    MessageStatus.Sent -> Icon(IosIcons.CheckSingle, color, sz(13f))
                    MessageStatus.Read -> Icon(IosIcons.CheckDouble, color, sz(13f))
                    MessageStatus.Failed -> Icon(Icons.Rounded.ErrorOutline, c.destructive, sz(14f))
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
    // Inline Apple emoji (added by rememberRichText) need their placeholders when measuring too.
    val inlineMap = remember(text) { appleEmojiInlineContent(text) }
    val placeholders = remember(text) { appleEmojiPlaceholders(text) }
    val policy = remember(text, resolved, maxTextWidth, measurer) { TextMetaPolicy(text, resolved, maxTextWidth, measurer, holder, placeholders) }
    // Quote bars / code-block backgrounds (annotated by rememberRichText); nothing extra for plain texts.
    val blocks = remember(text) { text.getStringAnnotations(BlockDecorTag, 0, text.length) }
    val decor = if (blocks.isEmpty()) Modifier else Modifier.drawBehind {
        val l = holder[0] ?: return@drawBehind
        for (b in blocks) {
            val color = Color(b.item.substringAfter(':').toIntOrNull() ?: continue)
            val end = b.end.coerceAtMost(l.layoutInput.text.length)
            if (b.start >= end) continue
            val top = l.getLineTop(l.getLineForOffset(b.start))
            val bottom = l.getLineBottom(l.getLineForOffset((end - 1).coerceAtLeast(b.start)))
            if (b.item.startsWith(BlockQuote)) {
                val r = CornerRadius(3.dp.toPx())
                drawRoundRect(color.copy(alpha = 0.10f), Offset(0f, top), Size(size.width, bottom - top), CornerRadius(4.dp.toPx()))
                drawRoundRect(color, Offset(0f, top), Size(3.dp.toPx(), bottom - top), r)
            } else {
                drawRoundRect(color, Offset(-2.dp.toPx(), top - 2.dp.toPx()), Size(size.width + 4.dp.toPx(), bottom - top + 4.dp.toPx()), CornerRadius(6.dp.toPx()))
            }
        }
    }
    Layout(
        modifier = modifier,
        content = {
            BasicText(text, style = resolved, onTextLayout = { holder[0] = it }, inlineContent = inlineMap, modifier = decor)
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
    private val placeholders: List<AnnotatedString.Range<Placeholder>> = emptyList(),
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
        val l = measurer.measure(text, style, placeholders = placeholders, constraints = Constraints(maxWidth = limit), layoutDirection = layoutDirection, density = this)
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
            val repo = LocalRepository.current
            val vf = p.videoFile
            val vPath = vf?.let { repo.filePath(it) }
            val vProgress = vf?.let { repo.fileProgress(it) } ?: 0f
            // The video file is being fetched (viewer opened / saving): ring + X over the play button, bytes in the pill.
            val vLoading = !p.loop && vf != null && vPath == null && vProgress > 0f
            if (!p.loop) {
                val ringShown by androidx.compose.animation.core.animateFloatAsState(if (vLoading) 1f else 0f, label = "videoRing")
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.45f))
                        .then(if (vLoading && vf != null) Modifier.fadeClickable { repo.cancelDownload(vf) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(IosIcons.Play, Color.White, 22.dp, Modifier.offset(x = 1.dp).graphicsLayer { alpha = 1f - ringShown })
                    DownloadRing(
                        if (vLoading) DownloadPhase.Loading else DownloadPhase.Done, vProgress, Color.White,
                        Modifier.matchParentSize(), glyphSize = 17.dp, inset = 4.dp,
                    )
                }
            }
            val pill = when {
                vLoading && vf != null && vf.size > 0 -> progressBytes((vf.size * vProgress).toLong(), vf.size)
                p.loop -> "GIF"
                p.duration > 0 -> formatDuration(p.duration)
                else -> null
            }
            if (pill != null) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(Capsule()).background(Color.Black.copy(0.4f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    T(pill, bubbleText().meta, Color.White, weight = FontWeight.Medium, maxLines = 1)
                }
            }
        } else {
            // Photos: blurred minithumbnail (TgImage) under Telegram's dark download ring until the picture is here.
            ImageDownloadOverlay(p.image)
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
            else EmojiGlyph(s.emoji, 140.dp)
        }
        Box(Modifier.stickerSetTap(s)) {
            if (anim != null) com.abtin.tglass.ui.components.TgsSticker(animPath, Modifier.size(160.dp), still)
            else still()
        }
        FreeformFooter(m, onReact)
    }
}

/** 1–3 emoji without a bubble, big — like Telegram. */
@Composable
private fun BigEmojiMessage(m: Message, text: String, count: Int, modifier: Modifier, onReact: (String) -> Unit) {
    val size = when (count) { 1 -> 64; 2 -> 50; else -> 42 }
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
        val emoji = rememberAppleEmojiText(text.trim(), remember(m.content, text) { com.abtin.tglass.core.emoji.customEmojiSpansTrimmed(m, text) })
        BasicText(
            emoji, style = TextStyle(fontSize = size.sp, lineHeight = (size * 1.2f).sp),
            inlineContent = remember(emoji) { appleEmojiInlineContent(emoji) },
        )
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
        if (f.music) playAudioMessage(context, repo, m, p, f.duration)
        else if (!com.abtin.tglass.core.media.Files.open(context, p, f.mime)) toast.show("No app can open this file")
    }
    LaunchedEffect(pending, path) {
        if (pending && path != null) {
            pending = false
            act(path)
        }
    }
    val progress = ref?.let { repo.fileProgress(it) } ?: 0f
    // Loading: tapped here, or TDLib is already fetching it (progress reported) — Telegram's ring + X either way.
    val downloading = ref != null && path == null && (pending || progress > 0f)
    val playing = f.music && player.currentKey == key && player.playing
    val text = bubbleText()
    Column {
        Row(Modifier.padding(start = 9.dp, end = 11.dp, top = 8.dp, bottom = if (f.caption != null) 2.dp else 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.control)
                    .bounceClickable {
                        when {
                            ref == null -> {}
                            path != null -> act(path)
                            downloading -> { pending = false; repo.cancelDownload(ref) }
                            else -> { pending = true; repo.requestImage(ref) }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val phase = when {
                    downloading -> DownloadPhase.Loading
                    ref != null && path == null && !f.music -> DownloadPhase.Remote
                    else -> DownloadPhase.Done
                }
                // The file / play glyph fades back in as the ring leaves.
                val glyphAlpha by androidx.compose.animation.core.animateFloatAsState(if (phase == DownloadPhase.Done) 1f else 0f, label = "fileGlyph")
                Box(Modifier.matchParentSize().graphicsLayer { alpha = glyphAlpha }, contentAlignment = Alignment.Center) {
                    if (f.music) {
                        val t by androidx.compose.animation.core.animateFloatAsState(if (playing) 1f else 0f, label = "musicPlay")
                        Canvas(Modifier.size(40.dp)) { drawPlayPause(t, colors.controlGlyph, scale = 40f / 44f) }
                    } else {
                        Icon(TgIcons.AttFile, colors.controlGlyph, 21.dp)
                    }
                }
                DownloadRing(phase, progress, colors.controlGlyph, Modifier.matchParentSize(), glyphSize = 15.dp, inset = 3.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 200.dp)) {
                T(f.name, text.fileTitle, colors.fileTitle, weight = FontWeight.Medium, maxLines = 2)
                if (f.music && f.performer != null) T(f.performer, text.fileInfo, colors.meta, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val info = when {
                        downloading && ref != null && ref.size > 0 -> progressBytes((ref.size * progress).toLong(), ref.size)
                        downloading -> "${(progress * 100).toInt()}% of ${f.size}"
                        playing -> "${formatDuration((player.positionMs / 1000).toInt())} / ${formatDuration(f.duration)}"
                        f.music && f.duration > 0 -> "${formatDuration(f.duration)} · ${f.size}"
                        else -> f.size
                    }
                    T(info, text.fileInfo, colors.meta, maxLines = 1)
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
    // Title / address wrap under the map instead of widening the bubble past it.
    CapWidth(minOf(maxMedia, 260.dp) + 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
            T(l.title, TgTheme.type.subheadline.copy(textDirection = TextDirection.Content), colors.text, weight = FontWeight.SemiBold)
            TextWithMeta(l.address, TgTheme.type.footnote.copy(color = colors.meta), { if (showMeta) MetaRow(m, colors.meta) }, maxTextWidth = minOf(maxMedia, 260.dp) - 16.dp)
        }
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
            Avatar(ct.name, if (ct.userId != 0L) ct.userId else ct.phone.hashCode().toLong(), 40.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                T(ct.name, bubbleText().fileTitle, colors.fileTitle, weight = FontWeight.SemiBold, maxLines = 1)
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
private fun LinkBody(m: Message, l: MessageContent.Link, colors: BubbleColors, textMax: Dp, top: Dp, showMeta: Boolean) {
    val handler = LocalLinkHandler.current
    val entities = l.entities.ifEmpty { detectEntities(l.text) }
    val url = entities.firstOrNull { it.type == EntityType.TextUrl }?.url
        ?: entities.firstOrNull { it.type == EntityType.Url }?.let { e -> l.text.substring(e.start.coerceIn(0, l.text.length), e.end.coerceIn(0, l.text.length)) }
    Column(Modifier.padding(start = 11.dp, end = 11.dp, top = top, bottom = 6.dp).widthIn(max = textMax)) {
        val rich = richFor(m, l.text, l.entities, colors)
        BasicText(rich, style = bodyStyle(colors), inlineContent = remember(rich) { appleEmojiInlineContent(rich) })
        Spacer(Modifier.height(6.dp))
        // The preview's long description must not stretch a short message's bubble to the full width.
        CapWidth(minOf(textMax, 260.dp)) { Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedRectangle(6.dp))
                .background(colors.accent.copy(alpha = if (TgTheme.colors.isDark) 0.16f else 0.1f))
                .drawBehind { drawRect(colors.accent, size = Size(3.dp.toPx(), size.height)) }
                .then(if (url != null) Modifier.fadeClickable { handler(Entity(0, url.length, EntityType.Url), url) } else Modifier)
                .padding(start = 11.dp, end = 8.dp, top = 5.dp, bottom = 6.dp)
        ) {
            val style = bubbleText().header
            if (l.site.isNotBlank()) T(l.site, style, colors.accent, weight = FontWeight.SemiBold, maxLines = 1)
            if (l.title.isNotBlank()) T(l.title, style, colors.text, weight = FontWeight.SemiBold, maxLines = 2)
            if (l.description.isNotBlank()) T(l.description, style, colors.text, maxLines = 4)
        } }
        if (showMeta) Box(Modifier.align(Alignment.End).padding(top = 4.dp)) { MetaRow(m, colors.meta) }
    }
}

/**
 * Telegram-iOS reaction button (ReactionButtonListComponent): a capsule in the bubble's accent tint with the
 * emoji and its counter; the chosen one is filled with the accent and shows a white counter.
 */
@Composable
private fun ReactionChip(r: Reaction, colors: BubbleColors, onReact: (String) -> Unit) {
    val scale = LocalAppSettings.current.textScale.coerceIn(0.85f, 1.3f)
    Row(
        Modifier
            .height((28f * scale).dp)
            .clip(Capsule())
            .background(if (r.chosen) colors.reactionActiveBg else colors.reactionBg)
            .bounceClickable { onReact(r.emoji) }
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.abtin.tglass.core.emoji.ReactionGlyph(r.emoji, (17f * scale).dp)
        Spacer(Modifier.width(4.dp))
        T(formatCount(r.count), bubbleText().reaction, if (r.chosen) colors.reactionActiveFg else colors.reactionFg, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * iOS reactions: small capsules wrapping into rows inside the bubble (never wider than [limit] on their own),
 * with the time on the last row when it fits, otherwise on its own line at the bottom right.
 */
@Composable
private fun ReactionsWithMeta(
    reactions: List<Reaction>,
    colors: BubbleColors,
    meta: @Composable () -> Unit,
    onReact: (String) -> Unit,
    limit: Dp,
    modifier: Modifier = Modifier,
) {
    val policy = remember(limit) { ReactionsPolicy(limit) }
    Layout(
        modifier = modifier,
        content = {
            reactions.forEach { r -> ReactionChip(r, colors, onReact) }
            meta()
        },
        measurePolicy = policy,
    )
}

private class ReactionsPolicy(private val limit: Dp) : MeasurePolicy {
    private class Flow(val positions: List<Pair<Int, Int>>, val widest: Int, val lastRowW: Int, val lastRowY: Int, val lastRowH: Int)

    /** Places items of [widths] × [heights] left to right, wrapping at [maxW]. */
    private fun flow(widths: List<Int>, heights: List<Int>, maxW: Int, spacing: Int): Flow {
        val positions = ArrayList<Pair<Int, Int>>(widths.size)
        var x = 0
        var y = 0
        var rowH = 0
        var widest = 0
        widths.forEachIndexed { i, w ->
            if (x > 0 && x + w > maxW) {
                y += rowH + spacing
                x = 0
                rowH = 0
            }
            positions += x to y
            x += w + spacing
            rowH = max(rowH, heights[i])
            widest = max(widest, x - spacing)
        }
        return Flow(positions, widest, (x - spacing).coerceAtLeast(0), y, rowH)
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val spacing = 6.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else limit.roundToPx()
        val chips = measurables.dropLast(1).map { it.measure(Constraints(maxWidth = maxW)) }
        val mp = measurables.last().measure(Constraints())
        val f = flow(chips.map { it.width }, chips.map { it.height }, maxW, spacing)
        val inline = f.lastRowW + gap + mp.width <= maxW
        val width = max(constraints.minWidth, if (inline) max(f.widest, f.lastRowW + gap + mp.width) else max(f.widest, mp.width)).coerceAtMost(maxW)
        val height = if (inline) f.lastRowY + max(f.lastRowH, mp.height) else f.lastRowY + f.lastRowH + 4.dp.roundToPx() + mp.height
        return layout(width, height) {
            chips.forEachIndexed { i, p -> p.place(f.positions[i].first, f.positions[i].second) }
            if (inline) mp.place(width - mp.width, f.lastRowY + f.lastRowH - mp.height - 1.dp.roundToPx())
            else mp.place(width - mp.width, height - mp.height)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val spacing = 6.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val maxW = limit.roundToPx().coerceAtLeast(1)
        val chips = measurables.dropLast(1)
        val widths = chips.map { it.maxIntrinsicWidth(height) }
        val f = flow(widths, widths.map { 0 }, maxW, spacing)
        val metaW = measurables.last().maxIntrinsicWidth(height)
        return if (f.lastRowW + gap + metaW <= maxW) max(f.widest, f.lastRowW + gap + metaW) else max(f.widest, metaW)
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
