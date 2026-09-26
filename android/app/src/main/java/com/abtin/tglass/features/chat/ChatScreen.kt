package com.abtin.tglass.features.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.Chat
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.data.senderName
import com.abtin.tglass.features.chatlist.ChatAvatar
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ContextMenuRequest
import com.abtin.tglass.ui.components.DefaultReactions
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

private fun sameDay(a: Long, b: Long): Boolean {
    val z = ZoneId.systemDefault()
    return Instant.ofEpochMilli(a).atZone(z).toLocalDate() == Instant.ofEpochMilli(b).atZone(z).toLocalDate()
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
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(if (repo.isLive) 4_000L else 0L)
            nav.pop()
        }
        Box(Modifier.fillMaxSize().background(c.background))
        return
    }
    val backdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    var text by rememberSaveable { mutableStateOf(chat.draft ?: "") }
    var replyToId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    var attachOpen by rememberSaveable { mutableStateOf(false) }
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
    val isGroup = chat.type == ChatType.Group
    val isChannel = chat.type == ChatType.Channel
    // Telegram-iOS ChatMessageItemCommon: compactInset 36 (+ avatarInset 38 in groups).
    val maxBubble: Dp = (LocalConfiguration.current.screenWidthDp - 36 - (if (isGroup) 38 else 0)).dp
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
    DisposableEffect(Unit) {
        onDispose {
            repo.setDraft(chatId, text.takeIf { editingId == null })
            repo.closeChat(chatId)
        }
    }
    // Reverse layout: the oldest loaded message is the last item, so near-the-end means "load older".
    val nearTop by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= listState.layoutInfo.totalItemsCount - 6 } == true } }
    LaunchedEffect(nearTop, messages.size) { if (nearTop && messages.isNotEmpty()) repo.loadOlderMessages(chatId) }
    LaunchedEffect(messages.size) {
        if (!initialScrollDone && messages.isNotEmpty()) {
            // Open at the first unread message, with the divider in the upper part of the screen.
            val idx = firstUnreadId?.let { id -> reversed.indexOfFirst { it.id == id } } ?: -1
            if (idx > 0) {
                listState.scrollToItem(idx)
                listState.scrollBy(-listState.layoutInfo.viewportSize.height * 0.55f)
            }
            if (idx >= 0) initialScrollDone = true
            return@LaunchedEffect
        }
        val last = messages.lastOrNull()
        if (last != null && last.outgoing && last.date > openedAt) wallpaperPhase++
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    fun jumpTo(id: Long) {
        val idx = reversed.indexOfFirst { it.id == id }
        if (idx >= 0) scope.launch {
            listState.animateScrollToItem(idx)
            highlightId = id
            kotlinx.coroutines.delay(1200)
            highlightId = -1
        }
    }

    /** Scrolls to a message, loading the history around it first if needed. */
    fun jumpToAny(id: Long) {
        if (messages.any { it.id == id }) jumpTo(id) else repo.loadAround(chatId, id) { pendingJump = id }
    }
    LaunchedEffect(pendingJump, messages.size) {
        val id = pendingJump ?: return@LaunchedEffect
        if (reversed.any { it.id == id }) {
            pendingJump = null
            jumpTo(id)
        }
    }
    val currentSearch = androidx.compose.runtime.rememberUpdatedState(searchQuery.trim())
    LaunchedEffect(searchQuery, searchMode) {
        results = emptyList()
        resultIndex = 0
        val q = searchQuery.trim()
        if (!searchMode || q.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        repo.searchInChat(chatId, q) { found ->
            if (currentSearch.value == q) {
                results = found
                found.firstOrNull()?.let { jumpToAny(it.id) }
            }
        }
    }

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
            repo.sendText(chatId, t, replyToId)
            replyToId = null
        }
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
        if (id != null) nav.push(Route.Chat(id)) else toast.show("No one uses @${name.removePrefix("@")}")
    }
    fun openUrl(raw: String) {
        val url = if (raw.startsWith("http", ignoreCase = true) || raw.startsWith("tg:", ignoreCase = true)) raw else "https://$raw"
        if (com.abtin.tglass.data.ProxyItem.fromLink(url) != null) {
            nav.push(Route.ProxyLink(url))
            return
        }
        // t.me/username links open inside the app, like Telegram.
        Regex("""(?i)^https?://(?:www\.)?(?:t|telegram)\.me/([A-Za-z][A-Za-z0-9_]{3,31})/?$""").find(url)?.let {
            openUsername(it.groupValues[1])
            return
        }
        runCatching { uriHandler.openUri(url) }.onFailure { toast.show("Can't open this link") }
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

    fun forward(ids: List<Long>) {
        val targets = repo.chats.filter { !it.archived && it.type != ChatType.Channel }.take(6)
        sheet.show(SheetRequest(title = "Forward to…", actions = targets.map { t ->
            SheetAction(t.title) {
                repo.forward(chatId, ids, t.id)
                toast.show("Forwarded to ${t.title}")
            }
        }))
    }

    fun confirmDelete(ids: List<Long>) {
        val chosen = messages.filter { it.id in ids }
        val done = { selected.clear(); selecting = false }
        val one = ids.size == 1
        // Telegram lets you delete for everyone in private chats and for your own messages elsewhere.
        val canRevoke = repo.isLive && chat.type != ChatType.Saved &&
            (chat.type == ChatType.Private || chat.type == ChatType.Bot || chosen.all { it.outgoing })
        val actions = if (canRevoke) listOf(
            SheetAction(if (chat.type == ChatType.Private) "Delete for Me and ${chat.title.substringBefore(' ')}" else "Delete for Everyone", destructive = true) {
                repo.deleteMessages(chatId, ids.toSet(), forEveryone = true); done()
            },
            SheetAction("Delete for Me", destructive = true) { repo.deleteMessages(chatId, ids.toSet(), forEveryone = false); done() },
        ) else listOf(
            SheetAction(if (one) "Delete Message" else "Delete ${ids.size} Messages", destructive = true) {
                repo.deleteMessages(chatId, ids.toSet(), forEveryone = chat.type == ChatType.Channel); done()
            },
        )
        sheet.show(SheetRequest(title = if (canRevoke) (if (one) "Delete this message?" else "Delete ${ids.size} messages?") else null, actions = actions))
    }

    fun openMenu(m: Message, bounds: Rect) {
        focus.clearFocus()
        keyboard?.hide()
        val hasText = m.text != null
        // Photos, videos, GIFs and files can get a caption even when they have none yet (round video notes can't).
        val captionable = when (val ct = m.content) {
            is MessageContent.Photo -> ct.emoji != "📹"
            is MessageContent.File -> true
            else -> false
        }
        val actions = listOfNotNull(
            if (!isChannel) MenuAction("Reply", TgIcons.CtxReply) { replyToId = m.id; editingId = null; focusRequester.requestFocus() } else null,
            if (hasText) MenuAction("Copy", TgIcons.CtxCopy) { copy(m) } else null,
            if (m.outgoing && (hasText || captionable)) MenuAction("Edit", TgIcons.CtxEdit) { editingId = m.id; replyToId = null; text = m.text ?: ""; focusRequester.requestFocus() } else null,
            MenuAction(if (m.pinned) "Unpin" else "Pin", if (m.pinned) TgIcons.CtxUnpin else TgIcons.CtxPin) { repo.togglePinMessage(chatId, m.id) },
            MenuAction("Forward", TgIcons.CtxForward) { forward(listOf(m.id)) },
            if (chat.type != ChatType.Saved) MenuAction("Save to Saved Messages", TgIcons.CtxSave) { repo.forward(chatId, listOf(m.id), repo.savedChatId); toast.show("Saved to Saved Messages") } else null,
            MenuAction("Select", TgIcons.CtxSelect) { selecting = true; selected.clear(); selected.add(m.id) },
            MenuAction("Delete", TgIcons.CtxDelete, destructive = true, groupStart = true) { confirmDelete(listOf(m.id)) },
        )
        menu.show(
            ContextMenuRequest(
                key = "msg-${m.id}",
                anchor = bounds,
                alignEnd = m.outgoing,
                actions = actions,
                reactions = if (m.content is MessageContent.Service) null else DefaultReactions,
                onReact = { e -> repo.toggleReaction(chatId, m.id, e) },
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

    androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides backdrop, LocalLinkHandler provides linkHandler) {
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
                    itemsIndexed(reversed, key = { _, m -> m.id }) { rIdx, m ->
                        val idx = messages.size - 1 - rIdx
                        val prev = messages.getOrNull(idx - 1)
                        val group = groupFor(messages, idx, isGroup)
                        Column(Modifier.animateItem()) {
                            if (prev == null || !sameDay(prev.date, m.date)) ServicePill(formatDay(m.date))
                            if (m.id == firstUnreadId) UnreadDivider()
                            if (m.content is MessageContent.Service) {
                                ServicePill((m.content as MessageContent.Service).text)
                            } else {
                                MessageRow(
                                    appear = m.date > openedAt,
                                    m = m,
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
                                    selected = m.id in selected,
                                    highlighted = highlightId == m.id,
                                    onTap = {
                                        if (selecting) {
                                            if (m.id in selected) selected.remove(m.id) else selected.add(m.id)
                                            if (selected.isEmpty()) selecting = false
                                        }
                                    },
                                    onLongPress = { b -> if (!selecting) { Haptics.longPress(view); openMenu(m, b) } },
                                    onSwipeReply = { replyToId = m.id; editingId = null; focusRequester.requestFocus() },
                                    onReplyClick = { m.replyToId?.let { jumpToAny(it) } },
                                    onReact = { e -> repo.toggleReaction(chatId, m.id, e) },
                                    onVote = { o -> repo.vote(chatId, m.id, o) },
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
                        ChatSearchField(searchQuery, { searchQuery = it }, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        GlassTextButton("Cancel", { searchMode = false; searchQuery = ""; focus.clearFocus() })
                    } else if (selecting) {
                        GlassTextButton("Cancel", { selecting = false; selected.clear() })
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            GlassBox(onClick = null, modifier = Modifier.height(44.dp)) {
                                T("${selected.size} Selected", TgTheme.type.headline, c.text, modifier = Modifier.padding(horizontal = 18.dp))
                            }
                        }
                        Spacer(Modifier.width(80.dp))
                    } else {
                        BackButton({ nav.pop() }, badge = repo.chats.filter { it.id != chatId && !it.archived && !it.muted }.sumOf { it.unread })
                        Box(Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                            val (subtitle, active) = chatSubtitle(chat, repo)
                            GlassBox(onClick = { nav.push(Route.Profile(chatId)) }, modifier = Modifier.height(48.dp)) {
                                Column(Modifier.padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    T(chat.title, TgTheme.type.headline.copy(fontSize = TgTheme.type.headline.fontSize * 0.95f), c.text, maxLines = 1)
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
                // Pinned message bar
                if (pinned != null && !selecting) {
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
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            ) {
                // Scroll-to-bottom
                val showDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
                AnimatedVisibility(showDown, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(), modifier = Modifier.align(Alignment.End).padding(end = 12.dp)) {
                    GlassIconButton(IosIcons.ChevronDown, { scope.launch { listState.animateScrollToItem(0) } }, size = 44.dp, iconSize = 24.dp)
                }
                when {
                    searchMode -> SearchResultsBar(
                        count = results.size,
                        index = resultIndex,
                        searching = searchQuery.isNotBlank(),
                        onOlder = { if (resultIndex < results.lastIndex) { resultIndex++; jumpToAny(results[resultIndex].id) } },
                        onNewer = { if (resultIndex > 0) { resultIndex--; jumpToAny(results[resultIndex].id) } },
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
                    !chat.joined -> GlassBox(
                        onClick = { repo.joinChat(chatId) },
                        shape = Capsule(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(46.dp),
                    ) { T(if (isChannel) "Join Channel" else "Join Group", TgTheme.type.body, c.accent, weight = FontWeight.SemiBold) }
                    isChannel && !(repo.isLive && chat.canPost) -> GlassBox(
                        onClick = { repo.toggleMute(chatId) },
                        shape = Capsule(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(46.dp),
                    ) { T(if (chat.muted) "Unmute" else "Mute", TgTheme.type.body, c.accent, weight = FontWeight.Medium) }
                    (chat.type == ChatType.Private || chat.type == ChatType.Bot) && repo.isBlocked(chatId) -> {
                        val bot = chat.type == ChatType.Bot
                        ChatBottomBar(if (bot) "Restart" else "Unblock", {
                            repo.setBlocked(chatId, false) { err ->
                                if (err != null) toast.show(err) else if (bot) repo.startBot(chatId)
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
                        Composer(
                            text = text,
                            onTextChange = {
                                text = it
                                if (it.isNotBlank()) repo.sendTyping(chatId)
                            },
                            replyTo = replyToId?.let { r -> messages.firstOrNull { it.id == r } },
                            replyName = replyToId?.let { r -> messages.firstOrNull { it.id == r }?.let { repo.senderName(it) } },
                            editing = editingId?.let { e -> messages.firstOrNull { it.id == e } },
                            onCancelContext = { if (editingId != null) text = ""; replyToId = null; editingId = null },
                            panelOpen = panelOpen,
                            onTogglePanel = {
                                if (panelOpen) {
                                    panelOpen = false
                                    focusRequester.requestFocus()
                                    keyboard?.show()
                                } else {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                    panelOpen = true
                                }
                            },
                            onAttach = { focus.clearFocus(); keyboard?.hide(); panelOpen = false; attachOpen = true },
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
                            onFocus = { if (it) panelOpen = false },
                            onSendLongPress = if (editingId == null) ({
                                sheet.show(SheetRequest(actions = listOf(SheetAction("Send Without Sound") { sendSilently() })))
                            }) else null,
                        )
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
                        AnimatedVisibility(panelOpen) {
                            EmojiPanel(
                                onEmoji = { e -> text += e },
                                onSticker = { e -> repo.sendContent(chatId, MessageContent.Sticker(e), replyToId); replyToId = null },
                                onGif = { i -> repo.sendContent(chatId, MessageContent.Photo(i + 3, 1.4f, null, "🎞"), replyToId); replyToId = null },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                onStickerItem = { st -> repo.sendSticker(chatId, st, replyToId); replyToId = null },
                                onGifItem = { g -> repo.sendGif(chatId, g, replyToId); replyToId = null },
                            )
                        }
                    }
                }
            }

            AttachSheet(
                visible = attachOpen,
                onDismiss = { attachOpen = false },
                onSend = { items ->
                    attachOpen = false
                    repo.sendMedia(chatId, items, replyToId)
                    replyToId = null
                },
            )
        }
    }
}

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
) {
    val c = TgTheme.colors
    val menu = LocalContextMenu.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bounds = remember { arrayOf(Rect.Zero) }
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
            onLongPress(bounds[0])
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
                        com.abtin.tglass.ui.components.Avatar(u?.name ?: "?", m.senderId, 34.dp)
                    }
                }
            }
            if (m.outgoing) Spacer(Modifier.weight(1f))
            val bubble: @Composable () -> Unit = { Box(
                Modifier
                    .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
                    .graphicsLayer { alpha = if (menu.activeKey == key) 0f else 1f }
                    .pointerInput(selecting) {
                        if (!selecting) detectTapGestures(
                            onLongPress = { onLongPress(bounds[0]) },
                            onDoubleTap = { Haptics.tap(view); onReact("👍") },
                        )
                    }
            ) {
                MessageBubble(
                    m = m,
                    group = group,
                    senderName = if (isGroup && group.showName) repo.user(m.senderId)?.name else null,
                    senderSeed = m.senderId,
                    replyTo = replyTo,
                    replyName = replyTo?.let { repo.senderName(it) },
                    isChannel = isChannel,
                    maxWidth = maxBubble,
                    onReplyClick = onReplyClick,
                    onReact = onReact,
                    onVote = onVote,
                    onMediaClick = onMedia,
                )
            } }
            if (keyboard == null) bubble()
            else BubbleWithKeyboard(
                alignEnd = m.outgoing,
                maxWidth = maxBubble,
                bubble = bubble,
                keyboard = { InlineKeyboardView(keyboard, m.outgoing, busyButton, onInlineButton) },
            )
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
                T(chat.title, TgTheme.type.headline, c.text, maxLines = 1)
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

@Composable
private fun ChatSearchField(value: String, onValue: (String) -> Unit, modifier: Modifier) {
    val c = TgTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    GlassBox(onClick = null, shape = Capsule(), modifier = modifier.height(44.dp), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            if (value.isEmpty()) T("Search", TgTheme.type.body, c.secondaryText)
            androidx.compose.foundation.text.BasicTextField(
                value, onValue,
                Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                textStyle = TgTheme.type.body.copy(color = c.text),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            )
        }
    }
}

@Composable
private fun SearchResultsBar(count: Int, index: Int, searching: Boolean, onOlder: () -> Unit, onNewer: () -> Unit) {
    val c = TgTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.weight(1f).height(46.dp)) {
            T(
                when {
                    !searching -> "Search messages"
                    count == 0 -> "No results"
                    else -> "${index + 1} of $count"
                },
                TgTheme.type.body, c.text,
            )
        }
        Spacer(Modifier.width(8.dp))
        GlassIconButton(IosIcons.ChevronDown, onOlder, modifier = Modifier.graphicsLayer { rotationZ = 180f }, size = 46.dp, iconSize = 20.dp)
        Spacer(Modifier.width(8.dp))
        GlassIconButton(IosIcons.ChevronDown, onNewer, size = 46.dp, iconSize = 20.dp)
    }
}
