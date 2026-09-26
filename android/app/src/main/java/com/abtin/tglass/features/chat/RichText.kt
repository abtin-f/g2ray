package com.abtin.tglass.features.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.abtin.tglass.data.Entity
import com.abtin.tglass.data.EntityType

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
): AnnotatedString {
    val handler = LocalLinkHandler.current
    val all = remember(text, entities) { entities.ifEmpty { detectEntities(text) } }
    return remember(text, all, link, codeBackground, spoilerColor, spoilersRevealed, handler) {
        buildAnnotatedString {
            append(text)
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
                    EntityType.Code, EntityType.Pre -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground), start, end)
                    EntityType.Quote -> addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = link), start, end)
                    EntityType.Spoiler -> if (!spoilersRevealed) {
                        addStyle(SpanStyle(color = Color.Transparent, background = spoilerColor), start, end)
                        addLink(LinkAnnotation.Clickable("spoiler") { onRevealSpoiler() }, start, end)
                    }
                    else -> addLink(
                        LinkAnnotation.Clickable(e.type.name, TextLinkStyles(SpanStyle(color = link))) { handler(e, value) },
                        start, end,
                    )
                }
            }
        }
    }
}
