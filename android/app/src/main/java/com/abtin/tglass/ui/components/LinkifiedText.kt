package com.abtin.tglass.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.Entity
import com.abtin.tglass.data.EntityType
import com.abtin.tglass.data.ProxyItem
import com.abtin.tglass.data.ResolvedLink
import com.abtin.tglass.data.TelegramLinks
import com.abtin.tglass.features.chat.detectEntities
import com.abtin.tglass.features.main.LocalRepository

private val linkTypes = setOf(
    EntityType.Url, EntityType.TextUrl, EntityType.Mention, EntityType.MentionName, EntityType.Hashtag,
    EntityType.Cashtag, EntityType.BotCommand, EntityType.Email, EntityType.Phone,
)

private val emailRegex = Regex("""(?<![\w.%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")
private val cashtagRegex = Regex("""(?<![\w$])\$[A-Z]{3,8}(?![\w$])""")
private val phoneRegex = Regex("""(?<![\w+])(?:\+\d[\d\s\-()]{6,17}\d|\d{10,15})(?!\w)""")

/**
 * Finds everything Telegram highlights in a plain text (bio, description): links, t.me links, @usernames,
 * #hashtags, $cashtags, emails, phone numbers and bot commands. Emails win over the links inside them.
 */
fun detectAllEntities(text: String): List<Entity> {
    val found = ArrayList<Entity>()
    fun overlaps(e: Entity) = found.any { it.start < e.end && e.start < it.end }
    emailRegex.findAll(text).forEach { m -> found += Entity(m.range.first, m.range.last + 1, EntityType.Email) }
    detectEntities(text).forEach { if (!overlaps(it)) found += it }
    cashtagRegex.findAll(text).forEach { m ->
        val e = Entity(m.range.first, m.range.last + 1, EntityType.Cashtag)
        if (!overlaps(e)) found += e
    }
    phoneRegex.findAll(text).forEach { m ->
        val e = Entity(m.range.first, m.range.last + 1, EntityType.Phone)
        if (!overlaps(e)) found += e
    }
    return found.sortedBy { it.start }
}

/** A clickable range of a [LinkifiedText]. */
private class LinkSpan(val start: Int, val end: Int, val entity: Entity, val value: String) {
    val isWebLink: Boolean get() = entity.type == EntityType.Url || entity.type == EntityType.TextUrl

    fun copyLabel(): String = when (entity.type) {
        EntityType.Url, EntityType.TextUrl -> "Copy Link"
        EntityType.Mention, EntityType.MentionName -> "Copy Username"
        EntityType.Hashtag -> "Copy Hashtag"
        EntityType.Cashtag -> "Copy Cashtag"
        EntityType.Email -> "Copy Email"
        EntityType.Phone -> "Copy Number"
        else -> "Copy"
    }

    fun copyValue(): String = if (entity.type == EntityType.TextUrl) entity.url ?: value else value
}

private fun linkSpans(text: String, entities: List<Entity>): List<LinkSpan> {
    val all = ArrayList<Entity>(entities.filter { it.type in linkTypes && it.start in 0 until text.length && it.end > it.start })
    detectAllEntities(text).forEach { d -> if (all.none { it.start < d.end && d.start < it.end }) all += d }
    return all.sortedBy { it.start }.map {
        val end = it.end.coerceAtMost(text.length)
        LinkSpan(it.start, end, it, text.substring(it.start, end))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", text))
    }
}

/**
 * What a tap on a link outside a chat does: t.me / tg:// links and @usernames open inside the app,
 * web links open the browser, emails the mail app, phone numbers the dialer; the rest is copied.
 */
@Composable
fun rememberLinkOpener(): (Entity, String) -> Unit {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    return remember(repo, nav, toast, sheet, uriHandler, context) {
        val open: (Entity, String) -> Unit = { e, value ->
            fun browser(url: String) {
                runCatching { uriHandler.openUri(url) }.onFailure { toast.show("Can't open this link") }
            }
            fun resolved(r: ResolvedLink, url: String) {
                val target = r.chatId
                val invite = r.invite
                val error = r.error
                when {
                    target != null -> nav.push(Route.Chat(target))
                    invite != null -> sheet.show(
                        SheetRequest(
                            title = invite.title,
                            actions = listOf(
                                SheetAction(if (invite.channel) "Join Channel" else "Join Group", bold = true) {
                                    repo.joinByInviteLink(invite.link) { id, err ->
                                        if (id != null) nav.push(Route.Chat(id)) else if (err != null) toast.error(err)
                                    }
                                },
                            ),
                        ),
                    )
                    error != null -> toast.error(error)
                    else -> browser(url)
                }
            }
            fun url(raw: String) {
                val u = if (raw.startsWith("http", ignoreCase = true) || raw.startsWith("tg:", ignoreCase = true)) raw else "https://$raw"
                when {
                    ProxyItem.fromLink(u) != null -> nav.push(Route.ProxyLink(u))
                    TelegramLinks.isTelegramLink(u) -> repo.resolveLink(u) { resolved(it, u) }
                    else -> browser(u)
                }
            }
            when (e.type) {
                EntityType.Url -> url(value)
                EntityType.TextUrl -> url(e.url ?: value)
                EntityType.Email -> runCatching { uriHandler.openUri("mailto:$value") }
                EntityType.Phone -> runCatching { uriHandler.openUri("tel:" + value.filter { it.isDigit() || it == '+' }) }
                EntityType.Mention -> repo.resolveUsername(value) { id ->
                    if (id != null) nav.push(Route.Chat(id)) else toast.error("No one uses @${value.removePrefix("@")}")
                }
                EntityType.MentionName -> nav.push(Route.Chat(repo.privateChatWith(e.userId)))
                else -> {
                    copyToClipboard(context, value)
                    toast.show("Copied")
                }
            }
        }
        open
    }
}

/**
 * Bio / description text with Telegram's highlighting: links, @usernames, #hashtags, $cashtags, emails, phone numbers
 * and bot commands are drawn in [linkColor]. Tap opens them; long press on a link shows Open / Copy Link / Share,
 * long press anywhere else copies the whole text. [entities] are the server's (links hidden behind text); anything
 * they miss is detected from the plain text. Direction follows the text (Persian / Arabic read right to left).
 */
@Composable
fun LinkifiedText(
    text: String,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
    entities: List<Entity> = emptyList(),
    maxLines: Int = Int.MAX_VALUE,
) {
    val spans = remember(text, entities) { linkSpans(text, entities) }
    val annotated = remember(text, spans, linkColor) {
        buildAnnotatedString {
            append(text)
            spans.forEach { addStyle(SpanStyle(color = linkColor), it.start, it.end) }
        }
    }
    val open = rememberLinkOpener()
    val sheet = LocalActionSheet.current
    val toast = LocalToast.current
    val context = LocalContext.current
    val view = LocalView.current
    val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val currentSpans = rememberUpdatedState(spans)
    val currentOpen = rememberUpdatedState(open)
    val currentText = rememberUpdatedState(text)

    fun spanAt(p: Offset): LinkSpan? {
        val l = layout[0] ?: return null
        if (l.lineCount == 0) return null
        val line = l.getLineForVerticalPosition(p.y)
        if (p.y < l.getLineTop(line) || p.y > l.getLineBottom(line)) return null
        if (p.x < l.getLineLeft(line) || p.x > l.getLineRight(line)) return null
        val offset = l.getOffsetForPosition(p)
        return currentSpans.value.firstOrNull { offset >= it.start && offset < it.end }
    }

    BasicText(
        text = annotated,
        style = style.copy(color = color, textDirection = TextDirection.Content),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout[0] = it },
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { p -> spanAt(p)?.let { currentOpen.value(it.entity, it.value) } },
                onLongPress = { p ->
                    Haptics.longPress(view)
                    val s = spanAt(p)
                    if (s == null) {
                        sheet.show(
                            SheetRequest(
                                actions = listOf(SheetAction("Copy") { copyToClipboard(context, currentText.value); toast.show("Copied") }),
                            ),
                        )
                    } else {
                        val actions = ArrayList<SheetAction>()
                        actions += SheetAction("Open") { currentOpen.value(s.entity, s.value) }
                        actions += SheetAction(s.copyLabel()) { copyToClipboard(context, s.copyValue()); toast.show("Copied") }
                        if (s.isWebLink) {
                            actions += SheetAction("Share") {
                                runCatching {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                        .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, s.copyValue())
                                    context.startActivity(
                                        android.content.Intent.createChooser(send, null).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        }
                        actions += SheetAction("Copy Text") { copyToClipboard(context, currentText.value); toast.show("Copied") }
                        sheet.show(SheetRequest(title = s.copyValue(), actions = actions))
                    }
                },
            )
        },
    )
}
