package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/**
 * What the long-press menu shows about who saw a message (Telegram iOS: "Read at 14:32", "5 Seen").
 * Loaded once per menu (TDLib getMessageProperties + getMessageReadDate / getMessageViewers).
 */
@Immutable
data class MessageSeenInfo(
    /** Private chats: when the other person read the message (ms); null when unknown, unread or hidden. */
    val readAt: Long? = null,
    /** Groups: members who viewed the message, most recent first; null when not available. */
    val viewers: List<MessageViewerInfo>? = null,
)

/** One member who viewed a message and when (ms, 0 = unknown). */
@Immutable
data class MessageViewerInfo(val userId: Long, val date: Long)

/** One reaction someone put on a message: [senderId] is a user id or (negative) chat id; [date] in ms (0 = unknown). */
@Immutable
data class AddedReactionInfo(val senderId: Long, val emoji: String, val date: Long, val outgoing: Boolean = false)

/** A page of [AddedReactionInfo]; [nextOffset] is empty when there are no more. */
@Immutable
data class AddedReactionsPage(val items: List<AddedReactionInfo>, val total: Int, val nextOffset: String)

/**
 * Telegram's animations of an emoji reaction (TDLib getEmojiReaction): each is a TGS file (null when absent).
 * [appear] plays when the picker opens, [select] when the reaction is pressed there, [activate] at the center when
 * it is chosen, [effect] (big, around the message) at the same time.
 */
@Immutable
data class ReactionAnimations(
    val emoji: String,
    val appear: ImageRef? = null,
    val select: ImageRef? = null,
    val activate: ImageRef? = null,
    val effect: ImageRef? = null,
    val around: ImageRef? = null,
    val center: ImageRef? = null,
)
