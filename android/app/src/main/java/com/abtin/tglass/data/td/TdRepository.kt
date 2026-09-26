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
import com.abtin.tglass.data.GifItem
import com.abtin.tglass.data.GlobalResults
import com.abtin.tglass.data.StickerItem
import com.abtin.tglass.data.StickerPack
import com.abtin.tglass.data.MediaKind
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
    override val stories: List<UiStory> get() = emptyList()
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
            is UiContent.Contact -> InputMessageContact(Contact(content.phone, content.name, "", "", 0))
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
        scope.launch {
            if (chosen) client.removeMessageReaction(chatId, messageId, ReactionTypeEmoji(emoji)).orReport()
            else client.addMessageReaction(chatId, messageId, ReactionTypeEmoji(emoji), false, true).orReport()
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

    override fun markStorySeen(userId: Long) {}

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
            client.allUpdates.collect { handle(it) }
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
            val dir = app.filesDir
            val r = client.setTdlibParameters(
                useTestDc = false,
                databaseDirectory = java.io.File(dir, "td").absolutePath,
                filesDirectory = java.io.File(dir, "td_files").absolutePath,
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
            is AuthorizationStateClosed -> { reset(); start(); AuthStep.Starting }
            else -> AuthStep.Unsupported("This login method is not supported yet.")
        }
    }

    private fun onReady() {
        scope.launch {
            client.getMe().let { if (it is TdlResult.Success) { myId = it.result.id; onUser(it.result) } }
            client.loadChats(ChatListMain(), 100)
            client.loadChats(ChatListArchive(), 50)
            loadContacts()
            loadSessions()
            loadCalls()
        }
    }

    private fun reset() {
        com.abtin.tglass.notify.Notifier.cancelAll(app)
        chatStates.clear(); rawUsers.clear(); basicGroups.clear(); supergroups.clear()
        typingJobs.values.forEach { it.cancel() }; typingJobs.clear()
        downloading.clear(); fileProgressMap.clear(); historyLoading.clear(); historyComplete.clear(); openChats.clear()
        chatMap.clear(); userMap.clear(); lastMessages.clear(); messageStore.clear(); avatars.clear(); filePaths.clear()
        contactIds.clear(); callList.clear(); sessionList.clear(); folderInfos = emptyList()
        chatInfos.clear(); sharedMediaStore.clear(); messageCache.clear(); requestedMessages.clear(); pinnedStore.clear()
        packs.clear(); recents.clear(); gifs.clear(); stickersLoaded = false
        myId = 0
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
                scope.launch { u.chatFolders.forEach { client.loadChats(ChatListFolder(it.id), 100) } }
            }
            is UpdateNewMessage -> {
                addMessage(u.message)
                if (u.message.chatId in openChats && !u.message.isOutgoing) {
                    scope.launch { client.viewMessages(u.message.chatId, longArrayOf(u.message.id), null, true) }
                }
                maybeNotify(u.message)
            }
            is UpdateMessageSendSucceeded -> replaceMessage(u.message, u.oldMessageId)
            is UpdateMessageSendFailed -> {
                replaceMessage(u.message, u.oldMessageId)
                _errors.tryEmit(humanize(u.error.message))
            }
            is UpdateMessageContent -> updateMessage(u.chatId, u.messageId) { it.copy(content = mapContent(u.newContent, u.chatId)) }
            is UpdateMessageEdited -> updateMessage(u.chatId, u.messageId) { it.copy(edited = true) }
            is UpdateMessageIsPinned -> {
                updateMessage(u.chatId, u.messageId) { it.copy(pinned = u.isPinned) }
                if (u.chatId in openChats) loadPinned(u.chatId)
            }
            is UpdateMessageInteractionInfo -> updateMessage(u.chatId, u.messageId) {
                it.copy(reactions = mapReactions(u.interactionInfo), views = if (it.views != null) u.interactionInfo?.viewCount else null)
            }
            is UpdateDeleteMessages -> if (!u.fromCache) {
                val ids = u.messageIds.toHashSet()
                messageStore[u.chatId]?.removeAll { it.id in ids }
            }
            is UpdateFile -> onFile(u.file)
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
        userMap[u.id] = mapUser(u)
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
            is ChatTypeBasicGroup -> { members = basicGroups[t.basicGroupId]?.memberCount ?: 0; UiChatType.Group }
            is ChatTypeSupergroup -> {
                val sg = supergroups[t.supergroupId]
                members = sg?.memberCount ?: 0
                verified = sg?.verificationStatus?.isVerified == true
                username = sg?.usernames?.activeUsernames?.firstOrNull()
                joined = sg?.status.let { it !is ChatMemberStatusLeft && it !is ChatMemberStatusBanned }
                canPost = sg?.status.let { it is ChatMemberStatusCreator || it is ChatMemberStatusAdministrator }
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
        )
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
        ImageRef(f.id, localPath(f), mini?.data, width, height)

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
        )
    }

    private fun mapReactions(info: MessageInteractionInfo?): List<UiReaction> =
        info?.reactions?.reactions?.mapNotNull { r ->
            (r.type as? ReactionTypeEmoji)?.let { UiReaction(it.emoji, r.totalCount, r.isChosen) }
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
        is MessageVideoNote -> UiContent.Photo(
            seed = c.videoNote.video.id, aspect = 1f, emoji = "📹",
            image = c.videoNote.thumbnail?.let { imageOf(it.file, c.videoNote.minithumbnail) }, video = true,
            videoFile = imageOf(c.videoNote.video, null, c.videoNote.length, c.videoNote.length), duration = c.videoNote.duration,
        )
        is MessageVoiceNote -> UiContent.Voice(c.voiceNote.duration, waveform(c.voiceNote.waveform), imageOf(c.voiceNote.voice, null))
        is MessageAudio -> UiContent.File(
            name = c.audio.title.ifBlank { c.audio.fileName.ifBlank { "Audio" } },
            size = Formats.size(c.audio.audio.size),
            file = imageOf(c.audio.audio, null),
            mime = c.audio.mimeType,
            music = true,
            duration = c.audio.duration,
            performer = c.audio.performer.ifBlank { null },
        )
        is MessageDocument -> UiContent.File(
            name = c.document.fileName.ifBlank { "File" },
            size = Formats.size(c.document.document.size),
            file = imageOf(c.document.document, null),
            mime = c.document.mimeType,
        )
        is MessageSticker -> {
            val s = c.sticker
            val image = when {
                s.format is StickerFormatWebp -> imageOf(s.sticker, null)
                s.thumbnail?.format.let { it is ThumbnailFormatWebp || it is ThumbnailFormatJpeg } -> imageOf(s.thumbnail!!.file, null)
                else -> null
            }
            UiContent.Sticker(s.emoji.ifBlank { "🙂" }, image, s.sticker.takeIf { s.format is StickerFormatTgs }?.let { imageOf(it, null) })
        }
        is MessageAnimatedEmoji -> {
            val s = c.animatedEmoji.sticker
            UiContent.Sticker(c.emoji, null, s?.takeIf { it.format is StickerFormatTgs }?.let { imageOf(it.sticker, null) })
        }
        is MessageDice -> UiContent.Sticker(c.emoji)
        is MessageLocation -> UiContent.Location("Location", "%.5f, %.5f".format(Locale.US, c.location.latitude, c.location.longitude))
        is MessageVenue -> UiContent.Location(c.venue.title, c.venue.address)
        is MessageContact -> UiContent.Contact("${c.contact.firstName} ${c.contact.lastName}".trim(), c.contact.phoneNumber)
        is MessagePoll -> UiContent.Poll(
            question = c.poll.question.text,
            options = c.poll.options.map { it.text.text },
            votes = c.poll.options.map { it.voterCount },
            voted = c.poll.options.indexOfFirst { it.isChosen }.takeIf { it >= 0 },
            quiz = c.poll.type is PollTypeQuiz,
        )
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
            MediaKind.Voice -> SearchMessagesFilterVoiceNote()
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
}

/** Process-wide TDLib instance (TDLib must not be created twice for the same database). */
object Td {
    @Volatile private var instance: TdRepository? = null

    fun get(context: Context): TdRepository =
        instance ?: synchronized(this) { instance ?: TdRepository(context.applicationContext).also { instance = it } }
}
