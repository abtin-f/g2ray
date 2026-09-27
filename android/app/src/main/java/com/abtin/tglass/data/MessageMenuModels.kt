package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/**
 * Reactions a message can get (TDLib getMessageAvailableReactions), emoji only.
 * [top] is what the capsule shows first; [all] everything the chat allows (for the expanded grid).
 * [available] is false when the chat has reactions turned off.
 */
@Immutable
data class AvailableReactionsInfo(
    val top: List<String>,
    val all: List<String>,
    val available: Boolean = true,
)

/** What the user may do with one message (TDLib getMessageProperties). */
@Immutable
data class MessageCaps(
    val canReply: Boolean = true,
    val canEdit: Boolean = false,
    val canForward: Boolean = true,
    val canPin: Boolean = true,
    val canSave: Boolean = true,
    val canGetLink: Boolean = false,
    val canReport: Boolean = false,
    val canDeleteForSelf: Boolean = true,
    val canDeleteForAll: Boolean = false,
)

/** One reason offered by Telegram when reporting messages. */
class ReportChoice(val id: ByteArray, val title: String)

/** Where a report stands after a step: pick a reason, done, or failed. */
sealed interface ReportStep {
    class Options(val title: String, val options: List<ReportChoice>) : ReportStep
    data object Done : ReportStep
    class Failed(val message: String) : ReportStep
}

/** Telegram's default top reactions (demo mode, and before a chat's own list loads). */
val TopReactions = listOf("👍", "❤️", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🎉", "🤩", "🙏", "👌", "😢", "💯")

/** All free emoji reactions Telegram offers. */
val FreeReactions = listOf(
    "👍", "👎", "❤️", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🤬", "😢", "🎉", "🤩", "🤮", "💩",
    "🙏", "👌", "🕊", "🤡", "🥱", "🥴", "😍", "🐳", "❤️‍🔥", "🌚", "🌭", "💯", "🤣", "⚡", "🍌", "🏆",
    "💔", "🤨", "😐", "🍓", "🍾", "💋", "🖕", "😈", "😴", "😭", "🤓", "👻", "👨‍💻", "👀", "🎃", "🙈",
    "😇", "😨", "🤝", "✍", "🤗", "🫡", "🎅", "🎄", "☃", "💅", "🤪", "🗿", "🆒", "💘", "🙉", "🦄",
    "😘", "💊", "🙊", "😎", "👾", "🤷‍♂", "🤷", "🤷‍♀", "😡",
)
