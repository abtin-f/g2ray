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

/**
 * A file (picture, voice note, video) that may live in a local file ([path]) or still need downloading ([fileId], via the repository).
 * [mini] is Telegram's tiny inline JPEG preview, shown blurred until the real file is ready.
 */
@Immutable
class ImageRef(
    val fileId: Int,
    val path: String? = null,
    val mini: ByteArray? = null,
    val width: Int = 0,
    val height: Int = 0,
    /** File size in bytes (0 = unknown); with the download progress it gives Telegram's "2.3 / 14 MB". */
    val size: Long = 0,
) {
    override fun equals(other: Any?) = other is ImageRef && other.fileId == fileId && other.path == path
    override fun hashCode() = fileId * 31 + (path?.hashCode() ?: 0)
}

/** Formatting / link span inside a message text, in UTF-16 offsets (same as TDLib and Kotlin strings). */
@Immutable
data class Entity(val start: Int, val end: Int, val type: EntityType, val url: String? = null, val userId: Long = 0)

enum class EntityType { Bold, Italic, Underline, Strike, Code, Pre, Spoiler, Quote, Url, TextUrl, Mention, MentionName, Hashtag, Cashtag, BotCommand, Email, Phone }

@Immutable
data class Reaction(val emoji: String, val count: Int, val chosen: Boolean)

@Immutable
sealed interface MessageContent {
    data class Text(val text: String, val entities: List<Entity> = emptyList()) : MessageContent
    data class Photo(
        val seed: Int,
        val aspect: Float,
        val caption: String? = null,
        val emoji: String = "🏞",
        val captionEntities: List<Entity> = emptyList(),
        /** Picture to show (the photo itself, or a video's thumbnail). */
        val image: ImageRef? = null,
        val video: Boolean = false,
        /** The playable video file when [video] is true. */
        val videoFile: ImageRef? = null,
        val duration: Int = 0,
        /** GIF-style animation: loops silently. */
        val loop: Boolean = false,
    ) : MessageContent
    /** [listened]: false while the recipient has not played it yet (Telegram's small dot next to the duration). */
    data class Voice(val seconds: Int, val waveform: List<Float>, val media: ImageRef? = null, val listened: Boolean = true) : MessageContent
    /**
     * A round video message ("video note"): [video] is the square MP4, [thumb] its cover (with Telegram's blurred minithumbnail),
     * [viewed] whether the recipient has played it.
     */
    data class VideoNote(val seconds: Int, val video: ImageRef? = null, val thumb: ImageRef? = null, val viewed: Boolean = false) : MessageContent
    /** [animation] is a Telegram animated sticker (gzipped Lottie, .tgs). */
    data class Sticker(val emoji: String, val image: ImageRef? = null, val animation: ImageRef? = null) : MessageContent
    /** A document; [music] files play inline like Telegram's audio player. */
    data class File(
        val name: String,
        val size: String,
        val file: ImageRef? = null,
        val mime: String? = null,
        val music: Boolean = false,
        val duration: Int = 0,
        val performer: String? = null,
        val caption: String? = null,
        val captionEntities: List<Entity> = emptyList(),
    ) : MessageContent
    data class Location(val title: String, val address: String) : MessageContent
    /** [userId] is the Telegram user behind the contact (0 if not on Telegram / unknown). */
    data class Contact(val name: String, val phone: String, val userId: Long = 0) : MessageContent
    /** [correctOption] / [explanation] are only set for quizzes being sent from the New Poll screen. */
    data class Poll(
        val question: String,
        val options: List<String>,
        val votes: List<Int>,
        val voted: Int? = null,
        val quiz: Boolean = false,
        val anonymous: Boolean = true,
        val multiple: Boolean = false,
        val correctOption: Int? = null,
        val explanation: String? = null,
        /** Every option the user chose (several for multiple-answer polls); [voted] is the first of them. */
        val chosen: List<Int> = listOfNotNull(voted),
        /** Nobody can vote any more ("Final Results"). */
        val closed: Boolean = false,
        /** People who voted (with multiple answers this is less than the sum of [votes]); -1 = sum of [votes]. */
        val totalVoters: Int = -1,
        /** Server-rounded percentages per option (they add up to 100); empty = computed from [votes]. */
        val percents: List<Int> = emptyList(),
        /** Voters can be listed ("View Results"): public polls the user can see the results of. */
        val canGetVoters: Boolean = false,
        /** False when the results stay hidden until the poll closes. */
        val canSeeResults: Boolean = true,
        /** A few recent voters (user / chat ids) for the avatars next to "Public Poll". */
        val recentVoters: List<Long> = emptyList(),
        /** The vote can be taken back (Retract Vote); quizzes never allow it. */
        val canRetract: Boolean = !quiz,
    ) : MessageContent {
        val voterCount: Int get() = if (totalVoters >= 0) totalVoters else votes.sum()
        fun percentOf(i: Int): Int {
            percents.getOrNull(i)?.let { return it }
            val total = votes.sum()
            return if (total > 0) ((votes.getOrElse(i) { 0 } * 100f) / total).roundToIntSafe() else 0
        }
    }
    data class Link(val text: String, val site: String, val title: String, val description: String, val entities: List<Entity> = emptyList()) : MessageContent
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
    /** TDLib media album (Message.mediaAlbumId): photos/videos/files sent together; 0 if none. */
    val albumId: Long = 0,
    /** Original sender of a forwarded message: user id (> 0) or chat id (< 0, groups / channels); 0 = unknown or hidden. */
    val forwardPeerId: Long = 0,
    /** The original channel post of a forwarded message (to jump to it), 0 if none. */
    val forwardMessageId: Long = 0,
    /** Posts of a channel with a discussion group: number of comments; null = the post has no comments section. */
    val comments: Int? = null,
) {
    val text: String?
        get() = when (val c = content) {
            is MessageContent.Text -> c.text
            is MessageContent.Photo -> c.caption
            is MessageContent.File -> c.caption
            is MessageContent.Link -> c.text
            else -> null
        }

    /** One-line preview used by the chat list, search and replies. */
    val preview: String
        get() = when (val c = content) {
            is MessageContent.Text -> c.text
            is MessageContent.Photo -> c.caption?.let { "🖼 $it" } ?: if (c.video) "Video" else "Photo"
            is MessageContent.Voice -> "Voice message"
            is MessageContent.VideoNote -> "Video message"
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
    /** Sort key inside its list (TDLib chat position order); 0 for the local demo data. */
    val order: Long = 0,
    /** TDLib chat folders this chat is in. */
    val folderIds: Set<Int> = emptySet(),
    /** False for public groups/channels opened from search that the user has not joined. */
    val joined: Boolean = true,
    /** Channels: whether the user may post (owner/admin). */
    val canPost: Boolean = false,
    /** Groups/channels: what the user may manage (owner or admin); null for regular members. */
    val rights: ChatRights? = null,
)

/** Management rights of the current user in a group or channel. */
@Immutable
data class ChatRights(
    val owner: Boolean = false,
    /** Edit title, description and photo. */
    val changeInfo: Boolean = false,
    /** Add members / subscribers. */
    val inviteUsers: Boolean = false,
    /** Remove members. */
    val banMembers: Boolean = false,
) {
    companion object {
        val Owner = ChatRights(owner = true, changeInfo = true, inviteUsers = true, banMembers = true)
    }
}

@Immutable
data class CallRecord(
    val id: Long,
    val userId: Long,
    val date: Long,
    val outgoing: Boolean,
    val missed: Boolean,
    val video: Boolean,
    val durationSec: Int,
    /** Chat that holds the call message (the private chat with [userId]); [id] is that message's id. */
    val chatId: Long = userId,
)

/**
 * One story. Demo stories are drawn from [emoji] + [colors]; live ones carry the real media:
 * [image] is the photo (or the video's cover) and [video] the video file, both downloaded through the repository.
 * [date] is in epoch milliseconds. [loaded] is false while only the story id is known (full story not fetched yet).
 */
@Immutable
data class Story(
    val userId: Long,
    val emoji: String = "",
    val colors: List<Long> = emptyList(),
    val caption: String = "",
    val date: Long = 0,
    /** Telegram story id (0 in the demo). */
    val id: Int = 0,
    /** Chat that posted the story (for private chats the same as [userId]). */
    val chatId: Long = userId,
    val image: ImageRef? = null,
    val video: ImageRef? = null,
    /** Video length in seconds (0 for photos). */
    val durationSec: Double = 0.0,
    /** Whether the user has already viewed this story. */
    val seen: Boolean = false,
    val loaded: Boolean = true,
    /** Pinned to the top of the profile's Posts tab. */
    val pinned: Boolean = false,
)

@Immutable
data class Session(val device: String, val app: String, val location: String, val lastActive: String, val current: Boolean = false, val id: Long = 0)

/** Details shown on a profile page (bio / description, public link, members). */
@Immutable
data class ChatInfo(val about: String? = null, val link: String? = null, val memberCount: Int = 0, val members: List<Member> = emptyList())

@Immutable
data class Member(val userId: Long, val role: String? = null)

/** Results of a server-side search: public chats/users and messages from all chats. */
@Immutable
data class GlobalResults(val chats: List<Chat>, val messages: List<Message>)

/** A sticker from the user's sticker sets; [image] is a static preview, [animation] the .tgs if animated. */
@Immutable
data class StickerItem(val fileId: Int, val emoji: String, val image: ImageRef?, val animation: ImageRef?, val width: Int, val height: Int)

@Immutable
data class StickerPack(val id: Long, val title: String, val stickers: List<StickerItem>)

/** A saved GIF (MP4 animation). */
@Immutable
data class GifItem(val fileId: Int, val thumb: ImageRef?, val width: Int, val height: Int, val duration: Int)

/** Privacy settings Telegram exposes as Everybody / My Contacts / Nobody. */
enum class PrivacyKey(val title: String) {
    PhoneNumber("Phone Number"),
    LastSeen("Last Seen & Online"),
    ProfilePhoto("Profile Photos"),
    Forwards("Forwarded Messages"),
    Calls("Calls"),
    Invites("Groups & Channels"),
}

enum class PrivacyValue(val title: String) { Everybody("Everybody"), Contacts("My Contacts"), Nobody("Nobody") }

enum class ProxyKind(val title: String) { Socks5("SOCKS5"), MTProto("MTProto"), Http("HTTP") }

/** A saved proxy server; [ping] is the last measured round trip in ms (null = unknown, -1 = unavailable). */
@Immutable
data class ProxyItem(
    val id: Int,
    val server: String,
    val port: Int,
    val kind: ProxyKind,
    val secret: String = "",
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = false,
    val ping: Int? = null,
) {
    /** Shareable t.me link, like Telegram's "Share" action. */
    val link: String
        get() = when (kind) {
            ProxyKind.MTProto -> "https://t.me/proxy?server=$server&port=$port&secret=$secret"
            else -> "https://t.me/socks?server=$server&port=$port" +
                (if (username.isNotEmpty()) "&user=${android.net.Uri.encode(username)}&pass=${android.net.Uri.encode(password)}" else "")
        }

    companion object {
        /** Parses tg://proxy, tg://socks, t.me/proxy and t.me/socks links (null if it isn't one). */
        fun fromLink(link: String): ProxyItem? {
            val uri = runCatching { android.net.Uri.parse(link.trim()) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            val kind = when {
                scheme == "tg" && uri.host == "proxy" -> ProxyKind.MTProto
                scheme == "tg" && uri.host == "socks" -> ProxyKind.Socks5
                scheme.startsWith("http") && uri.host?.lowercase()?.removePrefix("www.") in setOf("t.me", "telegram.me") ->
                    when (uri.pathSegments.firstOrNull()?.lowercase()) {
                        "proxy" -> ProxyKind.MTProto
                        "socks" -> ProxyKind.Socks5
                        else -> return null
                    }
                else -> return null
            }
            val server = uri.getQueryParameter("server")?.takeIf { it.isNotBlank() } ?: return null
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: return null
            return ProxyItem(
                id = 0, server = server, port = port, kind = kind,
                secret = uri.getQueryParameter("secret").orEmpty(),
                username = uri.getQueryParameter("user").orEmpty(),
                password = uri.getQueryParameter("pass").orEmpty(),
            )
        }
    }
}

/** Shared media tabs of a profile. */
enum class MediaKind { Media, Files, Links, Voice, Gifs }

/** Sample bubbles for the Appearance preview. */
object BubbleDemo {
    private val now = System.currentTimeMillis()
    val incoming = Message(-10, -1, 1, now - 120_000, MessageContent.Text("Do you like the new Liquid Glass look? 😍"), outgoing = false)
    val outgoing = Message(-11, -1, 0, now - 60_000, MessageContent.Text("It looks exactly like iOS!"), outgoing = true, replyToId = -10)
}

private fun Float.roundToIntSafe(): Int = if (isNaN()) 0 else kotlin.math.round(this).toInt()
