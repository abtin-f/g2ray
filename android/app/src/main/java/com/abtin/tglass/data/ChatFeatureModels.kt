package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/** What an inline keyboard button does when tapped. Other Telegram button types are shown but not supported yet. */
enum class InlineButtonKind { Url, Callback, Copy, Unsupported }

/**
 * One button of a bot's inline keyboard (shown under the message).
 * [url] is set for [InlineButtonKind.Url], [data] for [InlineButtonKind.Callback], [copyText] for [InlineButtonKind.Copy].
 */
@Immutable
class InlineButton(
    val text: String,
    val kind: InlineButtonKind,
    val url: String? = null,
    val data: ByteArray? = null,
    val copyText: String? = null,
)

@Immutable
class InlineKeyboard(val rows: List<List<InlineButton>>)

/** A button of a bot's custom reply keyboard; [sendsText] is false for request-phone/location/… buttons we don't support. */
@Immutable
data class ReplyButton(val text: String, val sendsText: Boolean = true)

/** Custom keyboard a bot shows in place of the system keyboard (ReplyMarkupShowKeyboard). */
@Immutable
data class ReplyKeyboard(
    /** Message that carried the keyboard (in groups, button presses reply to it). */
    val messageId: Long,
    val rows: List<List<ReplyButton>>,
    val resize: Boolean = true,
    val oneTime: Boolean = false,
    val placeholder: String = "",
)

/** A bot's answer to a pressed callback button. */
@Immutable
data class BotAnswer(val text: String, val alert: Boolean, val url: String)

/** How the history of a chat may be cleared. */
@Immutable
data class ClearHistoryOptions(val forMe: Boolean, val forEveryone: Boolean)
