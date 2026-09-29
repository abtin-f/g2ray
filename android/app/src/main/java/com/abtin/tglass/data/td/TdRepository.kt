package com.abtin.tglass.data.td

import android.content.Context
import android.os.Build
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.abtin.tglass.BuildConfig
import com.abtin.tglass.data.CallRecord
import com.abtin.tglass.data.ChatInfo
import com.abtin.tglass.data.ChatRights
import com.abtin.tglass.data.GifItem
import com.abtin.tglass.data.GlobalResults
import com.abtin.tglass.data.StickerItem
import com.abtin.tglass.data.StickerPack
import com.abtin.tglass.data.MediaKind
import com.abtin.tglass.data.PrivacyKey
import com.abtin.tglass.data.ProxyItem
import com.abtin.tglass.data.ProxyKind
import com.abtin.tglass.data.PrivacyValue
import com.abtin.tglass.data.Member
import com.abtin.tglass.data.Entity
import com.abtin.tglass.data.EntityType
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.MessageStatus
import com.abtin.tglass.data.TelegramRepository
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.Locale
import com.abtin.tglass.data.Chat as UiChat
import com.abtin.tglass.data.ChatType as UiChatType
import com.abtin.tglass.data.Message as UiMessage
import com.abtin.tglass.data.MessageContent as UiContent
import com.abtin.tglass.data.Reaction as UiReaction
import com.abtin.tglass.data.Session as UiSession
import com.abtin.tglass.data.Story as UiStory
import com.abtin.tglass.data.User as UiUser

/** Where the login flow currently is. */
sealed interface AuthStep {
    data object Starting : AuthStep
    data object NeedCredentials : AuthStep
    data object WaitPhone : AuthStep
    data class WaitCode(val phone: String, val length: Int, val viaApp: Boolean, val canResend: Boolean) : AuthStep
    data class WaitPassword(val hint: String) : AuthStep
    data object WaitRegistration : AuthStep
    data class Unsupported(val what: String) : AuthStep
    data object Ready : AuthStep
    data object LoggingOut : AuthStep
}

/**
 * [TelegramRepository] backed by TDLib (through tdl-coroutines). TDLib pushes updates; this class folds
 * them into Compose snapshot state so every screen recomposes on its own, exactly like the demo data.
 * All state is touched on the main thread only.
 */
class TdRepository(context: Context) : TelegramRepository {
    private val app = context.applicationContext
    private val config = TdConfig(app)
    // A failing TDLib request or mapping must not take the whole app down: log it and keep going.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.e("TGlass", "TDLib task failed", e) },
    )

    private lateinit var client: TdlClient
    private var updatesJob: Job? = null

    // ---- Public login state ----
    var auth by mutableStateOf<AuthStep>(AuthStep.Starting)
        private set
    var authError by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Errors from actions the user took (sending, deleting…), to be shown as toasts. */
    val errors: SharedFlow<String> = _errors

    override val isLive get() = true

    // ---- Raw TDLib state (not observed by Compose) ----
    private class ChatState(
        val id: Long,
        var type: ChatType,
        var title: String,
        var photo: ChatPhotoInfo?,
        var lastMessage: Message?,
        val positions: HashMap<String, ChatPosition> = HashMap(),
        var unread: Int,
        var mentions: Int,
        var markedUnread: Boolean,
        var notifications: ChatNotificationSettings,
        var draft: String?,
        var lastReadOutbox: Long,
        var lastReadInbox: Long = 0,
        var typing: String? = null,
    )

    private val chatStates = HashMap<Long, ChatState>()
    private val rawUsers = HashMap<Long, User>()
    private val basicGroups = HashMap<Long, BasicGroup>()
    private val supergroups = HashMap<Long, Supergroup>()
    private val scopeMute = HashMap<String, Int>()
    private val typingJobs = HashMap<Long, Job>()
    private val downloading = HashSet<Int>()
    private val historyLoading = HashSet<Long>()
    private val historyComplete = HashSet<Long>()
    private val openChats = HashSet<Long>()
    private var myId = 0L
    private var folderInfos by mutableStateOf<List<ChatFolderInfo>>(emptyList())

    // ---- Observed state ----
    private val chatMap = mutableStateMapOf<Long, UiChat>()
    private val userMap = mutableStateMapOf<Long, UiUser>()
    private val lastMessages = mutableStateMapOf<Long, UiMessage>()
    private val messageStore = mutableStateMapOf<Long, SnapshotStateList<UiMessage>>()
    private val avatars = mutableStateMapOf<Long, ImageRef>()
    private val filePaths = mutableStateMapOf<Int, String>()
    private val fileProgressMap = mutableStateMapOf<Int, Float>()
    private val contactIds = mutableStateListOf<Long>()
    private val chatInfos = mutableStateMapOf<Long, ChatInfo>()
    private val sharedMediaStore = mutableStateMapOf<String, List<UiMessage>>()
    private val messageCache = mutableStateMapOf<String, UiMessage>()
    private val requestedMessages = HashSet<String>()
    private val pinnedStore = mutableStateMapOf<Long, UiMessage>()
    private val lastTypingSent = HashMap<Long, Long>()
    private val packs = mutableStateListOf<StickerPack>()
    private val recents = mutableStateListOf<StickerItem>()
    private val gifs = mutableStateListOf<GifItem>()
    private var stickersLoaded = false
    private val callList = mutableStateListOf<CallRecord>()
    private val sessionList = mutableStateListOf<UiSession>()
    private var connection by mutableStateOf<String?>("Connecting…")

    private val sortedChats by derivedStateOf {
        chatMap.values.filter { it.order != 0L }.sortedByDescending { it.order }
    }

    init {
        start()
    }

    // =====================================================================================
    // TelegramRepository
    // =====================================================================================

    override val connectionStatus: String? get() = if (auth == AuthStep.Ready) connection else null
    override val savedChatId: Long get() = myId
    override val me: UiUser get() = userMap[myId] ?: UiUser(myId, "")
    override val users: Map<Long, UiUser> get() = userMap
    override val chats: List<UiChat> get() = sortedChats
    override val calls: List<CallRecord> get() = callList
    override val sessions: List<UiSession> get() = sessionList
    override val folders: List<String> get() = listOf("All Chats") + folderInfos.map { it.name.text.text }
    override val contacts: List<UiUser> get() = contactIds.mapNotNull { userMap[it] }.sortedBy { it.name.lowercase() }

    override fun isInFolder(chat: UiChat, index: Int): Boolean {
        if (index == 0) return true
        val folder = folderInfos.getOrNull(index - 1) ?: return true
        return folder.id in chat.folderIds
    }

    override fun chat(id: Long): UiChat? = chatMap[id]
    override fun messages(chatId: Long): List<UiMessage> = messageStore[chatId] ?: emptyList()
    override fun lastMessage(chatId: Long): UiMessage? = lastMessages[chatId]
    override fun avatar(peerId: Long): ImageRef? = avatars[peerId]
    override fun filePath(image: ImageRef): String? = image.path ?: filePaths[image.fileId]

    override fun requestImage(image: ImageRef) {
        if (image.fileId <= 0 || filePath(image) != null || !downloading.add(image.fileId)) return
        scope.launch {
            when (val r = client.downloadFile(image.fileId, 1, 0, 0, false)) {
                is TdlResult.Success -> onFile(r.result)
                is TdlResult.Failure -> downloading.remove(image.fileId)
            }
        }
    }

    override fun sendText(chatId: Long, text: String, replyTo: Long?) {
        send(chatId, replyTo, InputMessageText(FormattedText(text, emptyArray()), null, true))
    }

    override fun sendContent(chatId: Long, content: UiContent, replyTo: Long?) {
        val input: InputMessageContent = when (content) {
            is UiContent.Text -> InputMessageText(FormattedText(content.text, emptyArray()), null, true)
            is UiContent.Sticker -> InputMessageText(FormattedText(content.emoji, emptyArray()), null, true)
            is UiContent.Contact -> contactInput(content)
            is UiContent.Poll -> pollInput(content)
            is UiContent.Photo -> inputMedia(content) ?: run {
                _errors.tryEmit("This kind of message can't be sent yet")
                return
            }
            is UiContent.File -> {
                val path = content.file?.takeIf { it.fileId == 0 }?.path ?: run {
                    _errors.tryEmit("This kind of message can't be sent yet")
                    return
                }
                InputMessageDocument(InputDocument(InputFileLocal(path), null, false), null)
            }
            is UiContent.Location -> {
                val parts = content.address.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                if (parts.size != 2) {
                    _errors.tryEmit("This kind of message can't be sent yet")
                    return
                }
                InputMessageLocation(Location(parts[0], parts[1], 0.0))
            }
            is UiContent.Voice -> {
                val path = content.media?.path ?: run {
                    _errors.tryEmit("This kind of message can't be sent yet")
                    return
                }
                InputMessageVoiceNote(InputFileLocal(path), content.seconds, packWaveform(content.waveform), null, null)
            }
            is UiContent.VideoNote -> videoNoteInput(content) ?: run {
                _errors.tryEmit("This kind of message can't be sent yet")
                return
            }
            else -> {
                _errors.tryEmit("This kind of message can't be sent yet")
                return
            }
        }
        send(chatId, replyTo, input)
    }

    override fun sendMedia(chatId: Long, items: List<UiContent>, replyTo: Long?) {
        val inputs = items.mapNotNull { (it as? UiContent.Photo)?.let(::inputMedia) }
        if (inputs.size < 2 || inputs.size != items.size) {
            super.sendMedia(chatId, items, replyTo)
            return
        }
        scope.launch {
            inputs.chunked(10).forEachIndexed { i, chunk ->
                val reply = if (i == 0) replyTo?.let { InputMessageReplyToMessage(it, null, 0, "") } else null
                if (chunk.size == 1) client.sendMessage(chatId = chatId, replyTo = reply, inputMessageContent = chunk[0]).orReport()
                else client.sendMessageAlbum(chatId = chatId, replyTo = reply, inputMessageContents = chunk.toTypedArray()).also { r ->
                    if (r is TdlResult.Success) r.result.messages.filterNotNull().forEach { addMessage(it) }
                }.orReport()
            }
        }
    }

    /** A locally prepared photo/video (see MediaPrep) as TDLib input. */
    private fun inputMedia(p: UiContent.Photo): InputMessageContent? {
        val caption = p.caption?.let { FormattedText(it, emptyArray()) }
        val image = p.image?.takeIf { it.fileId == 0 && it.path != null }
        if (p.video) {
            val vf = p.videoFile?.takeIf { it.fileId == 0 } ?: return null
            val file = vf.path ?: return null
            val thumb = image?.let { InputThumbnail(InputFileLocal(it.path!!), it.width, it.height) }
            return InputMessageVideo(InputVideo(InputFileLocal(file), thumb, null, 0, IntArray(0), p.duration, vf.width, vf.height, true), caption, false, null, false)
        }
        image ?: return null
        return InputMessagePhoto(InputPhoto(InputFileLocal(image.path!!), null, null, IntArray(0), image.width, image.height), caption, false, null, false)
    }

    /** Inverse of [waveform]: 0..1 levels → Telegram's packed 5-bit samples. */
    private fun packWaveform(levels: List<Float>): ByteArray {
        val values = levels.map { (it.coerceIn(0f, 1f) * 31).toInt() }
        val bytes = ByteArray((values.size * 5 + 7) / 8)
        values.forEachIndexed { i, v ->
            val bit = i * 5
            val idx = bit / 8
            val shift = bit % 8
            val word = v shl shift
            bytes[idx] = (bytes[idx].toInt() or (word and 0xFF)).toByte()
            if (idx + 1 < bytes.size) bytes[idx + 1] = (bytes[idx + 1].toInt() or ((word shr 8) and 0xFF)).toByte()
        }
        return bytes
    }

    override fun fileProgress(image: ImageRef): Float {
        if (filePath(image) != null) return 1f
        return fileProgressMap[image.fileId] ?: 0f
    }

    private fun send(chatId: Long, replyTo: Long?, input: InputMessageContent) = scope.launch {
        client.sendMessage(
            chatId = chatId,
            replyTo = replyTo?.let { InputMessageReplyToMessage(it, null, 0, "") },
            inputMessageContent = input,
        ).also { r -> if (r is TdlResult.Success) addMessage(r.result) }.orReport()
    }

    override fun editText(chatId: Long, messageId: Long, text: String) {
        scope.launch {
            client.editMessageText(chatId, messageId, null, InputMessageText(FormattedText(text, emptyArray()), null, false)).orReport()
        }
    }

    override fun deleteMessages(chatId: Long, ids: Set<Long>, forEveryone: Boolean) {
        scope.launch { client.deleteMessages(chatId, ids.toLongArray(), forEveryone).orReport() }
    }

    override fun findMessage(chatId: Long, messageId: Long): UiMessage? =
        super.findMessage(chatId, messageId) ?: messageCache["$chatId:$messageId"]

    override fun requestMessage(chatId: Long, messageId: Long) {
        val key = "$chatId:$messageId"
        if (messageCache.containsKey(key) || !requestedMessages.add(key)) return
        scope.launch {
            val r = client.getMessage(chatId, messageId)
            if (r is TdlResult.Success) messageCache[key] = mapMessage(r.result)
        }
    }

    override fun loadAround(chatId: Long, messageId: Long, onLoaded: () -> Unit) {
        if (messages(chatId).any { it.id == messageId }) return onLoaded()
        scope.launch {
            val r = client.getChatHistory(chatId, messageId, -25, 50, false)
            if (r is TdlResult.Success) {
                messageStore.getOrPut(chatId) { mutableStateListOf() }
                r.result.messages.filterNotNull().forEach { addMessage(it) }
            }
            onLoaded()
        }
    }

    override fun pinnedMessage(chatId: Long): UiMessage? = pinnedStore[chatId]

    private fun loadPinned(chatId: Long) {
        scope.launch {
            val r = client.getChatPinnedMessage(chatId)
            if (r is TdlResult.Success) pinnedStore[chatId] = mapMessage(r.result) else pinnedStore.remove(chatId)
        }
    }

    override fun readAnchor(chatId: Long): Long? {
        val st = chatStates[chatId] ?: return null
        return st.lastReadInbox.takeIf { st.unread > 0 }
    }

    override fun sendTyping(chatId: Long) {
        val now = System.currentTimeMillis()
        if (now - (lastTypingSent[chatId] ?: 0L) < 5_000) return
        lastTypingSent[chatId] = now
        scope.launch { client.sendChatAction(chatId = chatId, businessConnectionId = "", action = ChatActionTyping()) }
    }

    override fun searchInChat(chatId: Long, query: String, onResult: (List<UiMessage>) -> Unit) {
        scope.launch {
            val r = client.searchChatMessages(chatId = chatId, query = query, fromMessageId = 0, offset = 0, limit = 100)
            onResult(if (r is TdlResult.Success) r.result.messages.map { mapMessage(it) } else emptyList())
        }
    }

    override fun toggleReaction(chatId: Long, messageId: Long, emoji: String) {
        val chosen = messages(chatId).firstOrNull { it.id == messageId }?.reactions?.any { it.emoji == emoji && it.chosen } == true
        val type: ReactionType = com.abtin.tglass.data.CustomReactions.idOf(emoji)?.let { ReactionTypeCustomEmoji(it) } ?: ReactionTypeEmoji(emoji)
        scope.launch {
            if (chosen) client.removeMessageReaction(chatId, messageId, type).orReport()
            else client.addMessageReaction(chatId, messageId, type, false, true).orReport()
        }
    }

    override fun togglePinMessage(chatId: Long, messageId: Long) {
        val pinned = messages(chatId).firstOrNull { it.id == messageId }?.pinned == true
        scope.launch {
            if (pinned) client.unpinChatMessage(chatId, messageId).orReport()
            else client.pinChatMessage(chatId, messageId, true, false).orReport()
        }
    }

    override fun vote(chatId: Long, messageId: Long, option: Int) {
        scope.launch { client.setPollAnswer(chatId, messageId, intArrayOf(option)).orReport() }
    }

    override fun openChat(chatId: Long) {
        openChats += chatId
        scope.launch {
            client.openChat(chatId)
            loadPinned(chatId)
            if (messageStore[chatId] == null) loadHistory(chatId, initial = true)
            markRead(chatId)
        }
    }

    override fun ensureChat(chatId: Long) {
        if (chatId == 0L || chatMap[chatId] != null) return
        scope.launch {
            // TDLib announces the chat with updateNewChat before answering; a user it never saw needs a private chat.
            val r = client.getChat(chatId)
            if (r !is TdlResult.Success && chatId > 0) client.createPrivateChat(chatId, false).orReport()
        }
    }

    override fun closeChat(chatId: Long) {
        openChats -= chatId
        scope.launch { client.closeChat(chatId) }
    }

    override fun loadOlderMessages(chatId: Long) {
        scope.launch { loadHistory(chatId, initial = false) }
    }

    override fun setDraft(chatId: Long, draft: String?) {
        val text = draft?.takeIf { it.isNotBlank() }
        if (chatStates[chatId]?.draft == text) return
        scope.launch {
            client.setChatDraftMessage(
                chatId = chatId,
                draftMessage = text?.let { DraftMessage(null, 0, DraftMessageContentText(FormattedText(it, emptyArray()), null), 0, null) },
            )
        }
    }

    override fun togglePin(chatId: Long) {
        val c = chat(chatId) ?: return
        scope.launch {
            client.toggleChatIsPinned(if (c.archived) ChatListArchive() else ChatListMain(), chatId, !c.pinned).orReport()
        }
    }

    override fun toggleMute(chatId: Long) {
        val st = chatStates[chatId] ?: return
        val muted = isMuted(st)
        val n = st.notifications
        val settings = ChatNotificationSettings(
            false, if (muted) 0 else Int.MAX_VALUE,
            n.useDefaultSound, n.soundId,
            n.useDefaultShowPreview, n.showPreview,
            n.useDefaultMuteStories, n.muteStories,
            n.useDefaultStorySound, n.storySoundId,
            n.useDefaultShowStoryPoster, n.showStoryPoster,
            n.useDefaultDisablePinnedMessageNotifications, n.disablePinnedMessageNotifications,
            n.useDefaultDisableMentionNotifications, n.disableMentionNotifications,
        )
        scope.launch { client.setChatNotificationSettings(chatId, settings).orReport() }
    }

    override fun toggleRead(chatId: Long) {
        val st = chatStates[chatId] ?: return
        scope.launch {
            if (st.unread > 0 || st.markedUnread || st.mentions > 0) {
                if (st.markedUnread) client.toggleChatIsMarkedAsUnread(chatId, false)
                st.lastMessage?.let { client.viewMessages(chatId, longArrayOf(it.id), null, true) }
                if (st.mentions > 0) client.readAllChatMentions(chatId)
            } else {
                client.toggleChatIsMarkedAsUnread(chatId, true).orReport()
            }
        }
    }

    override fun toggleArchive(chatId: Long) {
        val c = chat(chatId) ?: return
        scope.launch { client.addChatToList(chatId, if (c.archived) ChatListMain() else ChatListArchive()).orReport() }
    }

    override fun deleteChat(chatId: Long) {
        val c = chat(chatId) ?: return
        scope.launch {
            if (c.type == UiChatType.Group || c.type == UiChatType.Channel) client.leaveChat(chatId)
            client.deleteChatHistory(chatId, true, false)
        }
    }

    override fun privateChatWith(userId: Long): Long {
        if (chatMap[userId] == null) scope.launch { client.createPrivateChat(userId, false).orReport() }
        return userId
    }

    override fun terminateSession(session: UiSession) {
        scope.launch {
            client.terminateSession(session.id).orReport()
            loadSessions()
        }
    }

    override fun terminateOtherSessions() {
        scope.launch {
            client.terminateAllOtherSessions().orReport()
            loadSessions()
        }
    }

    override fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long) {
        scope.launch {
            client.forwardMessages(chatId = toChatId, fromChatId = fromChatId, messageIds = messageIds.toLongArray(), sendCopy = false, removeCaption = false).orReport()
        }
    }

    override fun updateProfile(firstName: String, lastName: String, bio: String, onDone: (String?) -> Unit) {
        scope.launch {
            val a = client.setName(firstName, lastName)
            val b = client.setBio(bio)
            val error = (a as? TdlResult.Failure ?: b as? TdlResult.Failure)?.message
            if (error == null) loadChatInfo(myId)
            onDone(error?.let { humanize(it) })
        }
    }

    override fun updateUsername(username: String, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.setUsername(username.removePrefix("@"))
            onDone(if (r is TdlResult.Failure) when {
                "USERNAME_OCCUPIED" in r.message -> "This username is already taken."
                "USERNAME_INVALID" in r.message -> "This username is invalid."
                else -> humanize(r.message)
            } else null)
        }
    }

    override fun updateProfilePhoto(path: String, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.setProfilePhoto(InputChatPhotoStatic(InputFileLocal(path)), false)
            if (r is TdlResult.Success) refreshMe()
            onDone(if (r is TdlResult.Failure) humanize(r.message) else null)
        }
    }

    override fun deleteProfilePhoto(onDone: (String?) -> Unit) {
        scope.launch {
            val id = rawUsers[myId]?.profilePhoto?.id
            if (id == null) { onDone(null); return@launch }
            val r = client.deleteProfilePhoto(id)
            if (r is TdlResult.Success) refreshMe()
            onDone(if (r is TdlResult.Failure) humanize(r.message) else null)
        }
    }

    /** Re-reads our own user so a new/removed photo shows at once, without waiting for updateUser. */
    private suspend fun refreshMe() {
        client.getMe().let { if (it is TdlResult.Success) onUser(it.result) }
    }

    private val privacyValues = mutableStateMapOf<PrivacyKey, PrivacyValue>()

    private fun privacySetting(key: PrivacyKey): UserPrivacySetting = when (key) {
        PrivacyKey.PhoneNumber -> UserPrivacySettingShowPhoneNumber()
        PrivacyKey.LastSeen -> UserPrivacySettingShowStatus()
        PrivacyKey.ProfilePhoto -> UserPrivacySettingShowProfilePhoto()
        PrivacyKey.Forwards -> UserPrivacySettingShowLinkInForwardedMessages()
        PrivacyKey.Calls -> UserPrivacySettingAllowCalls()
        PrivacyKey.Invites -> UserPrivacySettingAllowChatInvites()
    }

    override fun privacy(key: PrivacyKey): PrivacyValue? = privacyValues[key]

    override fun loadPrivacy() {
        scope.launch {
            for (key in PrivacyKey.entries) {
                val r = client.getUserPrivacySettingRules(privacySetting(key))
                if (r is TdlResult.Success) {
                    val rules = r.result.rules
                    privacyValues[key] = when {
                        rules.any { it is UserPrivacySettingRuleAllowAll } -> PrivacyValue.Everybody
                        rules.any { it is UserPrivacySettingRuleAllowContacts } -> PrivacyValue.Contacts
                        else -> PrivacyValue.Nobody
                    }
                }
            }
        }
    }

    override fun setPrivacy(key: PrivacyKey, value: PrivacyValue) {
        val rules: Array<UserPrivacySettingRule> = when (value) {
            PrivacyValue.Everybody -> arrayOf(UserPrivacySettingRuleAllowAll())
            PrivacyValue.Contacts -> arrayOf(UserPrivacySettingRuleAllowContacts(), UserPrivacySettingRuleRestrictAll())
            PrivacyValue.Nobody -> arrayOf(UserPrivacySettingRuleRestrictAll())
        }
        val previous = privacyValues[key]
        privacyValues[key] = value
        scope.launch {
            val r = client.setUserPrivacySettingRules(privacySetting(key), UserPrivacySettingRules(rules))
            if (r is TdlResult.Failure) {
                if (previous != null) privacyValues[key] = previous
                _errors.tryEmit(humanize(r.message))
            }
        }
    }

    override fun joinChat(chatId: Long) {
        scope.launch { client.joinChat(chatId).orReport() }
    }

    override fun logOut() {
        scope.launch { client.logOut() }
    }

    // =====================================================================================
    // Login
    // =====================================================================================

    /** Called after the user entered API credentials on the setup screen. */
    fun credentialsChanged() {
        authError = null
        // TDLib keeps its parameters until it is closed, so restart it unless it is still waiting for them.
        if (auth == AuthStep.NeedCredentials) sendParameters() else scope.launch { client.close() }
    }

    fun submitPhone(phone: String) = authCall { client.setAuthenticationPhoneNumber(phone.filter { it.isDigit() || it == '+' }) }
    fun submitCode(code: String) = authCall { client.checkAuthenticationCode(code) }
    fun submitPassword(password: String) = authCall { client.checkAuthenticationPassword(password) }
    fun register(first: String, last: String) = authCall { client.registerUser(first, last, false) }
    fun resendCode() = authCall { client.resendAuthenticationCode() }

    private fun authCall(block: suspend () -> TdlResult<*>) {
        busy = true
        authError = null
        scope.launch {
            val r = block()
            busy = false
            if (r is TdlResult.Failure) authError = humanize(r.message)
        }
    }

    private fun humanize(error: String): String = when {
        "PHONE_NUMBER_INVALID" in error -> "Invalid phone number. Please check the number and try again."
        "PHONE_NUMBER_BANNED" in error -> "This phone number is banned."
        "PHONE_CODE_INVALID" in error -> "Invalid code. Please try again."
        "PHONE_CODE_EXPIRED" in error -> "The code has expired. Please request a new one."
        "PASSWORD_HASH_INVALID" in error -> "Invalid password. Please try again."
        "API_ID_INVALID" in error || "API_ID_PUBLISHED_FLOOD" in error -> "Your API ID / API hash is not valid. Check them on my.telegram.org."
        "FLOOD" in error || "Too Many Requests" in error -> "Too many attempts. Please try again later."
        else -> error
    }

    private fun start() {
        client = TdlClient.create()
        updatesJob?.cancel()
        // Subscribe before the first request so no update is missed.
        updatesJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            // One bad update must never stop the stream (that would silently freeze chats and notifications).
            client.allUpdates.collect { u ->
                try {
                    handle(u)
                } catch (e: Exception) {
                    android.util.Log.e("TGlass", "Failed to handle ${u::class.simpleName}", e)
                }
            }
        }
        scope.launch {
            client.setLogVerbosityLevel(1)
            client.getAuthorizationState()
        }
    }

    private fun sendParameters() {
        if (!config.hasCredentials) {
            auth = AuthStep.NeedCredentials
            return
        }
        scope.launch {
            // Each signed-in account has its own database (Accounts & Settings v2): slot 0 = "td", N = "td_N".
            TdAccounts.attach(app)
            val slot = TdAccounts.active
            val r = client.setTdlibParameters(
                useTestDc = false,
                databaseDirectory = TdAccounts.databaseDir(app, slot).absolutePath,
                filesDirectory = TdAccounts.filesDir(app, slot).absolutePath,
                databaseEncryptionKey = ByteArray(0),
                useFileDatabase = true,
                useChatInfoDatabase = true,
                useMessageDatabase = true,
                useSecretChats = false,
                apiId = config.apiId,
                apiHash = config.apiHash,
                systemLanguageCode = Locale.getDefault().language.ifBlank { "en" },
                deviceModel = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
                systemVersion = "Android ${Build.VERSION.RELEASE}",
                applicationVersion = BuildConfig.VERSION_NAME,
            )
            if (r is TdlResult.Failure) {
                authError = humanize(r.message)
                auth = AuthStep.NeedCredentials
            }
        }
    }

    private fun onAuth(state: AuthorizationState) {
        authError = null
        auth = when (state) {
            is AuthorizationStateWaitTdlibParameters -> { sendParameters(); return }
            is AuthorizationStateWaitPhoneNumber -> AuthStep.WaitPhone
            is AuthorizationStateWaitCode -> {
                val info = state.codeInfo
                val length = when (val t = info.type) {
                    is AuthenticationCodeTypeTelegramMessage -> t.length
                    is AuthenticationCodeTypeSms -> t.length
                    is AuthenticationCodeTypeCall -> t.length
                    else -> 5
                }
                AuthStep.WaitCode("+" + info.phoneNumber.trimStart('+'), length, info.type is AuthenticationCodeTypeTelegramMessage, info.nextType != null)
            }
            is AuthorizationStateWaitPassword -> AuthStep.WaitPassword(state.passwordHint)
            is AuthorizationStateWaitRegistration -> AuthStep.WaitRegistration
            is AuthorizationStateWaitEmailAddress, is AuthorizationStateWaitEmailCode ->
                AuthStep.Unsupported("Telegram asks for an email login for this account. Please sign in once in the official app first.")
            is AuthorizationStateWaitOtherDeviceConfirmation -> AuthStep.Unsupported("Please confirm this login in another Telegram app.")
            is AuthorizationStateReady -> { onReady(); AuthStep.Ready }
            is AuthorizationStateLoggingOut -> AuthStep.LoggingOut
            is AuthorizationStateClosing -> return
            is AuthorizationStateClosed -> { onAccountClosed(auth == AuthStep.LoggingOut); reset(); start(); AuthStep.Starting }
            else -> AuthStep.Unsupported("This login method is not supported yet.")
        }
    }

    private fun onReady() {
        scope.launch {
            client.getMe().let { if (it is TdlResult.Success) { myId = it.result.id; onUser(it.result) } }
            client.loadChats(ChatListMain(), 100)
            client.loadChats(ChatListArchive(), 50)
            loadFolderLists(folderInfos.map { it.id })
            loadContacts()
            loadSessions()
            loadCalls()
        }
        loadActiveStoryLists()
    }

    private fun reset() {
        com.abtin.tglass.notify.Notifier.cancelAll(app)
        chatStates.clear(); rawUsers.clear(); basicGroups.clear(); supergroups.clear()
        typingJobs.values.forEach { it.cancel() }; typingJobs.clear()
        downloading.clear(); fileProgressMap.clear(); historyLoading.clear(); historyComplete.clear(); openChats.clear()
        chatMap.clear(); userMap.clear(); lastMessages.clear(); messageStore.clear(); avatars.clear(); filePaths.clear()
        contactIds.clear(); callList.clear(); sessionList.clear(); folderInfos = emptyList()
        chatInfos.clear(); sharedMediaStore.clear(); messageCache.clear(); requestedMessages.clear(); pinnedStore.clear()
        folderPositions.clear()
        packs.clear(); recents.clear(); gifs.clear(); stickersLoaded = false
        myId = 0
        resetStories()
        resetChatFeatures()
    }

    private suspend fun loadContacts() {
        val r = client.getContacts()
        if (r is TdlResult.Success) {
            contactIds.clear()
            contactIds.addAll(r.result.userIds.toList())
            r.result.userIds.filter { it !in rawUsers }.forEach { id -> client.getUser(id).let { u -> if (u is TdlResult.Success) onUser(u.result) } }
        }
    }

    private suspend fun loadSessions() {
        val r = client.getActiveSessions()
        if (r is TdlResult.Success) {
            sessionList.clear()
            sessionList.addAll(r.result.sessions.sortedByDescending { it.isCurrent }.map { s ->
                UiSession(
                    device = s.deviceModel.ifBlank { s.platform },
                    app = "${s.applicationName} ${s.applicationVersion}".trim(),
                    location = s.location.ifBlank { s.ipAddress },
                    lastActive = if (s.isCurrent) "online" else Formats.shortDate(s.lastActiveDate * 1000L),
                    current = s.isCurrent,
                    id = s.id,
                )
            })
        }
    }

    private suspend fun loadCalls() {
        val r = client.searchCallMessages("", 50, false)
        if (r is TdlResult.Success) {
            callList.clear()
            callList.addAll(r.result.messages.mapNotNull { m ->
                val c = m.content as? MessageCall ?: return@mapNotNull null
                CallRecord(
                    id = m.id,
                    userId = m.chatId,
                    date = m.date * 1000L,
                    outgoing = m.isOutgoing,
                    missed = !m.isOutgoing && (c.discardReason is CallDiscardReasonMissed || c.discardReason is CallDiscardReasonDeclined),
                    video = c.isVideo,
                    durationSec = c.duration,
                )
            })
        }
    }

    // =====================================================================================
    // History
    // =====================================================================================

    private suspend fun loadHistory(chatId: Long, initial: Boolean) {
        if (chatId in historyComplete || !historyLoading.add(chatId)) return
        try {
            val list = messageStore.getOrPut(chatId) { mutableStateListOf() }
            // TDLib may return only a few messages per call at first; ask again until we have a screenful.
            var rounds = 0
            val target = list.size + if (initial) 40 else 50
            while (list.size < target && rounds < 5) {
                rounds++
                val from = list.firstOrNull()?.id ?: 0L
                val r = client.getChatHistory(chatId, from, 0, 50, false)
                if (r !is TdlResult.Success) break
                val batch = r.result.messages.filterNotNull()
                if (batch.isEmpty()) {
                    historyComplete += chatId
                    break
                }
                batch.forEach { addMessage(it) }
            }
        } finally {
            historyLoading -= chatId
        }
    }

    private fun markRead(chatId: Long) {
        val ids = messages(chatId).filter { !it.outgoing }.takeLast(100).map { it.id }
        val last = chatStates[chatId]?.lastMessage?.id
        val all = (ids + listOfNotNull(last)).distinct()
        if (all.isEmpty()) return
        scope.launch {
            client.viewMessages(chatId, all.toLongArray(), null, true)
            if ((chatStates[chatId]?.mentions ?: 0) > 0) client.readAllChatMentions(chatId)
            if (chatStates[chatId]?.markedUnread == true) client.toggleChatIsMarkedAsUnread(chatId, false)
        }
    }

    private fun addMessage(m: Message) {
        val list = messageStore[m.chatId] ?: return
        val ui = mapMessage(m)
        val existing = list.indexOfFirst { it.id == m.id }
        if (existing >= 0) {
            list[existing] = ui
            return
        }
        var i = list.size
        while (i > 0 && list[i - 1].id > m.id) i--
        list.add(i, ui)
    }

    private fun updateMessage(chatId: Long, id: Long, f: (UiMessage) -> UiMessage) {
        messageStore[chatId]?.let { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) list[i] = f(list[i])
        }
        lastMessages[chatId]?.takeIf { it.id == id }?.let { lastMessages[chatId] = f(it) }
    }

    // =====================================================================================
    // Updates
    // =====================================================================================

    private fun handle(u: Update) {
        onChatFeatureUpdate(u)
        when (u) {
            is UpdateAuthorizationState -> onAuth(u.authorizationState)
            is UpdateOption -> if (u.name == "my_id") (u.value as? OptionValueInteger)?.let { myId = it.value }
            is UpdateConnectionState -> connection = when (u.state) {
                is ConnectionStateWaitingForNetwork -> "Waiting for network…"
                is ConnectionStateConnectingToProxy -> "Connecting to proxy…"
                is ConnectionStateConnecting -> "Connecting…"
                is ConnectionStateUpdating -> "Updating…"
                else -> null
            }
            is UpdateUser -> onUser(u.user)
            is UpdateUserStatus -> rawUsers[u.userId]?.let { old -> onUser(copyStatus(old, u.status)) }
            is UpdateBasicGroup -> {
                basicGroups[u.basicGroup.id] = u.basicGroup
                chatStates.values.filter { (it.type as? ChatTypeBasicGroup)?.basicGroupId == u.basicGroup.id }.forEach { publish(it) }
            }
            is UpdateSupergroup -> {
                supergroups[u.supergroup.id] = u.supergroup
                chatStates.values.filter { (it.type as? ChatTypeSupergroup)?.supergroupId == u.supergroup.id }.forEach { publish(it) }
            }
            is UpdateNewChat -> {
                val c = u.chat
                val st = ChatState(
                    id = c.id, type = c.type, title = c.title, photo = c.photo, lastMessage = c.lastMessage,
                    unread = c.unreadCount, mentions = c.unreadMentionCount, markedUnread = c.isMarkedAsUnread,
                    notifications = c.notificationSettings, draft = draftText(c.draftMessage), lastReadOutbox = c.lastReadOutboxMessageId,
                    lastReadInbox = c.lastReadInboxMessageId,
                )
                c.positions.forEach { st.positions[key(it.list)] = it }
                chatStates[c.id] = st
                setChatPhoto(c.id, c.photo)
                publish(st)
                publishLast(st)
            }
            is UpdateChatTitle -> chat(u.chatId) { title = u.title }
            is UpdateChatPhoto -> { setChatPhoto(u.chatId, u.photo); chat(u.chatId) { photo = u.photo } }
            is UpdateChatLastMessage -> chat(u.chatId) {
                lastMessage = u.lastMessage
                u.positions.forEach { positions[key(it.list)] = it }
                publishLast(this)
            }
            is UpdateChatPosition -> chat(u.chatId) { positions[key(u.position.list)] = u.position }
            is UpdateChatReadInbox -> {
                chat(u.chatId) { unread = u.unreadCount; lastReadInbox = u.lastReadInboxMessageId }
                if (u.unreadCount == 0) com.abtin.tglass.notify.Notifier.cancel(app, u.chatId)
            }
            is UpdateChatReadOutbox -> chat(u.chatId) {
                lastReadOutbox = u.lastReadOutboxMessageId
                messageStore[u.chatId]?.let { list ->
                    for (i in list.indices) {
                        val m = list[i]
                        if (m.outgoing && m.status == MessageStatus.Sent && m.id <= u.lastReadOutboxMessageId) list[i] = m.copy(status = MessageStatus.Read)
                    }
                }
                publishLast(this)
            }
            is UpdateChatUnreadMentionCount -> chat(u.chatId) { mentions = u.unreadMentionCount }
            is UpdateChatNotificationSettings -> chat(u.chatId) { notifications = u.notificationSettings }
            is UpdateChatIsMarkedAsUnread -> chat(u.chatId) { markedUnread = u.isMarkedAsUnread }
            is UpdateChatDraftMessage -> chat(u.chatId) {
                draft = draftText(u.draftMessage)
                u.positions.forEach { positions[key(it.list)] = it }
            }
            is UpdateScopeNotificationSettings -> {
                val k = when (u.scope) {
                    is NotificationSettingsScopePrivateChats -> "p"
                    is NotificationSettingsScopeGroupChats -> "g"
                    else -> "c"
                }
                scopeMute[k] = u.notificationSettings.muteFor
                chatStates.values.forEach { publish(it) }
            }
            is UpdateChatAction -> onChatAction(u)
            is UpdateChatFolders -> {
                folderInfos = u.chatFolders.toList()
                loadFolderLists(u.chatFolders.map { it.id })
            }
            is UpdateNewMessage -> {
                addMessage(u.message)
                if (u.message.chatId in openChats && !u.message.isOutgoing) {
                    scope.launch { client.viewMessages(u.message.chatId, longArrayOf(u.message.id), null, true) }
                }
                try {
                    maybeNotify(u.message)
                } catch (e: Exception) {
                    android.util.Log.e("TGlass", "Notification failed", e)
                }
            }
            is UpdateMessageSendSucceeded -> replaceMessage(u.message, u.oldMessageId)
            is UpdateMessageSendFailed -> {
                replaceMessage(u.message, u.oldMessageId)
                _errors.tryEmit(humanize(u.error.message))
            }
            is UpdateMessageContent -> updateMessage(u.chatId, u.messageId) { it.copy(content = mapContent(u.newContent, u.chatId)) }
            is UpdateMessageEdited -> updateMessage(u.chatId, u.messageId) { it.copy(edited = true) }
            is UpdateMessageContentOpened -> onContentOpened(u.chatId, u.messageId)
            is UpdateMessageIsPinned -> {
                updateMessage(u.chatId, u.messageId) { it.copy(pinned = u.isPinned) }
                if (u.chatId in openChats) loadPinned(u.chatId)
            }
            is UpdateMessageInteractionInfo -> updateMessage(u.chatId, u.messageId) {
                it.copy(
                    reactions = mapReactions(u.interactionInfo), views = if (it.views != null) u.interactionInfo?.viewCount else null,
                    comments = if (it.views != null) u.interactionInfo?.replyInfo?.replyCount else it.comments,
                )
            }
            is UpdateDeleteMessages -> if (!u.fromCache) {
                val ids = u.messageIds.toHashSet()
                messageStore[u.chatId]?.removeAll { it.id in ids }
            }
            is UpdateFile -> onFile(u.file)
            is UpdateChatActiveStories -> onActiveStories(u.activeStories)
            is UpdateStory -> onStory(u.story)
            is UpdateStoryDeleted -> onStoryDeleted(u.storyPosterChatId, u.storyId)
            else -> Unit
        }
    }

    /** Marks everything in a chat as read (used by the notification action). */
    fun markChatRead(chatId: Long) {
        val st = chatStates[chatId] ?: return
        scope.launch {
            st.lastMessage?.let { client.viewMessages(chatId, longArrayOf(it.id), null, true) }
            if (st.mentions > 0) client.readAllChatMentions(chatId)
            if (st.markedUnread) client.toggleChatIsMarkedAsUnread(chatId, false)
        }
    }

    /** New incoming message → system notification (app closed) or in-app banner (app open, other chat). */
    private fun maybeNotify(m: Message) {
        if (m.isOutgoing || auth != AuthStep.Ready) return
        val st = chatStates[m.chatId] ?: return
        if (isMuted(st)) return
        if (System.currentTimeMillis() / 1000 - m.date > 30 * 60) return
        val foreground = com.abtin.tglass.notify.AppVisibility.foreground
        if (foreground && m.chatId in openChats) return
        val chat = chatMap[m.chatId] ?: return
        val senderId = when (val s = m.senderId) {
            is MessageSenderUser -> s.userId
            is MessageSenderChat -> s.chatId
            else -> m.chatId
        }
        val senderName = userMap[senderId]?.name ?: chatMap[senderId]?.title ?: chat.title
        val text = mapMessage(m).preview
        if (foreground) {
            val prefs = app.getSharedPreferences("tglass", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("inAppPreview", true)) return
            val title = if (chat.type == UiChatType.Group) "$senderName @ ${chat.title}" else chat.title
            com.abtin.tglass.notify.InAppBanners.post(com.abtin.tglass.notify.Banner(m.chatId, m.chatId, title, text))
            return
        }
        fun path(peer: Long) = avatars[peer]?.let { filePath(it) }
        com.abtin.tglass.notify.Notifier.show(
            app,
            com.abtin.tglass.notify.NotifyMessage(
                chatId = m.chatId,
                messageId = m.id,
                chatTitle = chat.title,
                group = chat.type == UiChatType.Group,
                senderId = senderId,
                senderName = senderName,
                text = text,
                date = m.date * 1000L,
                senderAvatarPath = path(senderId),
                chatAvatarPath = path(m.chatId),
            ),
        )
    }

    private inline fun chat(id: Long, f: ChatState.() -> Unit) {
        val st = chatStates[id] ?: return
        st.f()
        publish(st)
    }

    private fun replaceMessage(m: Message, oldId: Long) {
        messageStore[m.chatId]?.removeAll { it.id == oldId }
        addMessage(m)
        lastMessages[m.chatId]?.takeIf { it.id == oldId }?.let { lastMessages[m.chatId] = mapMessage(m) }
    }

    private fun onChatAction(u: UpdateChatAction) {
        val sender = (u.senderId as? MessageSenderUser)?.userId ?: return
        if (sender == myId) return
        val st = chatStates[u.chatId] ?: return
        typingJobs.remove(u.chatId)?.cancel()
        val verb = when (u.action) {
            is ChatActionCancel -> null
            is ChatActionRecordingVoiceNote -> "recording voice"
            is ChatActionRecordingVideoNote -> "recording video"
            is ChatActionUploadingPhoto -> "sending photo"
            is ChatActionUploadingVideo -> "sending video"
            is ChatActionUploadingDocument -> "sending file"
            is ChatActionChoosingSticker -> "choosing sticker"
            else -> "typing"
        }
        st.typing = verb?.let { v ->
            if (st.type is ChatTypePrivate) v else "${rawUsers[sender]?.firstName ?: "Someone"} is $v"
        }
        publish(st)
        if (verb != null) typingJobs[u.chatId] = scope.launch {
            delay(6_000)
            st.typing = null
            publish(st)
        }
    }

    private fun onUser(u: User) {
        rawUsers[u.id] = u
        userMap[u.id] = withStoryFlags(mapUser(u))
        val photo = u.profilePhoto
        if (photo != null) avatars[u.id] = imageOf(photo.small, photo.minithumbnail) else if (chatStates[u.id]?.photo == null) avatars.remove(u.id)
        chatStates[u.id]?.let { publish(it) }
    }

    private fun onFile(f: File) {
        if (f.local.isDownloadingCompleted && f.local.path.isNotEmpty()) {
            filePaths[f.id] = f.local.path
            fileProgressMap.remove(f.id)
            downloading.remove(f.id)
        } else if (f.id in downloading) {
            val total = if (f.size > 0) f.size else f.expectedSize
            if (total > 0) fileProgressMap[f.id] = (f.local.downloadedSize.toFloat() / total).coerceIn(0f, 1f)
        }
    }

    private fun setChatPhoto(chatId: Long, photo: ChatPhotoInfo?) {
        if (photo != null) avatars[chatId] = imageOf(photo.small, photo.minithumbnail)
        else if (rawUsers[chatId]?.profilePhoto == null) avatars.remove(chatId)
    }

    // =====================================================================================
    // Mapping TDLib objects → UI models
    // =====================================================================================

    private fun key(list: ChatList): String = when (list) {
        is ChatListMain -> "main"
        is ChatListArchive -> "archive"
        is ChatListFolder -> "f${list.chatFolderId}"
        else -> "other"
    }

    private fun isMuted(st: ChatState): Boolean {
        val n = st.notifications
        if (!n.useDefaultMuteFor) return n.muteFor > 0
        val scopeKey = when (val t = st.type) {
            is ChatTypePrivate, is ChatTypeSecret -> "p"
            is ChatTypeSupergroup -> if (t.isChannel) "c" else "g"
            else -> "g"
        }
        return (scopeMute[scopeKey] ?: 0) > 0
    }

    private fun publish(st: ChatState) {
        var peer: Long? = null
        var members = 0
        var verified = false
        var username: String? = null
        var joined = true
        var canPost = false
        var rights: ChatRights? = null
        val type = when (val t = st.type) {
            is ChatTypePrivate -> {
                peer = t.userId
                val u = rawUsers[t.userId]
                verified = u?.verificationStatus?.isVerified == true
                username = u?.usernames?.activeUsernames?.firstOrNull()
                when {
                    t.userId == myId -> UiChatType.Saved
                    u?.type is UserTypeBot -> UiChatType.Bot
                    else -> UiChatType.Private
                }
            }
            is ChatTypeSecret -> { peer = t.userId; UiChatType.Private }
            is ChatTypeBasicGroup -> {
                members = basicGroups[t.basicGroupId]?.memberCount ?: 0
                rights = rightsOf(basicGroups[t.basicGroupId]?.status)
                UiChatType.Group
            }
            is ChatTypeSupergroup -> {
                val sg = supergroups[t.supergroupId]
                members = sg?.memberCount ?: 0
                verified = sg?.verificationStatus?.isVerified == true
                username = sg?.usernames?.activeUsernames?.firstOrNull()
                joined = sg?.status.let { it !is ChatMemberStatusLeft && it !is ChatMemberStatusBanned }
                canPost = sg?.status.let { it is ChatMemberStatusCreator || it is ChatMemberStatusAdministrator }
                rights = rightsOf(sg?.status)
                if (t.isChannel) UiChatType.Channel else UiChatType.Group
            }
            else -> UiChatType.Private
        }
        val main = st.positions["main"]?.takeIf { it.order != 0L }
        val archive = st.positions["archive"]?.takeIf { it.order != 0L }
        val archived = main == null && archive != null
        val pos = if (archived) archive else main
        chatMap[st.id] = UiChat(
            id = st.id,
            type = type,
            title = if (type == UiChatType.Saved) "Saved Messages" else st.title.ifBlank { "Deleted Account" },
            peerUserId = peer,
            members = members,
            pinned = pos?.isPinned == true,
            muted = isMuted(st),
            unread = st.unread,
            mentions = st.mentions,
            markedUnread = st.markedUnread,
            archived = archived,
            draft = st.draft,
            typing = st.typing,
            verified = verified,
            username = username,
            order = pos?.order ?: 0L,
            folderIds = st.positions.values.mapNotNull { p -> (p.list as? ChatListFolder)?.chatFolderId?.takeIf { p.order != 0L } }.toSet(),
            joined = joined,
            canPost = canPost,
            rights = rights,
        )
        publishFolderPositions(st)
    }

    private fun publishLast(st: ChatState) {
        val m = st.lastMessage
        if (m == null) lastMessages.remove(st.id) else lastMessages[st.id] = mapMessage(m)
    }

    private fun draftText(d: DraftMessage?): String? = (d?.content as? DraftMessageContentText)?.text?.text?.takeIf { it.isNotBlank() }

    private fun copyStatus(u: User, status: UserStatus) = User(
        u.id, u.firstName, u.lastName, u.usernames, u.phoneNumber, status, u.profilePhoto, u.accentColorId,
        u.backgroundCustomEmojiId, u.upgradedGiftColors, u.profileAccentColorId, u.profileBackgroundCustomEmojiId,
        u.emojiStatus, u.isContact, u.isMutualContact, u.isCloseFriend, u.verificationStatus, u.isPremium, u.isSupport,
        u.restrictionInfo, u.activeStoryState, u.restrictsNewChats, u.paidMessageStarCount, u.haveAccess, u.type,
        u.languageCode, u.addedToAttachmentMenu,
    )

    private fun mapUser(u: User): UiUser {
        val deleted = u.type is UserTypeDeleted
        return UiUser(
            id = u.id,
            firstName = if (deleted) "Deleted Account" else u.firstName,
            lastName = if (deleted) "" else u.lastName,
            username = u.usernames?.activeUsernames?.firstOrNull(),
            phone = u.phoneNumber.takeIf { it.isNotBlank() }?.let { "+$it" } ?: "",
            online = u.status is UserStatusOnline,
            lastSeen = when {
                u.id == 777000L -> "service notifications"
                u.type is UserTypeBot -> "bot"
                else -> when (val s = u.status) {
                    is UserStatusOnline -> "online"
                    is UserStatusOffline -> Formats.lastSeen(s.wasOnline * 1000L)
                    is UserStatusRecently -> "last seen recently"
                    is UserStatusLastWeek -> "last seen within a week"
                    is UserStatusLastMonth -> "last seen within a month"
                    else -> "last seen a long time ago"
                }
            },
            verified = u.verificationStatus?.isVerified == true,
            premium = u.isPremium,
        )
    }

    private fun localPath(f: File): String? {
        if (f.local.isDownloadingCompleted && f.local.path.isNotEmpty()) {
            filePaths[f.id] = f.local.path
            return f.local.path
        }
        return null
    }

    private fun imageOf(f: File, mini: Minithumbnail?, width: Int = 0, height: Int = 0) =
        ImageRef(f.id, localPath(f), mini?.data, width, height, if (f.size > 0) f.size else f.expectedSize)

    private fun mapMessage(m: Message): UiMessage {
        val st = chatStates[m.chatId]
        val sender = when (val s = m.senderId) {
            is MessageSenderUser -> s.userId
            is MessageSenderChat -> s.chatId
            else -> 0L
        }
        val status = when (m.sendingState) {
            is MessageSendingStatePending -> MessageStatus.Sending
            is MessageSendingStateFailed -> MessageStatus.Failed
            else -> if (!m.isOutgoing || (st != null && m.id <= st.lastReadOutbox) || st?.type.let { it is ChatTypePrivate && it.userId == myId }) MessageStatus.Read else MessageStatus.Sent
        }
        val reply = (m.replyTo as? MessageReplyToMessage)?.takeIf { it.chatId == 0L || it.chatId == m.chatId }?.messageId
        rememberInlineKeyboard(m.chatId, m.id, m.replyMarkup)
        return UiMessage(
            id = m.id,
            chatId = m.chatId,
            senderId = sender,
            date = m.date * 1000L,
            content = mapContent(m.content, m.chatId, sender),
            outgoing = m.isOutgoing,
            status = status,
            replyToId = reply,
            edited = m.editDate > 0,
            reactions = mapReactions(m.interactionInfo),
            views = if (m.isChannelPost) m.interactionInfo?.viewCount else null,
            forwardedFrom = m.forwardInfo?.origin?.let { originName(it) },
            pinned = m.isPinned,
            albumId = m.mediaAlbumId,
            forwardPeerId = when (val o = m.forwardInfo?.origin) {
                is MessageOriginUser -> o.senderUserId
                is MessageOriginChat -> o.senderChatId
                is MessageOriginChannel -> o.chatId
                else -> 0L
            },
            forwardMessageId = (m.forwardInfo?.origin as? MessageOriginChannel)?.messageId ?: 0L,
            comments = if (m.isChannelPost) m.interactionInfo?.replyInfo?.replyCount else null,
        )
    }

    private fun mapReactions(info: MessageInteractionInfo?): List<UiReaction> =
        info?.reactions?.reactions?.mapNotNull { r ->
            when (val t = r.type) {
                is ReactionTypeEmoji -> UiReaction(t.emoji, r.totalCount, r.isChosen)
                is ReactionTypeCustomEmoji -> UiReaction(com.abtin.tglass.data.CustomReactions.key(t.customEmojiId), r.totalCount, r.isChosen)
                else -> null
            }
        }.orEmpty()

    private fun originName(o: MessageOrigin): String = when (o) {
        is MessageOriginUser -> userMap[o.senderUserId]?.name ?: "Unknown"
        is MessageOriginChat -> chatStates[o.senderChatId]?.title ?: "Unknown"
        is MessageOriginChannel -> chatStates[o.chatId]?.title ?: "Channel"
        is MessageOriginHiddenUser -> o.senderName
        else -> "Unknown"
    }

    private fun name(userId: Long) = if (userId == myId) "You" else userMap[userId]?.name ?: "Someone"

    private fun mapContent(c: MessageContent, chatId: Long, sender: Long = 0): UiContent = when (c) {
        is MessageText -> {
            val lp = c.linkPreview
            if (lp != null && (lp.title.isNotBlank() || lp.description.text.isNotBlank()))
                UiContent.Link(c.text.text, lp.siteName.ifBlank { lp.displayUrl }, lp.title, lp.description.text, entities(c.text))
            else UiContent.Text(c.text.text, entities(c.text))
        }
        is MessagePhoto -> {
            val sizes = c.photo.sizes
            val best = sizes.lastOrNull { it.width <= 1280 && it.height <= 1280 } ?: sizes.lastOrNull()
            UiContent.Photo(
                seed = best?.photo?.id ?: 0,
                aspect = if (best != null && best.height > 0) best.width.toFloat() / best.height else 1f,
                caption = c.caption.text.ifBlank { null },
                captionEntities = entities(c.caption),
                image = best?.let { imageOf(it.photo, c.photo.minithumbnail, it.width, it.height) },
            )
        }
        is MessageVideo -> {
            val v = c.video
            UiContent.Photo(
                seed = v.video.id,
                aspect = if (v.height > 0) v.width.toFloat() / v.height else 1.6f,
                caption = c.caption.text.ifBlank { null },
                captionEntities = entities(c.caption),
                emoji = "🎬",
                image = v.thumbnail?.let { imageOf(it.file, v.minithumbnail, it.width, it.height) } ?: v.minithumbnail?.let { ImageRef(-v.video.id, null, it.data) },
                video = true,
                videoFile = imageOf(v.video, null, v.width, v.height),
                duration = v.duration,
            )
        }
        is MessageAnimation -> {
            val a = c.animation
            UiContent.Photo(
                seed = a.animation.id,
                aspect = if (a.height > 0) a.width.toFloat() / a.height else 1.4f,
                caption = c.caption.text.ifBlank { null },
                captionEntities = entities(c.caption),
                emoji = "🎞",
                image = a.thumbnail?.let { imageOf(it.file, a.minithumbnail, it.width, it.height) } ?: a.minithumbnail?.let { ImageRef(-a.animation.id, null, it.data) },
                video = true,
                videoFile = imageOf(a.animation, null, a.width, a.height),
                duration = a.duration,
                loop = true,
            )
        }
        is MessageVideoNote -> videoNoteContent(c)
        is MessageVoiceNote -> UiContent.Voice(c.voiceNote.duration, waveform(c.voiceNote.waveform), imageOf(c.voiceNote.voice, null), listened = c.isListened)
        is MessageAudio -> UiContent.File(
            name = c.audio.title.ifBlank { c.audio.fileName.ifBlank { "Audio" } },
            size = Formats.size(c.audio.audio.size),
            file = imageOf(c.audio.audio, null),
            mime = c.audio.mimeType,
            music = true,
            duration = c.audio.duration,
            performer = c.audio.performer.ifBlank { null },
            caption = c.caption.text.ifBlank { null },
            captionEntities = entities(c.caption),
        )
        is MessageDocument -> UiContent.File(
            name = c.document.fileName.ifBlank { "File" },
            size = Formats.size(c.document.document.size),
            file = imageOf(c.document.document, null),
            mime = c.document.mimeType,
            caption = c.caption.text.ifBlank { null },
            captionEntities = entities(c.caption),
        )
        is MessageSticker -> {
            val s = c.sticker
            val image = when {
                s.format is StickerFormatWebp -> imageOf(s.sticker, null)
                s.thumbnail?.format.let { it is ThumbnailFormatWebp || it is ThumbnailFormatJpeg } -> imageOf(s.thumbnail!!.file, null)
                else -> null
            }
            UiContent.Sticker(s.emoji.ifBlank { "🙂" }, image, s.sticker.takeIf { s.format is StickerFormatTgs }?.let { imageOf(it, null) }, setId = s.setId)
        }
        is MessageAnimatedEmoji -> {
            val s = c.animatedEmoji.sticker
            UiContent.Sticker(c.emoji, null, s?.takeIf { it.format is StickerFormatTgs }?.let { imageOf(it.sticker, null) })
        }
        is MessageDice -> UiContent.Sticker(c.emoji)
        is MessageLocation -> UiContent.Location("Location", "%.5f, %.5f".format(Locale.US, c.location.latitude, c.location.longitude))
        is MessageVenue -> UiContent.Location(c.venue.title, c.venue.address)
        is MessageContact -> UiContent.Contact("${c.contact.firstName} ${c.contact.lastName}".trim(), c.contact.phoneNumber, c.contact.userId)
        is MessagePoll -> mapPollContent(c)
        is MessageCall -> UiContent.Text(
            (if (c.isVideo) "📹 " else "📞 ") + (if (c.duration > 0) "Call (${Formats.duration(c.duration)})" else "Missed call"),
        )
        is MessageChatAddMembers -> UiContent.Service(
            if (c.memberUserIds.size == 1 && c.memberUserIds[0] == sender) "${name(sender)} joined the group"
            else "${name(sender)} added ${c.memberUserIds.joinToString(", ") { name(it) }}",
        )
        is MessageChatJoinByLink -> UiContent.Service("${name(sender)} joined the group via invite link")
        is MessageChatDeleteMember -> UiContent.Service(
            if (c.userId == sender) "${name(sender)} left the group" else "${name(sender)} removed ${name(c.userId)}",
        )
        is MessageChatChangeTitle -> UiContent.Service("${name(sender)} changed the group name to \"${c.title}\"")
        is MessageChatChangePhoto -> UiContent.Service("${name(sender)} updated the group photo")
        is MessageBasicGroupChatCreate -> UiContent.Service("${name(sender)} created the group \"${c.title}\"")
        is MessageSupergroupChatCreate -> UiContent.Service(
            if (chatStates[chatId]?.type.let { it is ChatTypeSupergroup && it.isChannel }) "Channel created" else "${name(sender)} created the group \"${c.title}\"",
        )
        is MessagePinMessage -> UiContent.Service("${name(sender)} pinned a message")
        is MessageContactRegistered -> UiContent.Service("${name(sender)} joined Telegram")
        is MessageScreenshotTaken -> UiContent.Service("${name(sender)} took a screenshot")
        is MessageChatUpgradeFrom, is MessageChatUpgradeTo -> UiContent.Service("Group upgraded to supergroup")
        is MessageExpiredPhoto -> UiContent.Service("Photo has expired")
        is MessageExpiredVideo -> UiContent.Service("Video has expired")
        else -> UiContent.Service("This message is not supported yet")
    }

    private fun entities(t: FormattedText): List<Entity> = t.entities.mapNotNull { e ->
        val end = e.offset + e.length
        when (val type = e.type) {
            is TextEntityTypeBold -> Entity(e.offset, end, EntityType.Bold)
            is TextEntityTypeItalic -> Entity(e.offset, end, EntityType.Italic)
            is TextEntityTypeUnderline -> Entity(e.offset, end, EntityType.Underline)
            is TextEntityTypeStrikethrough -> Entity(e.offset, end, EntityType.Strike)
            is TextEntityTypeCode -> Entity(e.offset, end, EntityType.Code)
            is TextEntityTypePre, is TextEntityTypePreCode -> Entity(e.offset, end, EntityType.Pre)
            is TextEntityTypeSpoiler -> Entity(e.offset, end, EntityType.Spoiler)
            is TextEntityTypeBlockQuote, is TextEntityTypeExpandableBlockQuote -> Entity(e.offset, end, EntityType.Quote)
            is TextEntityTypeUrl -> Entity(e.offset, end, EntityType.Url)
            is TextEntityTypeTextUrl -> Entity(e.offset, end, EntityType.TextUrl, url = type.url)
            is TextEntityTypeMention -> Entity(e.offset, end, EntityType.Mention)
            is TextEntityTypeMentionName -> Entity(e.offset, end, EntityType.MentionName, userId = type.userId)
            is TextEntityTypeHashtag -> Entity(e.offset, end, EntityType.Hashtag)
            is TextEntityTypeCashtag -> Entity(e.offset, end, EntityType.Cashtag)
            is TextEntityTypeBotCommand -> Entity(e.offset, end, EntityType.BotCommand)
            is TextEntityTypeEmailAddress -> Entity(e.offset, end, EntityType.Email)
            is TextEntityTypePhoneNumber -> Entity(e.offset, end, EntityType.Phone)
            is TextEntityTypeCustomEmoji -> Entity(e.offset, end, EntityType.CustomEmoji, customEmojiId = type.customEmojiId)
            else -> null
        }
    }

    override fun chatInfo(chatId: Long): ChatInfo? = chatInfos[chatId]

    override fun loadChatInfo(chatId: Long) {
        val st = chatStates[chatId] ?: return
        scope.launch {
            val info = when (val t = st.type) {
                is ChatTypePrivate -> {
                    val full = client.getUserFullInfo(t.userId)
                    ChatInfo(
                        about = if (full is TdlResult.Success) full.result.bio?.text?.ifBlank { null } else null,
                        link = rawUsers[t.userId]?.usernames?.activeUsernames?.firstOrNull(),
                    )
                }
                is ChatTypeBasicGroup -> {
                    val r = client.getBasicGroupFullInfo(t.basicGroupId)
                    if (r !is TdlResult.Success) return@launch
                    ChatInfo(
                        about = r.result.description.ifBlank { null },
                        link = r.result.inviteLink?.inviteLink,
                        memberCount = r.result.members.size,
                        members = r.result.members.mapNotNull { member(it) },
                    )
                }
                is ChatTypeSupergroup -> {
                    val r = client.getSupergroupFullInfo(t.supergroupId)
                    if (r !is TdlResult.Success) return@launch
                    val full = r.result
                    val members = if (!t.isChannel && full.canGetMembers) {
                        val m = client.getSupergroupMembers(t.supergroupId, SupergroupMembersFilterRecent(), 0, 100)
                        if (m is TdlResult.Success) m.result.members.mapNotNull { member(it) } else emptyList()
                    } else emptyList()
                    ChatInfo(
                        about = full.description.ifBlank { null },
                        link = supergroups[t.supergroupId]?.usernames?.activeUsernames?.firstOrNull() ?: full.inviteLink?.inviteLink,
                        memberCount = full.memberCount,
                        members = members,
                    )
                }
                else -> return@launch
            }
            chatInfos[chatId] = info
        }
    }

    private fun member(m: ChatMember): Member? {
        val id = (m.memberId as? MessageSenderUser)?.userId ?: return null
        val role = when (m.status) {
            is ChatMemberStatusCreator -> "owner"
            is ChatMemberStatusAdministrator -> "admin"
            else -> null
        }
        return Member(id, role)
    }

    override fun sharedMedia(chatId: Long, kind: MediaKind): List<UiMessage> = sharedMediaStore["$chatId:$kind"] ?: emptyList()

    override fun loadSharedMedia(chatId: Long, kind: MediaKind) {
        val filter: SearchMessagesFilter = when (kind) {
            MediaKind.Media -> SearchMessagesFilterPhotoAndVideo()
            MediaKind.Files -> SearchMessagesFilterDocument()
            MediaKind.Links -> SearchMessagesFilterUrl()
            MediaKind.Voice -> SearchMessagesFilterVoiceAndVideoNote()
            MediaKind.Gifs -> SearchMessagesFilterAnimation()
        }
        scope.launch {
            val r = client.searchChatMessages(chatId = chatId, query = "", fromMessageId = 0, offset = 0, limit = 90, filter = filter)
            if (r is TdlResult.Success) sharedMediaStore["$chatId:$kind"] = r.result.messages.map { mapMessage(it) }
        }
    }

    override val stickerPacks: List<StickerPack> get() = packs
    override val recentStickers: List<StickerItem> get() = recents
    override val savedGifs: List<GifItem> get() = gifs

    override fun loadStickers() {
        if (stickersLoaded) return
        stickersLoaded = true
        scope.launch {
            val recent = client.getRecentStickers(false)
            if (recent is TdlResult.Success) { recents.clear(); recents.addAll(recent.result.stickers.map { stickerItem(it) }) }
            val saved = client.getSavedAnimations()
            if (saved is TdlResult.Success) {
                gifs.clear()
                gifs.addAll(saved.result.animations.map { a ->
                    GifItem(a.animation.id, a.thumbnail?.let { imageOf(it.file, a.minithumbnail, it.width, it.height) }, a.width, a.height, a.duration)
                })
            }
            val sets = client.getInstalledStickerSets(StickerTypeRegular())
            if (sets is TdlResult.Success) {
                packs.clear()
                for (info in sets.result.sets.take(40)) {
                    val set = client.getStickerSet(info.id)
                    if (set is TdlResult.Success) packs.add(StickerPack(info.id, info.title, set.result.stickers.map { stickerItem(it) }))
                }
            }
        }
    }

    private fun stickerItem(s: Sticker): StickerItem {
        val thumb = s.thumbnail?.takeIf { it.format is ThumbnailFormatWebp || it.format is ThumbnailFormatJpeg }
        return StickerItem(
            fileId = s.sticker.id,
            emoji = s.emoji,
            image = if (s.format is StickerFormatWebp) imageOf(s.sticker, null) else thumb?.let { imageOf(it.file, null) },
            animation = if (s.format is StickerFormatTgs) imageOf(s.sticker, null) else null,
            width = s.width,
            height = s.height,
            setId = s.setId,
            customEmojiId = (s.fullType as? StickerFullTypeCustomEmoji)?.customEmojiId ?: 0L,
        )
    }

    override fun sendSticker(chatId: Long, sticker: StickerItem, replyTo: Long?) {
        send(chatId, replyTo, InputMessageSticker(InputFileId(sticker.fileId), null, sticker.width, sticker.height, sticker.emoji))
    }

    override fun sendGif(chatId: Long, gif: GifItem, replyTo: Long?) {
        send(chatId, replyTo, InputMessageAnimation(InputAnimation(InputFileId(gif.fileId), null, IntArray(0), gif.duration, gif.width, gif.height), null, false, false))
    }

    override fun searchGlobal(query: String, onResult: (GlobalResults) -> Unit) {
        scope.launch {
            val publicChats = if (query.length >= 4) {
                val r = client.searchPublicChats(query)
                if (r is TdlResult.Success) r.result.chatIds.toList().mapNotNull { chatMap[it] } else emptyList()
            } else emptyList()
            val found = client.searchMessages(query = query, offset = "", limit = 40, minDate = 0, maxDate = 0)
            val messages = if (found is TdlResult.Success) found.result.messages.map { mapMessage(it) } else emptyList()
            onResult(GlobalResults(publicChats, messages))
        }
    }

    override fun resolveUsername(username: String, onResult: (Long?) -> Unit) {
        scope.launch {
            val r = client.searchPublicChat(username.removePrefix("@"))
            onResult(if (r is TdlResult.Success) r.result.id else null)
        }
    }

    /** TDLib voice waveforms are packed 5-bit samples. */
    private fun waveform(bytes: ByteArray): List<Float> {
        val count = bytes.size * 8 / 5
        if (count == 0) return List(40) { 0.3f }
        val raw = List(count) { i ->
            val bit = i * 5
            val byte = bit / 8
            val shift = bit % 8
            val lo = bytes[byte].toInt() and 0xFF
            val hi = if (byte + 1 < bytes.size) bytes[byte + 1].toInt() and 0xFF else 0
            (((hi shl 8) or lo) shr shift) and 0x1F
        }
        val bars = 40
        return List(bars) { i ->
            val from = i * raw.size / bars
            val to = maxOf(from + 1, (i + 1) * raw.size / bars)
            (raw.subList(from, minOf(to, raw.size)).maxOrNull() ?: 0) / 31f
        }.map { it.coerceIn(0.08f, 1f) }
    }

    private fun <T> TdlResult<T>.orReport(): TdlResult<T> {
        if (this is TdlResult.Failure) _errors.tryEmit(humanize(message))
        return this
    }

    // =====================================================================================
    // ---- Stories ----
    // =====================================================================================

    /** Active stories per poster chat (for users the chat id equals the user id), from updateChatActiveStories. */
    private val activeStories by lazy { mutableStateMapOf<Long, ChatActiveStories>() }
    /** Full stories fetched with getStory (or pushed by updateStory), keyed by [storyKey]. */
    private val storyCache by lazy { mutableStateMapOf<String, UiStory>() }
    private val storyRequests by lazy { HashSet<String>() }

    private fun storyKey(chatId: Long, storyId: Int) = "$chatId:$storyId"

    private fun isStorySeen(a: ChatActiveStories): Boolean = a.stories.all { it.storyId <= a.maxReadStoryId }

    override val storyUsers: List<UiUser>
        get() = activeStories.values
            .filter { it.list is StoryListMain && it.stories.isNotEmpty() && it.chatId != myId && userMap.containsKey(it.chatId) }
            .sortedWith(compareBy<ChatActiveStories>({ isStorySeen(it) }, { -it.order }, { -it.chatId }))
            .mapNotNull { userMap[it.chatId] }

    override val stories: List<UiStory> get() = storyUsers.flatMap { storiesOf(it.id) }

    override fun storiesOf(userId: Long): List<UiStory> {
        val a = activeStories[userId] ?: return emptyList()
        return a.stories.sortedBy { it.storyId }.map { info ->
            val seen = info.storyId <= a.maxReadStoryId
            storyCache[storyKey(a.chatId, info.storyId)]?.copy(seen = seen)
                ?: UiStory(userId = userId, date = info.date * 1000L, id = info.storyId, chatId = a.chatId, seen = seen, loaded = false)
        }
    }

    override fun loadStories(userId: Long) {
        val a = activeStories[userId] ?: return
        a.stories.forEach { fetchStory(a.chatId, it.storyId) }
    }

    /** Viewing a story in TDLib (openStory) also marks it as read; TDLib then sends updateChatActiveStories. */
    override fun openStory(story: UiStory) {
        if (story.id == 0) return
        scope.launch {
            val key = storyKey(story.chatId, story.id)
            if (storyCache[key]?.loaded != true) {
                val r = client.getStory(story.chatId, story.id, false)
                if (r is TdlResult.Success) storyCache[key] = mapStory(r.result)
            }
            client.openStory(story.chatId, story.id)
        }
    }

    override fun closeStory(story: UiStory) {
        if (story.id == 0) return
        scope.launch { client.closeStory(story.chatId, story.id) }
    }

    /** Seen state comes from TDLib (maxReadStoryId), see [openStory]. */
    override fun markStorySeen(userId: Long) {}

    private fun fetchStory(chatId: Long, storyId: Int) {
        val key = storyKey(chatId, storyId)
        if (storyCache.containsKey(key) || !storyRequests.add(key)) return
        scope.launch {
            val r = client.getStory(chatId, storyId, false)
            // Not available (expired, deleted, no access): a loaded story without media, shown as "can't be shown".
            storyCache[key] = if (r is TdlResult.Success) mapStory(r.result) else UiStory(userId = chatId, id = storyId, chatId = chatId, date = System.currentTimeMillis(), loaded = true)
        }
    }

    private fun loadActiveStoryLists() {
        scope.launch {
            // Fails with 404 once everything is loaded; the stories themselves arrive as updateChatActiveStories.
            repeat(20) { if (client.loadActiveStories(StoryListMain()) !is TdlResult.Success) return@launch }
        }
    }

    private fun onActiveStories(a: ChatActiveStories) {
        if (a.stories.isEmpty()) activeStories.remove(a.chatId) else activeStories[a.chatId] = a
        // Forget full stories that are no longer active.
        val ids = a.stories.map { it.storyId }.toHashSet()
        storyCache.values.filter { it.chatId == a.chatId && it.id !in ids }.forEach {
            val key = storyKey(it.chatId, it.id)
            storyCache.remove(key)
            storyRequests.remove(key)
        }
        userMap[a.chatId]?.let { u -> userMap[a.chatId] = withStoryFlags(u) }
    }

    private fun onStory(s: Story) {
        val key = storyKey(s.posterChatId, s.id)
        val active = activeStories[s.posterChatId]?.stories?.any { it.storyId == s.id } == true
        if (active || storyCache.containsKey(key)) storyCache[key] = mapStory(s)
    }

    private fun onStoryDeleted(chatId: Long, storyId: Int) {
        storyCache.remove(storyKey(chatId, storyId))
    }

    /** hasStory / storySeen of a user from the active stories we know about. */
    private fun withStoryFlags(u: UiUser): UiUser {
        val a = activeStories[u.id]
        val has = a != null && a.stories.isNotEmpty()
        val seen = a != null && isStorySeen(a)
        return if (u.hasStory == has && u.storySeen == seen) u else u.copy(hasStory = has, storySeen = seen)
    }

    private fun mapStory(s: Story): UiStory {
        var image: ImageRef? = null
        var video: ImageRef? = null
        var duration = 0.0
        when (val c = s.content) {
            is StoryContentPhoto -> {
                val mini = c.photo.minithumbnail
                val size = c.photo.sizes.maxByOrNull { it.width * it.height }
                image = if (size != null) imageOf(size.photo, mini, size.width, size.height)
                else mini?.let { ImageRef(0, null, it.data, it.width, it.height) }
            }
            is StoryContentVideo -> {
                val v = c.video
                val thumb = v.thumbnail?.takeIf { it.format is ThumbnailFormatJpeg }
                image = if (thumb != null) imageOf(thumb.file, v.minithumbnail, thumb.width, thumb.height)
                else v.minithumbnail?.let { ImageRef(0, null, it.data, it.width, it.height) }
                video = imageOf(v.video, v.minithumbnail, v.width, v.height)
                duration = v.duration
            }
            else -> Unit
        }
        val a = activeStories[s.posterChatId]
        return UiStory(
            userId = s.posterChatId,
            caption = s.caption.text,
            date = s.date * 1000L,
            id = s.id,
            chatId = s.posterChatId,
            image = image,
            video = video,
            durationSec = duration,
            seen = a != null && s.id <= a.maxReadStoryId,
            loaded = true,
        )
    }

    private fun resetStories() {
        activeStories.clear()
        storyCache.clear()
        storyRequests.clear()
    }

    // =====================================================================================
    // ---- Groups & channels ----
    // =====================================================================================

    /** The user's management rights from their member status in a basic group / supergroup / channel. */
    private fun rightsOf(status: ChatMemberStatus?): ChatRights? = when (status) {
        is ChatMemberStatusCreator -> ChatRights.Owner
        is ChatMemberStatusAdministrator -> ChatRights(
            owner = false,
            changeInfo = status.rights.canChangeInfo,
            inviteUsers = status.rights.canInviteUsers,
            banMembers = status.rights.canRestrictMembers,
        )
        else -> null
    }

    private fun groupError(error: String): String = when {
        "USER_PRIVACY_RESTRICTED" in error -> "This user's privacy settings don't allow adding them to groups."
        "USER_NOT_MUTUAL_CONTACT" in error -> "This user can only be added by a mutual contact."
        "USERS_TOO_MUCH" in error -> "The group has reached its member limit."
        "USER_CHANNELS_TOO_MUCH" in error -> "One of the users is in too many groups and channels."
        "CHANNELS_TOO_MUCH" in error -> "You are in too many groups and channels. Leave some before creating new ones."
        "CHAT_ADMIN_REQUIRED" in error || "RIGHT_FORBIDDEN" in error -> "You don't have the rights to do this."
        "CHAT_TITLE_EMPTY" in error -> "Please enter a name."
        "PHOTO_CROP_SIZE_SMALL" in error -> "This photo is too small."
        else -> humanize(error)
    }

    private fun failedToAddText(failed: Int): String? = when {
        failed <= 0 -> null
        failed == 1 -> "1 user couldn't be added because of their privacy settings."
        else -> "$failed users couldn't be added because of their privacy settings."
    }

    override fun muteFor(chatId: Long, seconds: Int) {
        val st = chatStates[chatId] ?: return
        val n = st.notifications
        val settings = ChatNotificationSettings(
            false, seconds.coerceAtLeast(0),
            n.useDefaultSound, n.soundId,
            n.useDefaultShowPreview, n.showPreview,
            n.useDefaultMuteStories, n.muteStories,
            n.useDefaultStorySound, n.storySoundId,
            n.useDefaultShowStoryPoster, n.showStoryPoster,
            n.useDefaultDisablePinnedMessageNotifications, n.disablePinnedMessageNotifications,
            n.useDefaultDisableMentionNotifications, n.disableMentionNotifications,
        )
        scope.launch { client.setChatNotificationSettings(chatId, settings).orReport() }
    }

    override fun createGroup(title: String, userIds: List<Long>, photoPath: String?, onDone: (chatId: Long?, error: String?) -> Unit) {
        scope.launch {
            when (val r = client.createNewBasicGroupChat(userIds.toLongArray(), title.trim(), 0)) {
                is TdlResult.Success -> {
                    val chatId = r.result.chatId
                    if (photoPath != null) {
                        val p = client.setChatPhoto(chatId, InputChatPhotoStatic(InputFileLocal(photoPath)))
                        if (p is TdlResult.Failure) _errors.tryEmit(groupError(p.message))
                    }
                    failedToAddText(r.result.failedToAddMembers.failedToAddMembers.size)?.let { _errors.tryEmit(it) }
                    onDone(chatId, null)
                }
                is TdlResult.Failure -> onDone(null, groupError(r.message))
            }
        }
    }

    override fun createChannel(title: String, description: String, photoPath: String?, onDone: (chatId: Long?, error: String?) -> Unit) {
        scope.launch {
            val r = client.createNewSupergroupChat(
                title = title.trim(),
                isForum = false,
                isChannel = true,
                description = description.trim(),
                location = null,
                messageAutoDeleteTime = 0,
                forImport = false,
            )
            when (r) {
                is TdlResult.Success -> {
                    val chatId = r.result.id
                    if (photoPath != null) {
                        val p = client.setChatPhoto(chatId, InputChatPhotoStatic(InputFileLocal(photoPath)))
                        if (p is TdlResult.Failure) _errors.tryEmit(groupError(p.message))
                    }
                    onDone(chatId, null)
                }
                is TdlResult.Failure -> onDone(null, groupError(r.message))
            }
        }
    }

    override fun editChat(chatId: Long, title: String, description: String, onDone: (String?) -> Unit) {
        val newTitle = title.trim()
        val newAbout = description.trim()
        val oldTitle = chatStates[chatId]?.title
        val oldAbout = chatInfos[chatId]?.about ?: ""
        scope.launch {
            var error: String? = null
            if (newTitle.isNotEmpty() && newTitle != oldTitle) {
                val r = client.setChatTitle(chatId, newTitle)
                if (r is TdlResult.Failure && "NOT_MODIFIED" !in r.message) error = groupError(r.message)
            }
            if (error == null && newAbout != oldAbout) {
                val r = client.setChatDescription(chatId, newAbout)
                if (r is TdlResult.Failure && "NOT_MODIFIED" !in r.message) error = groupError(r.message)
            }
            loadChatInfo(chatId)
            onDone(error)
        }
    }

    override fun updateChatPhoto(chatId: Long, path: String?, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.setChatPhoto(chatId, path?.let { InputChatPhotoStatic(InputFileLocal(it)) })
            onDone(if (r is TdlResult.Failure) groupError(r.message) else null)
        }
    }

    override fun addMembers(chatId: Long, userIds: List<Long>, onDone: (String?) -> Unit) {
        val st = chatStates[chatId] ?: return onDone("Chat not found")
        if (userIds.isEmpty()) return onDone(null)
        scope.launch {
            var failed = 0
            var error: String? = null
            if (st.type is ChatTypeBasicGroup) {
                // addChatMembers is supergroup/channel only; basic groups take one user at a time.
                for (id in userIds) {
                    when (val r = client.addChatMember(chatId, id, 100)) {
                        is TdlResult.Success -> failed += r.result.failedToAddMembers.size
                        is TdlResult.Failure -> if (userIds.size == 1) error = groupError(r.message) else failed++
                    }
                }
            } else {
                // At most 20 users per request.
                for (chunk in userIds.chunked(20)) {
                    when (val r = client.addChatMembers(chatId, chunk.toLongArray())) {
                        is TdlResult.Success -> failed += r.result.failedToAddMembers.size
                        is TdlResult.Failure -> { error = groupError(r.message); break }
                    }
                }
            }
            loadChatInfo(chatId)
            onDone(error ?: failedToAddText(failed))
        }
    }

    override fun removeMember(chatId: Long, userId: Long, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.banChatMember(chatId, MessageSenderUser(userId), 0, false)
            if (r is TdlResult.Success) {
                chatInfos[chatId]?.let { info ->
                    chatInfos[chatId] = info.copy(members = info.members.filter { it.userId != userId }, memberCount = (info.memberCount - 1).coerceAtLeast(0))
                }
                loadChatInfo(chatId)
                onDone(null)
            } else if (r is TdlResult.Failure) {
                onDone(groupError(r.message))
            }
        }
    }

    override fun deleteChatForAll(chatId: Long, onDone: (String?) -> Unit) {
        scope.launch {
            when (val r = client.deleteChat(chatId)) {
                is TdlResult.Success -> onDone(null)
                is TdlResult.Failure -> onDone(groupError(r.message))
            }
        }
    }
    // ---- Polls, contacts, folders ----
    // =====================================================================================

    /** A poll/quiz from the New Poll screen (options already trimmed and non-empty). */
    private fun pollInput(p: UiContent.Poll): InputMessageContent {
        val type: InputPollType = if (p.quiz) {
            val correct = (p.correctOption ?: 0).coerceIn(0, (p.options.size - 1).coerceAtLeast(0))
            InputPollTypeQuiz(intArrayOf(correct), FormattedText(p.explanation?.trim().orEmpty(), emptyArray()), null)
        } else {
            InputPollTypeRegular(false)
        }
        return InputMessagePoll(
            question = FormattedText(p.question.trim(), emptyArray()),
            options = p.options.map { InputPollOption(FormattedText(it.trim(), emptyArray()), null) }.toTypedArray(),
            description = null,
            media = null,
            isAnonymous = p.anonymous,
            allowsMultipleAnswers = p.multiple && !p.quiz,
            allowsRevoting = !p.quiz,
            membersOnly = false,
            countryCodes = emptyArray(),
            shuffleOptions = false,
            hideResultsUntilCloses = false,
            type = type,
            openPeriod = 0,
            closeDate = 0,
            isClosed = false,
        )
    }

    /** A shared contact; a known Telegram user is sent with their real name, phone and user id. */
    private fun contactInput(c: UiContent.Contact): InputMessageContent {
        val u = if (c.userId != 0L) rawUsers[c.userId] else null
        val first = u?.firstName?.takeIf { it.isNotBlank() } ?: c.name.substringBefore(' ')
        val last = if (u != null) u.lastName else c.name.substringAfter(' ', "")
        val phone = u?.phoneNumber?.takeIf { it.isNotBlank() } ?: c.phone
        return InputMessageContact(Contact(phone, first, last, "", if (u != null) u.id else 0L))
    }

    /** Folders as last fetched for editing, so saving keeps icon, color and pinned chats. */
    private val folderCache = HashMap<Int, ChatFolder>()

    override val editableFolders: List<com.abtin.tglass.data.FolderSummary>
        get() = folderInfos.map { f ->
            val n = chatMap.values.count { f.id in it.folderIds }
            com.abtin.tglass.data.FolderSummary(
                id = f.id,
                title = f.name.text.text,
                subtitle = when (n) { 0 -> "No chats"; 1 -> "1 chat"; else -> "$n chats" },
            )
        }

    override fun loadFolder(folderId: Int, onResult: (com.abtin.tglass.data.FolderDraft?) -> Unit) {
        scope.launch {
            val r = client.getChatFolder(folderId)
            if (r is TdlResult.Success) {
                val f = r.result
                folderCache[folderId] = f
                onResult(
                    com.abtin.tglass.data.FolderDraft(
                        name = f.name.text.text,
                        includeContacts = f.includeContacts,
                        includeNonContacts = f.includeNonContacts,
                        includeGroups = f.includeGroups,
                        includeChannels = f.includeChannels,
                        includeBots = f.includeBots,
                        excludeMuted = f.excludeMuted,
                        excludeRead = f.excludeRead,
                        excludeArchived = f.excludeArchived,
                        includedChatIds = (f.pinnedChatIds.toList() + f.includedChatIds.toList()).distinct(),
                        excludedChatIds = f.excludedChatIds.toList(),
                    ),
                )
            } else {
                r.orReport()
                onResult(null)
            }
        }
    }

    override fun saveFolder(folderId: Int?, draft: com.abtin.tglass.data.FolderDraft, onDone: (String?) -> Unit) {
        scope.launch {
            var old: ChatFolder? = null
            if (folderId != null) {
                old = folderCache[folderId]
                if (old == null) {
                    val r = client.getChatFolder(folderId)
                    if (r is TdlResult.Success) old = r.result
                }
            }
            val name = draft.name.trim()
            val chosen = draft.includedChatIds.distinct()
            val pinned = old?.pinnedChatIds?.toList().orEmpty().filter { it in chosen }
            val oldName = old?.name
            val folder = ChatFolder(
                name = if (oldName != null && oldName.text.text == name) oldName else ChatFolderName(FormattedText(name, emptyArray()), oldName?.animateCustomEmoji ?: false),
                icon = old?.icon,
                colorId = old?.colorId ?: -1,
                isShareable = old?.isShareable ?: false,
                pinnedChatIds = pinned.toLongArray(),
                includedChatIds = chosen.filter { it !in pinned }.toLongArray(),
                excludedChatIds = draft.excludedChatIds.distinct().filter { it !in chosen }.toLongArray(),
                excludeMuted = draft.excludeMuted,
                excludeRead = draft.excludeRead,
                excludeArchived = draft.excludeArchived,
                includeContacts = draft.includeContacts,
                includeNonContacts = draft.includeNonContacts,
                includeBots = draft.includeBots,
                includeGroups = draft.includeGroups,
                includeChannels = draft.includeChannels,
            )
            val r = if (folderId == null) client.createChatFolder(folder) else client.editChatFolder(folderId, folder)
            if (folderId != null) folderCache.remove(folderId)
            onDone(if (r is TdlResult.Failure) folderError(r.message) else null)
        }
    }

    override fun deleteFolder(folderId: Int, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.deleteChatFolder(folderId, LongArray(0))
            folderCache.remove(folderId)
            onDone(if (r is TdlResult.Failure) folderError(r.message) else null)
        }
    }

    private fun folderError(error: String): String = when {
        "FILTER_INCLUDE_EMPTY" in error -> "Please add at least one chat or chat type to the folder."
        "FILTER_TITLE_EMPTY" in error -> "Please enter a folder name."
        "FILTERS_TOO_MUCH" in error || "CHATLISTS_TOO_MUCH" in error -> "You have reached the limit of chat folders."
        "CHAT_FOLDER" in error && "LIMIT" in error.uppercase() -> "You have reached the limit of chats in a folder."
        else -> humanize(error)
    }
    // ---- end Polls, contacts, folders ----

    // =====================================================================================
    // ---- Proxy ----
    // =====================================================================================

    private val proxyList = mutableStateListOf<ProxyItem>()
    private val proxyPings = mutableStateMapOf<Int, Int>()

    override val proxies: List<ProxyItem> get() = proxyList.map { it.copy(ping = proxyPings[it.id]) }

    private fun toItem(p: AddedProxy): ProxyItem {
        val base = ProxyItem(p.id, p.proxy.server, p.proxy.port, ProxyKind.Socks5, enabled = p.isEnabled)
        return when (val t = p.proxy.type) {
            is ProxyTypeMtproto -> base.copy(kind = ProxyKind.MTProto, secret = t.secret)
            is ProxyTypeHttp -> base.copy(kind = ProxyKind.Http, username = t.username, password = t.password)
            is ProxyTypeSocks5 -> base.copy(username = t.username, password = t.password)
            else -> base
        }
    }

    /** "https://1.2.3.4/ " → "1.2.3.4": people paste addresses with a scheme, path or spaces. */
    private fun cleanServer(raw: String): String =
        raw.trim().substringAfter("://").substringBefore('/').substringBefore('?').trim()

    /**
     * TDLib wants the MTProto secret in hex. Links and channels also hand them out in base64 (standard or URL-safe,
     * with or without padding, "ee"/"dd" fake-TLS prefixes included), and a '+' often arrives as a space after URL
     * decoding; anything that is not plain hex is decoded from base64 and re-encoded as hex.
     */
    private fun normalizeSecret(raw: String): String {
        val s = raw.trim().replace(' ', '+').filterNot { it.isWhitespace() }
        if (s.isEmpty() || s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return s.lowercase()
        val b64 = s.replace('-', '+').replace('_', '/').trimEnd('=')
        val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
        val bytes = runCatching { android.util.Base64.decode(padded, android.util.Base64.DEFAULT) }.getOrNull()
            ?: return s
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun toProxy(p: ProxyItem): Proxy = Proxy(
        cleanServer(p.server), p.port,
        when (p.kind) {
            ProxyKind.MTProto -> ProxyTypeMtproto(normalizeSecret(p.secret))
            ProxyKind.Http -> ProxyTypeHttp(p.username, p.password, false)
            ProxyKind.Socks5 -> ProxyTypeSocks5(p.username, p.password)
        },
    )

    override fun loadProxies() {
        scope.launch {
            val r = client.getProxies()
            if (r is TdlResult.Success) {
                proxyList.clear()
                proxyList.addAll(r.result.proxies.map { toItem(it) })
            }
        }
    }

    override fun saveProxy(id: Int?, proxy: ProxyItem, enable: Boolean, onDone: (String?) -> Unit) {
        scope.launch {
            val r = if (id == null) client.addProxy(toProxy(proxy), enable, "") else client.editProxy(id, toProxy(proxy), enable, "")
            if (r is TdlResult.Success) {
                loadProxies()
                onDone(null)
            } else if (r is TdlResult.Failure) {
                val msg = r.message
                onDone(
                    when {
                        "SECRET" in msg.uppercase() -> "This proxy's secret isn't valid. Copy the whole proxy link again and paste it here."
                        "PORT" in msg.uppercase() -> "The port must be a number between 1 and 65535."
                        "SERVER" in msg.uppercase() || "PROXY" in msg.uppercase() -> "Check the server address of this proxy. ($msg)"
                        else -> humanize(msg)
                    },
                )
            }
        }
    }

    override fun enableProxy(id: Int) {
        scope.launch { client.enableProxy(id).orReport(); loadProxies() }
    }

    override fun disableProxy() {
        scope.launch { client.disableProxy().orReport(); loadProxies() }
    }

    override fun removeProxy(id: Int) {
        scope.launch { client.removeProxy(id).orReport(); loadProxies() }
    }

    override fun pingProxies() {
        proxyList.toList().forEach { p ->
            scope.launch {
                val r = client.pingProxy(toProxy(p))
                proxyPings[p.id] = if (r is TdlResult.Success) (r.result.seconds * 1000).toInt() else -1
            }
        }
    }


    // ---- Contacts, media, calls ----

    override fun addContact(firstName: String, lastName: String, phone: String, onDone: (userId: Long?, error: String?) -> Unit) {
        val number = phone.filter { it.isDigit() || it == '+' }
        scope.launch {
            val r = client.importContacts(arrayOf(ImportedContact(number, firstName.trim(), lastName.trim(), null)))
            if (r is TdlResult.Failure) {
                onDone(null, humanize(r.message))
                return@launch
            }
            val userId = if (r is TdlResult.Success) r.result.userIds.firstOrNull() ?: 0L else 0L
            if (userId == 0L) {
                // The number is not registered on Telegram.
                onDone(null, null)
                return@launch
            }
            client.getUser(userId).let { u -> if (u is TdlResult.Success) onUser(u.result) }
            if (userId !in contactIds) contactIds.add(userId)
            client.createPrivateChat(userId, false)
            onDone(userId, null)
        }
    }

    override fun removeContact(userId: Long, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.removeContacts(longArrayOf(userId))
            if (r is TdlResult.Failure) {
                onDone(humanize(r.message))
            } else {
                contactIds.remove(userId)
                client.getUser(userId).let { u -> if (u is TdlResult.Success) onUser(u.result) }
                onDone(null)
            }
        }
    }

    override fun lastSeenOrder(userId: Long): Long {
        val now = System.currentTimeMillis() / 1000L
        return when (val s = rawUsers[userId]?.status) {
            is UserStatusOnline -> Long.MAX_VALUE
            is UserStatusOffline -> s.wasOnline.toLong()
            is UserStatusRecently -> now - 3L * 86_400L
            is UserStatusLastWeek -> now - 7L * 86_400L
            is UserStatusLastMonth -> now - 30L * 86_400L
            else -> 0L
        }
    }

    override fun deleteCallRecords(records: List<CallRecord>) {
        if (records.isEmpty()) return
        scope.launch {
            records.groupBy { it.chatId }.forEach { (chatId, list) ->
                val r = client.deleteMessages(chatId, list.map { it.id }.toLongArray(), false)
                if (r is TdlResult.Success) {
                    val ids = list.map { it.id }.toSet()
                    callList.removeAll { it.chatId == chatId && it.id in ids }
                } else r.orReport()
            }
        }
    }
    // ---- end Contacts, media, calls ----

    // =====================================================================================
    // ---- Chat features ----
    // Bot keyboards, block / unblock, clear history, report spam, captions, silent send, online counts.
    // =====================================================================================

    /** Inline keyboards under messages, keyed "chatId:messageId" (filled by [mapMessage] and updateMessageEdited). */
    private val inlineKeyboards = mutableStateMapOf<String, com.abtin.tglass.data.InlineKeyboard>()
    /** Custom reply keyboards a bot currently shows, per chat. */
    private val replyKeyboards = mutableStateMapOf<Long, com.abtin.tglass.data.ReplyKeyboard>()
    /** chat.replyMarkupMessageId: the message whose reply keyboard is shown (0 = none). */
    private val replyMarkupIds = HashMap<Long, Long>()
    /** Chats whose sender (user / bot) is on the user's block list. */
    private val blockedChats = mutableStateMapOf<Long, Boolean>()
    private val clearOptions = HashMap<Long, com.abtin.tglass.data.ClearHistoryOptions>()
    private val reportableChats = HashSet<Long>()
    private val onlineCounts = mutableStateMapOf<Long, Int>()

    /** Called for every update before the main handler; only reads what the chat features need. */
    private fun onChatFeatureUpdate(u: Update) {
        when (u) {
            is UpdateNewChat -> recordChatFeatures(u.chat)
            is UpdateChatBlockList -> setBlockedLocally(u.chatId, u.blockList != null)
            is UpdateChatReplyMarkup -> {
                val m = u.replyMarkupMessage
                replyMarkupIds[u.chatId] = m?.id ?: 0L
                val kb = m?.let { mapReplyKeyboard(it.id, it.replyMarkup) }
                if (kb != null) replyKeyboards[u.chatId] = kb else replyKeyboards.remove(u.chatId)
            }
            is UpdateChatOnlineMemberCount -> onlineCounts[u.chatId] = u.onlineMemberCount
            is UpdateMessageEdited -> rememberInlineKeyboard(u.chatId, u.messageId, u.replyMarkup)
            else -> Unit
        }
    }

    private fun recordChatFeatures(c: Chat) {
        setBlockedLocally(c.id, c.blockList != null)
        clearOptions[c.id] = com.abtin.tglass.data.ClearHistoryOptions(forMe = c.canBeDeletedOnlyForSelf, forEveryone = c.canBeDeletedForAllUsers)
        if (c.canBeReported) reportableChats += c.id else reportableChats -= c.id
        replyMarkupIds[c.id] = c.replyMarkupMessageId
    }

    private fun setBlockedLocally(chatId: Long, blocked: Boolean) {
        if (blocked) blockedChats[chatId] = true else if (blockedChats.containsKey(chatId)) blockedChats.remove(chatId)
    }

    private fun resetChatFeatures() {
        inlineKeyboards.clear(); replyKeyboards.clear(); replyMarkupIds.clear()
        blockedChats.clear(); clearOptions.clear(); reportableChats.clear(); onlineCounts.clear()
    }

    /** Stores (or forgets) the inline keyboard of a message. */
    private fun rememberInlineKeyboard(chatId: Long, messageId: Long, markup: ReplyMarkup?) {
        val key = "$chatId:$messageId"
        val kb = (markup as? ReplyMarkupInlineKeyboard)?.let { mapInlineKeyboard(it) }
        if (kb != null) inlineKeyboards[key] = kb else if (inlineKeyboards.containsKey(key)) inlineKeyboards.remove(key)
    }

    private fun mapInlineKeyboard(k: ReplyMarkupInlineKeyboard): com.abtin.tglass.data.InlineKeyboard? {
        val rows = k.rows.map { row ->
            row.map { b ->
                when (val t = b.type) {
                    is InlineKeyboardButtonTypeUrl -> com.abtin.tglass.data.InlineButton(b.text, com.abtin.tglass.data.InlineButtonKind.Url, url = t.url)
                    // Telegram would first ask the bot to authorize the user; opening the plain URL still works for most sites.
                    is InlineKeyboardButtonTypeLoginUrl -> com.abtin.tglass.data.InlineButton(b.text, com.abtin.tglass.data.InlineButtonKind.Url, url = t.url)
                    is InlineKeyboardButtonTypeCallback -> com.abtin.tglass.data.InlineButton(b.text, com.abtin.tglass.data.InlineButtonKind.Callback, data = t.data)
                    is InlineKeyboardButtonTypeCopyText -> com.abtin.tglass.data.InlineButton(b.text, com.abtin.tglass.data.InlineButtonKind.Copy, copyText = t.text)
                    else -> com.abtin.tglass.data.InlineButton(b.text, com.abtin.tglass.data.InlineButtonKind.Unsupported)
                }
            }
        }.filter { it.isNotEmpty() }
        return if (rows.isEmpty()) null else com.abtin.tglass.data.InlineKeyboard(rows)
    }

    private fun mapReplyKeyboard(messageId: Long, markup: ReplyMarkup?): com.abtin.tglass.data.ReplyKeyboard? {
        val k = markup as? ReplyMarkupShowKeyboard ?: return null
        val rows = k.rows.map { row ->
            row.map { b -> com.abtin.tglass.data.ReplyButton(b.text, sendsText = b.type is KeyboardButtonTypeText) }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        return com.abtin.tglass.data.ReplyKeyboard(
            messageId = messageId,
            rows = rows,
            resize = k.resizeKeyboard,
            oneTime = k.oneTime,
            placeholder = k.inputFieldPlaceholder,
        )
    }

    override fun loadChatExtras(chatId: Long) {
        scope.launch {
            val r = client.getChat(chatId)
            if (r is TdlResult.Success) recordChatFeatures(r.result)
            val markupId = replyMarkupIds[chatId] ?: 0L
            if (markupId != 0L) {
                val m = client.getMessage(chatId, markupId)
                val kb = if (m is TdlResult.Success) mapReplyKeyboard(markupId, m.result.replyMarkup) else null
                if (kb != null) replyKeyboards[chatId] = kb else replyKeyboards.remove(chatId)
            } else if (replyKeyboards.containsKey(chatId)) {
                replyKeyboards.remove(chatId)
            }
            // Member / subscriber counts for the header (supergroup.memberCount is often 0 until the full info is loaded).
            val type = chatStates[chatId]?.type
            if ((type is ChatTypeSupergroup || type is ChatTypeBasicGroup) && chatInfos[chatId] == null) loadChatInfo(chatId)
        }
    }

    override fun inlineKeyboard(chatId: Long, messageId: Long): com.abtin.tglass.data.InlineKeyboard? = inlineKeyboards["$chatId:$messageId"]

    override fun replyKeyboard(chatId: Long): com.abtin.tglass.data.ReplyKeyboard? = replyKeyboards[chatId]

    override fun pressCallbackButton(chatId: Long, messageId: Long, data: ByteArray, onAnswer: (com.abtin.tglass.data.BotAnswer?) -> Unit) {
        scope.launch {
            when (val r = client.getCallbackQueryAnswer(chatId, messageId, CallbackQueryPayloadData(data))) {
                is TdlResult.Success -> onAnswer(com.abtin.tglass.data.BotAnswer(r.result.text, r.result.showAlert, r.result.url))
                is TdlResult.Failure -> {
                    _errors.tryEmit(if (r.code == 502 || "TIMEOUT" in r.message) "The bot didn't respond. Please try again." else humanize(r.message))
                    onAnswer(null)
                }
            }
        }
    }

    override fun startBot(chatId: Long) {
        val botId = (chatStates[chatId]?.type as? ChatTypePrivate)?.userId ?: return super.startBot(chatId)
        scope.launch {
            client.sendBotStartMessage(botId, chatId, "").also { r -> if (r is TdlResult.Success) addMessage(r.result) }.orReport()
        }
    }

    override fun isBlocked(chatId: Long): Boolean = blockedChats[chatId] == true

    override fun setBlocked(chatId: Long, blocked: Boolean, onDone: (String?) -> Unit) {
        val userId = (chatStates[chatId]?.type as? ChatTypePrivate)?.userId ?: return onDone("Only users and bots can be blocked.")
        val before = isBlocked(chatId)
        setBlockedLocally(chatId, blocked)
        scope.launch {
            val r = client.setMessageSenderBlockList(MessageSenderUser(userId), if (blocked) BlockListMain() else null)
            if (r is TdlResult.Failure) {
                setBlockedLocally(chatId, before)
                onDone(humanize(r.message))
            } else {
                onDone(null)
            }
        }
    }

    override fun clearHistoryOptions(chatId: Long): com.abtin.tglass.data.ClearHistoryOptions =
        clearOptions[chatId] ?: com.abtin.tglass.data.ClearHistoryOptions(forMe = chatStates[chatId]?.type !is ChatTypeSupergroup, forEveryone = false)

    override fun clearHistory(chatId: Long, forEveryone: Boolean) {
        scope.launch {
            val r = client.deleteChatHistory(chatId, false, forEveryone).orReport()
            if (r is TdlResult.Success) {
                messageStore[chatId]?.clear()
                pinnedStore.remove(chatId)
            }
        }
    }

    override fun canReportSpam(chatId: Long): Boolean = chatId in reportableChats

    override fun reportSpam(chatId: Long, onDone: (String?) -> Unit) {
        scope.launch {
            var option = ByteArray(0)
            var text = ""
            // Telegram asks for a reason (and sometimes optional details) step by step.
            repeat(4) {
                when (val r = client.reportChat(chatId, option, LongArray(0), text)) {
                    is TdlResult.Failure -> {
                        onDone(humanize(r.message))
                        return@launch
                    }
                    is TdlResult.Success -> when (val res = r.result) {
                        is ReportChatResultOk -> {
                            onDone(null)
                            return@launch
                        }
                        is ReportChatResultOptionRequired -> {
                            val spam = res.options.firstOrNull { it.text.contains("spam", ignoreCase = true) }
                            if (spam == null) {
                                onDone("This chat can't be reported as spam.")
                                return@launch
                            }
                            option = spam.id
                        }
                        is ReportChatResultTextRequired -> {
                            if (!res.isOptional) {
                                onDone("Telegram needs more details for this report.")
                                return@launch
                            }
                            option = res.optionId
                            text = ""
                        }
                        else -> {
                            onDone("This chat can't be reported from here.")
                            return@launch
                        }
                    }
                }
            }
            onDone("This chat can't be reported from here.")
        }
    }

    override fun editCaption(chatId: Long, messageId: Long, caption: String) {
        scope.launch {
            client.editMessageCaption(
                chatId = chatId,
                messageId = messageId,
                replyMarkup = null,
                caption = caption.takeIf { it.isNotBlank() }?.let { FormattedText(it, emptyArray()) },
                showCaptionAboveMedia = false,
            ).also { r -> if (r is TdlResult.Success) addMessage(r.result) }.orReport()
        }
    }

    override fun sendTextSilently(chatId: Long, text: String, replyTo: Long?) {
        scope.launch {
            client.sendMessage(
                chatId = chatId,
                replyTo = replyTo?.let { InputMessageReplyToMessage(it, null, 0, "") },
                options = MessageSendOptions(
                    suggestedPostInfo = null,
                    disableNotification = true,
                    fromBackground = false,
                    protectContent = false,
                    allowPaidBroadcast = false,
                    paidMessageStarCount = 0L,
                    updateOrderOfInstalledStickerSets = false,
                    schedulingState = null,
                    effectId = 0L,
                    sendingId = 0,
                    onlyPreview = false,
                ),
                inputMessageContent = InputMessageText(FormattedText(text, emptyArray()), null, true),
            ).also { r -> if (r is TdlResult.Success) addMessage(r.result) }.orReport()
        }
    }

    override fun onlineMemberCount(chatId: Long): Int? = onlineCounts[chatId]?.takeIf { it > 0 }
    // ---- end Chat features ----

    // ---- Video messages ----
    /** A received round video message. */
    private fun videoNoteContent(c: MessageVideoNote): UiContent.VideoNote {
        val n = c.videoNote
        return UiContent.VideoNote(
            seconds = n.duration,
            video = imageOf(n.video, null, n.length, n.length),
            thumb = n.thumbnail?.let { imageOf(it.file, n.minithumbnail, it.width, it.height) }
                ?: n.minithumbnail?.let { ImageRef(-n.video.id, null, it.data, n.length, n.length) },
            viewed = c.isViewed,
        )
    }

    /** A freshly recorded video message (local square MP4, see VideoNoteRecorder) as TDLib input. */
    private fun videoNoteInput(v: UiContent.VideoNote): InputMessageContent? {
        val ref = v.video?.takeIf { it.fileId == 0 } ?: return null
        val path = ref.path ?: return null
        val thumb = v.thumb?.let { t ->
            t.path?.takeIf { t.fileId == 0 && t.width > 0 && t.height > 0 }?.let { p -> InputThumbnail(InputFileLocal(p), t.width, t.height) }
        }
        // Width = height of the square video; Telegram allows at most 640.
        val length = (if (ref.width > 0) minOf(ref.width, if (ref.height > 0) ref.height else ref.width) else 384).coerceIn(1, 640)
        return InputMessageVideoNote(InputFileLocal(path), thumb, v.seconds.coerceIn(1, 60), length, null)
    }

    /** A video message was played (by us or on another device): drop its "not viewed" dot. */
    private fun onContentOpened(chatId: Long, messageId: Long) = updateMessage(chatId, messageId) { m ->
        val c = m.content
        if (c is UiContent.VideoNote && !c.viewed) m.copy(content = c.copy(viewed = true))
        else if (c is UiContent.Voice && !c.listened) m.copy(content = c.copy(listened = true)) else m
    }

    override fun openMessageContent(chatId: Long, messageId: Long) {
        if (messageId <= 0) return
        scope.launch { client.openMessageContent(chatId, messageId) }
    }
    // ---- end Video messages ----

    // =====================================================================================
    // ---- Settings (real) ----
    // Blocked users, 2-step verification, account TTL, storage, data usage, auto-download,
    // scope notification settings, support chat and the user's link.
    // State holders are lazy because this class body runs start() from init before later properties exist.
    // =====================================================================================

    private val blockedState by lazy { mutableStateOf<List<com.abtin.tglass.data.BlockedPeer>?>(null) }
    private val blockedTotalState by lazy { mutableStateOf<Int?>(null) }
    private val passwordState by lazy { mutableStateOf<com.abtin.tglass.data.PasswordInfo?>(null) }
    private val accountTtlState by lazy { mutableStateOf<Int?>(null) }
    private val storageState by lazy { mutableStateOf<com.abtin.tglass.data.StorageInfo?>(null) }
    private val dataUsageState by lazy { mutableStateOf<com.abtin.tglass.data.DataUsage?>(null) }
    private val scopeStore by lazy { mutableStateMapOf<com.abtin.tglass.data.NotifyScope, com.abtin.tglass.data.ScopeNotifications>() }
    private val rawScopeSettings by lazy { HashMap<com.abtin.tglass.data.NotifyScope, ScopeNotificationSettings>() }

    override val blockedPeers: List<com.abtin.tglass.data.BlockedPeer>? get() = blockedState.value
    override val blockedCount: Int? get() = blockedTotalState.value

    override fun loadBlocked() {
        scope.launch {
            val r = client.getBlockedMessageSenders(BlockListMain(), 0, 100)
            if (r !is TdlResult.Success) {
                r.orReport()
                return@launch
            }
            val peers = ArrayList<com.abtin.tglass.data.BlockedPeer>()
            for (sender in r.result.senders) {
                when (sender) {
                    is MessageSenderUser -> {
                        var u = userMap[sender.userId]
                        if (u == null) {
                            val ur = client.getUser(sender.userId)
                            if (ur is TdlResult.Success) {
                                onUser(ur.result)
                                u = userMap[sender.userId]
                            }
                        }
                        val subtitle = when {
                            u == null -> ""
                            u.username != null -> "@${u.username}"
                            else -> u.phone
                        }
                        peers.add(com.abtin.tglass.data.BlockedPeer(sender.userId, true, u?.name?.takeIf { it.isNotBlank() } ?: "Deleted Account", subtitle))
                    }
                    is MessageSenderChat -> {
                        var title = chatMap[sender.chatId]?.title
                        if (title == null) {
                            val cr = client.getChat(sender.chatId)
                            if (cr is TdlResult.Success) title = cr.result.title
                        }
                        peers.add(com.abtin.tglass.data.BlockedPeer(sender.chatId, false, title ?: "Chat", ""))
                    }
                }
            }
            blockedTotalState.value = maxOf(r.result.totalCount, peers.size)
            blockedState.value = peers
        }
    }

    override fun unblock(peer: com.abtin.tglass.data.BlockedPeer, onDone: (String?) -> Unit) {
        scope.launch {
            val sender: MessageSender = if (peer.isUser) MessageSenderUser(peer.id) else MessageSenderChat(peer.id)
            val r = client.setMessageSenderBlockList(sender, null)
            if (r is TdlResult.Failure) {
                onDone(humanize(r.message))
            } else {
                blockedState.value = blockedState.value?.filter { !(it.id == peer.id && it.isUser == peer.isUser) }
                blockedTotalState.value = blockedTotalState.value?.let { (it - 1).coerceAtLeast(0) }
                onDone(null)
            }
        }
    }

    override val passwordInfo: com.abtin.tglass.data.PasswordInfo? get() = passwordState.value

    private fun onPasswordState(p: PasswordState) {
        val pending = p.recoveryEmailAddressCodeInfo
        passwordState.value = com.abtin.tglass.data.PasswordInfo(
            hasPassword = p.hasPassword,
            hint = p.passwordHint,
            hasRecoveryEmail = p.hasRecoveryEmailAddress,
            pendingEmailPattern = pending?.emailAddressPattern,
            pendingEmailCodeLength = pending?.length ?: 0,
        )
    }

    private fun passwordError(error: String): String = when {
        "PASSWORD_HASH_INVALID" in error -> "Invalid password. Please try again."
        "EMAIL_INVALID" in error -> "Please enter a valid email address."
        "CODE_INVALID" in error || "EMAIL_CODE_INVALID" in error -> "Invalid code. Please try again."
        "CODE_EXPIRED" in error -> "The code has expired. Please request a new one."
        "SESSION_TOO_FRESH" in error || "PASSWORD_TOO_FRESH" in error ->
            "For security reasons, this can only be changed 24 hours after logging in on this device."
        else -> humanize(error)
    }

    override fun loadPasswordInfo() {
        scope.launch {
            val r = client.getPasswordState()
            if (r is TdlResult.Success) onPasswordState(r.result) else r.orReport()
        }
    }

    override fun setPassword(oldPassword: String, newPassword: String, hint: String, recoveryEmail: String?, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.setPassword(oldPassword, newPassword, hint, recoveryEmail != null, recoveryEmail?.trim() ?: "")
            when (r) {
                is TdlResult.Success -> {
                    onPasswordState(r.result)
                    onDone(null)
                }
                is TdlResult.Failure -> onDone(passwordError(r.message))
            }
        }
    }

    override fun setRecoveryEmail(password: String, email: String, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.setRecoveryEmailAddress(password, email.trim())
            when (r) {
                is TdlResult.Success -> {
                    onPasswordState(r.result)
                    onDone(null)
                }
                is TdlResult.Failure -> onDone(passwordError(r.message))
            }
        }
    }

    override fun confirmRecoveryEmail(code: String, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.checkRecoveryEmailAddressCode(code.trim())
            when (r) {
                is TdlResult.Success -> {
                    onPasswordState(r.result)
                    onDone(null)
                }
                is TdlResult.Failure -> onDone(passwordError(r.message))
            }
        }
    }

    override fun resendRecoveryEmailCode(onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.resendRecoveryEmailAddressCode()
            when (r) {
                is TdlResult.Success -> {
                    onPasswordState(r.result)
                    onDone(null)
                }
                is TdlResult.Failure -> onDone(passwordError(r.message))
            }
        }
    }

    override val accountTtlDays: Int? get() = accountTtlState.value

    override fun loadAccountTtl() {
        scope.launch {
            val r = client.getAccountTtl()
            if (r is TdlResult.Success) accountTtlState.value = r.result.days
        }
    }

    override fun setAccountTtl(days: Int) {
        val previous = accountTtlState.value
        accountTtlState.value = days
        scope.launch {
            val r = client.setAccountTtl(AccountTtl(days))
            if (r is TdlResult.Failure) {
                accountTtlState.value = previous
                _errors.tryEmit(humanize(r.message))
            }
        }
    }

    override val storageInfo: com.abtin.tglass.data.StorageInfo? get() = storageState.value

    private suspend fun fetchStorage(): com.abtin.tglass.data.StorageInfo? {
        val r = client.getStorageStatisticsFast()
        if (r !is TdlResult.Success) return null
        val s = r.result
        val info = com.abtin.tglass.data.StorageInfo(s.filesSize, s.fileCount, s.databaseSize, s.languagePackDatabaseSize + s.logSize)
        storageState.value = info
        return info
    }

    override fun loadStorage() {
        scope.launch { fetchStorage() }
    }

    override fun clearCache(onDone: (freed: Long?, error: String?) -> Unit) {
        scope.launch {
            val before = storageState.value?.filesSize ?: fetchStorage()?.filesSize
            // size/ttl/count/immunity 0 = delete everything TDLib may delete by default
            // (thumbnails, profile photos, stickers and wallpapers are kept).
            val r = client.optimizeStorage(0L, 0, 0, 0, emptyArray<FileType>(), LongArray(0), LongArray(0), false, 0)
            if (r is TdlResult.Failure) {
                onDone(null, humanize(r.message))
                return@launch
            }
            // Deleted files must be downloaded again when shown.
            filePaths.clear()
            downloading.clear()
            val after = fetchStorage()?.filesSize
            onDone(if (before != null && after != null) (before - after).coerceAtLeast(0L) else null, null)
        }
    }

    override val dataUsage: com.abtin.tglass.data.DataUsage? get() = dataUsageState.value

    private fun fileTypeTitle(t: FileType?): String = when (t) {
        null -> "Messages and Other"
        is FileTypePhoto -> "Photos"
        is FileTypeVideo, is FileTypeAnimation, is FileTypeVideoNote -> "Videos"
        is FileTypeAudio -> "Music"
        is FileTypeVoiceNote -> "Voice Messages"
        is FileTypeDocument -> "Files"
        is FileTypeSticker -> "Stickers"
        else -> "Other Files"
    }

    private fun mapNetworkStatistics(s: NetworkStatistics): com.abtin.tglass.data.DataUsage {
        // network -> title -> [sent, received]
        val sums = HashMap<Int, LinkedHashMap<String, LongArray>>()
        for (e in s.entries) {
            val net: NetworkType
            val title: String
            val sent: Long
            val received: Long
            when (e) {
                is NetworkStatisticsEntryFile -> {
                    net = e.networkType; title = fileTypeTitle(e.fileType); sent = e.sentBytes; received = e.receivedBytes
                }
                is NetworkStatisticsEntryCall -> {
                    net = e.networkType; title = "Calls"; sent = e.sentBytes; received = e.receivedBytes
                }
            }
            val bucket = when (net) {
                is NetworkTypeMobile -> 0
                is NetworkTypeMobileRoaming -> 2
                else -> 1
            }
            val row = sums.getOrPut(bucket) { LinkedHashMap() }.getOrPut(title) { LongArray(2) }
            row[0] += sent
            row[1] += received
        }
        fun rows(bucket: Int): List<com.abtin.tglass.data.DataUsageRow> =
            sums[bucket].orEmpty().map { (title, v) -> com.abtin.tglass.data.DataUsageRow(title, v[0], v[1]) }
                .filter { it.sent > 0 || it.received > 0 }
                .sortedByDescending { it.sent + it.received }
        return com.abtin.tglass.data.DataUsage(s.sinceDate * 1000L, rows(0), rows(1), rows(2))
    }

    override fun loadDataUsage() {
        scope.launch {
            val r = client.getNetworkStatistics(false)
            if (r is TdlResult.Success) dataUsageState.value = mapNetworkStatistics(r.result) else r.orReport()
        }
    }

    override fun resetDataUsage() {
        scope.launch {
            client.resetNetworkStatistics().orReport()
            val r = client.getNetworkStatistics(false)
            if (r is TdlResult.Success) dataUsageState.value = mapNetworkStatistics(r.result)
        }
    }

    override fun applyAutoDownload(network: com.abtin.tglass.data.DownloadNetwork, value: com.abtin.tglass.data.AutoDownload) {
        scope.launch {
            val presets = client.getAutoDownloadSettingsPresets()
            if (presets !is TdlResult.Success) return@launch
            val base = when (network) {
                com.abtin.tglass.data.DownloadNetwork.Cellular -> presets.result.medium
                com.abtin.tglass.data.DownloadNetwork.WiFi -> presets.result.high
                com.abtin.tglass.data.DownloadNetwork.Roaming -> presets.result.low
            }
            val settings = AutoDownloadSettings(
                value.enabled,
                if (value.photos) base.maxPhotoFileSize else 0,
                if (value.videos) base.maxVideoFileSize else 0L,
                if (value.files) base.maxOtherFileSize else 0L,
                base.videoUploadBitrate,
                base.preloadLargeVideos,
                base.preloadNextAudio,
                base.preloadStories,
                base.useLessDataForCalls,
            )
            val type: NetworkType = when (network) {
                com.abtin.tglass.data.DownloadNetwork.Cellular -> NetworkTypeMobile()
                com.abtin.tglass.data.DownloadNetwork.WiFi -> NetworkTypeWiFi()
                com.abtin.tglass.data.DownloadNetwork.Roaming -> NetworkTypeMobileRoaming()
            }
            client.setAutoDownloadSettings(settings, type).orReport()
        }
    }

    private fun tdScope(kind: com.abtin.tglass.data.NotifyScope): NotificationSettingsScope = when (kind) {
        com.abtin.tglass.data.NotifyScope.Private -> NotificationSettingsScopePrivateChats()
        com.abtin.tglass.data.NotifyScope.Groups -> NotificationSettingsScopeGroupChats()
        com.abtin.tglass.data.NotifyScope.Channels -> NotificationSettingsScopeChannelChats()
    }

    override fun scopeNotifications(kind: com.abtin.tglass.data.NotifyScope): com.abtin.tglass.data.ScopeNotifications? = scopeStore[kind]

    override fun loadScopeNotifications() {
        scope.launch {
            for (kind in com.abtin.tglass.data.NotifyScope.entries) {
                val r = client.getScopeNotificationSettings(tdScope(kind))
                if (r is TdlResult.Success) {
                    rawScopeSettings[kind] = r.result
                    scopeStore[kind] = com.abtin.tglass.data.ScopeNotifications(enabled = r.result.muteFor == 0, preview = r.result.showPreview)
                }
            }
        }
    }

    override fun setScopeNotifications(kind: com.abtin.tglass.data.NotifyScope, value: com.abtin.tglass.data.ScopeNotifications) {
        val prev = rawScopeSettings[kind] ?: return
        val settings = ScopeNotificationSettings(
            if (value.enabled) 0 else TelegramRepository.MUTE_FOREVER,
            prev.soundId,
            value.preview,
            prev.useDefaultMuteStories,
            prev.muteStories,
            prev.storySoundId,
            prev.showStoryPoster,
            prev.disablePinnedMessageNotifications,
            prev.disableMentionNotifications,
        )
        val previousUi = scopeStore[kind]
        rawScopeSettings[kind] = settings
        scopeStore[kind] = value
        scope.launch {
            val r = client.setScopeNotificationSettings(tdScope(kind), settings)
            if (r is TdlResult.Failure) {
                rawScopeSettings[kind] = prev
                if (previousUi != null) scopeStore[kind] = previousUi
                _errors.tryEmit(humanize(r.message))
            }
        }
    }

    override fun openSupportChat(onResult: (Long?) -> Unit) {
        scope.launch {
            val u = client.getSupportUser()
            if (u !is TdlResult.Success) {
                u.orReport()
                onResult(null)
                return@launch
            }
            onUser(u.result)
            val c = client.createPrivateChat(u.result.id, false)
            if (c is TdlResult.Success) onResult(c.result.id)
            else {
                c.orReport()
                onResult(null)
            }
        }
    }

    override fun loadMyLink(onResult: (String?) -> Unit) {
        val username = me.username
        if (username != null) {
            onResult("https://t.me/$username")
            return
        }
        scope.launch {
            val r = client.getUserLink()
            onResult(if (r is TdlResult.Success) r.result.url else null)
        }
    }
    // ---- end Settings (real) ----

    // =====================================================================================
    // ---- Profile ----
    // Profile page: full user info (birthday, profile music, personal channel, business, emoji status,
    // profile color), profile photos, received gifts, shared-media counts, music, groups in common,
    // adding / renaming contacts.
    // State holders are lazy because this class body runs start() from init before later properties exist.
    // =====================================================================================

    private val profileDetailsStore by lazy { mutableStateMapOf<Long, com.abtin.tglass.data.ProfileDetails>() }
    private val profilePhotoStore by lazy { mutableStateMapOf<Long, List<com.abtin.tglass.data.ProfilePhotoItem>>() }
    private val profileGiftStore by lazy { mutableStateMapOf<Long, List<com.abtin.tglass.data.ProfileGift>>() }
    private val sharedCountStore by lazy { mutableStateMapOf<String, Int>() }
    private val sharedMusicStore by lazy { mutableStateMapOf<Long, List<UiMessage>>() }
    private val commonGroupStore by lazy { mutableStateMapOf<Long, List<Long>>() }

    override fun profileDetails(chatId: Long): com.abtin.tglass.data.ProfileDetails? = profileDetailsStore[chatId]

    /** The sticker of a custom-emoji (or collectible gift) emoji status. */
    private suspend fun emojiStatusSticker(status: EmojiStatus?): StickerItem? {
        val id = when (val t = status?.type) {
            is EmojiStatusTypeCustomEmoji -> t.customEmojiId
            is EmojiStatusTypeUpgradedGift -> t.modelCustomEmojiId
            else -> 0L
        }
        if (id == 0L) return null
        val r = client.getCustomEmojiStickers(longArrayOf(id))
        return if (r is TdlResult.Success) r.result.stickers.firstOrNull()?.let { stickerItem(it) } else null
    }

    private fun profileBirthdayText(b: Birthdate): String {
        val month = java.time.Month.of(b.month.coerceIn(1, 12)).getDisplayName(java.time.format.TextStyle.SHORT, Locale.US)
        return if (b.year > 0) "${b.day} $month ${b.year}" else "${b.day} $month"
    }

    private fun profileBirthdayAge(b: Birthdate): Int? {
        if (b.year <= 0) return null
        return runCatching {
            java.time.Period.between(java.time.LocalDate.of(b.year, b.month, b.day), java.time.LocalDate.now()).years
        }.getOrNull()
    }

    private fun profileInHours(seconds: Int): String = when {
        seconds < 3600 -> "${(seconds / 60).coerceAtLeast(1)} min"
        seconds < 86_400 -> "${seconds / 3600} h"
        else -> "${seconds / 86_400} d"
    }

    override fun loadProfileDetails(chatId: Long) {
        val st = chatStates[chatId] ?: return
        scope.launch {
            val t = st.type
            if (t is ChatTypePrivate) {
                val full = client.getUserFullInfo(t.userId)
                if (full !is TdlResult.Success) return@launch
                val f = full.result
                val raw = rawUsers[t.userId]
                if (f.personalChatId != 0L) ensureChat(f.personalChatId)
                val business = f.businessInfo
                val hours = if (business == null || business.openingHours == null) null else when {
                    business.nextCloseIn > 0 -> "Open now · closes in ${profileInHours(business.nextCloseIn)}"
                    business.nextOpenIn > 0 -> "Closed · opens in ${profileInHours(business.nextOpenIn)}"
                    else -> null
                }
                profileDetailsStore[chatId] = com.abtin.tglass.data.ProfileDetails(
                    birthday = f.birthdate?.let { profileBirthdayText(it) },
                    age = f.birthdate?.let { profileBirthdayAge(it) },
                    music = f.firstProfileAudio?.let { a ->
                        com.abtin.tglass.data.ProfileMusic(
                            title = a.title.ifBlank { a.fileName.ifBlank { "Audio" } },
                            performer = a.performer,
                            duration = a.duration,
                            file = imageOf(a.audio, null),
                            cover = a.albumCoverThumbnail?.let { imageOf(it.file, a.albumCoverMinithumbnail) },
                        )
                    },
                    personalChatId = f.personalChatId,
                    businessAddress = business?.location?.address?.ifBlank { null },
                    businessHours = hours,
                    giftCount = f.giftCount,
                    commonGroupCount = f.groupInCommonCount,
                    canCall = f.canBeCalled,
                    videoCalls = f.supportsVideoCalls,
                    emojiStatus = emojiStatusSticker(raw?.emojiStatus),
                    profileColorId = raw?.profileAccentColorId ?: -1,
                    botDescription = f.botInfo?.shortDescription?.ifBlank { null },
                )
            } else {
                val r = client.getChat(chatId)
                if (r !is TdlResult.Success) return@launch
                profileDetailsStore[chatId] = com.abtin.tglass.data.ProfileDetails(
                    emojiStatus = emojiStatusSticker(r.result.emojiStatus),
                    profileColorId = r.result.profileAccentColorId,
                )
            }
        }
    }

    private fun profilePhotoItem(p: ChatPhoto): com.abtin.tglass.data.ProfilePhotoItem {
        val big = p.sizes.maxByOrNull { it.width }
        val small = p.sizes.minByOrNull { it.width }
        return com.abtin.tglass.data.ProfilePhotoItem(
            id = p.id,
            image = big?.let { imageOf(it.photo, p.minithumbnail, it.width, it.height) },
            thumb = small?.let { imageOf(it.photo, p.minithumbnail, it.width, it.height) },
            date = p.addedDate.toLong(),
        )
    }

    /** Earlier photos of a group / channel, from its "changed the photo" service messages. */
    private suspend fun chatPhotoHistory(chatId: Long): List<ChatPhoto> {
        val r = client.searchChatMessages(chatId = chatId, query = "", fromMessageId = 0, offset = 0, limit = 50, filter = SearchMessagesFilterChatPhoto())
        return if (r is TdlResult.Success) r.result.messages.mapNotNull { (it.content as? MessageChatChangePhoto)?.photo } else emptyList()
    }

    override fun profilePhotos(chatId: Long): List<com.abtin.tglass.data.ProfilePhotoItem> =
        profilePhotoStore[chatId] ?: super.profilePhotos(chatId)

    override fun loadProfilePhotos(chatId: Long) {
        val st = chatStates[chatId] ?: return
        scope.launch {
            val photos: List<ChatPhoto> = when (val t = st.type) {
                is ChatTypePrivate -> {
                    val r = client.getUserProfilePhotos(t.userId, 0, 100)
                    if (r is TdlResult.Success) r.result.photos.toList() else return@launch
                }
                is ChatTypeBasicGroup -> {
                    val r = client.getBasicGroupFullInfo(t.basicGroupId)
                    listOfNotNull(if (r is TdlResult.Success) r.result.photo else null) + chatPhotoHistory(chatId)
                }
                is ChatTypeSupergroup -> {
                    val r = client.getSupergroupFullInfo(t.supergroupId)
                    listOfNotNull(if (r is TdlResult.Success) r.result.photo else null) + chatPhotoHistory(chatId)
                }
                else -> return@launch
            }
            profilePhotoStore[chatId] = photos.distinctBy { it.id }.map { profilePhotoItem(it) }
        }
    }

    private fun profileShortCount(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0).replace(".0K", "K")
        else -> n.toString()
    }

    private fun profileGift(g: ReceivedGift): com.abtin.tglass.data.ProfileGift? {
        val sender = when (val s = g.senderId) {
            is MessageSenderUser -> userMap[s.userId]?.name
            is MessageSenderChat -> chatMap[s.chatId]?.title
            else -> null
        }
        return when (val sent = g.gift) {
            is SentGiftRegular -> {
                val gift = sent.gift
                com.abtin.tglass.data.ProfileGift(
                    id = g.receivedGiftId.ifBlank { "g${gift.id}:${g.date}" },
                    sticker = stickerItem(gift.sticker),
                    emoji = gift.sticker.emoji.ifBlank { "🎁" },
                    stars = gift.starCount,
                    senderName = sender,
                    message = g.text.text.ifBlank { null },
                    date = g.date.toLong(),
                    centerColor = gift.background.centerColor,
                    edgeColor = gift.background.edgeColor,
                    pinned = g.isPinned,
                    ribbon = gift.overallLimits?.let { "1 of ${profileShortCount(it.totalCount)}" },
                )
            }
            is SentGiftUpgraded -> {
                val u = sent.gift
                com.abtin.tglass.data.ProfileGift(
                    id = g.receivedGiftId.ifBlank { "u${u.id}" },
                    sticker = stickerItem(u.model.sticker),
                    emoji = u.model.sticker.emoji.ifBlank { "🎁" },
                    title = "${u.title} #${u.number}",
                    senderName = sender,
                    message = g.text.text.ifBlank { null },
                    date = g.date.toLong(),
                    centerColor = u.backdrop.colors.centerColor,
                    edgeColor = u.backdrop.colors.edgeColor,
                    pinned = g.isPinned,
                    ribbon = "1 of ${profileShortCount(maxOf(u.maxUpgradedCount, u.totalUpgradedCount))}",
                )
            }
            else -> null
        }
    }

    // No Gifts tab while live gift loading is off (see GIFTS_ENABLED).
    override fun profileGifts(chatId: Long): List<com.abtin.tglass.data.ProfileGift> =
        if (GIFTS_ENABLED) profileGiftStore[chatId] ?: emptyList() else emptyList()

    override fun loadProfileGifts(chatId: Long) {
        // Disabled: tdl-coroutines 13.0.0 treats Gift.background as required, but the server sends gifts without it.
        // The deserializer then throws on TDLib's receiver thread and the whole app crashes (reported from a device:
        // "Key background is missing in the map"). Re-enable once the library handles the optional field.
        if (GIFTS_ENABLED.not()) return
        val st = chatStates[chatId] ?: return
        val owner: MessageSender = when (val t = st.type) {
            is ChatTypePrivate -> MessageSenderUser(t.userId)
            is ChatTypeSupergroup -> if (t.isChannel) MessageSenderChat(chatId) else return
            else -> return
        }
        scope.launch {
            val r = client.getReceivedGifts(
                businessConnectionId = "",
                ownerId = owner,
                collectionId = 0,
                excludeUnsaved = false,
                excludeSaved = false,
                excludeUnlimited = false,
                excludeUpgradable = false,
                excludeNonUpgradable = false,
                excludeUpgraded = false,
                excludeWithoutColors = false,
                excludeHosted = false,
                sortByPrice = false,
                offset = "",
                limit = 60,
            )
            if (r is TdlResult.Success) {
                val list = r.result.gifts.mapNotNull { profileGift(it) }
                // Pinned gifts first, like Telegram.
                profileGiftStore[chatId] = list.filter { it.pinned } + list.filter { !it.pinned }
            }
        }
    }

    private fun profileSharedFilter(kind: com.abtin.tglass.data.SharedKind): SearchMessagesFilter = when (kind) {
        com.abtin.tglass.data.SharedKind.Media -> SearchMessagesFilterPhotoAndVideo()
        com.abtin.tglass.data.SharedKind.Files -> SearchMessagesFilterDocument()
        com.abtin.tglass.data.SharedKind.Links -> SearchMessagesFilterUrl()
        com.abtin.tglass.data.SharedKind.Music -> SearchMessagesFilterAudio()
        com.abtin.tglass.data.SharedKind.Voice -> SearchMessagesFilterVoiceAndVideoNote()
        com.abtin.tglass.data.SharedKind.Gifs -> SearchMessagesFilterAnimation()
    }

    override fun sharedCount(chatId: Long, kind: com.abtin.tglass.data.SharedKind): Int? = sharedCountStore["$chatId:$kind"]

    override fun loadSharedCounts(chatId: Long) {
        scope.launch {
            for (kind in com.abtin.tglass.data.SharedKind.entries) {
                val r = client.getChatMessageCount(chatId = chatId, filter = profileSharedFilter(kind), returnLocal = false)
                if (r is TdlResult.Success) sharedCountStore["$chatId:$kind"] = r.result.count
            }
        }
    }

    override fun sharedMusic(chatId: Long): List<UiMessage> = sharedMusicStore[chatId] ?: emptyList()

    override fun loadSharedMusic(chatId: Long) {
        scope.launch {
            val r = client.searchChatMessages(chatId = chatId, query = "", fromMessageId = 0, offset = 0, limit = 90, filter = SearchMessagesFilterAudio())
            if (r is TdlResult.Success) sharedMusicStore[chatId] = r.result.messages.map { mapMessage(it) }
        }
    }

    override fun commonGroups(userId: Long): List<Long> = commonGroupStore[userId] ?: emptyList()

    override fun loadCommonGroups(userId: Long) {
        scope.launch {
            val r = client.getGroupsInCommon(userId, 0, 100)
            if (r is TdlResult.Success) {
                r.result.chatIds.forEach { ensureChat(it) }
                commonGroupStore[userId] = r.result.chatIds.toList()
            }
        }
    }

    override fun isContact(userId: Long): Boolean {
        // Reading userMap subscribes the caller to user updates (isContact changes arrive with updateUser).
        userMap[userId]
        return userId in contactIds || rawUsers[userId]?.isContact == true
    }

    override fun saveContact(userId: Long, firstName: String, lastName: String, sharePhone: Boolean, onDone: (String?) -> Unit) {
        scope.launch {
            // The phone number may stay empty: TDLib keeps the known one (or adds the user by id).
            val r = client.addContact(userId, ImportedContact("", firstName.trim(), lastName.trim(), null), sharePhone)
            if (r is TdlResult.Failure) {
                onDone(humanize(r.message))
                return@launch
            }
            if (userId !in contactIds) contactIds.add(userId)
            client.getUser(userId).let { u -> if (u is TdlResult.Success) onUser(u.result) }
            onDone(null)
        }
    }
    // ---- end Profile ----

    // ---- Composer ----
    /** User id of the @gif inline bot, resolved once. */
    private var gifBotId = 0L

    override fun searchGifs(chatId: Long, query: String, onResult: (List<GifItem>) -> Unit) {
        scope.launch {
            if (gifBotId == 0L) {
                val bot = client.searchPublicChat("gif")
                if (bot is TdlResult.Success) {
                    val type = bot.result.type
                    if (type is ChatTypePrivate) gifBotId = type.userId
                }
            }
            val botId = gifBotId
            if (botId == 0L) {
                onResult(emptyList())
                return@launch
            }
            val r = client.getInlineQueryResults(botUserId = botId, chatId = chatId, userLocation = null, query = query, offset = "")
            if (r !is TdlResult.Success) {
                onResult(emptyList())
                return@launch
            }
            onResult(r.result.results.mapNotNull { res ->
                val a = (res as? InlineQueryResultAnimation)?.animation ?: return@mapNotNull null
                val still = a.thumbnail?.takeIf { it.format is ThumbnailFormatJpeg || it.format is ThumbnailFormatPng || it.format is ThumbnailFormatWebp }
                GifItem(a.animation.id, still?.let { imageOf(it.file, a.minithumbnail, it.width, it.height) }, a.width, a.height, a.duration)
            })
        }
    }
    // ---- end Composer ----

    // ---- Message menu ----
    override fun loadAvailableReactions(chatId: Long, messageId: Long, onResult: (com.abtin.tglass.data.AvailableReactionsInfo?) -> Unit) {
        scope.launch {
            when (val r = client.getMessageAvailableReactions(chatId, messageId, 8)) {
                is TdlResult.Failure -> onResult(null)
                is TdlResult.Success -> {
                    val res = r.result
                    val premium = me.premium
                    // Custom-emoji and paid reactions can't be drawn here; premium-only ones need Premium.
                    fun emojis(list: Array<AvailableReaction>): List<String> = list.mapNotNull { a ->
                        if (a.needsPremium && !premium) null else (a.type as? ReactionTypeEmoji)?.emoji
                    }
                    val top = emojis(res.topReactions)
                    val all = (top + emojis(res.recentReactions) + emojis(res.popularReactions)).distinct()
                    onResult(
                        com.abtin.tglass.data.AvailableReactionsInfo(
                            top = top.ifEmpty { all },
                            all = all,
                            available = all.isNotEmpty() && res.unavailabilityReason == null,
                        )
                    )
                }
            }
        }
    }

    override fun loadMessageCaps(chatId: Long, messageId: Long, onResult: (com.abtin.tglass.data.MessageCaps?) -> Unit) {
        scope.launch {
            when (val r = client.getMessageProperties(chatId, messageId)) {
                is TdlResult.Failure -> onResult(null)
                is TdlResult.Success -> {
                    val p = r.result
                    onResult(
                        com.abtin.tglass.data.MessageCaps(
                            canReply = p.canBeReplied,
                            canEdit = p.canBeEdited,
                            canForward = p.canBeForwarded,
                            canPin = p.canBePinned,
                            canSave = p.canBeSaved,
                            canGetLink = p.canGetLink,
                            canReport = p.canReportChat,
                            canDeleteForSelf = p.canBeDeletedOnlyForSelf,
                            canDeleteForAll = p.canBeDeletedForAllUsers,
                        )
                    )
                }
            }
        }
    }

    override fun loadMessageLink(chatId: Long, messageId: Long, onResult: (String?) -> Unit) {
        scope.launch {
            val r = client.getMessageLink(chatId, messageId, 0, 0, "", false, false)
            onResult(if (r is TdlResult.Success) r.result.link else null)
        }
    }

    override fun reportMessages(chatId: Long, messageIds: List<Long>, optionId: ByteArray?, onResult: (com.abtin.tglass.data.ReportStep) -> Unit) {
        scope.launch {
            var option = optionId ?: ByteArray(0)
            repeat(3) {
                when (val r = client.reportChat(chatId, option, messageIds.toLongArray(), "")) {
                    is TdlResult.Failure -> {
                        onResult(com.abtin.tglass.data.ReportStep.Failed(humanize(r.message)))
                        return@launch
                    }
                    is TdlResult.Success -> when (val res = r.result) {
                        is ReportChatResultOk -> {
                            onResult(com.abtin.tglass.data.ReportStep.Done)
                            return@launch
                        }
                        is ReportChatResultOptionRequired -> {
                            onResult(
                                com.abtin.tglass.data.ReportStep.Options(
                                    res.title.ifBlank { "Report" },
                                    res.options.map { com.abtin.tglass.data.ReportChoice(it.id, it.text) },
                                )
                            )
                            return@launch
                        }
                        is ReportChatResultTextRequired -> {
                            if (!res.isOptional) {
                                onResult(com.abtin.tglass.data.ReportStep.Failed("Telegram needs more details for this report."))
                                return@launch
                            }
                            // Details are optional: send the report without them.
                            option = res.optionId
                        }
                        else -> {
                            onResult(com.abtin.tglass.data.ReportStep.Failed("These messages can't be reported from here."))
                            return@launch
                        }
                    }
                }
            }
            onResult(com.abtin.tglass.data.ReportStep.Failed("These messages can't be reported from here."))
        }
    }
    // ---- end Message menu ----

    // ---- Chat bubbles & links ----
    override fun resolveLink(url: String, onResult: (com.abtin.tglass.data.ResolvedLink) -> Unit) {
        val full = if (url.startsWith("http", true) || url.startsWith("tg:", true)) url else "https://$url"
        scope.launch {
            val type = client.getInternalLinkType(full)
            if (type !is TdlResult.Success) {
                onResult(com.abtin.tglass.data.ResolvedLink(external = true))
                return@launch
            }
            onResult(resolveInternalLink(type.result))
        }
    }

    private suspend fun resolveInternalLink(t: InternalLinkType): com.abtin.tglass.data.ResolvedLink {
        fun fail(msg: String) = com.abtin.tglass.data.ResolvedLink(error = msg)
        suspend fun byUsername(name: String): com.abtin.tglass.data.ResolvedLink {
            val r = client.searchPublicChat(name)
            return if (r is TdlResult.Success) com.abtin.tglass.data.ResolvedLink(chatId = r.result.id) else fail("No one uses @$name")
        }
        return when (t) {
            is InternalLinkTypePublicChat -> byUsername(t.chatUsername)
            is InternalLinkTypeBotStart -> byUsername(t.botUsername)
            is InternalLinkTypeBotStartInGroup -> byUsername(t.botUsername)
            is InternalLinkTypeSavedMessages -> com.abtin.tglass.data.ResolvedLink(chatId = myId)
            is InternalLinkTypeMessage -> {
                val r = client.getMessageLinkInfo(t.url)
                when {
                    r !is TdlResult.Success -> fail("This message isn't available")
                    r.result.chatId == 0L -> fail("This message isn't available")
                    else -> com.abtin.tglass.data.ResolvedLink(chatId = r.result.chatId, messageId = r.result.message?.id)
                }
            }
            is InternalLinkTypeChatInvite -> {
                val r = client.checkChatInviteLink(t.inviteLink)
                if (r !is TdlResult.Success) return fail("This invite link is invalid or expired")
                val info = r.result
                // Already a member (or the chat can be previewed): just open it.
                if (info.chatId != 0L && (info.accessibleFor > 0 || chatMap[info.chatId]?.joined == true)) {
                    com.abtin.tglass.data.ResolvedLink(chatId = info.chatId)
                } else {
                    com.abtin.tglass.data.ResolvedLink(
                        invite = com.abtin.tglass.data.InviteInfo(
                            link = t.inviteLink,
                            title = info.title,
                            memberCount = info.memberCount,
                            channel = info.type is InviteLinkChatTypeChannel,
                            requestNeeded = info.createsJoinRequest,
                        ),
                    )
                }
            }
            is InternalLinkTypeUserPhoneNumber -> {
                val u = client.searchUserByPhoneNumber(t.phoneNumber, false)
                if (u !is TdlResult.Success) return fail("No Telegram account with this number")
                val c = client.createPrivateChat(u.result.id, false)
                if (c is TdlResult.Success) com.abtin.tglass.data.ResolvedLink(chatId = c.result.id) else fail("Can't open this chat")
            }
            else -> com.abtin.tglass.data.ResolvedLink(external = true)
        }
    }

    override fun joinByInviteLink(link: String, onDone: (chatId: Long?, error: String?) -> Unit) {
        scope.launch {
            when (val r = client.joinChatByInviteLink(link)) {
                is TdlResult.Success -> when (val res = r.result) {
                    is ChatJoinResultSuccess -> onDone(res.chatId, null)
                    is ChatJoinResultRequestSent -> onDone(null, "Request to join sent")
                    else -> onDone(null, "Can't join this chat")
                }
                is TdlResult.Failure -> onDone(null, humanize(r.message))
            }
        }
    }

    override fun loadDiscussionChat(chatId: Long, onResult: (Long?) -> Unit) {
        val t = chatStates[chatId]?.type as? ChatTypeSupergroup ?: return onResult(null)
        if (!t.isChannel) return onResult(null)
        scope.launch {
            val r = client.getSupergroupFullInfo(t.supergroupId)
            val linked = (r as? TdlResult.Success)?.result?.linkedChatId?.takeIf { it != 0L }
            if (linked != null && chatMap[linked] == null) client.getChat(linked)
            onResult(linked)
        }
    }
    // ---- end Chat bubbles & links ----

    // ---- Edit Profile & Appearance ----
    private var profileBirthdate by mutableStateOf<com.abtin.tglass.data.ProfileBirthdate?>(null)
    private var profileChannel by mutableStateOf<com.abtin.tglass.data.PersonalChannel?>(null)
    private var profileNameColor by mutableStateOf(-1)

    override val myBirthdate: com.abtin.tglass.data.ProfileBirthdate? get() = profileBirthdate
    override val myPersonalChannel: com.abtin.tglass.data.PersonalChannel? get() = profileChannel
    override val myNameColorId: Int get() = if (profileNameColor >= 0) profileNameColor else (rawUsers[myId]?.accentColorId ?: 5)

    private suspend fun channelTitle(chatId: Long): com.abtin.tglass.data.PersonalChannel? {
        val r = client.getChat(chatId)
        if (r !is TdlResult.Success) return null
        return com.abtin.tglass.data.PersonalChannel(chatId, r.result.title)
    }

    override fun loadProfileExtras() {
        scope.launch {
            client.getMe().let { if (it is TdlResult.Success) { onUser(it.result); profileNameColor = it.result.accentColorId } }
            val full = client.getUserFullInfo(myId)
            if (full is TdlResult.Success) {
                profileBirthdate = full.result.birthdate?.let { com.abtin.tglass.data.ProfileBirthdate(it.day, it.month, it.year) }
                val pc = full.result.personalChatId
                profileChannel = if (pc != 0L) channelTitle(pc) else null
            }
        }
    }

    override fun setBirthdate(value: com.abtin.tglass.data.ProfileBirthdate?, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.setBirthdate(value?.let { Birthdate(it.day, it.month, it.year) })
            if (r is TdlResult.Success) profileBirthdate = value
            onDone(if (r is TdlResult.Failure) humanize(r.message) else null)
        }
    }

    override fun loadPersonalChannelCandidates(onResult: (List<com.abtin.tglass.data.PersonalChannel>) -> Unit) {
        scope.launch {
            val r = client.getSuitablePersonalChats()
            if (r !is TdlResult.Success) { r.orReport(); onResult(emptyList()); return@launch }
            onResult(r.result.chatIds.toList().mapNotNull { channelTitle(it) })
        }
    }

    override fun setPersonalChannel(chatId: Long?, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.setPersonalChat(chatId ?: 0L)
            if (r is TdlResult.Success) profileChannel = chatId?.let { channelTitle(it) }
            onDone(if (r is TdlResult.Failure) humanize(r.message) else null)
        }
    }

    override fun setNameColor(colorId: Int, onDone: (String?) -> Unit) {
        scope.launch {
            // Keep a Premium user's background emoji.
            val emoji = rawUsers[myId]?.backgroundCustomEmojiId ?: 0L
            val r = client.setAccentColor(colorId, emoji)
            if (r is TdlResult.Success) { profileNameColor = colorId; refreshMe() }
            onDone(if (r is TdlResult.Failure) humanize(r.message) else null)
        }
    }

    override fun checkUsername(username: String, onResult: (com.abtin.tglass.data.UsernameCheck) -> Unit) {
        com.abtin.tglass.data.localUsernameCheck(username)?.let { onResult(it); return }
        if (username.equals(me.username, true)) { onResult(com.abtin.tglass.data.UsernameCheck.Available); return }
        scope.launch {
            val r = client.checkChatUsername(myId, username)
            onResult(
                if (r !is TdlResult.Success) com.abtin.tglass.data.UsernameCheck.Error
                else when (r.result) {
                    is CheckChatUsernameResultOk -> com.abtin.tglass.data.UsernameCheck.Available
                    is CheckChatUsernameResultUsernameOccupied -> com.abtin.tglass.data.UsernameCheck.Taken
                    is CheckChatUsernameResultUsernamePurchasable -> com.abtin.tglass.data.UsernameCheck.Purchasable
                    is CheckChatUsernameResultUsernameInvalid -> com.abtin.tglass.data.UsernameCheck.Invalid
                    is CheckChatUsernameResultPublicChatsTooMany -> com.abtin.tglass.data.UsernameCheck.TooMany
                    else -> com.abtin.tglass.data.UsernameCheck.Error
                }
            )
        }
    }
    // ---- end Edit Profile & Appearance ----

    // ---- Chat polls, audio & search ----

    private fun pollSenderId(s: MessageSender): Long? = when (s) {
        is MessageSenderUser -> s.userId
        is MessageSenderChat -> s.chatId
        else -> null
    }

    /** TDLib poll -> UI poll with votes, the user's answers, quiz answer/explanation and closed state. */
    private fun mapPollContent(c: MessagePoll): UiContent.Poll {
        val poll = c.poll
        val options = poll.options
        val chosen = options.indices.filter { options[it].isChosen }
        val quiz = poll.type as? PollTypeQuiz
        return UiContent.Poll(
            question = poll.question.text,
            options = options.map { it.text.text },
            votes = options.map { it.voterCount },
            voted = chosen.firstOrNull(),
            quiz = quiz != null,
            anonymous = poll.isAnonymous,
            multiple = poll.allowsMultipleAnswers,
            correctOption = quiz?.correctOptionIds?.firstOrNull(),
            explanation = quiz?.explanation?.text?.takeIf { it.isNotBlank() },
            chosen = chosen,
            closed = poll.isClosed,
            totalVoters = poll.totalVoterCount,
            percents = options.map { it.votePercentage },
            canGetVoters = poll.canGetVoters,
            canSeeResults = poll.canSeeResults,
            recentVoters = poll.recentVoterIds.mapNotNull { pollSenderId(it) },
            canRetract = quiz == null,
        )
    }

    override fun votePoll(chatId: Long, messageId: Long, optionIds: List<Int>) {
        if (optionIds.isEmpty()) return
        scope.launch { client.setPollAnswer(chatId, messageId, optionIds.distinct().sorted().toIntArray()).orReport() }
    }

    override fun retractPollVote(chatId: Long, messageId: Long) {
        scope.launch { client.setPollAnswer(chatId, messageId, IntArray(0)).orReport() }
    }

    override fun stopPoll(chatId: Long, messageId: Long, onDone: (String?) -> Unit) {
        scope.launch {
            val r = client.stopPoll(chatId = chatId, messageId = messageId)
            onDone(if (r is TdlResult.Failure) humanize(r.message) else null)
        }
    }

    override fun pollVoters(chatId: Long, messageId: Long, option: Int, offset: Int, onResult: (com.abtin.tglass.data.PollVotersPage?) -> Unit) {
        scope.launch {
            val r = client.getPollVoters(chatId, messageId, option, offset, 50)
            onResult(
                if (r is TdlResult.Success) com.abtin.tglass.data.PollVotersPage(r.result.voters.mapNotNull { pollSenderId(it.voterId) }, r.result.totalCount)
                else null
            )
        }
    }

    override fun searchChatPage(chatId: Long, query: String, fromMessageId: Long, onResult: (com.abtin.tglass.data.ChatSearchPage) -> Unit) {
        scope.launch {
            val r = client.searchChatMessages(chatId = chatId, query = query, fromMessageId = fromMessageId, offset = 0, limit = 100)
            if (r is TdlResult.Success) {
                val found = r.result.messages.map { mapMessage(it) }
                // Keep the found messages at hand (findMessage) even before the history around them is loaded.
                found.forEach { messageCache["$chatId:${it.id}"] = it }
                val total = if (r.result.totalCount >= 0) r.result.totalCount else found.size
                onResult(com.abtin.tglass.data.ChatSearchPage(found, maxOf(total, found.size), r.result.nextFromMessageId))
            } else {
                android.util.Log.w("TGlass", "searchChatMessages failed: " + (r as TdlResult.Failure).message)
                onResult(com.abtin.tglass.data.ChatSearchPage(emptyList(), 0, 0L))
            }
        }
    }

    override fun loadAroundMessage(chatId: Long, messageId: Long, onLoaded: (Boolean) -> Unit) {
        if (messages(chatId).any { it.id == messageId }) return onLoaded(true)
        scope.launch {
            messageStore.getOrPut(chatId) { mutableStateListOf() }
            val r = client.getChatHistory(chatId, messageId, -25, 50, false)
            // Remembers where this piece doesn't touch the loaded history (see historyGaps).
            if (r is TdlResult.Success) addHistoryPiece(chatId, r.result.messages.filterNotNull())
            if (messages(chatId).none { it.id == messageId }) {
                // The history call may answer with only what it has cached: fetch the message itself.
                val one = client.getMessage(chatId, messageId)
                if (one is TdlResult.Success) addHistoryPiece(chatId, listOf(one.result))
            }
            onLoaded(messages(chatId).any { it.id == messageId })
        }
    }
    // ---- end Chat polls, audio & search ----

    // ---- History gaps & profile posts ----

    /** Per chat: loaded messages right after which newer history is missing (see [historyGaps]). */
    private val historyGapMap by lazy { mutableStateMapOf<Long, Set<Long>>() }
    private val gapLoading by lazy { HashSet<Long>() }

    override fun historyGaps(chatId: Long): Set<Long> = historyGapMap[chatId] ?: emptySet()

    /**
     * Adds a contiguous piece of history (continuing the loaded message [from], if given) and updates the gap
     * markers: gaps inside the piece close, and a gap opens on each side where it doesn't touch loaded messages.
     */
    private fun addHistoryPiece(chatId: Long, piece: List<Message>, from: Long? = null) {
        val bounds = piece.map { it.id } + listOfNotNull(from)
        if (bounds.isEmpty()) return
        val list = messageStore.getOrPut(chatId) { mutableStateListOf() }
        val loaded = list.mapTo(HashSet()) { it.id }
        val lo = bounds.min()
        val hi = bounds.max()
        piece.forEach { addMessage(it) }
        val gaps = historyGaps(chatId).filterTo(HashSet()) { it < lo || it >= hi }
        if (lo !in loaded) list.lastOrNull { it.id < lo }?.let { gaps += it.id }
        if (hi !in loaded) {
            val next = list.firstOrNull { it.id > hi }
            val newest = chatStates[chatId]?.lastMessage?.id
            if (next != null || (newest != null && newest > hi)) gaps += hi
        }
        historyGapMap[chatId] = gaps
    }

    override fun loadNewerMessages(chatId: Long, afterMessageId: Long) {
        if (afterMessageId !in historyGaps(chatId) || !gapLoading.add(chatId)) return
        scope.launch {
            try {
                var piece = emptyList<Message>()
                var ok = false
                // TDLib may first answer with only what it has cached (just the message itself): ask again.
                for (attempt in 0 until 3) {
                    val r = client.getChatHistory(chatId, afterMessageId, -49, 50, false)
                    if (r !is TdlResult.Success) break
                    ok = true
                    piece = r.result.messages.filterNotNull()
                    if (piece.any { it.id > afterMessageId }) break
                }
                onNewerPiece(chatId, afterMessageId, piece, ok)
            } finally {
                gapLoading -= chatId
            }
        }
    }

    private fun onNewerPiece(chatId: Long, afterMessageId: Long, piece: List<Message>, ok: Boolean) {
        val newest = chatStates[chatId]?.lastMessage?.id
        if (piece.any { it.id > afterMessageId }) {
            addHistoryPiece(chatId, piece, from = afterMessageId)
        } else if (ok && (newest == null || newest <= afterMessageId)) {
            // Nothing newer exists: the gap was only apparent.
            historyGapMap[chatId] = historyGaps(chatId) - afterMessageId
        }
    }

    override fun loadLatestMessages(chatId: Long, onLoaded: () -> Unit) {
        val newest = chatStates[chatId]?.lastMessage?.id
        if (newest == null || messages(chatId).any { it.id == newest }) return onLoaded()
        scope.launch {
            val r = client.getChatHistory(chatId, 0, 0, 50, false)
            if (r is TdlResult.Success) addHistoryPiece(chatId, r.result.messages.filterNotNull())
            onLoaded()
        }
    }

    /** Posts tab: stories posted to the chat's page (pinned first) plus its active stories. */
    private val profileStoryStore by lazy { mutableStateMapOf<Long, List<UiStory>>() }

    override fun profileStories(chatId: Long): List<UiStory> = profileStoryStore[chatId] ?: emptyList()

    override fun loadProfileStories(chatId: Long) {
        val chat = chat(chatId) ?: return
        if (chat.type == UiChatType.Saved) return
        scope.launch {
            val found = LinkedHashMap<Int, UiStory>()
            val pinnedOrder = ArrayList<Int>()
            var from = 0
            for (page in 0 until 4) {
                val r = client.getChatPostedToChatPageStories(chatId, from, 50)
                if (r !is TdlResult.Success) break
                r.result.pinnedStoryIds.forEach { if (it !in pinnedOrder) pinnedOrder += it }
                val fresh = r.result.stories.filter { it.id !in found }
                fresh.forEach { found[it.id] = mapStory(it) }
                if (fresh.isEmpty() || found.size >= r.result.totalCount) break
                from = r.result.stories.minOf { it.id }
            }
            // Active stories that are not on the page (yet).
            val active = client.getChatActiveStories(chatId)
            if (active is TdlResult.Success) {
                for (info in active.result.stories) {
                    if (info.storyId in found) continue
                    val cached = storyCache[storyKey(chatId, info.storyId)]?.takeIf { it.loaded }
                    if (cached != null) {
                        found[info.storyId] = cached
                        continue
                    }
                    val s = client.getStory(chatId, info.storyId, false)
                    if (s is TdlResult.Success) found[info.storyId] = mapStory(s.result)
                }
            }
            val all = found.values.map { it.copy(pinned = it.id in pinnedOrder) }
            profileStoryStore[chatId] = all.filter { it.pinned }.sortedBy { pinnedOrder.indexOf(it.id) } +
                all.filter { !it.pinned }.sortedByDescending { it.id }
        }
    }
    // ---- end History gaps & profile posts ----

    // ---- Custom emoji ----
    private val customEmojiStore by lazy { mutableStateMapOf<Long, StickerItem>() }
    /** Ids asked for and not answered yet (never asked twice at the same time). */
    private val customEmojiPending = HashSet<Long>()
    private val customEmojiQueue = LinkedHashSet<Long>()
    private var customEmojiFlush: Job? = null

    override fun customEmoji(id: Long): StickerItem? = customEmojiStore[id]

    override fun loadCustomEmoji(ids: Collection<Long>) {
        var added = false
        for (id in ids) {
            if (id != 0L && !customEmojiStore.containsKey(id) && customEmojiPending.add(id)) {
                customEmojiQueue.add(id)
                added = true
            }
        }
        if (!added || customEmojiFlush?.isActive == true) return
        customEmojiFlush = scope.launch {
            // Gather the ids of everything composed in this frame into one request (TDLib takes up to 200).
            delay(24)
            while (customEmojiQueue.isNotEmpty()) {
                val batch = customEmojiQueue.take(200)
                customEmojiQueue.removeAll(batch.toSet())
                val r = client.getCustomEmojiStickers(batch.toLongArray())
                if (r is TdlResult.Success) {
                    for (st in r.result.stickers) {
                        val id = (st.fullType as? StickerFullTypeCustomEmoji)?.customEmojiId ?: continue
                        customEmojiStore[id] = stickerItem(st)
                    }
                }
                // Unknown / failed ids may be asked again later (next time they're shown).
                batch.forEach { customEmojiPending.remove(it) }
            }
        }
    }

    override fun loadStickerSet(setId: Long, onResult: (com.abtin.tglass.data.StickerSetInfo?) -> Unit) {
        if (setId == 0L) return onResult(null)
        scope.launch {
            when (val r = client.getStickerSet(setId)) {
                is TdlResult.Failure -> onResult(null)
                is TdlResult.Success -> {
                    val set = r.result
                    val items = set.stickers.map { stickerItem(it) }
                    items.forEach { if (it.customEmojiId != 0L) customEmojiStore[it.customEmojiId] = it }
                    onResult(
                        com.abtin.tglass.data.StickerSetInfo(
                            id = set.id,
                            title = set.title,
                            name = set.name,
                            installed = set.isInstalled && !set.isArchived,
                            emoji = set.stickerType is StickerTypeCustomEmoji,
                            stickers = items,
                        )
                    )
                }
            }
        }
    }

    override fun setStickerSetInstalled(setId: Long, installed: Boolean, onDone: (String?) -> Unit) {
        scope.launch {
            when (val r = client.changeStickerSet(setId, installed, false)) {
                is TdlResult.Failure -> onDone(humanize(r.message))
                is TdlResult.Success -> {
                    // Keep the emoji panel's sticker tabs in step.
                    if (!installed) {
                        packs.removeAll { it.id == setId }
                    } else if (packs.none { it.id == setId }) {
                        val set = client.getStickerSet(setId)
                        if (set is TdlResult.Success && set.result.stickerType is StickerTypeRegular) {
                            packs.add(0, StickerPack(setId, set.result.title, set.result.stickers.map { stickerItem(it) }))
                        }
                    }
                    onDone(null)
                }
            }
        }
    }

    private suspend fun reloadSavedGifs() {
        val saved = client.getSavedAnimations()
        if (saved is TdlResult.Success) {
            gifs.clear()
            gifs.addAll(saved.result.animations.map { a ->
                GifItem(a.animation.id, a.thumbnail?.let { imageOf(it.file, a.minithumbnail, it.width, it.height) }, a.width, a.height, a.duration)
            })
        }
    }

    override fun saveGif(fileId: Int, onDone: (String?) -> Unit) {
        if (fileId <= 0) return onDone("This GIF can't be saved")
        scope.launch {
            when (val r = client.addSavedAnimation(InputFileId(fileId))) {
                is TdlResult.Failure -> onDone(humanize(r.message))
                is TdlResult.Success -> {
                    reloadSavedGifs()
                    onDone(null)
                }
            }
        }
    }

    override fun removeSavedGif(fileId: Int, onDone: (String?) -> Unit) {
        scope.launch {
            when (val r = client.removeSavedAnimation(InputFileId(fileId))) {
                is TdlResult.Failure -> onDone(humanize(r.message))
                is TdlResult.Success -> {
                    gifs.removeAll { it.fileId == fileId }
                    onDone(null)
                }
            }
        }
    }
    // ---- end Custom emoji ----

    // ---- Accounts & Settings v2 ----
    // Several accounts: one TDLib database per slot (see TdAccounts). Only the active slot runs; switching
    // closes the client, and when TDLib reports Closed, [onAccountClosed] picks the next slot before the usual
    // reset() + start(), so this same repository instance (and every screen bound to it) serves the new account.
    private var accountSwitchTo = -1
    private var accountDropSlot = -1

    /** Keeps the active slot's cached name / phone / photo fresh, for the account list of other sessions. */
    @Suppress("unused")
    private val accountCacheJob = scope.launch {
        try {
            TdAccounts.attach(app)
            androidx.compose.runtime.snapshotFlow {
                if (auth != AuthStep.Ready || myId == 0L) null
                else userMap[myId]?.let { u -> Triple(u, avatars[u.id]?.let { filePath(it) }, TdAccounts.active) }
            }.collect { v ->
                if (v != null) {
                    TdAccounts.cache(v.third, v.first.id, v.first.name, v.first.phone, v.second)
                    TdAccounts.signedIn()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("TGlass", "Account cache failed", e)
        }
    }

    override val accounts: List<com.abtin.tglass.data.AccountInfo>
        get() {
            TdAccounts.attach(app)
            // Read so the list recomposes when the cached info changes.
            if (TdAccounts.version < 0) return emptyList()
            val active = TdAccounts.active
            val adding = TdAccounts.adding
            return TdAccounts.signedInSlots().filter { !(adding && it == active) }.map { slot ->
                if (slot == active) {
                    val m = me
                    com.abtin.tglass.data.AccountInfo(slot, m.id, m.name.ifBlank { TdAccounts.name(slot) }, m.phone.ifBlank { TdAccounts.phone(slot) }, null, active = true)
                } else {
                    com.abtin.tglass.data.AccountInfo(slot, TdAccounts.userId(slot), TdAccounts.name(slot).ifBlank { "Account" }, TdAccounts.phone(slot), TdAccounts.photo(slot), active = false)
                }
            }
        }

    // Telegram allows 3 accounts per app (4 with Premium); keep to 3.
    override val canAddAccount: Boolean
        get() = !TdAccounts.adding && accountSwitchTo < 0 && TdAccounts.signedInSlots().size < 3

    override val addingAccount: Boolean get() = TdAccounts.adding

    override fun addAccount() {
        try {
            TdAccounts.attach(app)
            if (TdAccounts.adding || accountSwitchTo >= 0) return
            accountSwitchTo = TdAccounts.newSlot()
            closeForAccountChange()
        } catch (e: Exception) {
            android.util.Log.e("TGlass", "Add account failed", e)
            if (accountSwitchTo >= 0) runCatching { TdAccounts.abortAdding(accountSwitchTo) }
            accountSwitchTo = -1
            _errors.tryEmit("Couldn't add an account")
        }
    }

    override fun switchAccount(slot: Int) {
        try {
            TdAccounts.attach(app)
            if (slot == TdAccounts.active || slot !in TdAccounts.slots || accountSwitchTo >= 0) return
            accountSwitchTo = slot
            beginAccountSwitch(slot)
            // Let the UI fade out first, so the old account's state being cleared is never seen.
            scope.launch {
                delay(ACCOUNT_FADE_OUT_MS)
                try {
                    closeForAccountChange()
                } catch (e: Exception) {
                    android.util.Log.e("TGlass", "Switch account failed", e)
                    accountSwitchTo = -1
                    endAccountSwitch()
                    _errors.tryEmit("Couldn't switch accounts")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TGlass", "Switch account failed", e)
            accountSwitchTo = -1
            endAccountSwitch()
            _errors.tryEmit("Couldn't switch accounts")
        }
    }

    override fun cancelAddAccount() {
        try {
            val back = TdAccounts.returnTo
            if (back < 0 || accountSwitchTo >= 0) return
            accountDropSlot = TdAccounts.active
            accountSwitchTo = back
            closeForAccountChange()
        } catch (e: Exception) {
            android.util.Log.e("TGlass", "Cancel add account failed", e)
            accountSwitchTo = -1
            accountDropSlot = -1
        }
    }

    private fun closeForAccountChange() {
        runCatching { com.abtin.tglass.core.media.VoicePlayer.stop() }
        auth = AuthStep.Starting
        scope.launch {
            val r = try {
                client.close()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("TGlass", "close() threw", e)
                null
            }
            if (r !is TdlResult.Success) {
                // TDLib did not close: stay on the current account.
                val target = accountSwitchTo
                if (target >= 0 && TdAccounts.adding && accountDropSlot < 0) TdAccounts.abortAdding(target)
                accountSwitchTo = -1
                accountDropSlot = -1
                endAccountSwitch()
                val s = client.getAuthorizationState()
                if (s is TdlResult.Success) onAuth(s.result)
            }
        }
    }

    /** TDLib closed: decide which slot starts next (a switch, a cancelled sign-in, or a log out). */
    private fun onAccountClosed(loggedOut: Boolean) {
        try {
            TdAccounts.attach(app)
            val closed = TdAccounts.active
            val target = accountSwitchTo
            val drop = accountDropSlot
            accountSwitchTo = -1
            accountDropSlot = -1
            if (target >= 0 || loggedOut) resetAccountExtras()
            when {
                target >= 0 -> {
                    TdAccounts.activate(target)
                    if (drop >= 0 && drop != target) {
                        TdAccounts.remove(drop)
                        deleteAccountFiles(drop)
                    }
                }
                loggedOut -> {
                    // Logged out (here or from another device): forget this account and continue with another.
                    val others = TdAccounts.signedInSlots().filter { it != closed && TdAccounts.userId(it) != 0L }
                    if (others.isNotEmpty()) {
                        TdAccounts.remove(closed)
                        deleteAccountFiles(closed)
                        TdAccounts.activate(others.first())
                    } else {
                        TdAccounts.forget(closed)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TGlass", "Account change failed", e)
        }
    }

    /**
     * Per-account state kept by other areas that reset() does not clear (it only ever had one account):
     * privacy, security, storage, notification scopes, profile caches… so the next account never shows them.
     */
    private fun resetAccountExtras() {
        runCatching {
            privacyValues.clear()
            folderCache.clear()
            blockedState.value = null
            blockedTotalState.value = null
            passwordState.value = null
            accountTtlState.value = null
            storageState.value = null
            dataUsageState.value = null
            scopeStore.clear()
            rawScopeSettings.clear()
            profileDetailsStore.clear()
            profilePhotoStore.clear()
            profileGiftStore.clear()
            sharedCountStore.clear()
            sharedMusicStore.clear()
            commonGroupStore.clear()
            gifBotId = 0L
            profileBirthdate = null
            profileChannel = null
            profileNameColor = -1
            historyGapMap.clear()
            gapLoading.clear()
            profileStoryStore.clear()
        }.onFailure { android.util.Log.e("TGlass", "Account state reset failed", it) }
    }

    /** Removes a dropped slot's database (never slot 0's, which TDLib itself empties on log out). */
    private fun deleteAccountFiles(slot: Int) {
        if (slot == 0) return
        runCatching {
            TdAccounts.databaseDir(app, slot).deleteRecursively()
            TdAccounts.filesDir(app, slot).deleteRecursively()
        }
    }
    // ---- end Accounts & Settings v2 ----

    // ---- Bubbles v2 ----
    /** Chats requested for forward headers (getChat once each; TDLib then sends updateNewChat). */
    private val originRequested = HashSet<Long>()

    override fun originChat(chatId: Long): com.abtin.tglass.data.OriginChat? {
        if (chatId == 0L) return null
        val known = chatMap[chatId]
        if (known == null) {
            if (originRequested.add(chatId)) scope.launch { client.getChat(chatId) }
            return null
        }
        return com.abtin.tglass.data.OriginChat(known.title, avatars[chatId])
    }

    override fun cancelDownload(image: ImageRef) {
        if (image.fileId <= 0 || filePath(image) != null) return
        downloading.remove(image.fileId)
        fileProgressMap.remove(image.fileId)
        scope.launch { client.cancelDownloadFile(image.fileId, false) }
    }

    override fun loadCommentsTarget(chatId: Long, messageId: Long, onResult: (com.abtin.tglass.data.CommentsTarget?) -> Unit) {
        scope.launch {
            val r = client.getMessageThread(chatId, messageId)
            if (r is TdlResult.Success) {
                val info = r.result
                val starter = info.messages.minByOrNull { it.id }?.id ?: 0L
                if (chatMap[info.chatId] == null) client.getChat(info.chatId)
                onResult(com.abtin.tglass.data.CommentsTarget(info.chatId, starter))
            } else {
                // Fall back to the channel's discussion group without a position.
                loadDiscussionChat(chatId) { id -> onResult(id?.let { com.abtin.tglass.data.CommentsTarget(it) }) }
            }
        }
    }
    // ---- end Bubbles v2 ----

    // ---- Folder pins ----
    /** Order + pinned state of a chat inside one folder's chat list (ChatListFolder). */
    private data class FolderSlot(val order: Long, val pinned: Boolean)

    /** Chat id → folder id → its position in that folder (only lists the chat is in). Lazy: see activeStories. */
    private val folderPositions by lazy { mutableStateMapOf<Long, Map<Int, FolderSlot>>() }

    /** One cached, sorted chat list per folder id (recomputed only when positions or chats change). */
    private val folderChatLists by lazy { HashMap<Int, androidx.compose.runtime.State<List<UiChat>>>() }

    private fun publishFolderPositions(st: ChatState) {
        val m = HashMap<Int, FolderSlot>()
        st.positions.values.forEach { p ->
            val list = p.list
            if (list is ChatListFolder && p.order != 0L) m[list.chatFolderId] = FolderSlot(p.order, p.isPinned)
        }
        if (m.isEmpty()) {
            if (folderPositions.containsKey(st.id)) folderPositions.remove(st.id)
        } else if (folderPositions[st.id] != m) {
            folderPositions[st.id] = m
        }
    }

    /**
     * Loads every folder's chat list so TDLib sends the chats' positions (order + isPinned) in it. TDLib may load
     * fewer chats than asked per call, so keep asking (bounded) until it answers 404 (the list is complete).
     */
    private fun loadFolderLists(ids: List<Int>) {
        ids.forEach { id ->
            scope.launch {
                repeat(4) { if (client.loadChats(ChatListFolder(id), 100) is TdlResult.Failure) return@launch }
            }
        }
    }

    private fun folderIdAt(index: Int): Int? = if (index <= 0) null else folderInfos.getOrNull(index - 1)?.id

    override fun chatsInFolder(index: Int): List<UiChat> {
        val id = folderIdAt(index) ?: return sortedChats.filter { !it.archived }
        return folderChatLists.getOrPut(id) {
            derivedStateOf {
                folderPositions.mapNotNull { (chatId, slots) ->
                    val slot = slots[id] ?: return@mapNotNull null
                    val c = chatMap[chatId] ?: return@mapNotNull null
                    if (c.pinned == slot.pinned && c.order == slot.order) c else c.copy(pinned = slot.pinned, order = slot.order)
                }.sortedWith(compareByDescending<UiChat> { it.order }.thenByDescending { it.id })
            }
        }.value
    }

    override fun togglePinInFolder(chatId: Long, index: Int) {
        val id = folderIdAt(index) ?: return togglePin(chatId)
        val pinned = folderPositions[chatId]?.get(id)?.pinned == true
        scope.launch { client.toggleChatIsPinned(ChatListFolder(id), chatId, !pinned).orReport() }
    }

    // Account switch transition (see TelegramRepository.switchingAccount).
    private val switchTargetState by lazy { mutableStateOf<com.abtin.tglass.data.AccountInfo?>(null) }
    private var switchWatch: Job? = null

    override val switchingAccount: com.abtin.tglass.data.AccountInfo? get() = switchTargetState.value

    /** Shows the switch to [slot] until its TDLib is up and its chats arrived (or it failed / timed out). */
    private fun beginAccountSwitch(slot: Int) {
        val info = accounts.firstOrNull { it.slot == slot }
            ?: com.abtin.tglass.data.AccountInfo(slot, TdAccounts.userId(slot), TdAccounts.name(slot).ifBlank { "Account" }, TdAccounts.phone(slot), TdAccounts.photo(slot), active = false)
        switchTargetState.value = info
        switchWatch?.cancel()
        switchWatch = scope.launch {
            // All of this state lives on the main thread (scope = Main.immediate): a light poll is enough.
            val arrived = kotlinx.coroutines.withTimeoutOrNull(25_000L) {
                while (true) {
                    val started = auth != AuthStep.Starting
                    if (TdAccounts.active == slot && started) break
                    // The switch was abandoned (it failed after TDLib closed): the old account is back up.
                    if (accountSwitchTo < 0 && TdAccounts.active != slot && started) break
                    delay(40)
                }
                TdAccounts.active == slot
            }
            if (arrived == true && auth == AuthStep.Ready) {
                kotlinx.coroutines.withTimeoutOrNull(2_500L) { while (sortedChats.isEmpty()) delay(40) }
                delay(120)
            }
            switchTargetState.value = null
            switchWatch = null
        }
    }

    private fun endAccountSwitch() {
        switchWatch?.cancel()
        switchWatch = null
        switchTargetState.value = null
    }
    // ---- end Folder pins ----

    // ---- Profile media grid ----
    override fun forgetSharedMessages(chatId: Long, ids: Set<Long>) {
        for (k in sharedMediaStore.keys.toList()) {
            if (!k.startsWith("$chatId:")) continue
            val list = sharedMediaStore[k] ?: continue
            sharedMediaStore[k] = list.filter { it.id !in ids }
        }
    }
    // ---- end Profile media grid ----
}

private const val GIFTS_ENABLED = false

/** How long the UI fades out before TDLib closes for an account switch (AccountSwitchTransition's fade-out). */
private const val ACCOUNT_FADE_OUT_MS = 240L

/** Process-wide TDLib instance (TDLib must not be created twice for the same database). */
object Td {
    @Volatile private var instance: TdRepository? = null

    fun get(context: Context): TdRepository =
        instance ?: synchronized(this) { instance ?: TdRepository(context.applicationContext).also { instance = it } }
}
