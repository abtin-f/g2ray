package com.abtin.tglass.features.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.abtin.tglass.core.emoji.CustomEmojiSpan
import com.abtin.tglass.core.emoji.appendWithAppleEmoji
import com.abtin.tglass.core.emoji.appleEmojiKeyFor
import com.abtin.tglass.data.Entity
import com.abtin.tglass.data.EntityType
import com.abtin.tglass.features.main.LocalRepository

/** String annotation of quote / code-block ranges: "kind:argbColor". */
const val BlockDecorTag = "tglass.block"
const val BlockQuote = "q"
const val BlockPre = "p"

/** What to do when a link, @mention, #hashtag or /command in a message is tapped. */
val LocalLinkHandler = staticCompositionLocalOf<(Entity, String) -> Unit> { { _, _ -> } }

private val urlRegex = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"]+|[a-z0-9.-]+\.(?:com|org|net|io|me|dev|ir|app|co)(?:/[^\s<>"]*)?)""")
private val mentionRegex = Regex("""(?<![\w@])@[A-Za-z][A-Za-z0-9_]{3,31}""")
private val hashtagRegex = Regex("""(?<![\w#])#[\p{L}\p{N}_]+""")
private val commandRegex = Regex("""(?<![\w/])/[A-Za-z][A-Za-z0-9_]{0,31}""")

/** Finds links, mentions, hashtags and bot commands in plain text (for texts that come without entities). */
fun detectEntities(text: String): List<Entity> {
    val found = ArrayList<Entity>()
    fun add(r: Regex, type: EntityType) = r.findAll(text).forEach { m ->
        val e = Entity(m.range.first, m.range.last + 1, type)
        if (found.none { it.start < e.end && e.start < it.end }) found += e
    }
    add(urlRegex, EntityType.Url)
    add(mentionRegex, EntityType.Mention)
    add(hashtagRegex, EntityType.Hashtag)
    add(commandRegex, EntityType.BotCommand)
    return found.sortedBy { it.start }
}

/**
 * Telegram message text with its formatting: bold/italic/underline/strike, monospace code,
 * spoilers (hidden until tapped) and tappable links, mentions, hashtags and commands.
 */
@Composable
fun rememberRichText(
    text: String,
    entities: List<Entity>,
    link: Color,
    codeBackground: Color,
    spoilerColor: Color,
    spoilersRevealed: Boolean,
    onRevealSpoiler: () -> Unit,
    /** Underline links (when they have the same color as the text, like night bubbles). */
    underlineLinks: Boolean = false,
): AnnotatedString {
    val handler = LocalLinkHandler.current
    val all = remember(text, entities) { entities.ifEmpty { detectEntities(text) } }
    val emojiKey = appleEmojiKeyFor(text)
    // Premium emoji: drawn inline (Apple fallback until getCustomEmojiStickers answers); tap opens the emoji pack.
    val custom = remember(all) {
        all.filter { it.type == EntityType.CustomEmoji && it.customEmojiId != 0L }.map { CustomEmojiSpan(it.start, it.end, it.customEmojiId) }
    }
    val repo = LocalRepository.current
    LaunchedEffect(custom) { if (custom.isNotEmpty()) repo.loadCustomEmoji(custom.map { it.id }.distinct()) }
    return remember(text, all, custom, link, codeBackground, spoilerColor, spoilersRevealed, handler, underlineLinks, emojiKey) {
        val linkStyle = SpanStyle(color = link, textDecoration = if (underlineLinks) TextDecoration.Underline else null)
        buildAnnotatedString {
            val blockRanges = ArrayList<IntRange>()
            // Marks a block (code / quote) for background drawing and insets its paragraph(s); paragraphs must end at newlines.
            fun addBlock(kind: String, color: Color, start: Int, end: Int, indent: TextUnit) {
                addStringAnnotation(BlockDecorTag, kind + ":" + color.toArgb(), start, end)
                var ps = start
                while (ps > 0 && text[ps - 1] != '\n') ps--
                var pe = text.indexOf('\n', end - 1).let { if (it < 0) text.length else it + 1 }
                if (pe < end) pe = end
                if (blockRanges.any { it.first < pe && ps <= it.last }) return
                blockRanges += ps until pe
                addStyle(ParagraphStyle(textIndent = TextIndent(indent, indent)), ps, pe)
            }
            // Apple emoji inline (same offsets); hidden spoilers keep their glyphs so the emoji stay covered.
            appendWithAppleEmoji(
                text,
                keepText = { s, e -> !spoilersRevealed && all.any { it.type == EntityType.Spoiler && it.start < e && s < it.end } },
                custom = custom,
                tappable = true,
            )
            for (e in all) {
                val start = e.start.coerceIn(0, text.length)
                val end = e.end.coerceIn(start, text.length)
                if (start == end) continue
                val value = text.substring(start, end)
                when (e.type) {
                    EntityType.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, end)
                    EntityType.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    EntityType.Underline -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    EntityType.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                    // Inline code: monospace a bit smaller than the text (15 of 17), tinted chip.
                    EntityType.Code -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.88.em, background = codeBackground), start, end)
                    // Code block: rounded background drawn by TextWithMeta (BlockDecorTag), text inset like iOS.
                    EntityType.Pre -> {
                        addStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 0.88.em), start, end)
                        addBlock(BlockPre, codeBackground, start, end, 8.sp)
                    }
                    // Quote: accent bar on the left (drawn by TextWithMeta), text inset.
                    EntityType.Quote -> addBlock(BlockQuote, link, start, end, 12.sp)
                    EntityType.CustomEmoji -> {}
                    EntityType.Spoiler -> if (!spoilersRevealed) {
                        addStyle(SpanStyle(color = Color.Transparent, background = spoilerColor), start, end)
                        addLink(LinkAnnotation.Clickable("spoiler") { onRevealSpoiler() }, start, end)
                    }
                    else -> addLink(
                        LinkAnnotation.Clickable(e.type.name, TextLinkStyles(linkStyle)) { handler(e, value) },
                        start, end,
                    )
                }
            }
        }
    }
}
