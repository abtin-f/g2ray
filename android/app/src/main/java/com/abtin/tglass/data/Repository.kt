package com.abtin.tglass.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The UI talks only to this interface (spec §61). [DemoRepository] backs it with local sample data,
 * [com.abtin.tglass.data.td.TdRepository] with a real account through TDLib.
 */
interface TelegramRepository {
    /** True when backed by a real Telegram account. */
    val isLive: Boolean get() = false

    /** "Connecting…", "Updating…" etc. while the connection is not ready; null when online. */
    val connectionStatus: String? get() = null

    /** Chat that holds Saved Messages. */
    val savedChatId: Long get() = 100

    val me: User
    val users: Map<Long, User>
    val chats: List<Chat>
    val calls: List<CallRecord>
    val stories: List<Story>
    val sessions: List<Session>
    val folders: List<String>

    fun chat(id: Long): Chat?
    fun user(id: Long): User? = users[id]
    fun messages(chatId: Long): List<Message>
    fun lastMessage(chatId: Long): Message? = messages(chatId).lastOrNull()

    fun sendText(chatId: Long, text: String, replyTo: Long?)
    fun sendContent(chatId: Long, content: MessageContent, replyTo: Long? = null)
    fun editText(chatId: Long, messageId: Long, text: String)
    fun deleteMessages(chatId: Long, ids: Set<Long>, forEveryone: Boolean = false)
    fun toggleReaction(chatId: Long, messageId: Long, emoji: String)
    fun togglePinMessage(chatId: Long, messageId: Long)
    fun vote(chatId: Long, messageId: Long, option: Int)
    fun openChat(chatId: Long)
    fun setDraft(chatId: Long, draft: String?)

    fun togglePin(chatId: Long)
    fun toggleMute(chatId: Long)
    fun toggleRead(chatId: Long)
    fun toggleArchive(chatId: Long)
    fun deleteChat(chatId: Long)
    fun privateChatWith(userId: Long): Long
    fun markStorySeen(userId: Long)
    fun terminateSession(session: Session)
    fun terminateOtherSessions()

    /** People shown on the Contacts tab and in New Message. */
    val contacts: List<User>
        get() = users.values.filter { it.id != me.id && it.id != 10L }

    /** Whether [chat] belongs to the folder at [index] of [folders] (0 = All Chats). */
    fun isInFolder(chat: Chat, index: Int): Boolean = when (folders.getOrNull(index)) {
        "Personal" -> chat.folder == "Personal" || chat.type == ChatType.Private
        "Work" -> chat.folder == "Work"
        "Unread" -> chat.unread > 0 || chat.markedUnread
        else -> true
    }

    /** Profile photo of a user or chat (keyed by user id / chat id), if it has one. */
    fun avatar(peerId: Long): ImageRef? = null

    /** Local path of a downloaded file, or null while it is not available yet. */
    fun filePath(image: ImageRef): String? = image.path

    /** Starts downloading the file behind [image]; [filePath] turns non-null once it is done. */
    fun requestImage(image: ImageRef) {}

    /** Download progress 0..1 of a file started with [requestImage]. */
    fun fileProgress(image: ImageRef): Float = if (filePath(image) != null) 1f else 0f

    /** Sends picked photos/videos; several at once are grouped into an album where supported. */
    fun sendMedia(chatId: Long, items: List<MessageContent>, replyTo: Long?) {
        items.forEachIndexed { i, it -> sendContent(chatId, it, if (i == 0) replyTo else null) }
    }

    /** Loads older messages of a chat when the user scrolls to the top of the history. */
    fun loadOlderMessages(chatId: Long) {}

    fun closeChat(chatId: Long) {}

    /** Profile details; null until [loadChatInfo] delivered them (the demo builds them locally). */
    fun chatInfo(chatId: Long): ChatInfo? {
        val chat = chat(chatId) ?: return null
        val user = chat.peerUserId?.let { user(it) }
        val members = if (chat.type == ChatType.Group) {
            users.values.filter { it.id != me.id }.take(8).mapIndexed { i, u -> Member(u.id, if (i == 0) "owner" else if (i < 3) "admin" else null) }
        } else emptyList()
        return ChatInfo(about = user?.bio ?: chat.description, link = user?.username ?: chat.username, memberCount = chat.members, members = members)
    }

    fun loadChatInfo(chatId: Long) {}

    /** Messages of one shared-media kind, newest first. */
    fun sharedMedia(chatId: Long, kind: MediaKind): List<Message> = messages(chatId).asReversed().filter { m ->
        when (kind) {
            MediaKind.Media -> (m.content as? MessageContent.Photo)?.loop == false
            MediaKind.Gifs -> (m.content as? MessageContent.Photo)?.loop == true
            MediaKind.Files -> m.content is MessageContent.File
            MediaKind.Links -> m.content is MessageContent.Link
            MediaKind.Voice -> m.content is MessageContent.Voice
        }
    }

    fun loadSharedMedia(chatId: Long, kind: MediaKind) {}

    /** The account's installed sticker sets, recent stickers and saved GIFs (empty in the demo). */
    val stickerPacks: List<StickerPack> get() = emptyList()
    val recentStickers: List<StickerItem> get() = emptyList()
    val savedGifs: List<GifItem> get() = emptyList()
    fun loadStickers() {}
    fun sendSticker(chatId: Long, sticker: StickerItem, replyTo: Long?) {}
    fun sendGif(chatId: Long, gif: GifItem, replyTo: Long?) {}

    /** Server-side search (public usernames and all messages). The demo has no server, so nothing. */
    fun searchGlobal(query: String, onResult: (GlobalResults) -> Unit) {}

    /** A message from the loaded history or from shared media. */
    fun findMessage(chatId: Long, messageId: Long): Message? =
        messages(chatId).firstOrNull { it.id == messageId }
            ?: MediaKind.entries.firstNotNullOfOrNull { k -> sharedMedia(chatId, k).firstOrNull { it.id == messageId } }

    /** Fetches a single message (e.g. the original of a reply) so [findMessage] can return it. */
    fun requestMessage(chatId: Long, messageId: Long) {}

    /** Loads the history around [messageId] (search results, old pins); [onLoaded] runs when it is in [messages]. */
    fun loadAround(chatId: Long, messageId: Long, onLoaded: () -> Unit) = onLoaded()

    /** The chat's pinned message (latest one). */
    fun pinnedMessage(chatId: Long): Message? = messages(chatId).lastOrNull { it.pinned }

    /** Id of the last message the user has read; newer incoming ones are unread. Null when nothing is unread. */
    fun readAnchor(chatId: Long): Long? {
        val chat = chat(chatId) ?: return null
        if (chat.unread <= 0) return null
        val incoming = messages(chatId).filter { !it.outgoing }
        return incoming.dropLast(chat.unread).lastOrNull()?.id ?: 0L
    }

    /** Tells the other side "typing…" (throttled by the implementation). */
    fun sendTyping(chatId: Long) {}

    /** Messages of a chat containing [query], newest first. */
    fun searchInChat(chatId: Long, query: String, onResult: (List<Message>) -> Unit) {
        onResult(messages(chatId).filter { it.preview.contains(query, ignoreCase = true) }.asReversed())
    }

    /** Finds the chat behind a public @username (null if there is none). */
    fun resolveUsername(username: String, onResult: (Long?) -> Unit) {
        val name = username.removePrefix("@")
        val id = chats.firstOrNull { it.username.equals(name, true) }?.id
            ?: users.values.firstOrNull { it.username.equals(name, true) }?.let { privateChatWith(it.id) }
        onResult(id)
    }

    fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long) {
        messageIds.mapNotNull { id -> messages(fromChatId).firstOrNull { it.id == id } }.forEach { sendContent(toChatId, it.content) }
    }

    fun joinChat(chatId: Long) {}

    /** Saves name and bio; [onDone] gets an error message or null. */
    fun updateProfile(firstName: String, lastName: String, bio: String, onDone: (String?) -> Unit) = onDone(null)
    fun updateUsername(username: String, onDone: (String?) -> Unit) = onDone(null)
    fun updateProfilePhoto(path: String, onDone: (String?) -> Unit) = onDone(null)

    fun privacy(key: PrivacyKey): PrivacyValue? = when (key) {
        PrivacyKey.PhoneNumber -> PrivacyValue.Contacts
        else -> PrivacyValue.Everybody
    }
    fun loadPrivacy() {}
    fun setPrivacy(key: PrivacyKey, value: PrivacyValue) {}

    fun logOut() {}

    // ---- Stories ----

    /** People shown in the stories strip, in Telegram's order (unseen first). The current user is not included. */
    val storyUsers: List<User>
        get() = users.values.filter { it.hasStory && it.id != me.id }.sortedBy { it.storySeen }

    /** Active stories of one user, oldest first. Live stories may still be placeholders (`loaded == false`) until [loadStories]. */
    fun storiesOf(userId: Long): List<Story> = stories.filter { it.userId == userId }

    /** Fetches the full stories (media, caption) of [userId]; [storiesOf] updates when they arrive. */
    fun loadStories(userId: Long) {}

    /** The viewer started showing [story]: marks it as viewed. */
    fun openStory(story: Story) = markStorySeen(story.userId)

    /** The viewer stopped showing [story]. */
    fun closeStory(story: Story) {}

    // ---- Groups & channels, mute durations ----

    /** Mutes notifications of a chat for [seconds] ([MUTE_FOREVER] = forever); 0 unmutes. */
    fun muteFor(chatId: Long, seconds: Int) {
        val chat = chat(chatId) ?: return
        if ((seconds > 0) != chat.muted) toggleMute(chatId)
    }

    /** Creates a group with [userIds] and an optional photo; [onDone] gets the new chat id or an error message. */
    fun createGroup(title: String, userIds: List<Long>, photoPath: String?, onDone: (chatId: Long?, error: String?) -> Unit) =
        onDone(null, "Not available in the demo")

    /** Creates a channel; [onDone] gets the new chat id or an error message. */
    fun createChannel(title: String, description: String, photoPath: String?, onDone: (chatId: Long?, error: String?) -> Unit) =
        onDone(null, "Not available in the demo")

    /** Changes the title and description of a group/channel; [onDone] gets an error message or null. */
    fun editChat(chatId: Long, title: String, description: String, onDone: (String?) -> Unit) = onDone(null)

    /** Sets (or with a null [path] removes) the photo of a group/channel. */
    fun updateChatPhoto(chatId: Long, path: String?, onDone: (String?) -> Unit) = onDone(null)

    /** Adds users to a group/channel; [onDone] gets an error message (e.g. privacy restrictions) or null. */
    fun addMembers(chatId: Long, userIds: List<Long>, onDone: (String?) -> Unit) = onDone(null)

    /** Removes a member from a group. */
    fun removeMember(chatId: Long, userId: Long, onDone: (String?) -> Unit) = onDone(null)

    /** Deletes a group/channel for all members (owner only). */
    fun deleteChatForAll(chatId: Long, onDone: (String?) -> Unit) {
        deleteChat(chatId)
        onDone(null)
    }

    companion object {
        /** Anything over a year counts as "forever" for Telegram. */
        const val MUTE_FOREVER = Int.MAX_VALUE
    }
}

class DemoRepository(private val scope: CoroutineScope) : TelegramRepository {

    private val now = System.currentTimeMillis()
    private fun ago(minutes: Long) = now - minutes * 60_000

    override val me = User(0, "Abtin", "", "abtin", "+98 912 000 0000", "Building my own Telegram ✨", online = true, premium = true)

    private val userList = listOf(
        me,
        User(1, "Sara", "Ahmadi", "sara_a", "+98 912 111 2233", "Designer • Coffee lover ☕️", online = true, hasStory = true),
        User(2, "Reza", "Karimi", "rezak", "+98 935 222 3344", "Android dev", lastSeen = "last seen 5 minutes ago", hasStory = true),
        User(3, "Mina", "Rahimi", "mina", "+98 901 333 4455", lastSeen = "last seen at 11:24", hasStory = true, storySeen = true),
        User(4, "Ali", "Moradi", null, "+98 912 444 5566", online = true),
        User(5, "Niloofar", "", "nilo", "+98 919 555 6677", "🌸", lastSeen = "last seen yesterday at 22:10", hasStory = true),
        User(6, "Pavel", "Durov", "durov", "+42 000 0000", "Founder", lastSeen = "last seen recently", verified = true, premium = true),
        User(7, "Kian", "Tehrani", "kian", "+98 930 777 8899", lastSeen = "last seen within a week"),
        User(8, "Donya", "Farahani", null, "+98 912 888 9900", online = true, hasStory = true),
        User(9, "Behnam", "", "behnam", "+98 912 999 0011", lastSeen = "last seen a long time ago"),
        User(10, "Telegram", "", "telegram", "42777", "Official notifications", verified = true),
        User(11, "Hamid", "Soleimani", "hamid", "+98 912 121 2121", lastSeen = "last seen 2 hours ago"),
        User(12, "Elham", "Jafari", null, "+98 912 131 3131", lastSeen = "last seen recently"),
    )
    override val users: Map<Long, User> = mutableStateMapOf<Long, User>().apply { userList.forEach { put(it.id, it) } }

    private val chatList: SnapshotStateList<Chat> = mutableStateListOf(
        Chat(100, ChatType.Saved, "Saved Messages", peerUserId = 0, pinned = true),
        Chat(101, ChatType.Private, "Sara Ahmadi", peerUserId = 1, pinned = true, unread = 3, typing = null),
        Chat(102, ChatType.Group, "Android Devs 🇮🇷", members = 1284, unread = 42, mentions = 1, folder = "Work",
            description = "Everything Kotlin, Compose and Android.", username = "androiddevs_ir"),
        Chat(103, ChatType.Private, "Reza Karimi", peerUserId = 2, draft = "See you tomorrow at 10?", folder = "Work"),
        Chat(104, ChatType.Channel, "Telegram News", members = 9_870_000, unread = 2, muted = true, verified = true,
            description = "The official Telegram channel.", username = "telegram"),
        Chat(105, ChatType.Private, "Mina Rahimi", peerUserId = 3),
        Chat(106, ChatType.Group, "Family 👨‍👩‍👧", members = 8, unread = 5, muted = true, folder = "Personal"),
        Chat(107, ChatType.Private, "Ali Moradi", peerUserId = 4, folder = "Personal"),
        Chat(108, ChatType.Bot, "BotFather", verified = true, description = "BotFather is the one bot to rule them all.", username = "BotFather"),
        Chat(109, ChatType.Private, "Niloofar", peerUserId = 5, markedUnread = true),
        Chat(110, ChatType.Private, "Pavel Durov", peerUserId = 6, verified = true),
        Chat(111, ChatType.Channel, "Design Daily", members = 48_200, description = "UI/UX inspiration every day.", username = "designdaily"),
        Chat(112, ChatType.Private, "Kian Tehrani", peerUserId = 7, archived = true),
        Chat(113, ChatType.Group, "Old University Friends", members = 23, archived = true, muted = true),
        Chat(114, ChatType.Private, "Telegram", peerUserId = 10, verified = true),
        Chat(115, ChatType.Private, "Donya Farahani", peerUserId = 8),
    )
    override val chats: List<Chat> get() = chatList

    private val messageStore = mutableStateMapOf<Long, SnapshotStateList<Message>>()
    private var nextId = 10_000L

    override val calls: List<CallRecord> = listOf(
        CallRecord(1, 1, ago(35), outgoing = true, missed = false, video = false, durationSec = 312),
        CallRecord(2, 2, ago(160), outgoing = false, missed = true, video = false, durationSec = 0),
        CallRecord(3, 4, ago(60 * 5), outgoing = false, missed = false, video = true, durationSec = 1240),
        CallRecord(4, 3, ago(60 * 26), outgoing = true, missed = false, video = false, durationSec = 65),
        CallRecord(5, 5, ago(60 * 30), outgoing = false, missed = true, video = true, durationSec = 0),
        CallRecord(6, 8, ago(60 * 50), outgoing = true, missed = false, video = false, durationSec = 1802),
        CallRecord(7, 1, ago(60 * 75), outgoing = false, missed = false, video = false, durationSec = 94),
        CallRecord(8, 9, ago(60 * 24 * 4), outgoing = true, missed = true, video = false, durationSec = 0),
    )

    override val stories: List<Story> = listOf(
        Story(1, "🏔", listOf(0xFF4FACFE, 0xFF00F2FE), "Weekend in Darband", ago(120)),
        Story(2, "💻", listOf(0xFF434343, 0xFF000000), "New Compose release!", ago(300)),
        Story(3, "🌅", listOf(0xFFFA709A, 0xFFFEE140), "Sunset vibes", ago(500)),
        Story(5, "🌸", listOf(0xFFA18CD1, 0xFFFBC2EB), "Spring is here", ago(700)),
        Story(8, "☕️", listOf(0xFFF6D365, 0xFFFDA085), "Morning coffee", ago(900)),
    )

    override val sessions: List<Session> get() = sessionList
    private val sessionList = mutableStateListOf(
        Session("Pixel 9 Pro", "TGlass 0.1.0", "Tehran, Iran", "online", current = true),
        Session("MacBook Pro", "Telegram macOS 11.3", "Tehran, Iran", "10:42"),
        Session("iPhone 16 Pro", "Telegram iOS 12.1", "Karaj, Iran", "yesterday"),
        Session("Chrome 139, Windows", "Telegram Web A 3.2", "Isfahan, Iran", "Sep 12"),
    )

    override val folders = listOf("All Chats", "Personal", "Work", "Unread")

    init {
        seedMessages()
    }

    private fun msg(chatId: Long, sender: Long, minutesAgo: Long, content: MessageContent, reactions: List<Reaction> = emptyList(), reply: Long? = null, views: Int? = null, status: MessageStatus = MessageStatus.Read): Message =
        Message(nextId++, chatId, sender, ago(minutesAgo), content, outgoing = sender == 0L, status = status, reactions = reactions, replyToId = reply, views = views)

    private fun text(s: String) = MessageContent.Text(s)

    private fun wave(n: Int, seed: Int) = List(n) { i -> (0.2f + 0.8f * (((i * 37 + seed * 11) % 23) / 23f)) }

    private fun seedMessages() {
        fun put(chatId: Long, list: List<Message>) {
            messageStore[chatId] = mutableStateListOf<Message>().apply { addAll(list) }
        }
        put(100, listOf(
            msg(100, 0, 60 * 30, text("Ideas for the app:\n• iOS 26 Liquid Glass tab bar\n• Blurred context menu on long press\n• Swipe back everywhere")),
            msg(100, 0, 60 * 29, MessageContent.Link("https://github.com/Kyant0/AndroidLiquidGlass", "github.com", "Kyant0/AndroidLiquidGlass", "Compose Multiplatform Liquid Glass effect")),
            msg(100, 0, 60 * 2, MessageContent.File("Telegram_iOS_Exact_UI_UX_Design_Spec.md", "38 KB")),
        ))
        val s1 = msg(101, 1, 180, text("Hey! Did you see the new Telegram update? 😍"))
        put(101, listOf(
            msg(101, 1, 60 * 25, text("Good morning ☀️")),
            msg(101, 0, 60 * 25 - 2, text("Morning! How was the trip?")),
            msg(101, 1, 60 * 25 - 4, MessageContent.Photo(3, 1.33f, "Darband was beautiful 🏔", "🏔"), listOf(Reaction("❤️", 1, true))),
            msg(101, 1, 60 * 25 - 5, text("We should go together next time")),
            msg(101, 0, 60 * 24, text("Definitely! Let me know when")),
            s1,
            msg(101, 0, 178, text("Yes!! The Liquid Glass design is insane"), reply = s1.id),
            msg(101, 0, 177, text("I'm building my own client that looks exactly like it, for Android 🤓")),
            msg(101, 1, 150, MessageContent.Voice(14, wave(40, 3))),
            msg(101, 1, 149, text("Send me the APK when it's ready!"), listOf(Reaction("🔥", 2, false), Reaction("👍", 1, true))),
            msg(101, 1, 12, MessageContent.Sticker("🥳")),
            msg(101, 1, 11, text("Also, are we still on for dinner tonight?")),
        ))
        put(102, listOf(
            msg(102, 2, 400, text("Anyone tried Compose 1.10 yet?")),
            msg(102, 11, 390, text("Yes, the new shared element APIs are great")),
            msg(102, 12, 385, text("Performance is much better on low-end devices too"), listOf(Reaction("👍", 12, false), Reaction("🔥", 4, false))),
            msg(102, 0, 300, text("Here's a Liquid Glass tab bar I built with the backdrop library")),
            msg(102, 0, 299, MessageContent.Photo(5, 0.75f, null, "📱")),
            msg(102, 2, 200, text("Wow, that looks exactly like iOS 26 🤯"), listOf(Reaction("😍", 7, false))),
            msg(102, 11, 100, MessageContent.Poll("Which architecture do you use?", listOf("MVVM", "MVI", "Clean + MVVM", "Just vibes"), listOf(34, 21, 48, 12))),
            msg(102, 9, 20, text("@abtin can you share the repo?")),
            msg(102, 12, 3, text("Meetup is on Friday at 18:00, don't forget!")),
        ))
        put(103, listOf(
            msg(103, 2, 60 * 3, text("The build is green now ✅")),
            msg(103, 0, 60 * 3 - 1, text("Great, thanks Reza!")),
            msg(103, 2, 60 * 2, text("Want to review the PR together?")),
        ))
        put(104, listOf(
            msg(104, -1, 60 * 48, MessageContent.Photo(1, 1.6f, "Telegram now fully supports Liquid Glass on iOS — transparent elements and refraction effects throughout the app.", "✨"), listOf(Reaction("❤️", 18_400, false), Reaction("🔥", 9_200, false), Reaction("👍", 5_100, false)), views = 2_400_000),
            msg(104, -1, 60 * 6, text("You can control interface effects in Settings → Power Saving."), listOf(Reaction("👍", 3_900, false)), views = 1_200_000),
            msg(104, -1, 30, text("New in this update: AI summaries for long posts, comments in video chats and threads for bots."), listOf(Reaction("🎉", 7_700, false), Reaction("❤️", 2_300, false)), views = 860_000),
        ))
        put(105, listOf(
            msg(105, 3, 60 * 20, text("Can you send me the files from the meeting?")),
            msg(105, 0, 60 * 19, MessageContent.File("Meeting_Notes.pdf", "1.2 MB")),
            msg(105, 3, 60 * 19 - 1, text("Thank you! 🙏")),
        ))
        put(106, listOf(
            msg(106, 5, 90, text("Dinner at grandma's on Friday 🍲")),
            msg(106, 4, 80, text("I'll bring dessert")),
            msg(106, 5, 70, MessageContent.Location("Grandma's house", "Vanak Sq, Tehran")),
        ))
        put(107, listOf(
            msg(107, 4, 60 * 50, text("Here's my number")),
            msg(107, 4, 60 * 50 - 1, MessageContent.Contact("Ali Moradi", "+98 912 444 5566")),
            msg(107, 0, 60 * 49, text("Saved 👌")),
        ))
        put(108, listOf(
            msg(108, -2, 60 * 72, text("I can help you create and manage Telegram bots.\n\n/newbot - create a new bot\n/mybots - edit your bots")),
        ))
        put(109, listOf(msg(109, 5, 60 * 5, text("Happy birthday!! 🎂🎉"))))
        put(110, listOf(msg(110, 6, 60 * 24 * 3, text("Thanks for building on Telegram."))))
        put(111, listOf(
            msg(111, -1, 60 * 10, MessageContent.Photo(7, 1.0f, "Glassmorphism done right.", "🧊"), listOf(Reaction("😍", 540, false)), views = 22_000),
        ))
        put(112, listOf(msg(112, 7, 60 * 24 * 9, text("Long time no see!"))))
        put(113, listOf(msg(113, 9, 60 * 24 * 12, text("Reunion next month?"))))
        put(114, listOf(msg(114, 10, 60 * 24 * 20, text("Login code: 12345. Do not give this code to anyone, even if they say they are from Telegram!"))))
        put(115, listOf(msg(115, 8, 60 * 24 * 6, text("See you soon 👋"))))
    }

    override fun chat(id: Long) = chatList.firstOrNull { it.id == id }

    override fun messages(chatId: Long): List<Message> = messageStore[chatId] ?: emptyList()

    private fun list(chatId: Long) = messageStore.getOrPut(chatId) { mutableStateListOf() }

    private fun updateChat(chatId: Long, f: (Chat) -> Chat) {
        val i = chatList.indexOfFirst { it.id == chatId }
        if (i >= 0) chatList[i] = f(chatList[i])
    }

    private fun updateMessage(chatId: Long, messageId: Long, f: (Message) -> Message) {
        val l = messageStore[chatId] ?: return
        val i = l.indexOfFirst { it.id == messageId }
        if (i >= 0) l[i] = f(l[i])
    }

    private fun bumpToTop(chatId: Long) {
        val i = chatList.indexOfFirst { it.id == chatId }
        if (i > 0) {
            val c = chatList.removeAt(i)
            chatList.add(if (c.pinned) 0 else chatList.count { it.pinned }, c)
        }
    }

    override fun sendText(chatId: Long, text: String, replyTo: Long?) {
        sendContent(chatId, MessageContent.Text(text), replyTo)
    }

    override fun sendContent(chatId: Long, content: MessageContent, replyTo: Long?) {
        val m = Message(nextId++, chatId, 0, System.currentTimeMillis(), content, outgoing = true, status = MessageStatus.Sending, replyToId = replyTo)
        list(chatId).add(m)
        updateChat(chatId) { it.copy(draft = null, archived = false) }
        bumpToTop(chatId)
        scope.launch {
            delay(450)
            updateMessage(chatId, m.id) { it.copy(status = MessageStatus.Sent) }
            val chat = chat(chatId) ?: return@launch
            if (chat.type != ChatType.Private || chat.peerUserId == null || chat.peerUserId == 0L) return@launch
            delay(900)
            updateMessage(chatId, m.id) { it.copy(status = MessageStatus.Read) }
            updateChat(chatId) { it.copy(typing = "typing") }
            delay(1600)
            updateChat(chatId) { it.copy(typing = null) }
            val reply = autoReplies[(m.id % autoReplies.size).toInt()]
            list(chatId).add(Message(nextId++, chatId, chat.peerUserId, System.currentTimeMillis(), MessageContent.Text(reply), outgoing = false))
        }
    }

    private val autoReplies = listOf("Haha nice 😄", "Sounds good!", "Wait, really? 😮", "👍", "Let me check and get back to you", "Love it ❤️", "Okay, see you then!")

    override fun editText(chatId: Long, messageId: Long, text: String) = updateMessage(chatId, messageId) {
        val c = it.content
        val newContent = when (c) {
            is MessageContent.Photo -> c.copy(caption = text)
            else -> MessageContent.Text(text)
        }
        it.copy(content = newContent, edited = true)
    }

    override fun deleteMessages(chatId: Long, ids: Set<Long>, forEveryone: Boolean) {
        messageStore[chatId]?.removeAll { it.id in ids }
    }

    override fun toggleReaction(chatId: Long, messageId: Long, emoji: String) = updateMessage(chatId, messageId) { m ->
        val existing = m.reactions.firstOrNull { it.emoji == emoji }
        val others = m.reactions.map { r -> if (r.chosen && r.emoji != emoji) r.copy(count = r.count - 1, chosen = false) else r }.filter { it.count > 0 }
        val reactions = when {
            existing == null -> others + Reaction(emoji, 1, true)
            existing.chosen -> others.mapNotNull { if (it.emoji == emoji) it.copy(count = it.count - 1, chosen = false).takeIf { r -> r.count > 0 } else it }
            else -> others.map { if (it.emoji == emoji) it.copy(count = it.count + 1, chosen = true) else it }
        }
        m.copy(reactions = reactions)
    }

    override fun togglePinMessage(chatId: Long, messageId: Long) = updateMessage(chatId, messageId) { it.copy(pinned = !it.pinned) }

    override fun vote(chatId: Long, messageId: Long, option: Int) = updateMessage(chatId, messageId) { m ->
        val p = m.content as? MessageContent.Poll ?: return@updateMessage m
        if (p.voted != null) return@updateMessage m
        m.copy(content = p.copy(voted = option, votes = p.votes.mapIndexed { i, v -> if (i == option) v + 1 else v }))
    }

    override fun openChat(chatId: Long) = updateChat(chatId) { it.copy(unread = 0, mentions = 0, markedUnread = false) }

    override fun setDraft(chatId: Long, draft: String?) = updateChat(chatId) { it.copy(draft = draft?.takeIf { d -> d.isNotBlank() }) }

    override fun togglePin(chatId: Long) {
        val i = chatList.indexOfFirst { it.id == chatId }
        if (i < 0) return
        val c = chatList.removeAt(i)
        val updated = c.copy(pinned = !c.pinned)
        chatList.add(if (updated.pinned) 0 else chatList.count { it.pinned }, updated)
    }

    override fun toggleMute(chatId: Long) = updateChat(chatId) { it.copy(muted = !it.muted) }

    override fun toggleRead(chatId: Long) = updateChat(chatId) {
        if (it.unread > 0 || it.markedUnread) it.copy(unread = 0, mentions = 0, markedUnread = false) else it.copy(markedUnread = true)
    }

    override fun toggleArchive(chatId: Long) = updateChat(chatId) { it.copy(archived = !it.archived, pinned = false) }

    override fun deleteChat(chatId: Long) {
        chatList.removeAll { it.id == chatId }
        messageStore.remove(chatId)
    }

    override fun privateChatWith(userId: Long): Long {
        chatList.firstOrNull { it.peerUserId == userId && it.type != ChatType.Group }?.let { return it.id }
        if (userId == 0L) return 100
        val u = users[userId] ?: return 100
        val id = 1_000 + userId
        chatList.add(chatList.count { it.pinned }, Chat(id, ChatType.Private, u.name, peerUserId = userId))
        return id
    }

    override fun markStorySeen(userId: Long) {
        val u = users[userId] ?: return
        (users as MutableMap<Long, User>)[userId] = u.copy(storySeen = true)
    }

    override fun terminateSession(session: Session) {
        sessionList.remove(session)
    }

    override fun terminateOtherSessions() {
        sessionList.removeAll { !it.current }
    }

    // ---- Groups & channels (demo: everything happens locally) ----

    /** Member lists of groups the user created or changed in the demo (others use the generated default). */
    private val demoMembers = mutableStateMapOf<Long, List<Member>>()
    private val demoPhotos = mutableStateMapOf<Long, String>()
    private val demoAbout = mutableStateMapOf<Long, String>()
    private var nextChatId = 5_000L

    override fun avatar(peerId: Long): ImageRef? = demoPhotos[peerId]?.let { ImageRef(0, it) }

    override fun chatInfo(chatId: Long): ChatInfo? {
        val base = super.chatInfo(chatId) ?: return null
        val members = demoMembers[chatId]
        val about = demoAbout[chatId]
        if (members == null && about == null) return base
        return base.copy(
            about = about ?: base.about,
            members = members ?: base.members,
            memberCount = if (members != null) maxOf(chat(chatId)?.members ?: 0, members.size) else base.memberCount,
        )
    }

    override fun muteFor(chatId: Long, seconds: Int) = updateChat(chatId) { it.copy(muted = seconds > 0) }

    private fun createLocal(type: ChatType, title: String, description: String?, members: List<Member>, photoPath: String?, service: String): Long {
        val id = nextChatId++
        chatList.add(chatList.count { it.pinned }, Chat(id, type, title.trim(), members = members.size, description = description?.ifBlank { null }, rights = ChatRights.Owner, canPost = type == ChatType.Channel))
        if (type == ChatType.Group) demoMembers[id] = members
        if (photoPath != null) demoPhotos[id] = photoPath
        list(id).add(Message(nextId++, id, 0, System.currentTimeMillis(), MessageContent.Service(service), outgoing = true))
        return id
    }

    override fun createGroup(title: String, userIds: List<Long>, photoPath: String?, onDone: (chatId: Long?, error: String?) -> Unit) {
        val members = listOf(Member(me.id, "owner")) + userIds.map { Member(it) }
        onDone(createLocal(ChatType.Group, title, null, members, photoPath, "You created the group \"${title.trim()}\""), null)
    }

    override fun createChannel(title: String, description: String, photoPath: String?, onDone: (chatId: Long?, error: String?) -> Unit) {
        onDone(createLocal(ChatType.Channel, title, description, listOf(Member(me.id, "owner")), photoPath, "Channel created"), null)
    }

    override fun editChat(chatId: Long, title: String, description: String, onDone: (String?) -> Unit) {
        updateChat(chatId) { it.copy(title = title.trim().ifBlank { it.title }, description = description.trim().ifBlank { null }) }
        demoAbout[chatId] = description.trim()
        onDone(null)
    }

    override fun updateChatPhoto(chatId: Long, path: String?, onDone: (String?) -> Unit) {
        if (path == null) demoPhotos.remove(chatId) else demoPhotos[chatId] = path
        onDone(null)
    }

    override fun addMembers(chatId: Long, userIds: List<Long>, onDone: (String?) -> Unit) {
        val current = demoMembers[chatId] ?: super.chatInfo(chatId)?.members.orEmpty()
        val added = userIds.filter { id -> current.none { it.userId == id } }
        demoMembers[chatId] = current + added.map { Member(it) }
        updateChat(chatId) { it.copy(members = it.members + added.size) }
        onDone(null)
    }

    override fun removeMember(chatId: Long, userId: Long, onDone: (String?) -> Unit) {
        val current = demoMembers[chatId] ?: super.chatInfo(chatId)?.members.orEmpty()
        demoMembers[chatId] = current.filter { it.userId != userId }
        updateChat(chatId) { it.copy(members = (it.members - 1).coerceAtLeast(0)) }
        onDone(null)
    }
}

/** Sender label for a message in group chats. */
fun TelegramRepository.senderName(m: Message): String = when {
    m.outgoing -> "You"
    m.senderId < 0 -> (chat(m.senderId) ?: chat(m.chatId))?.title ?: ""
    else -> user(m.senderId)?.name ?: ""
}
