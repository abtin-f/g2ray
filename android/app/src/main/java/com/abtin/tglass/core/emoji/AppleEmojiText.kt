package com.abtin.tglass.core.emoji

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.em
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** String annotation (item = image name) marking an inline Apple emoji; lets callers find ids and placeholders. */
const val AppleEmojiTag = "tglass.appleEmoji"
private const val IdPrefix = "apple-emoji:"

/** Inline emoji box, relative to the font size of the text (Apple's emoji are a little wider than a letter). */
private val EmojiPlaceholder = Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.TextCenter)

/** String annotation marking an inline custom (premium) emoji; the item is its inline-content id. */
const val CustomEmojiTag = "tglass.customEmoji"
private const val CustomIdPrefix = "custom-emoji:"
private const val CustomTapIdPrefix = "custom-emoji-tap:"

/** A custom (premium) emoji entity of a text: UTF-16 range [start, end) (its fallback emoji) and the emoji id. */
@androidx.compose.runtime.Immutable
data class CustomEmojiSpan(val start: Int, val end: Int, val id: Long)

/**
 * Appends [text] with every emoji whose Apple image is cached replaced by an inline image placeholder. The emoji
 * characters stay in the string (as the placeholder's alternate text), so offsets of entities / links are unchanged.
 * Emoji not downloaded yet start downloading and stay system glyphs for now. [keepText] can exclude ranges (spoilers).
 * [custom] ranges (premium emoji entities) always become inline custom emoji (Apple fallback while they load);
 * [tappable] ones open their emoji pack when tapped (message bubbles).
 */
fun AnnotatedString.Builder.appendWithAppleEmoji(
    text: String,
    keepText: (start: Int, end: Int) -> Boolean = { _, _ -> false },
    custom: List<CustomEmojiSpan> = emptyList(),
    tappable: Boolean = false,
) {
    val customs = ArrayList<CustomEmojiSpan>()
    for (c in custom.sortedBy { it.start }) {
        if (c.id == 0L || c.start < 0 || c.end > text.length || c.start >= c.end) continue
        if (customs.isNotEmpty() && customs.last().end > c.start) continue
        if (keepText(c.start, c.end)) continue
        customs += c
    }
    if (!AppleEmoji.active && customs.isEmpty()) {
        append(text)
        return
    }
    var pos = 0
    var ci = 0
    // Custom emoji starting before [limit] go in first (Apple emoji they cover are skipped).
    fun flushCustom(limit: Int) {
        while (ci < customs.size && customs[ci].start < limit) {
            val c = customs[ci++]
            if (c.start < pos) continue
            if (c.start > pos) append(text, pos, c.start)
            val id = (if (tappable) CustomTapIdPrefix else CustomIdPrefix) + c.id
            pushStringAnnotation(CustomEmojiTag, id)
            appendInlineContent(id, text.substring(c.start, c.end))
            pop()
            pos = c.end
        }
    }
    val matches = if (AppleEmoji.active) AppleEmoji.find(text) else emptyList()
    for (m in matches) {
        flushCustom(m.end)
        if (m.start < pos) continue
        if (!AppleEmoji.isOnDisk(m.name)) {
            AppleEmoji.request(m.name)
            continue
        }
        if (keepText(m.start, m.end)) continue
        if (m.start > pos) append(text, pos, m.start)
        pushStringAnnotation(AppleEmojiTag, m.name)
        appendInlineContent(IdPrefix + m.name, text.substring(m.start, m.end))
        pop()
        pos = m.end
    }
    flushCustom(Int.MAX_VALUE)
    if (pos < text.length) append(text, pos, text.length)
}

/**
 * Changes whenever Apple emoji annotations should be rebuilt (style switched, index loaded, new images cached).
 * Use it as a `remember` key next to the text.
 */
@Composable
fun appleEmojiKey(): Int {
    val context = LocalContext.current
    remember { AppleEmoji.init(context); 0 }
    return if (AppleEmoji.active) AppleEmoji.revision else -1
}

/**
 * Like [appleEmojiKey] but only for [text]: changes when the style is switched or when an emoji *of this text* that was
 * still downloading becomes available. The global [appleEmojiKey] changes whenever any emoji anywhere lands on disk,
 * which re-annotated every visible text (every bubble and chat row) over and over while emoji were first downloaded.
 */
@Composable
fun appleEmojiKeyFor(text: String): Int {
    val context = LocalContext.current
    remember { AppleEmoji.init(context); 0 }
    val active = AppleEmoji.active
    if (!active) return -1
    val missing = remember(text) { AppleEmoji.find(text).map { it.name }.filter { !AppleEmoji.isOnDisk(it) }.distinct() }
    var ready = 0
    for (n in missing) if (AppleEmoji.readyState(n).value) ready++
    return ready
}

/** Whether Apple emoji are in use (loads the index on first call); unlike [appleEmojiKey] it ignores new downloads. */
@Composable
fun appleEmojiActive(): Boolean {
    val context = LocalContext.current
    remember { AppleEmoji.init(context); 0 }
    return AppleEmoji.active
}

/** [text] with inline Apple emoji (see [appendWithAppleEmoji]) and the [custom] premium emoji in it. */
@Composable
fun rememberAppleEmojiText(text: String, custom: List<CustomEmojiSpan> = emptyList()): AnnotatedString {
    val key = appleEmojiKeyFor(text)
    return remember(text, key, custom) { buildAnnotatedString { appendWithAppleEmoji(text, custom = custom) } }
}

private val inlineCache = HashMap<String, InlineTextContent>() // composition (main) thread only

/** The `inlineContent` map for a text built with [appendWithAppleEmoji] (empty when it has no inline emoji). */
fun appleEmojiInlineContent(text: AnnotatedString): Map<String, InlineTextContent> {
    val anns = text.getStringAnnotations(AppleEmojiTag, 0, text.length)
    val customs = text.getStringAnnotations(CustomEmojiTag, 0, text.length)
    if (anns.isEmpty() && customs.isEmpty()) return emptyMap()
    val map = HashMap<String, InlineTextContent>()
    for (a in customs) {
        val id = a.item
        if (map.containsKey(id)) continue
        val emojiId = id.substringAfterLast(':').toLongOrNull() ?: continue
        val tappable = id.startsWith(CustomTapIdPrefix)
        map[id] = inlineCache.getOrPut(id) {
            InlineTextContent(EmojiPlaceholder) { alt -> CustomEmojiInline(emojiId, alt, tappable) }
        }
    }
    for (a in anns) {
        val id = IdPrefix + a.item
        if (map.containsKey(id)) continue
        map[id] = inlineCache.getOrPut(id) {
            InlineTextContent(EmojiPlaceholder) { AppleEmojiBitmap(a.item, Modifier.fillMaxSize(), sync = true) }
        }
    }
    return map
}

/** Placeholders of the inline emoji, for measuring the text with a `TextMeasurer`. */
fun appleEmojiPlaceholders(text: AnnotatedString): List<AnnotatedString.Range<Placeholder>> =
    (text.getStringAnnotations(AppleEmojiTag, 0, text.length) + text.getStringAnnotations(CustomEmojiTag, 0, text.length))
        .sortedBy { it.start }
        .map { AnnotatedString.Range(EmojiPlaceholder, it.start, it.end) }

/** One cached Apple emoji image. [sync] decodes on the spot (a few emoji in a message) instead of in the background (grids). */
@Composable
fun AppleEmojiBitmap(name: String, modifier: Modifier, sync: Boolean = false) {
    val bmp: ImageBitmap? = if (sync) {
        remember(name) { AppleEmoji.decode(name) }
    } else {
        produceState(AppleEmoji.cachedBitmap(name), name) {
            if (value == null) value = withContext(Dispatchers.IO) { AppleEmoji.decode(name) }
        }.value
    }
    if (bmp != null) Image(bmp, null, modifier, contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
}

/**
 * A single emoji drawn [size]×[size]: the Apple image when available (downloading it on first use), otherwise the
 * system glyph. For the emoji panel, reaction chips and the reaction bar.
 */
@Composable
fun EmojiGlyph(emoji: String, size: Dp, modifier: Modifier = Modifier) {
    val active = appleEmojiActive()
    val name = remember(emoji, active) { if (active) AppleEmoji.single(emoji) else null }
    val ready = if (name != null) AppleEmoji.readyState(name).value else false
    if (name != null && !ready) {
        LaunchedEffect(name) { AppleEmoji.request(name) }
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (name != null && ready) {
            AppleEmojiBitmap(name, Modifier.fillMaxSize())
        } else {
            val fontSize = with(LocalDensity.current) { (size * 0.8f).toSp() }
            BasicText(emoji, style = TextStyle(fontSize = fontSize, lineHeight = fontSize * 1.2f), maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Plain text with inline Apple emoji — a drop-in for the app's `T` for user text (chat previews, drafts).
 * Content text direction, so Persian / Arabic read right-to-left.
 */
@Composable
fun EmojiText(
    text: String,
    style: TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    weight: FontWeight? = null,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    align: TextAlign? = null,
    /** Premium emoji in [text] (chat previews), drawn inline. */
    custom: List<CustomEmojiSpan> = emptyList(),
) {
    val annotated = rememberAppleEmojiText(text, custom)
    val inlineMap = remember(annotated) { appleEmojiInlineContent(annotated) }
    BasicText(
        text = annotated,
        modifier = modifier,
        style = style.copy(
            color = if (color.isSpecified) color else style.color,
            fontWeight = weight ?: style.fontWeight,
            textAlign = align ?: style.textAlign,
            textDirection = if (style.textDirection == TextDirection.Unspecified) TextDirection.Content else style.textDirection,
        ),
        overflow = overflow,
        maxLines = maxLines,
        inlineContent = inlineMap,
    )
}

/**
 * Apple emoji for a text field (Compose text fields can't hold inline images): the emoji glyphs are made transparent
 * by [transformation] and the images are drawn over them by [modifier], positioned from the field's text layout
 * (pass [onTextLayout]). Falls back to system glyphs when the text scrolls inside the field (more than [maxLines]).
 */
@Stable
class AppleEmojiField internal constructor(private val maxLines: Int) {
    private var layout by mutableStateOf<TextLayoutResult?>(null)
    internal var overflow by mutableStateOf(false)
    private var matches: List<EmojiMatch> = emptyList()
    private var matchedText: String? = null

    internal fun transform(text: AnnotatedString): TransformedText {
        val found = if (AppleEmoji.active && !overflow) {
            AppleEmoji.find(text.text).filter { m ->
                AppleEmoji.isOnDisk(m.name).also { if (!it) AppleEmoji.request(m.name) }
            }
        } else emptyList()
        matches = found
        matchedText = text.text
        if (found.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val b = AnnotatedString.Builder(text)
        found.forEach { b.addStyle(SpanStyle(color = Color.Transparent), it.start, it.end) }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    val onTextLayout: (TextLayoutResult) -> Unit = { l ->
        layout = l
        val over = l.lineCount > maxLines
        if (over != overflow) overflow = over
    }

    val modifier: Modifier = Modifier.drawWithContent {
        drawContent()
        val l = layout ?: return@drawWithContent
        val text = l.layoutInput.text.text
        if (matchedText != text) return@drawWithContent
        for (m in matches) {
            if (m.end > text.length) continue
            val bmp = AppleEmoji.decode(m.name) ?: continue
            val line = l.getLineForOffset(m.start)
            val top = l.getLineTop(line)
            val bottom = l.getLineBottom(line)
            val x1 = l.getHorizontalPosition(m.start, true)
            val left: Float
            val right: Float
            if (m.end == text.length || l.getLineForOffset(m.end) == line) {
                val x2 = l.getHorizontalPosition(m.end, true)
                left = min(x1, x2)
                right = max(x1, x2)
            } else if (l.getParagraphDirection(m.start) == ResolvedTextDirection.Rtl) {
                left = l.getLineLeft(line)
                right = x1
            } else {
                left = x1
                right = l.getLineRight(line)
            }
            val w = right - left
            if (w <= 1f) continue
            val side = min(w * 1.05f, (bottom - top)).roundToInt().coerceAtLeast(1)
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            drawImage(
                bmp,
                dstOffset = IntOffset((cx - side / 2f).roundToInt(), (cy - side / 2f).roundToInt()),
                dstSize = IntSize(side, side),
                filterQuality = FilterQuality.High,
            )
        }
    }

    /** New instance whenever the emoji to hide change, so the text field re-runs it. */
    internal fun newTransformation(): VisualTransformation = object : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText = transform(text)
    }
}

/** Remembers an [AppleEmojiField]; use `field.transformation` as the text field's `visualTransformation`. */
@Composable
fun rememberAppleEmojiField(maxLines: Int): Pair<AppleEmojiField, VisualTransformation> {
    val field = remember(maxLines) { AppleEmojiField(maxLines) }
    val key = appleEmojiKey()
    val transformation = remember(field, key, field.overflow) { field.newTransformation() }
    return field to transformation
}
