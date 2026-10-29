package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/**
 * What a Telegram link (t.me/…, telegram.me/…, tg://…) points to, as resolved by the repository.
 * - [chatId] set: open that chat (and scroll to [messageId] when given).
 * - [invite] set: an invite link to a chat the user is not in yet — ask before joining.
 * - [external]: not a link the app handles — open it in the browser.
 * - [error]: tell the user why nothing opened.
 */
@Immutable
data class ResolvedLink(
    val chatId: Long? = null,
    val messageId: Long? = null,
    val invite: InviteInfo? = null,
    val external: Boolean = false,
    val error: String? = null,
)

/** Preview of a chat behind an invite link (t.me/+…, t.me/joinchat/…). */
@Immutable
data class InviteInfo(val link: String, val title: String, val memberCount: Int, val channel: Boolean, val requestNeeded: Boolean)

/** A t.me / tg:// link split into its parts (used by the demo and to recognise Telegram links). */
data class TgLink(
    val username: String? = null,
    val messageId: Long? = null,
    val privateChannelId: Long? = null,
    val invite: String? = null,
    val phone: String? = null,
)

object TelegramLinks {
    private val host = Regex("""(?i)^(?:https?://)?(?:www\.)?(?:t\.me|telegram\.me|telegram\.dog)(?:/|$)""")
    private val username = Regex("^[A-Za-z][A-Za-z0-9_]{3,31}$")
    private val notChats = setOf(
        "proxy", "socks", "addstickers", "addemoji", "share", "setlanguage", "addtheme", "bg", "login",
        "invoice", "boost", "giftcode", "m", "contact", "addlist", "iv", "nft", "stars",
    )

    /** True for links Telegram opens inside the app. */
    fun isTelegramLink(url: String): Boolean {
        val u = url.trim()
        return u.startsWith("tg:", ignoreCase = true) || host.containsMatchIn(u)
    }

    fun parse(url: String): TgLink? {
        val u = url.trim()
        if (u.startsWith("tg:", ignoreCase = true)) {
            val normalized = "tg://" + u.substring(3).trimStart('/')
            val uri = runCatching { android.net.Uri.parse(normalized) }.getOrNull() ?: return null
            return when (uri.host?.lowercase()) {
                "resolve" -> {
                    val phone = uri.getQueryParameter("phone")
                    if (phone != null) TgLink(phone = phone)
                    else TgLink(username = uri.getQueryParameter("domain") ?: return null, messageId = uri.getQueryParameter("post")?.toLongOrNull())
                }
                "join" -> TgLink(invite = uri.getQueryParameter("invite") ?: return null)
                "privatepost" -> TgLink(
                    privateChannelId = uri.getQueryParameter("channel")?.toLongOrNull() ?: return null,
                    messageId = uri.getQueryParameter("post")?.toLongOrNull(),
                )
                else -> null
            }
        }
        val m = host.find(u) ?: return null
        val path = u.substring(m.range.last + 1).substringBefore('?').substringBefore('#').trim('/')
        val parts = path.split('/').filter { it.isNotEmpty() }
        val first = parts.firstOrNull() ?: return null
        return when {
            first.startsWith("+") && first.length > 5 && first.drop(1).all { it.isDigit() } -> TgLink(phone = first.drop(1))
            first.startsWith("+") -> TgLink(invite = first.drop(1))
            first.equals("joinchat", true) -> parts.getOrNull(1)?.let { TgLink(invite = it) }
            first.equals("c", true) -> parts.getOrNull(1)?.toLongOrNull()?.let { id ->
                TgLink(privateChannelId = id, messageId = if (parts.size >= 3) parts.last().toLongOrNull() else null)
            }
            first.lowercase() in notChats -> null
            username.matches(first) -> TgLink(username = first, messageId = parts.getOrNull(1)?.toLongOrNull())
            else -> null
        }
    }
}
