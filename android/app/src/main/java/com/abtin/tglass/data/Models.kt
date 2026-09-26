package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

enum class ChatType { Private, Group, Channel, Bot, Saved }

enum class MessageStatus { Sending, Sent, Read, Failed }

@Immutable
data class User(
    val id: Long,
    val firstName: String,
    val lastName: String = "",
    val username: String? = null,
    val phone: String = "",
    val bio: String? = null,
    val online: Boolean = false,
    val lastSeen: String = "last seen recently",
    val verified: Boolean = false,
    val premium: Boolean = false,
    val hasStory: Boolean = false,
    val storySeen: Boolean = false,
) {
    val name: String get() = if (lastName.isBlank()) firstName else "$firstName $lastName"
    val status: String get() = if (online) "online" else lastSeen
}

@Immutable
data class Reaction(val emoji: String, val count: Int, val chosen: Boolean)

@Immutable
sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Photo(val seed: Int, val aspect: Float, val caption: String? = null, val emoji: String = "🏞") : MessageContent
    data class Voice(val seconds: Int, val waveform: List<Float>) : MessageContent
    data class Sticker(val emoji: String) : MessageContent
    data class File(val name: String, val size: String) : MessageContent
    data class Location(val title: String, val address: String) : MessageContent
    data class Contact(val name: String, val phone: String) : MessageContent
    data class Poll(val question: String, val options: List<String>, val votes: List<Int>, val voted: Int? = null, val quiz: Boolean = false) : MessageContent
    data class Link(val text: String, val site: String, val title: String, val description: String) : MessageContent
    data class Service(val text: String) : MessageContent
}

@Immutable
data class Message(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val date: Long,
    val content: MessageContent,
    val outgoing: Boolean,
    val status: MessageStatus = MessageStatus.Read,
    val replyToId: Long? = null,
    val edited: Boolean = false,
    val reactions: List<Reaction> = emptyList(),
    val views: Int? = null,
    val forwardedFrom: String? = null,
    val pinned: Boolean = false,
) {
    val text: String?
        get() = when (val c = content) {
            is MessageContent.Text -> c.text
            is MessageContent.Photo -> c.caption
            is MessageContent.Link -> c.text
            else -> null
        }

    /** One-line preview used by the chat list, search and replies. */
    val preview: String
        get() = when (val c = content) {
            is MessageContent.Text -> c.text
            is MessageContent.Photo -> c.caption?.let { "🖼 $it" } ?: "Photo"
            is MessageContent.Voice -> "Voice message"
            is MessageContent.Sticker -> "${c.emoji} Sticker"
            is MessageContent.File -> "📄 ${c.name}"
            is MessageContent.Location -> "📍 Location"
            is MessageContent.Contact -> "👤 Contact"
            is MessageContent.Poll -> "📊 ${c.question}"
            is MessageContent.Link -> c.text
            is MessageContent.Service -> c.text
        }
}

@Immutable
data class Chat(
    val id: Long,
    val type: ChatType,
    val title: String,
    val peerUserId: Long? = null,
    val members: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val unread: Int = 0,
    val mentions: Int = 0,
    val markedUnread: Boolean = false,
    val archived: Boolean = false,
    val draft: String? = null,
    val typing: String? = null,
    val verified: Boolean = false,
    val description: String? = null,
    val username: String? = null,
    val folder: String? = null,
)

@Immutable
data class CallRecord(
    val id: Long,
    val userId: Long,
    val date: Long,
    val outgoing: Boolean,
    val missed: Boolean,
    val video: Boolean,
    val durationSec: Int,
)

@Immutable
data class Story(val userId: Long, val emoji: String, val colors: List<Long>, val caption: String, val date: Long)

@Immutable
data class Session(val device: String, val app: String, val location: String, val lastActive: String, val current: Boolean = false)

/** Sample bubbles for the Appearance preview. */
object BubbleDemo {
    private val now = System.currentTimeMillis()
    val incoming = Message(-10, -1, 1, now - 120_000, MessageContent.Text("Do you like the new Liquid Glass look? 😍"), outgoing = false)
    val outgoing = Message(-11, -1, 0, now - 60_000, MessageContent.Text("It looks exactly like iOS!"), outgoing = true, replyToId = -10)
}
