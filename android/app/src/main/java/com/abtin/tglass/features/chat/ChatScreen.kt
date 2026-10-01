package com.abtin.tglass.features.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.media.toContent
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.Chat
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageCaps
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.data.senderName
import com.abtin.tglass.features.chatlist.ChatAvatar
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ContextMenuRequest
import com.abtin.tglass.ui.components.AllFreeReactions
import com.abtin.tglass.ui.components.DefaultReactions
import com.abtin.tglass.ui.components.MenuReactions
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalContextMenu
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.MenuAction
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TypingText
import com.abtin.tglass.ui.components.BackButton
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatCount
import com.abtin.tglass.ui.components.formatDay
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Same calendar day in the device time zone (epoch-day arithmetic: this runs for every visible row on every recomposition). */
private fun sameDay(a: Long, b: Long): Boolean {
    val tz = java.util.TimeZone.getDefault()
    return Math.floorDiv(a + tz.getOffset(a), 86_400_000L) == Math.floorDiv(b + tz.getOffset(b), 86_400_000L)
}

private fun groupable(a: Message?, b: Message?): Boolean =
    a != null && b != null && a.senderId == b.senderId &&
        a.content !is MessageContent.Service && b.content !is MessageContent.Service &&
        abs(a.date - b.date) < 10 * 60_000 && sameDay(a.date, b.date)

fun chatSubtitle(chat: Chat, repo: TelegramRepository): Pair<String?, Boolean> {
    if (chat.typing != null) return "${chat.typing}…" to true
    // Live supergroups often report 0 members until their full info is loaded.
    fun memberCount() = chat.members.takeIf { it > 0 } ?: repo.chatInfo(chat.id)?.memberCount ?: 0
    return when (chat.type) {
        ChatType.Private -> chat.peerUserId?.let { repo.user(it) }?.let { it.status to it.online } ?: (null to false)
        ChatType.Group -> {
            val members = memberCount()
            val online = if (repo.isLive) repo.onlineMemberCount(chat.id) else (members / 40).coerceAtLeast(1)
            when {
                members <= 0 -> (if (repo.isLive) null else "group") to false
                online != null && online > 1 -> "${formatCount(members)} ${if (members == 1) "member" else "members"}, ${formatCount(online)} online" to false
                else -> "${formatCount(members)} ${if (members == 1) "member" else "members"}" to false
            }
        }
        ChatType.Channel -> memberCount().let { members -> if (members > 0) "${formatCount(members)} ${if (members == 1) "subscriber" else "subscribers"}" else "channel" } to false
        ChatType.Bot -> "bot" to false
        ChatType.Saved -> null to false
    }
}

@Composable
fun ChatScreen(chatId: Long) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val menu = LocalContextMenu.current
    val sheet = LocalActionSheet.current
    val toast = LocalToast.current
    val c = TgTheme.colors
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val chat = repo.chat(chatId)
    if (chat == null) {
        // A live chat may still be on its way from the server (e.g. just created); give it a moment.
        LaunchedEffect(chatId) {
            repo.ensureChat(chatId)
            kotlinx.coroutines.delay(if (repo.isLive) 10_000L else 0L)
            nav.pop()
        }
        Box(Modifier.fillMaxSize().background(c.groupedBackground), contentAlignment = Alignment.Center) {
            com.abtin.tglass.ui.components.ActivityIndicator(28.dp, c.secondaryText)
        }
        return
    }
    val backdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    // Unread count of the other chats (back button badge): recomposes only when the number itself changes.
    val backBadge by remember(chatId, repo) { derivedStateOf { repo.chats.filter { it.id != chatId && !it.archived && !it.muted }.sumOf { it.unread } } }
    val focusRequester = remember { FocusRequester() }

    var text by rememberSaveable { mutableStateOf(chat.draft ?: "") }
    var replyToId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    // Emoji panel -> keyboard: the panel stays under the rising keyboard until it is fully up, then goes away.
    var panelHandover by remember { mutableStateOf(false) }
    var composeEntities by remember { mutableStateOf(emptyList<com.abtin.tglass.data.Entity>()) }
    val imeInsets = WindowInsets.ime
    val navInsets = WindowInsets.navigationBars
    LaunchedEffect(panelHandover) {
        if (!panelHandover) return@LaunchedEffect
        kotlinx.coroutines.withTimeoutOrNull(1200) {
            snapshotFlow { imeInsets.getBottom(density) - navInsets.getBottom(density) }.first { px ->
                px > 0 && px >= KeyboardHeight.dp * density.density - 2f
            }
        }
        panelOpen = false
        panelHandover = false
    }
    var attachOpen by rememberSaveable { mutableStateOf(false) }
    var attachMenuOpen by remember { mutableStateOf(false) }
    var attachAction by remember { mutableStateOf<AttachAction?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    var highlightId by remember { mutableLongStateOf(-1L) }
    var headerHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    var wallpaperPhase by rememberSaveable { mutableIntStateOf(0) }
    val openedAt = remember { System.currentTimeMillis() }
    // Bot keyboards: callback buttons waiting for an answer ("messageId:row:col") and the reply keyboard the user hid.
    val busyButtons = remember { mutableStateListOf<String>() }
    var hiddenKeyboardId by rememberSaveable { mutableStateOf<Long?>(null) }

    val messages = repo.messages(chatId)
    val reversed = messages.asReversed()
    // Media albums become one list item (one bubble with a grid).
    // Derived: rebuilt only when the message list changes (not on every keystroke of the composer).
    val items by remember(chatId, repo) { derivedStateOf { buildChatItems(repo.messages(chatId)) } }
    val reversedItems = items.asReversed()
    fun itemIndexOf(id: Long) = reversedItems.indexOfFirst { it.contains(id) }
    val isGroup = chat.type == ChatType.Group
    val isChannel = chat.type == ChatType.Channel
    // Bubbles hug their content up to ~78 % of the chat width (at most ~320 pt + tail), next to the avatar column in groups.
    val maxBubble: Dp = ((LocalConfiguration.current.screenWidthDp - (if (isGroup) 38 else 0)) * 0.78f).coerceAtMost(326f).dp
    val pinned = repo.pinnedMessage(chatId)
    // "Unread Messages" divider: fixed when the chat opens (before it is marked as read).
    val readAnchor = remember { repo.readAnchor(chatId) }
    val firstUnreadId = readAnchor?.let { a -> messages.firstOrNull { !it.outgoing && it.id > a }?.id }
    var initialScrollDone by remember { mutableStateOf(readAnchor == null) }
    var pendingJump by remember { mutableStateOf<Long?>(null) }
    // In-chat search (opened from the profile's Search button).
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Message>>(emptyList()) }
    var resultIndex by remember { mutableIntStateOf(0) }
    // Server's count of all matches ("3 of 12"), the next page's start (0 = none) and whether a request is running.
    var resultTotal by remember { mutableIntStateOf(0) }
    var resultNextFrom by remember { mutableLongStateOf(0L) }
    var searchBusy by remember { mutableStateOf(false) }
    val searchGeneration = remember { intArrayOf(0) }
    // Poll voters page (View Results).
    var pollResults by remember { mutableStateOf<PollResultsTarget?>(null) }
    var messageInfo by remember { mutableStateOf<MessageInfoTarget?>(null) }
    val reactionEffects = remember { com.abtin.tglass.core.emoji.ReactionEffectsState() }
    LaunchedEffect(ChatSearchRequest.chatId) {
        if (ChatSearchRequest.chatId == chatId) {
            ChatSearchRequest.chatId = null
            searchMode = true
        }
    }

    LaunchedEffect(Unit) {
        repo.openChat(chatId)
        repo.loadChatExtras(chatId)
    }
    // Channels: the linked discussion group (the round button left of Mute).
    var discussionId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(isChannel) { if (isChannel) repo.loadDiscussionChat(chatId) { discussionId = it } }
    DisposableEffect(Unit) {
        onDispose {
            repo.setDraft(chatId, text.takeIf { editingId == null })
            repo.closeChat(chatId)
        }
    }
    // Reverse layout: the oldest loaded message is the last item, so near-the-end means "load older".
    val nearTop by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 6 } == true } }
    LaunchedEffect(nearTop, messages.size) { if (nearTop && messages.isNotEmpty()) repo.loadOlderMessages(chatId) }
    // After a jump to an old message (search result, reply, link) the history between it and the newest messages
    // is missing: load it page by page (newer messages) when that spot comes near the screen while scrolling down.
    val gaps = repo.historyGaps(chatId)
    val gapsState = rememberUpdatedState(gaps)
    val itemsState = rememberUpdatedState(reversedItems)
    val gapInView by remember {
        derivedStateOf<Long?> {
            val g = gapsState.value
            val list = itemsState.value
            val visible = listState.layoutInfo.visibleItemsInfo
            if (g.isEmpty() || visible.isEmpty() || list.isEmpty()) return@derivedStateOf null
            val from = (visible.first().index - 12).coerceIn(0, list.lastIndex)
            val to = (visible.last().index + 12).coerceIn(0, list.lastIndex)
            var lo = Long.MAX_VALUE
            var hi = Long.MIN_VALUE
            for (i in from..to) for (m in list[i].messages) {
                if (m.id < lo) lo = m.id
                if (m.id > hi) hi = m.id
            }
            g.filter { it in lo..hi }.minOrNull()
        }
    }
    LaunchedEffect(gapInView) { gapInView?.let { repo.loadNewerMessages(chatId, it) } }
    // The newest messages themselves aren't loaded (the chat was opened straight at an old message).
    val newestMissing = gaps.isNotEmpty() && messages.lastOrNull()?.id?.let { last -> gaps.any { it >= last } } == true
    val newestSeen = remember { longArrayOf(messages.lastOrNull()?.id ?: 0L) }
    LaunchedEffect(messages.size) {
        if (!initialScrollDone && messages.isNotEmpty()) {
            // Open at the first unread message, with the divider in the upper part of the screen.
            val idx = firstUnreadId?.let { id -> itemIndexOf(id) } ?: -1
            if (idx > 0) {
                listState.scrollToItem(idx)
                listState.scrollBy(-listState.layoutInfo.viewportSize.height * 0.55f)
            }
            if (idx >= 0) initialScrollDone = true
            return@LaunchedEffect
        }
        val last = messages.lastOrNull()
        if (last != null && last.outgoing && last.date > openedAt) wallpaperPhase++
        // Follow new messages at the bottom; history filled in above or below the viewport doesn't move the list.
        val newestChanged = (last?.id ?: 0L) != newestSeen[0]
        newestSeen[0] = last?.id ?: 0L
        if (newestChanged && listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    fun jumpTo(id: Long) {
        val idx = itemIndexOf(id)
        if (idx >= 0) scope.launch {
            highlightId = id
            listState.animateScrollToItem(idx)
            // Bring it up from the composer into the lower-middle of the screen, like Telegram.
            val lift = listState.layoutInfo.viewportSize.height * 0.3f
            if (idx > 0 && lift > 0f) listState.animateScrollBy(-lift)
            kotlinx.coroutines.delay(1500)
            if (highlightId == id) highlightId = -1
        }
    }

    /** Scrolls to a message, loading the history around it first if needed. */
    fun jumpToAny(id: Long) {
        if (messages.any { it.id == id }) jumpTo(id)
        else repo.loadAroundMessage(chatId, id) { found -> if (found) pendingJump = id else toast.error("Message not found") }
    }
    /** Scroll-to-bottom button: back to the newest messages (loading them first after a jump far back). */
    fun scrollToBottom() {
        repo.loadLatestMessages(chatId) {
            scope.launch {
                if (listState.firstVisibleItemIndex > 30) listState.scrollToItem(0) else listState.animateScrollToItem(0)
            }
        }
    }
    LaunchedEffect(pendingJump, messages.size) {
        val id = pendingJump ?: return@LaunchedEffect
        if (reversed.any { it.id == id }) {
            pendingJump = null
            jumpTo(id)
        }
    }
    // Opened from a t.me/…/123 link: scroll to that message.
    LaunchedEffect(Unit) {
        val target = ChatJumpRequest.take(chatId) ?: return@LaunchedEffect
        kotlinx.coroutines.delay(250)
        initialScrollDone = true
        jumpToAny(target)
    }
    // In-chat search: TDLib searchChatMessages, newest first; "N of M" with up (older) / down (newer) arrows.
    LaunchedEffect(searchQuery, searchMode) {
        val gen = ++searchGeneration[0]
        results = emptyList()
        resultIndex = 0
        resultTotal = 0
        resultNextFrom = 0L
        val q = searchQuery.trim()
        if (!searchMode || q.isEmpty()) {
            searchBusy = false
            return@LaunchedEffect
        }
        searchBusy = true
        kotlinx.coroutines.delay(350)
        repo.searchChatPage(chatId, q, 0L) { page ->
            if (gen != searchGeneration[0]) return@searchChatPage
            searchBusy = false
            results = page.messages
            resultTotal = maxOf(page.total, page.messages.size)
            resultNextFrom = page.nextFrom
            page.messages.firstOrNull()?.let { jumpToAny(it.id) }
        }
    }

    /** Shows search result [i] (loading the next page of older results when the end is near). */
    fun showResult(i: Int) {
        if (i < 0 || i >= results.size) return
        resultIndex = i
        jumpToAny(results[i].id)
        val q = searchQuery.trim()
        if (i >= results.size - 3 && resultNextFrom != 0L && !searchBusy && q.isNotEmpty()) {
            val gen = searchGeneration[0]
            searchBusy = true
            repo.searchChatPage(chatId, q, resultNextFrom) { page ->
                if (gen != searchGeneration[0]) return@searchChatPage
                searchBusy = false
                val known = results.map { it.id }.toSet()
                results = results + page.messages.filter { it.id !in known }
                resultNextFrom = page.nextFrom
                resultTotal = maxOf(resultTotal, results.size)
            }
        }
    }

    fun closeSearch() {
        searchMode = false
        searchQuery = ""
        focus.clearFocus()
        keyboard?.hide()
    }
    BackHandler(enabled = searchMode) { closeSearch() }

    fun send() {
        val t = text.trim()
        val editId = editingId
        if (editId != null) {
            val target = messages.firstOrNull { it.id == editId }
            // Media keep their picture/file: only the caption changes (and may be removed).
            if (target != null && (target.content is MessageContent.Photo || target.content is MessageContent.File)) repo.editCaption(chatId, editId, t)
            else if (t.isNotEmpty()) repo.editText(chatId, editId, t)
            editingId = null
        } else if (t.isNotEmpty()) {
            val lead = text.length - text.trimStart().length
            val ents = composeEntities.mapNotNull { e ->
                val st = (e.start - lead).coerceAtLeast(0)
                val en = (e.end - lead).coerceAtMost(t.length)
                if (en > st) e.copy(start = st, end = en) else null
            }
            if (ents.isEmpty()) repo.sendText(chatId, t, replyToId) else repo.sendFormattedText(chatId, t, ents, replyToId)
            replyToId = null
        }
        composeEntities = emptyList()
        text = ""
    }

    /** Send button long-press → "Send Without Sound". */
    fun sendSilently() {
        val t = text.trim()
        if (t.isEmpty() || editingId != null) return
        repo.sendTextSilently(chatId, t, replyToId)
        replyToId = null
        text = ""
    }

    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    fun openUsername(name: String) = repo.resolveUsername(name) { id ->
        if (id != null) nav.push(Route.Chat(id)) else toast.error("No one uses @${name.removePrefix("@")}")
    }
    fun openInBrowser(url: String) {
        runCatching { uriHandler.openUri(url) }.onFailure { toast.show("Can't open this link") }
    }
    fun openResolved(r: com.abtin.tglass.data.ResolvedLink, url: String) {
        val target = r.chatId
        val invite = r.invite
        when {
            target != null -> {
                if (target == chatId) r.messageId?.let { jumpToAny(it) }
                else {
                    r.messageId?.let { ChatJumpRequest.request(target, it) }
                    nav.push(Route.Chat(target))
                }
            }
            invite != null -> sheet.show(
                SheetRequest(
                    title = invite.title,
                    message = if (invite.memberCount > 0) "${formatCount(invite.memberCount)} ${if (invite.channel) "subscribers" else "members"}" else null,
                    actions = listOf(
                        SheetAction(
                            when {
                                invite.requestNeeded -> "Request to Join"
                                invite.channel -> "Join Channel"
                                else -> "Join Group"
                            },
                            bold = true,
                        ) {
                            repo.joinByInviteLink(invite.link) { id, err ->
                                if (id != null) nav.push(Route.Chat(id)) else if (err != null) toast.error(err)
                            }
                        },
                    ),
                )
            )
            r.error != null -> toast.error(r.error)
            else -> openInBrowser(url)
        }
    }
    fun openUrl(raw: String) {
        val url = if (raw.startsWith("http", ignoreCase = true) || raw.startsWith("tg:", ignoreCase = true)) raw else "https://$raw"
        if (com.abtin.tglass.data.ProxyItem.fromLink(url) != null) {
            nav.push(Route.ProxyLink(url))
            return
        }
        // t.me / telegram.me / tg:// links open inside the app (chats, bots, channels, posts, invites), like Telegram.
        if (com.abtin.tglass.data.TelegramLinks.isTelegramLink(url)) {
            repo.resolveLink(url) { r -> openResolved(r, url) }
            return
        }
        openInBrowser(url)
    }
    val linkHandler: (com.abtin.tglass.data.Entity, String) -> Unit = { e, value ->
        when (e.type) {
            com.abtin.tglass.data.EntityType.Url -> openUrl(value)
            com.abtin.tglass.data.EntityType.TextUrl -> openUrl(e.url ?: value)
            com.abtin.tglass.data.EntityType.Email -> runCatching { uriHandler.openUri("mailto:$value") }
            com.abtin.tglass.data.EntityType.Phone -> runCatching { uriHandler.openUri("tel:$value") }
            com.abtin.tglass.data.EntityType.Mention -> openUsername(value)
            com.abtin.tglass.data.EntityType.MentionName -> nav.push(Route.Chat(repo.privateChatWith(e.userId)))
            com.abtin.tglass.data.EntityType.BotCommand -> repo.sendText(chatId, value, null)
            else -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("text", value))
                toast.show("$value copied")
            }
        }
    }

    fun copy(m: Message) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("message", m.text ?: m.preview))
        toast.show("Copied to clipboard")
    }

    fun onInlineButton(m: Message, row: Int, col: Int, b: com.abtin.tglass.data.InlineButton) {
        when (b.kind) {
            com.abtin.tglass.data.InlineButtonKind.Url -> b.url?.let { openUrl(it) }
            com.abtin.tglass.data.InlineButtonKind.Copy -> {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("text", b.copyText ?: b.text))
                toast.show("Copied to clipboard")
            }
            com.abtin.tglass.data.InlineButtonKind.Callback -> {
                val data = b.data ?: return
                val key = "${m.id}:$row:$col"
                if (key in busyButtons) return
                busyButtons.add(key)
                repo.pressCallbackButton(chatId, m.id, data) { answer ->
                    busyButtons.remove(key)
                    if (answer != null) when {
                        answer.url.isNotBlank() -> openUrl(answer.url)
                        answer.text.isBlank() -> {}
                        answer.alert -> sheet.show(SheetRequest(title = chat.title, message = answer.text, actions = emptyList(), alert = true, cancel = "OK"))
                        else -> toast.show(answer.text)
                    }
                }
            }
            com.abtin.tglass.data.InlineButtonKind.Unsupported -> toast.show("This button isn't supported yet")
        }
    }

    // ---- Message menu (long press): forward sheet, delete alert, reactions ----
    var forwardIds by remember { mutableStateOf<List<Long>?>(null) }
    val saveMedia = rememberMediaSaver(repo, toast)

    fun forward(ids: List<Long>) {
        focus.clearFocus()
        keyboard?.hide()
        forwardIds = ids
    }

    fun confirmDelete(ids: List<Long>, caps: MessageCaps? = null) {
        val chosen = messages.filter { it.id in ids }
        if (chosen.isEmpty()) return
        sheet.show(deleteAlert(repo, chat, chosen, caps) { selected.clear(); selecting = false })
    }

    fun react(m: Message, emoji: String) {
        // Telegram plays the reaction's animation for a newly added reaction only (not when it is taken back).
        if (m.reactions.none { it.chosen && com.abtin.tglass.ui.components.sameReaction(it.emoji, emoji) }) reactionEffects.expect(emoji)
        repo.toggleReaction(chatId, m.id, emoji)
    }

    fun openMenu(m: Message, bounds: Rect) {
        focus.clearFocus()
        keyboard?.hide()
        val hasText = !m.text.isNullOrEmpty()
        // Photos, videos, GIFs and files can get a caption even when they have none yet (round video notes can't).
        val captionable = when (m.content) {
            is MessageContent.Photo -> true
            is MessageContent.File -> true
            else -> false
        }
        val isMedia = galleryFileOf(m) != null
        // Permissions from TDLib arrive a moment later and refine the menu (null = unknown → Telegram's usual rules).
        val caps = mutableStateOf<MessageCaps?>(null)
        repo.loadMessageCaps(chatId, m.id) { caps.value = it }
        val reactions = if (m.content is MessageContent.Service) null else MenuReactions(
            top = DefaultReactions,
            all = (DefaultReactions + AllFreeReactions).distinct(),
            chosen = m.reactions.filter { it.chosen }.map { it.emoji }.toSet(),
        ).also { r ->
            // Live: wait for the chat's own list (it pops in); demo answers at once.
            r.available = !repo.isLive
            repo.loadAvailableReactions(chatId, m.id) { info ->
                if (info != null) {
                    r.top = info.top
                    r.all = info.all
                    r.available = info.available
                } else {
                    r.available = true
                }
            }
        }
        val info = if (m.content is MessageContent.Service) null else loadMessageInfo(repo, m, chat.type)
        val actionsFor: () -> List<MenuAction> = {
            val k = caps.value
            val canDelete = k?.let { it.canDeleteForSelf || it.canDeleteForAll } ?: (!isChannel || chat.canPost)
            val canLink = (isChannel || isGroup) && (k?.canGetLink ?: (chat.username != null))
            val canReport = !m.outgoing && (isChannel || isGroup) && (k?.canReport ?: !repo.isLive)
            (if (info != null) messageInfoActions(repo, m, chat.type, info) { mode -> messageInfo = MessageInfoTarget(chatId, m.id, mode) } else emptyList()) + listOfNotNull(
                // Top row of the card (Telegram iOS 26): Select · Copy · Delete
                MenuAction("Select", TgIcons.CtxSelect, quick = true) { selecting = true; selected.clear(); selected.add(m.id) },
                if (hasText) MenuAction("Copy", TgIcons.CtxCopy, quick = true) { copy(m) } else null,
                if (canDelete) MenuAction("Delete", TgIcons.CtxDelete, destructive = true, quick = true) { confirmDelete(listOf(m.id), k) } else null,
                // List rows
                if (!isChannel && k?.canReply != false) MenuAction("Reply", TgIcons.CtxReply) { replyToId = m.id; editingId = null; focusRequester.requestFocus() } else null,
                pollMenuAction(m, retract = true)?.let { MenuAction("Retract Vote", Icons.AutoMirrored.Outlined.Undo) { repo.retractPollVote(chatId, m.id) } },
                pollMenuAction(m, retract = false)?.let { quiz ->
                    MenuAction(if (quiz) "Stop Quiz" else "Stop Poll", Icons.Outlined.StopCircle) {
                        sheet.show(
                            SheetRequest(
                                title = if (quiz) "Stop Quiz?" else "Stop Poll?",
                                message = "If you stop this ${if (quiz) "quiz" else "poll"} now, nobody will be able to ${if (quiz) "answer" else "vote in"} it anymore. This action cannot be undone.",
                                actions = listOf(
                                    SheetAction(if (quiz) "Stop Quiz" else "Stop Poll", destructive = true) {
                                        repo.stopPoll(chatId, m.id) { err -> if (err != null) toast.error(err) }
                                    },
                                ),
                                alert = true,
                            )
                        )
                    }
                },
                if (k?.canPin ?: true) MenuAction(if (m.pinned) "Unpin" else "Pin", if (m.pinned) TgIcons.CtxUnpin else TgIcons.CtxPin) { repo.togglePinMessage(chatId, m.id) } else null,
                if (k?.canForward != false) MenuAction("Forward", TgIcons.CtxForward) { forward(listOf(m.id)) } else null,
                if (m.outgoing && (hasText || captionable) && k?.canEdit != false) MenuAction("Edit", TgIcons.CtxEdit) { editingId = m.id; replyToId = null; text = m.text ?: ""; focusRequester.requestFocus() } else null,
                if (canLink) MenuAction("Copy Link", Icons.Outlined.Link) {
                    repo.loadMessageLink(chatId, m.id) { link ->
                        if (link == null) toast.show("This message has no link")
                        else {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", link))
                            toast.show("Link copied to clipboard")
                        }
                    }
                } else null,
                if (isMedia && k?.canSave != false) MenuAction(
                    if ((m.content as? MessageContent.Photo)?.let { it.video || it.loop } == true) "Save Video" else "Save Photo",
                    Icons.Outlined.Download,
                ) { saveMedia(m) } else null,
                if (chat.type != ChatType.Saved && k?.canForward != false) MenuAction("Save to Saved Messages", TgIcons.CtxSave) { repo.forward(chatId, listOf(m.id), repo.savedChatId); toast.show("Saved to Saved Messages") } else null,
                *stickerGifMenuActions(repo, toast, m).toTypedArray(),
                if (canReport) MenuAction("Report", Icons.Outlined.Report, destructive = true, groupStart = true) { reportMessagesFlow(repo, sheet, toast, chatId, listOf(m.id)) } else null,
            )
        }
        menu.show(
            ContextMenuRequest(
                key = "msg-${m.id}",
                anchor = bounds,
                alignEnd = m.outgoing,
                actions = actionsFor(),
                menuReactions = reactions,
                onReact = { e -> react(m, e) },
                dynamicActions = actionsFor,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = if (m.outgoing) Alignment.TopEnd else Alignment.TopStart) {
                    val idx = messages.indexOfFirst { it.id == m.id }
                    val group = groupFor(messages, idx, isGroup)
                    MessageBubble(
                        m, group, senderNameFor(repo, m, group, isGroup), m.senderId,
                        m.replyToId?.let { r -> messages.firstOrNull { it.id == r } },
                        m.replyToId?.let { r -> messages.firstOrNull { it.id == r }?.let { repo.senderName(it) } },
                        isChannel, maxBubble,
                    )
                }
            }
        )
    }
    // ---- end Message menu ----

    val videoNoteRecorder = com.abtin.tglass.core.media.rememberVideoNoteRecorder()

    androidx.compose.runtime.CompositionLocalProvider(
        LocalBackdrop provides backdrop, LocalLinkHandler provides linkHandler,
        com.abtin.tglass.core.emoji.LocalReactionEffects provides reactionEffects,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Z0: wallpaper + messages (the glass source)
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                ChatWallpaper(phase = wallpaperPhase)
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = with(density) { headerHeight.toDp() } + 8.dp,
                        bottom = with(density) { bottomHeight.toDp() } + 4.dp,
                    ),
                ) {
                    itemsIndexed(reversedItems, key = { _, it -> it.key }) { rIdx, item ->
                        val idx = items.size - 1 - rIdx
                        val prev = items.getOrNull(idx - 1)?.last
                        val group = groupForItems(items, idx, isGroup)
                        val m = item.head
                        val ids = item.messages.map { it.id }
                        Column(Modifier.animateItem()) {
                            if (prev == null || !sameDay(prev.date, item.first.date)) ServicePill(formatDay(item.first.date))
                            if (firstUnreadId != null && item.contains(firstUnreadId)) UnreadDivider()
                            if (m.content is MessageContent.Service) {
                                ServicePill((m.content as MessageContent.Service).text)
                            } else {
                                MessageRow(
                                    appear = m.date > openedAt,
                                    // Telegram iOS draws every channel post as an incoming bubble (left, with tail), even the admin's own.
                                    m = if (isChannel) m.asChannelPost() else m,
                                    group = group,
                                    repo = repo,
                                    isGroup = isGroup,
                                    isChannel = isChannel,
                                    maxBubble = maxBubble,
                                    replyTo = m.replyToId?.let { r ->
                                        repo.findMessage(chatId, r).also { found ->
                                            if (found == null) LaunchedEffect(r) { repo.requestMessage(chatId, r) }
                                        }
                                    },
                                    selecting = selecting,
                                    selected = ids.any { it in selected },
                                    highlighted = item.contains(highlightId),
                                    album = if (item.messages.size > 1) (if (isChannel) item.messages.map { it.asChannelPost() } else item.messages) else emptyList(),
                                    onAlbumItemClick = { id -> nav.push(Route.Media(chatId, id)) },
                                    onSenderClick = if (isGroup && repo.user(m.senderId) != null) ({ nav.push(Route.UserProfile(m.senderId)) }) else null,
                                    onShare = if (isChannel) ({ forward(ids) }) else null,
                                    onTap = {
                                        if (selecting) {
                                            if (ids.any { it in selected }) selected.removeAll(ids) else selected.addAll(ids)
                                            if (selected.isEmpty()) selecting = false
                                        }
                                    },
                                    onLongPress = { b -> if (!selecting) { Haptics.longPress(view); openMenu(m, b) } },
                                    onSwipeReply = { replyToId = m.id; editingId = null; focusRequester.requestFocus() },
                                    onReplyClick = { m.replyToId?.let { jumpToAny(it) } },
                                    onReact = { e -> react(m, e) },
                                    onVote = { o -> repo.votePoll(chatId, m.id, listOf(o)) },
                                    pollActions = if (m.content is MessageContent.Poll) PollActions(
                                        vote = { ids -> repo.votePoll(chatId, m.id, ids) },
                                        viewResults = { focus.clearFocus(); keyboard?.hide(); pollResults = PollResultsTarget(chatId, m.id) },
                                    ) else null,
                                    onMedia = { nav.push(Route.Media(chatId, m.id)) },
                                    keyboard = repo.inlineKeyboard(chatId, m.id),
                                    busyButton = { r, col -> "${m.id}:$r:$col" in busyButtons },
                                    onInlineButton = { r, col, b -> onInlineButton(m, r, col, b) },
                                )
                            }
                            Spacer(Modifier.height(if (group.groupedBottom) 2.dp else 6.dp))
                        }
                    }
                }
            }

            // Big reaction effects: a plain Lottie overlay above the list layer (nothing glass in here)
            com.abtin.tglass.core.emoji.ReactionEffectsOverlay(reactionEffects)

            // Camera circle while a video message is recorded (under the header and composer)
            VideoNoteRecordingOverlay(videoNoteRecorder)

            // Header (spec §11)
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerHeight = it.height }
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (searchMode) {
                        ChatSearchField(searchQuery, { searchQuery = it }, Modifier.weight(1f), onSubmit = { focus.clearFocus(); keyboard?.hide() })
                        Spacer(Modifier.width(8.dp))
                        GlassTextButton("Cancel", { closeSearch() })
                    } else if (selecting) {
                        GlassTextButton("Cancel", { selecting = false; selected.clear() })
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            GlassBox(onClick = null, modifier = Modifier.height(44.dp)) {
                                T("${selected.size} Selected", TgTheme.type.headline, c.text, modifier = Modifier.padding(horizontal = 18.dp))
                            }
                        }
                        Spacer(Modifier.width(80.dp))
                    } else {
                        BackButton({ nav.pop() }, badge = backBadge)
                        Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            val (subtitle, active) = chatSubtitle(chat, repo)
                            GlassBox(onClick = { nav.push(Route.Profile(chatId)) }, modifier = Modifier.height(48.dp)) {
                                Column(Modifier.padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    com.abtin.tglass.core.emoji.EmojiText(chat.title, TgTheme.type.headline.copy(fontSize = TgTheme.type.headline.fontSize * 0.95f), c.text, maxLines = 1)
                                    if (chat.typing != null) TypingText(chat.typing, TgTheme.type.caption1.copy(fontSize = TgTheme.type.caption1.fontSize * 1.05f), c.accent)
                                    else if (subtitle != null) T(subtitle, TgTheme.type.caption1.copy(fontSize = TgTheme.type.caption1.fontSize * 1.05f), if (active) c.accent else c.secondaryText, maxLines = 1)
                                }
                            }
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).fadeClickable { nav.push(Route.Profile(chatId)) }) {
                            ChatAvatar(chat, repo, 44.dp, showOnline = false)
                        }
                    }
                }
                // Voice / music / round video playing (global player): Telegram's media panel under the header.
                if (!searchMode) NowPlayingBar(repo, onOpen = { cid, mid ->
                    if (cid == chatId) jumpToAny(mid)
                    else {
                        ChatJumpRequest.request(cid, mid)
                        nav.push(Route.Chat(cid))
                    }
                })
                // Pinned message bar
                if (pinned != null && !selecting && !searchMode) {
                    Spacer(Modifier.height(6.dp))
                    GlassBox(onClick = { jumpToAny(pinned.id) }, shape = Capsule(), modifier = Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(2.dp).height(28.dp).clip(Capsule()).background(c.accent))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                T("Pinned Message", TgTheme.type.footnote, c.accent, weight = FontWeight.SemiBold, maxLines = 1)
                                T(pinned.preview, TgTheme.type.footnote, c.text, maxLines = 1)
                            }
                            Icon(TgIcons.MsgPinned, c.secondaryText, 16.dp)
                        }
                    }
                }
            }

            // Bottom: composer / selection / channel bar
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomHeight = it.height }
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Scroll-to-bottom
                val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
                AnimatedVisibility(showDown || newestMissing, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(), modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) {
                    GlassIconButton(IosIcons.ChevronDown, { scrollToBottom() }, size = 44.dp, iconSize = 24.dp)
                }
                when {
                    searchMode -> SearchResultsBar(
                        count = results.size,
                        total = resultTotal,
                        index = resultIndex,
                        query = searchQuery.trim(),
                        busy = searchBusy,
                        onOlder = { focus.clearFocus(); keyboard?.hide(); showResult(resultIndex + 1) },
                        onNewer = { focus.clearFocus(); keyboard?.hide(); showResult(resultIndex - 1) },
                    )
                    selecting -> Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        GlassIconButton(TgIcons.CtxDelete, { if (selected.isNotEmpty()) confirmDelete(selected.toList()) }, tint = c.destructive)
                        GlassIconButton(TgIcons.CtxCopy, {
                            val t = messages.filter { it.id in selected }.joinToString("\n") { it.text ?: it.preview }
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("messages", t))
                            toast.show("Copied ${selected.size} messages")
                            selecting = false; selected.clear()
                        })
                        GlassIconButton(TgIcons.CtxForward, { if (selected.isNotEmpty()) forward(selected.toList()) })
                    }
                    !chat.joined -> ChatBottomBar(if (isChannel) "Join Channel" else "Join Group", { repo.joinChat(chatId) })
                    isChannel && !(repo.isLive && chat.canPost) -> ChannelBottomBar(
                        muted = chat.muted,
                        onDiscuss = if (discussionId != null || !repo.isLive) ({
                            val d = discussionId
                            if (d != null) nav.push(Route.Chat(d)) else toast.show("This channel has no discussion group")
                        }) else null,
                        onMute = { repo.toggleMute(chatId) },
                        onSearch = { searchMode = true; selecting = false },
                    )
                    (chat.type == ChatType.Private || chat.type == ChatType.Bot) && repo.isBlocked(chatId) -> {
                        val bot = chat.type == ChatType.Bot
                        ChatBottomBar(if (bot) "Restart Bot" else "Unblock", {
                            repo.setBlocked(chatId, false) { err ->
                                if (err != null) toast.error(err) else if (bot) repo.startBot(chatId)
                            }
                        })
                    }
                    chat.type == ChatType.Bot && messages.isEmpty() && repo.lastMessage(chatId) == null ->
                        ChatBottomBar("Start", { repo.startBot(chatId) })
                    else -> {
                        val replyKb = repo.replyKeyboard(chatId)
                        val imeVisible = WindowInsets.ime.getBottom(density) > 0
                        if (replyKb != null && hiddenKeyboardId == replyKb.messageId && !panelOpen) {
                            ShowBotKeyboardButton(
                                onClick = {
                                    hiddenKeyboardId = null
                                    focus.clearFocus()
                                    keyboard?.hide()
                                },
                                modifier = Modifier.align(Alignment.Start).padding(start = 12.dp, top = 4.dp),
                            )
                        }
                        // Own recomposition scope: the text is read here, so a keystroke recomposes the composer only.
                        ScopedRead { Composer(
                            text = text,
                            onTextChange = {
                                text = it
                                if (it.isNotBlank()) repo.sendTyping(chatId)
                            },
                            replyTo = replyToId?.let { r -> messages.firstOrNull { it.id == r } },
                            replyName = replyToId?.let { r -> messages.firstOrNull { it.id == r }?.let { repo.senderName(it) } },
                            editing = editingId?.let { e -> messages.firstOrNull { it.id == e } },
                            onCancelContext = { if (editingId != null) text = ""; replyToId = null; editingId = null },
                            panelOpen = panelOpen && !panelHandover,
                            onTogglePanel = {
                                if (panelOpen && !panelHandover) {
                                    // Keyboard up, panel stays (same height) until the keyboard has fully arrived.
                                    panelHandover = true
                                    focusRequester.requestFocus()
                                    keyboard?.show()
                                } else {
                                    // Panel takes the keyboard's place in the same frame.
                                    panelHandover = false
                                    panelOpen = true
                                    focus.clearFocus()
                                    keyboard?.hide()
                                }
                            },
                            onAttach = { focus.clearFocus(); keyboard?.hide(); panelOpen = false; panelHandover = false; attachMenuOpen = true },
                            onSend = { send() },
                            onVoice = { secs, path, wave ->
                                repo.sendContent(
                                    chatId,
                                    MessageContent.Voice(
                                        secs,
                                        wave ?: List(40) { i -> 0.2f + 0.8f * (((i * 53 + secs * 7) % 17) / 17f) },
                                        path?.let { com.abtin.tglass.data.ImageRef(0, it) },
                                    ),
                                    replyToId,
                                )
                                replyToId = null
                            },
                            focusRequester = focusRequester,
                            onFocus = { if (it && panelOpen) panelHandover = true },
                            onEntities = { composeEntities = it },
                            onSendLongPress = if (editingId == null) ({
                                sheet.show(SheetRequest(actions = listOf(SheetAction("Send Without Sound") { sendSilently() })))
                            }) else null,
                            videoRecorder = videoNoteRecorder,
                            onVideoNote = { note ->
                                repo.sendContent(chatId, note.toContent(), replyToId)
                                replyToId = null
                            },
                        ) }
                        AnimatedVisibility(replyKb != null && hiddenKeyboardId != replyKb.messageId && !panelOpen && !imeVisible) {
                            if (replyKb != null) ReplyKeyboardPanel(
                                keyboard = replyKb,
                                onButton = { label, supported ->
                                    if (!supported) {
                                        toast.show("This button isn't supported yet")
                                    } else {
                                        repo.sendText(chatId, label, if (isGroup) replyKb.messageId else null)
                                        if (replyKb.oneTime) hiddenKeyboardId = replyKb.messageId
                                    }
                                },
                                onHide = { hiddenKeyboardId = replyKb.messageId },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                // Keyboard / emoji panel area: exactly as tall as the keyboard, so switching between them never moves the chat.
                KeyboardArea(open = panelOpen, panelHeight = (KeyboardHeight.dp.takeIf { it > 0f } ?: KeyboardHeight.DefaultDp).dp) {
                    EmojiPanel(
                        onEmoji = { e -> text += e },
                        onSticker = { e -> repo.sendContent(chatId, MessageContent.Sticker(e), replyToId); replyToId = null },
                        onGif = { i -> repo.sendContent(chatId, MessageContent.Photo(i + 3, 1.4f, null, "🎞"), replyToId); replyToId = null },
                        onStickerItem = { st -> repo.sendSticker(chatId, st, replyToId); replyToId = null },
                        onGifItem = { g -> repo.sendGif(chatId, g, replyToId); replyToId = null },
                        onBackspace = { text = dropLastGrapheme(text) },
                        onSwitchKeyboard = { panelHandover = true; focusRequester.requestFocus(); keyboard?.show() },
                        chatId = chatId,
                    )
                }
            }

            // "+" menu (iOS 26 glass popup above the composer)
            AttachMenu(
                visible = attachMenuOpen,
                bottom = (with(density) { bottomHeight.toDp() } - 8.dp).coerceAtLeast(0.dp),
                canPoll = !repo.isLive || chat.type != ChatType.Private,
                demo = !repo.isLive,
                onDismiss = { attachMenuOpen = false },
                onPick = { a ->
                    attachMenuOpen = false
                    if (a == AttachAction.Gallery || a == AttachAction.Camera) attachOpen = true
                    if (a != AttachAction.Gallery) attachAction = a
                },
            )

            AttachSheet(
                visible = attachOpen,
                onDismiss = { attachOpen = false },
                onSend = { items ->
                    attachOpen = false
                    repo.sendMedia(chatId, items, replyToId)
                    replyToId = null
                },
                action = attachAction,
                onActionHandled = { attachAction = null },
            )

            forwardIds?.let { ids ->
                com.abtin.tglass.features.media.ForwardSheet(chatId, ids, onDismiss = { forwardIds = null }, onDone = { selecting = false; selected.clear() })
            }

            PollResultsPage(pollResults, onDismiss = { pollResults = null })
            MessageInfoPage(messageInfo, onDismiss = { messageInfo = null })
        }
    }
}

/** Runs [content] in its own recomposition scope: state it reads (e.g. the composer text) recomposes only this part. */
@Composable
private fun ScopedRead(content: @Composable () -> Unit) = content()

fun groupFor(messages: List<Message>, idx: Int, isGroup: Boolean): BubbleGroup {
    val m = messages.getOrNull(idx) ?: return BubbleGroup(false, false, false, false)
    val top = groupable(messages.getOrNull(idx - 1), m)
    val bottom = groupable(m, messages.getOrNull(idx + 1))
    return BubbleGroup(
        groupedTop = top,
        groupedBottom = bottom,
        showName = isGroup && !m.outgoing && !top,
        showAvatar = isGroup && !m.outgoing && !bottom,
    )
}

/** One entry of the message list: a message, or all messages of a media album (shown as one bubble). */
@Immutable
class ChatItem(val messages: List<Message>) {
    val first: Message get() = messages.first()
    val last: Message get() = messages.last()
    /** The message that speaks for the item (the album's captioned one). */
    val head: Message get() = if (messages.size == 1) messages[0] else messages.firstOrNull { it.text != null } ?: messages[0]
    val key: Any get() = if (messages.size > 1) "album-${first.id}" else first.id
    fun contains(id: Long) = messages.any { it.id == id }
}

/** Groups consecutive messages with the same TDLib media album id (at most 10, like Telegram). */
fun buildChatItems(messages: List<Message>): List<ChatItem> {
    val out = ArrayList<ChatItem>(messages.size)
    var i = 0
    while (i < messages.size) {
        val m = messages[i]
        var j = i + 1
        if (m.albumId != 0L) {
            while (j < messages.size && j - i < 10 && messages[j].albumId == m.albumId && messages[j].content !is MessageContent.Service) j++
        }
        out += ChatItem(if (j - i == 1) listOf(m) else messages.subList(i, j).toList())
        i = j
    }
    return out
}

fun groupForItems(items: List<ChatItem>, idx: Int, isGroup: Boolean): BubbleGroup {
    val item = items.getOrNull(idx) ?: return BubbleGroup(false, false, false, false)
    val m = item.head
    val top = groupable(items.getOrNull(idx - 1)?.last, item.first)
    val bottom = groupable(item.last, items.getOrNull(idx + 1)?.first)
    return BubbleGroup(
        groupedTop = top,
        groupedBottom = bottom,
        showName = isGroup && !m.outgoing && !top,
        showAvatar = isGroup && !m.outgoing && !bottom,
    )
}

/** Set before opening a chat from a t.me/…/123 link; the chat screen scrolls to that message. */
object ChatJumpRequest {
    private var chatId: Long? = null
    private var messageId: Long? = null

    fun request(chatId: Long, messageId: Long) {
        this.chatId = chatId
        this.messageId = messageId
    }

    fun take(chatId: Long): Long? {
        if (this.chatId != chatId) return null
        val id = messageId
        this.chatId = null
        messageId = null
        return id
    }
}

private fun senderNameFor(repo: TelegramRepository, m: Message, group: BubbleGroup, isGroup: Boolean): String? =
    if (isGroup && group.showName) repo.user(m.senderId)?.name else null

/** One message line: selection check, avatar column, bubble, swipe-to-reply (spec §12, §19). */
@Composable
private fun MessageRow(
    appear: Boolean,
    m: Message,
    group: BubbleGroup,
    repo: TelegramRepository,
    isGroup: Boolean,
    isChannel: Boolean,
    maxBubble: Dp,
    replyTo: Message?,
    selecting: Boolean,
    selected: Boolean,
    highlighted: Boolean,
    onTap: () -> Unit,
    onLongPress: (Rect) -> Unit,
    onSwipeReply: () -> Unit,
    onReplyClick: () -> Unit,
    onReact: (String) -> Unit,
    onVote: (Int) -> Unit,
    onMedia: () -> Unit,
    keyboard: com.abtin.tglass.data.InlineKeyboard? = null,
    busyButton: (Int, Int) -> Boolean = { _, _ -> false },
    onInlineButton: (Int, Int, com.abtin.tglass.data.InlineButton) -> Unit = { _, _, _ -> },
    album: List<Message> = emptyList(),
    onAlbumItemClick: (Long) -> Unit = {},
    onSenderClick: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    pollActions: PollActions? = null,
) {
    val c = TgTheme.colors
    val menu = LocalContextMenu.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Only the coordinates are kept (computing boundsInRoot for every bubble on every scroll frame was wasted work).
    val coords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    fun currentBounds(): Rect = coords[0]?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
    val swipe = remember { Animatable(0f) }
    val threshold = with(density) { 64.dp.toPx() }
    val key = "msg-${m.id}"
    // New messages rise from the composer (outgoing) or pop in (incoming) with a spring.
    val enter = remember { Animatable(if (appear) 0f else 1f) }
    LaunchedEffect(Unit) { if (enter.value < 1f) enter.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 380f)) }

    LaunchedEffect(m.id) {
        if (com.abtin.tglass.DebugLaunch.autoMenuMessageId == m.id) {
            kotlinx.coroutines.delay(1500)
            com.abtin.tglass.DebugLaunch.autoMenuMessageId = null
            onLongPress(currentBounds())
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = enter.value
                if (p < 1f) {
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * (if (m.outgoing) 90.dp.toPx() else 24.dp.toPx())
                    val sc = 0.86f + 0.14f * p
                    scaleX = sc; scaleY = sc
                    transformOrigin = TransformOrigin(if (m.outgoing) 1f else 0f, 1f)
                }
            }
            .background(if (highlighted) c.accent.copy(alpha = 0.18f) else if (selected) c.accent.copy(alpha = 0.10f) else Color.Transparent)
            .pointerInput(selecting) {
                if (selecting) return@pointerInput
                var crossed = false
                detectHorizontalDragGestures(
                    onDragStart = { crossed = false },
                    onDragEnd = {
                        if (-swipe.value >= threshold) onSwipeReply()
                        scope.launch { swipe.animateTo(0f, tween(200)) }
                    },
                    onDragCancel = { scope.launch { swipe.animateTo(0f) } },
                ) { change, drag ->
                    val next = (swipe.value + drag).coerceIn(-threshold * 1.4f, 0f)
                    if (next != swipe.value) change.consume()
                    val over = -next >= threshold
                    if (over != crossed) { crossed = over; if (over) Haptics.tick(view) }
                    scope.launch { swipe.snapTo(next) }
                }
            }
            .pointerInput(selecting) { if (selecting) detectTapGestures { onTap() } }
            .padding(horizontal = 6.dp)
    ) {
        // Reply arrow revealed by the swipe
        val progress = (-swipe.value / threshold).coerceIn(0f, 1f)
        if (progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(32.dp)
                    .graphicsLayer { scaleX = progress; scaleY = progress; alpha = progress }
                    .clip(CircleShape)
                    .background(c.serviceBubble),
                contentAlignment = Alignment.Center,
            ) { Icon(TgIcons.CtxReply, Color.White, 20.dp) }
        }
        Row(
            Modifier.fillMaxWidth().offset { IntOffset(swipe.value.roundToInt(), 0) },
            verticalAlignment = Alignment.Bottom,
        ) {
            if (selecting) {
                Box(
                    Modifier
                        .padding(start = 4.dp, end = 8.dp, bottom = 6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.background(c.accent) else Modifier.border(1.5.dp, Color.White.copy(0.9f), CircleShape).background(Color.Black.copy(0.1f))),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(IosIcons.Checkmark, Color.White, 15.dp) }
            }
            if (isGroup && !m.outgoing) {
                Box(Modifier.width(38.dp)) { // Telegram-iOS avatarInset = 34 + 4
                    if (group.showAvatar) {
                        val u = repo.user(m.senderId)
                        val chatSender = if (u == null) repo.chat(m.senderId) else null
                        com.abtin.tglass.ui.components.Avatar(
                            u?.name ?: chatSender?.title ?: "?", m.senderId, 34.dp,
                            modifier = if (onSenderClick != null) Modifier.fadeClickable(onClick = onSenderClick) else Modifier,
                        )
                    }
                }
            }
            if (m.outgoing) Spacer(Modifier.weight(1f))
            val bubble: @Composable () -> Unit = { Box(
                Modifier
                    .onGloballyPositioned { coords[0] = it }
                    .graphicsLayer { alpha = if (menu.activeKey == key) 0f else 1f }
                    .messageLongPress(enabled = !selecting) { onLongPress(currentBounds()) }
                    .pointerInput(selecting) {
                        if (!selecting) detectTapGestures(onDoubleTap = { Haptics.tap(view); onReact("👍") })
                    }
            ) {
                MessageBubble(
                    m = m,
                    group = group,
                    senderName = if (isGroup && group.showName) (repo.user(m.senderId)?.name ?: repo.chat(m.senderId)?.title) else null,
                    senderSeed = m.senderId,
                    replyTo = replyTo,
                    replyName = replyTo?.let { repo.senderName(it) },
                    isChannel = isChannel,
                    maxWidth = maxBubble,
                    onReplyClick = onReplyClick,
                    onReact = onReact,
                    onVote = onVote,
                    onMediaClick = onMedia,
                    album = album,
                    onAlbumItemClick = onAlbumItemClick,
                    onSenderClick = onSenderClick,
                    pollActions = pollActions,
                )
            } }
            if (keyboard == null) bubble()
            else BubbleWithKeyboard(
                alignEnd = m.outgoing,
                maxWidth = maxBubble,
                bubble = bubble,
                keyboard = { InlineKeyboardView(keyboard, m.outgoing, busyButton, onInlineButton) },
            )
            if (onShare != null && !m.outgoing && !selecting) {
                // Channel posts: round share button next to the bubble (Telegram iOS).
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .padding(bottom = 2.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c.serviceBubble)
                        .fadeClickable(onClick = onShare),
                    contentAlignment = Alignment.Center,
                ) { Icon(TgIcons.CtxForward, Color.White, 17.dp) }
            }
        }
    }
}

/** Long-press peek of a chat from the chat list (spec §8). */
@Composable
fun ChatPeek(chatId: Long, onOpen: () -> Unit) {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    val chat = repo.chat(chatId) ?: return
    val messages = repo.messages(chatId).takeLast(12)
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.68f).dp
    val isGroup = chat.type == ChatType.Group
    Box(
        Modifier
            .fillMaxSize()
            .clip(com.kyant.shapes.RoundedRectangle(28.dp))
            .pointerInput(Unit) { detectTapGestures { onOpen() } }
    ) {
        ChatWallpaper()
        LazyColumn(
            reverseLayout = true,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 64.dp, bottom = 8.dp),
        ) {
            itemsIndexed(messages.asReversed(), key = { _, m -> m.id }) { rIdx, m ->
                val idx = messages.size - 1 - rIdx
                val group = groupFor(messages, idx, isGroup)
                if (m.content is MessageContent.Service) {
                    ServicePill((m.content as MessageContent.Service).text)
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = if (group.groupedBottom) 1.dp else 3.dp)) {
                        if (m.outgoing) Spacer(Modifier.weight(1f))
                        MessageBubble(
                            m, group, senderNameFor(repo, m, group, isGroup), m.senderId,
                            null, null, chat.type == ChatType.Channel, maxBubble,
                        )
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (c.isDark) Color(0xFF1C1C1E).copy(0.92f) else Color.White.copy(0.92f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatAvatar(chat, repo, 38.dp, showOnline = false)
            Spacer(Modifier.width(10.dp))
            Column {
                com.abtin.tglass.core.emoji.EmojiText(chat.title, TgTheme.type.headline, c.text, maxLines = 1)
                val (sub, active) = chatSubtitle(chat, repo)
                if (sub != null) T(sub, TgTheme.type.footnote, if (active) c.accent else c.secondaryText, maxLines = 1)
            }
        }
    }
}


/** Set by the profile's "Search" button; the chat screen picks it up and opens in-chat search. */
object ChatSearchRequest {
    var chatId by androidx.compose.runtime.mutableStateOf<Long?>(null)
}

@Composable
private fun UnreadDivider() {
    val c = TgTheme.colors
    Box(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).background(c.background.copy(alpha = 0.75f)).padding(vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        T("Unread Messages", TgTheme.type.footnote, c.secondaryText, weight = FontWeight.Medium)
    }
}

/**
 * Poll entries of the long-press menu. With [retract] true: non-null when the user can take the vote back.
 * With [retract] false: non-null (whether it is a quiz) when the user can stop the poll (their own open poll).
 */
private fun pollMenuAction(m: Message, retract: Boolean): Boolean? {
    val p = m.content as? MessageContent.Poll ?: return null
    if (p.closed) return null
    return if (retract) {
        val voted = p.chosen.isNotEmpty() || p.voted != null
        if (voted && !p.quiz && p.canRetract) true else null
    } else {
        val sent = m.status != com.abtin.tglass.data.MessageStatus.Sending && m.status != com.abtin.tglass.data.MessageStatus.Failed
        if (m.outgoing && sent) p.quiz else null
    }
}

@Composable
private fun ChatSearchField(value: String, onValue: (String) -> Unit, modifier: Modifier, onSubmit: () -> Unit) {
    val c = TgTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Let the page settle (it may still be sliding back in from the profile) before bringing up the keyboard.
        kotlinx.coroutines.delay(150)
        runCatching { focus.requestFocus() }
    }
    GlassBox(onClick = null, shape = Capsule(), modifier = modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(TgIcons.IcSearch, c.secondaryText, 18.dp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) T("Search", TgTheme.type.body, c.secondaryText, maxLines = 1)
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValue,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    singleLine = true,
                    textStyle = TgTheme.type.body.copy(color = c.text, textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit() }),
                )
            }
            if (value.isNotEmpty()) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).fadeClickable { onValue("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(17.dp).clip(CircleShape).background(c.secondaryText.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        Icon(IosIcons.Close, c.background, 9.dp)
                    }
                }
            }
        }
    }
}

/** iOS search toolbar at the bottom: "3 of 12" (or a spinner / No results) with the up (older) and down (newer) arrows. */
@Composable
private fun SearchResultsBar(count: Int, total: Int, index: Int, query: String, busy: Boolean, onOlder: () -> Unit, onNewer: () -> Unit) {
    val c = TgTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.weight(1f).height(46.dp)) {
            if (busy && count == 0) {
                com.abtin.tglass.ui.components.ActivityIndicator(18.dp, c.secondaryText)
            } else {
                T(
                    when {
                        query.isEmpty() -> "Search messages"
                        count == 0 -> "No results"
                        else -> "${index + 1} of ${maxOf(total, count)}"
                    },
                    TgTheme.type.body, if (query.isNotEmpty() && count == 0) c.secondaryText else c.text, maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        val canOlder = index < count - 1
        val canNewer = index > 0 && count > 0
        GlassIconButton(
            IosIcons.ChevronDown, onOlder,
            modifier = Modifier.graphicsLayer { rotationZ = 180f; alpha = if (canOlder) 1f else 0.4f },
            size = 46.dp, iconSize = 20.dp, tint = if (canOlder) c.accent else c.secondaryText,
        )
        Spacer(Modifier.width(8.dp))
        GlassIconButton(
            IosIcons.ChevronDown, onNewer,
            modifier = Modifier.graphicsLayer { alpha = if (canNewer) 1f else 0.4f },
            size = 46.dp, iconSize = 20.dp, tint = if (canNewer) c.accent else c.secondaryText,
        )
    }
}
