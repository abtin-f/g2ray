package com.abtin.tglass.features.chatlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.abtin.tglass.ui.components.TgAnimations
import kotlin.math.roundToInt
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.ui.components.GlassButtonGroup
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.ScrollEdgeBlur
import com.abtin.tglass.ui.components.TgIcons
import kotlinx.coroutines.launch
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.outlined.MarkChatUnread
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.Chat
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.features.chat.ChatPeek
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.features.main.TabBarController
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.ChipTabs
import com.abtin.tglass.ui.components.ContextMenuRequest
import com.abtin.tglass.ui.components.EmptyState
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalContextMenu
import com.abtin.tglass.ui.components.MenuAction
import com.abtin.tglass.ui.components.SearchField
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.StoryRing
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TextButton
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatListDate
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.shapes.Capsule

@Composable
fun ChatListScreen(backdrop: LayerBackdrop, tabBar: TabBarController) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val sheet = LocalActionSheet.current
    val toast = com.abtin.tglass.ui.components.LocalToast.current
    val c = TgTheme.colors
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }

    var folder by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() }
    val listState = rememberLazyListState()

    LaunchedEffect(searching, editing) { tabBar.hidden = searching || editing }
    DisposableEffect(Unit) { onDispose { tabBar.hidden = false } }
    LaunchedEffect(searching) { if (searching) searchFocus.requestFocus() }
    // Server search (live accounts), debounced; results for an outdated query are dropped.
    var global by remember { mutableStateOf<com.abtin.tglass.data.GlobalResults?>(null) }
    val currentQuery = rememberUpdatedState(query.trim())
    LaunchedEffect(query, searching) {
        global = null
        val q = query.trim()
        if (searching && repo.isLive && q.length >= 2) {
            kotlinx.coroutines.delay(350)
            repo.searchGlobal(q) { r -> if (currentQuery.value == q) global = r }
        }
    }
    var handledSearch by rememberSaveable { mutableIntStateOf(tabBar.searchRequests) }
    LaunchedEffect(tabBar.searchRequests) {
        if (tabBar.searchRequests > handledSearch) {
            handledSearch = tabBar.searchRequests
            searching = true
        }
    }

    // Stories: collapsed into the title; pulling the list down expands them (Telegram-iOS behaviour).
    val storyUsers = repo.storyUsers
    val hasStories = rememberUpdatedState(storyUsers.isNotEmpty())
    val storiesMax = with(density) { 104.dp.toPx() }
    var storiesPx by rememberSaveable { mutableFloatStateOf(0f) }
    val storiesFraction = (storiesPx / storiesMax).coerceIn(0f, 1f)
    // Like Telegram iOS 26, the stories row starts expanded at the top of the list; scrolling collapses it.
    var storiesShownOnce by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(storyUsers.isNotEmpty()) {
        if (storyUsers.isNotEmpty() && !storiesShownOnce) {
            storiesShownOnce = true
            animate(storiesPx, storiesMax, animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)) { v, _ -> storiesPx = v }
        }
    }
    // Hidden archive (Telegram-iOS): the "Archived Chats" row sits hidden above the chats; pulling the list down
    // at the top (after the stories are fully expanded) reveals it. Released past half its height it stays
    // revealed until the list is scrolled up again.
    val context = androidx.compose.ui.platform.LocalContext.current
    remember { ArchivePrefs.init(context) }
    val archiveRowPx = with(density) { com.abtin.tglass.core.design.LocalAppSettings.current.chatListSize.row.dp.toPx() }
    val archiveRowMax = rememberUpdatedState(archiveRowPx)
    var archivePx by remember { mutableFloatStateOf(0f) }
    val archiveRevealable = rememberUpdatedState(
        ArchivePrefs.hidden && folder == 0 && !editing && !searching && repo.chats.any { it.archived }
    )
    LaunchedEffect(archiveRevealable.value) { if (!archiveRevealable.value) archivePx = 0f }
    val view = androidx.compose.ui.platform.LocalView.current
    // Read through derivedStateOf so pulling does not recompose the whole screen every frame.
    val archiveFull by remember { androidx.compose.runtime.derivedStateOf { archivePx > 0f && archivePx >= archiveRowMax.value - 0.5f } }
    LaunchedEffect(archiveFull) { if (archiveFull) com.abtin.tglass.ui.components.Haptics.tick(view) }

    val storiesConnection = remember(storiesMax) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f) {
                    // Scrolling up: the revealed archive goes first, then the expanded stories.
                    var left = available.y
                    if (archivePx > 0f) {
                        val d = maxOf(left, -archivePx)
                        archivePx += d
                        left -= d
                    }
                    if (left < 0f && storiesPx > 0f) {
                        val d = maxOf(left, -storiesPx)
                        storiesPx += d
                        left -= d
                    }
                    return Offset(0f, available.y - left)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
                if (hasStories.value && storiesPx < storiesMax) {
                    val d = minOf(available.y * 0.6f, storiesMax - storiesPx)
                    storiesPx += d
                    return Offset(0f, available.y)
                }
                val rowMax = archiveRowMax.value
                if (archiveRevealable.value && archivePx < rowMax) {
                    // Rubber-band resistance like UIScrollView overscroll.
                    val d = minOf(available.y * 0.55f, rowMax - archivePx)
                    archivePx += d
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val rowMax = archiveRowMax.value
                val archiveMid = archivePx in 0.5f..(rowMax - 0.5f)
                val storiesMid = storiesPx in 0.5f..(storiesMax - 0.5f)
                if (!archiveMid && !storiesMid) return Velocity.Zero
                kotlinx.coroutines.coroutineScope {
                    if (archiveMid) launch {
                        val target = if (available.y > 600f || (available.y > -600f && archivePx > rowMax * 0.5f)) rowMax else 0f
                        animate(archivePx, target, animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f)) { v, _ -> archivePx = v }
                    }
                    if (storiesMid) launch {
                        val target = if (available.y > 600f || (available.y > -600f && storiesPx > storiesMax * 0.45f)) storiesMax else 0f
                        animate(storiesPx, target, animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)) { v, _ -> storiesPx = v }
                    }
                }
                return available
            }
        }
    }

    val all = repo.chats.filter { !it.archived }
    val folders = repo.folders
    if (folder >= folders.size) folder = 0
    fun inFolder(chat: Chat, f: Int) = repo.isInFolder(chat, f)
    val chats = all.filter { inFolder(it, folder) }
    val archived = repo.chats.filter { it.archived }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val headerHeight = 56.dp
    val storiesHeight = with(density) { storiesPx.toDp() }

    fun confirmDelete(chat: Chat) {
        val what = when (chat.type) {
            ChatType.Channel -> "Leave Channel"
            ChatType.Group -> "Delete and Leave"
            else -> "Delete Chat"
        }
        sheet.show(SheetRequest(title = "Are you sure you want to delete the chat with ${chat.title}?", actions = listOf(SheetAction(what, destructive = true) { repo.deleteChat(chat.id) })))
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(storiesConnection)
                .layerBackdrop(backdrop),
            contentPadding = PaddingValues(top = top + headerHeight + 4.dp + if (searching) 0.dp else storiesHeight, bottom = bottom + 112.dp),
        ) {
            if (searching) {
                searchResults(repo, query, global, onOpen = { focus.clearFocus(); nav.push(Route.Chat(it)) })
            } else {
                item(key = "folders") {
                    if (folders.size > 1) FolderTabs(folders, folder, { folder = it }, unreadFor = { f -> all.count { ch -> inFolder(ch, f) && (ch.unread > 0 || ch.markedUnread) && !ch.muted } })
                }
                if (archived.isNotEmpty() && folder == 0 && !editing) {
                    item(key = "archive") {
                        val hidden = ArchivePrefs.hidden
                        ArchiveItem(
                            archived = archived,
                            repo = repo,
                            hidden = hidden,
                            // Hidden: only the pulled-down part is laid out (bottom-aligned, clipped), so the row
                            // slides out from under the folders capsule as the list is pulled.
                            modifier = if (hidden) Modifier.revealHeight({ archivePx }, { archiveRowPx }) else Modifier,
                            onHide = {
                                ArchivePrefs.updateHidden(true)
                                scope.launch {
                                    animate(archivePx, 0f, animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f)) { v, _ -> archivePx = v }
                                }
                                toast.show("Archive hidden. Pull down the chat list to see it.", IosIcons.ArrowUp)
                            },
                            onPin = {
                                archivePx = 0f
                                ArchivePrefs.updateHidden(false)
                            },
                            onClick = { nav.push(Route.Archive) },
                        )
                    }
                }
                if (chats.isEmpty()) {
                    item(key = "empty") {
                        if (repo.isLive && all.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) { com.abtin.tglass.ui.components.ActivityIndicator(28.dp) }
                        } else {
                            EmptyState("💬", "No Chats", "There are no chats in this folder yet.", animation = com.abtin.tglass.ui.components.TgAnimations.ChatListEmpty)
                        }
                    }
                }
                items(chats, key = { it.id }) { chat ->
                    ChatListItem(
                        chat = chat,
                        repo = repo,
                        editing = editing,
                        selected = chat.id in selected,
                        modifier = Modifier.animateItem(),
                        onDelete = { confirmDelete(chat) },
                        onClick = {
                            if (editing) {
                                if (chat.id in selected) selected.remove(chat.id) else selected.add(chat.id)
                            } else nav.push(Route.Chat(chat.id))
                        },
                    )
                }
            }
        }

        // Header
        if (searching) {
            Column(Modifier.fillMaxWidth()) {
                Box {
                    ScrollEdgeBlur(top + 80.dp)
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.CenterStart) {
                            SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 6.dp), focusRequester = searchFocus, background = Color.Transparent)
                        }
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(IosIcons.Close, { searching = false; query = ""; focus.clearFocus() }, size = 48.dp)
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth()) {
                ScrollEdgeBlur(top + headerHeight + 24.dp + storiesHeight)
                Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                    Box(Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 12.dp)) {
                        GlassTextButton(if (editing) "Done" else "Edit", {
                            editing = !editing
                            selected.clear()
                        }, bold = editing, modifier = Modifier.align(Alignment.CenterStart))
                        ChatsTitle(
                            storyUsers.take(3).map { it.id to it.name },
                            status = repo.connectionStatus,
                            onStatus = { nav.push(Route.Proxy) },
                            collapsed = 1f - storiesFraction,
                            modifier = Modifier.align(Alignment.Center),
                            onStories = {
                                scope.launch {
                                    animate(storiesPx, storiesMax, animationSpec = spring(0.82f, 420f)) { v, _ -> storiesPx = v }
                                }
                            },
                        )
                        GlassButtonGroup(
                            TgIcons.IcAddStory to { nav.push(Route.Stories(storyUsers.firstOrNull()?.id ?: 1L)) },
                            TgIcons.IcCompose to { nav.push(Route.NewMessage) },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                    if (storiesFraction > 0.01f) {
                        StoriesRow(repo, storiesFraction, Modifier.height(storiesHeight)) { nav.push(Route.Stories(it)) }
                    }
                }
            }
        }

        // Edit-mode toolbar (spec §19 style)
        AnimatedVisibility(
            visible = editing,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            GlassBox(
                onClick = null,
                shape = Capsule(),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val any = selected.isNotEmpty()
                    TextButton(if (any) "Read" else "Read All", {
                        val ids = if (any) selected.toList() else chats.map { it.id }
                        ids.forEach { id -> if ((repo.chat(id)?.unread ?: 0) > 0 || repo.chat(id)?.markedUnread == true) repo.toggleRead(id) }
                        selected.clear()
                    })
                    TextButton("Archive", {
                        selected.toList().forEach { repo.toggleArchive(it) }
                        selected.clear()
                    }, enabled = any)
                    TextButton("Delete", {
                        val ids = selected.toList()
                        sheet.show(SheetRequest(actions = listOf(SheetAction("Delete ${ids.size} Chat${if (ids.size == 1) "" else "s"}", destructive = true) {
                            ids.forEach { repo.deleteChat(it) }
                            selected.clear()
                        })))
                    }, color = c.destructive, enabled = any)
                }
            }
        }
    }
}

/** "Chats" title with the stacked story avatars that Telegram shows when stories are collapsed. */
@Composable
private fun ChatsTitle(stories: List<Pair<Long, String>>, status: String?, collapsed: Float, modifier: Modifier, onStories: () -> Unit, onStatus: () -> Unit = {}) {
    val c = TgTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (stories.isNotEmpty() && collapsed > 0.01f) {
            Box(
                Modifier
                    .graphicsLayer { alpha = collapsed; scaleX = 0.6f + 0.4f * collapsed; scaleY = 0.6f + 0.4f * collapsed }
                    .fadeClickable(onClick = onStories)
                    .padding(end = 8.dp)
            ) {
                stories.forEachIndexed { i, (id, name) ->
                    Box(Modifier.padding(start = (i * 14).dp).zIndex((stories.size - i).toFloat())) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF34C76F), Color(0xFF3DA1FD))))
                                .padding(1.5.dp)
                                .clip(CircleShape)
                                .background(c.background)
                                .padding(1.5.dp)
                        ) { Avatar(name, id, 24.dp) }
                    }
                }
            }
        }
        // Telegram shows the connection state in place of the title until it is online.
        androidx.compose.animation.AnimatedContent(status, label = "chatsTitle") { st ->
            if (st == null) T("Chats", TgTheme.type.headline, c.text)
            else Row(Modifier.fadeClickable(onClick = onStatus), verticalAlignment = Alignment.CenterVertically) {
                com.abtin.tglass.ui.components.ActivityIndicator(16.dp)
                Spacer(Modifier.width(6.dp))
                T(st, TgTheme.type.headline, c.text)
            }
        }
    }
}

/** Folder tabs: text tabs with a glass pill under the selected one and unread counters. */
@Composable
private fun FolderTabs(folders: List<String>, selected: Int, onSelect: (Int) -> Unit, unreadFor: (Int) -> Int) {
    val c = TgTheme.colors
    // Telegram iOS 26: the folder tabs sit in one capsule, the selected tab in a lighter pill. The tabs scroll
    // inside the list that the glass bars sample (layerBackdrop), so the capsule must not be a backdrop glass itself:
    // that would draw the layer into itself and overflow the RenderThread stack (native crash).
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(42.dp).clip(Capsule()).background(c.searchField),
        contentAlignment = Alignment.CenterStart,
    ) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        folders.forEachIndexed { i, f ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) c.text.copy(alpha = if (c.isDark) 0.14f else 0.07f) else Color.Transparent, label = "folderBg")
            Row(
                Modifier
                    .height(34.dp)
                    .clip(Capsule())
                    .background(bg)
                    .fadeClickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(f, TgTheme.type.subheadline, if (sel) c.text else c.secondaryText, weight = FontWeight.SemiBold, maxLines = 1)
                val count = unreadFor(i)
                if (count > 0 && f != "Unread") {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.height(18.dp).clip(Capsule()).background(if (sel) c.accent else c.mutedBadge).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { T("$count", TgTheme.type.caption1, Color.White, weight = FontWeight.SemiBold) }
                }
            }
        }
    }
    }
}

/** Row + swipe actions + long-press peek (spec §8). */
@Composable
fun ChatListItem(
    chat: Chat,
    repo: TelegramRepository,
    editing: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val menu = LocalContextMenu.current
    val nav = LocalNavigator.current
    val sheet = LocalActionSheet.current
    val bounds = remember { arrayOf(Rect.Zero) }
    val key = "chat-${chat.id}"
    val (leading, trailing) = chatSwipeActions(chat, repo, onDelete)
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    Box(
        modifier
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .graphicsLayer { alpha = if (menu.activeKey == key) 0f else 1f }
    ) {
        SwipeableRow(leading, trailing, enabled = !editing) {
            ChatRow(
                chat = chat,
                repo = repo,
                editing = editing,
                selected = selected,
                onClick = onClick,
                onLongClick = if (editing) null else ({
                    val unread = chat.unread > 0 || chat.markedUnread
                    menu.show(
                        ContextMenuRequest(
                            key = key,
                            anchor = bounds[0],
                            alignEnd = false,
                            previewSize = (screenWidth - 24.dp) to 420.dp,
                            actions = listOfNotNull(
                                MenuAction(if (unread) "Mark as Read" else "Mark as Unread", TgIcons.CtxRead) { repo.toggleRead(chat.id) },
                                if (!chat.archived) MenuAction(if (chat.pinned) "Unpin" else "Pin", if (chat.pinned) TgIcons.CtxUnpin else TgIcons.CtxPin) { repo.togglePin(chat.id) } else null,
                                MenuAction(if (chat.muted) "Unmute" else "Mute", if (chat.muted) TgIcons.CtxUnmute else TgIcons.CtxMuted) { com.abtin.tglass.features.groups.toggleMuteWithOptions(sheet, repo, chat.id) },
                                MenuAction(if (chat.archived) "Unarchive" else "Archive", TgIcons.CtxArchive) { repo.toggleArchive(chat.id) },
                                MenuAction("Delete", TgIcons.CtxDelete, destructive = true, groupStart = true, onClick = onDelete),
                            ),
                        ) {
                            ChatPeek(chat.id, onOpen = { menu.dismiss(); nav.push(Route.Chat(chat.id)) })
                        }
                    )
                }),
            )
        }
    }
}

/** Lays out only [shown] px of the content's height, keeping its bottom edge (a row pulled out from above). */
private fun Modifier.revealHeight(shown: () -> Float, full: () -> Float): Modifier = this
    .clipToBounds()
    .layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val h = shown().coerceIn(0f, p.height.toFloat()).roundToInt()
        layout(p.width, h) {
            p.placeWithLayer(0, h - p.height) { alpha = (shown() / full().coerceAtLeast(1f)).coerceIn(0f, 1f) }
        }
    }

/**
 * The "Archived Chats" row with its iOS actions: swipe left → Hide / Pin, long press → the same in a menu
 * (plus Mark All as Read).
 */
@Composable
private fun ArchiveItem(
    archived: List<Chat>,
    repo: TelegramRepository,
    hidden: Boolean,
    modifier: Modifier,
    onHide: () -> Unit,
    onPin: () -> Unit,
    onClick: () -> Unit,
) {
    val c = TgTheme.colors
    val menu = LocalContextMenu.current
    val bounds = remember { arrayOf(Rect.Zero) }
    val key = "archive-row"
    val anyUnread = archived.any { it.unread > 0 || it.markedUnread }
    val trailing = listOf(
        if (hidden) SwipeAction("Pin", TgIcons.CtxPin, c.green, TgAnimations.Pin, onPin)
        else SwipeAction("Hide", TgIcons.CtxArchive, Color(0xFFAAAAAF), null, onHide),
    )
    Box(
        modifier
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .graphicsLayer { alpha = if (menu.activeKey == key) 0f else 1f }
    ) {
        SwipeableRow(leading = emptyList(), trailing = trailing) {
            ArchiveRow(archived, repo, onClick = onClick, onLongClick = {
                menu.show(
                    ContextMenuRequest(
                        key = key,
                        anchor = bounds[0],
                        alignEnd = false,
                        actions = listOfNotNull(
                            if (hidden) MenuAction("Pin to Top", TgIcons.CtxPin, onClick = onPin)
                            else MenuAction("Hide", IosIcons.ArrowUp, onClick = onHide),
                            if (anyUnread) MenuAction("Mark All as Read", TgIcons.CtxRead) {
                                archived.forEach { ch -> if (ch.unread > 0 || ch.markedUnread) repo.toggleRead(ch.id) }
                            } else null,
                        ),
                    ) {
                        ArchiveRow(archived, repo, onClick = { menu.dismiss(); onClick() })
                    }
                )
            })
        }
    }
}

@Composable
private fun ArchiveRow(archived: List<Chat>, repo: TelegramRepository, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val c = TgTheme.colors
    val size = com.abtin.tglass.core.design.LocalAppSettings.current.chatListSize
    val unread = archived.count { it.unread > 0 || it.markedUnread }
    Box(Modifier.fillMaxWidth().background(c.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .iosClickable(onLongClick = onLongClick, onClick = onClick)
                .height(size.row.dp)
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar("Archive", 0, size.avatar.dp, iconRes = TgIcons.IcArchiveLarge, iconColors = Color(0xFFDEDEE5) to Color(0xFFC5C6CC))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Archived Chats", TgTheme.type.headline.copy(fontSize = size.titleSp.sp, lineHeight = (size.titleSp + 4f).sp), c.text, maxLines = 1, modifier = Modifier.weight(1f))
                    archived.firstOrNull()?.let { repo.lastMessage(it.id) }?.let {
                        T(formatListDate(it.date), TgTheme.type.subheadline.copy(fontSize = (size.previewSp - 0.5f).sp), c.secondaryText, maxLines = 1)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(
                        archived.joinToString(", ") { it.title },
                        TgTheme.type.subheadline.copy(fontSize = size.previewSp.sp, lineHeight = (size.previewSp * 1.2f).sp),
                        c.secondaryText,
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                    // Archived chats are usually muted, so Telegram shows their unread count in a gray badge.
                    if (unread > 0) {
                        Spacer(Modifier.width(6.dp))
                        com.abtin.tglass.ui.components.Badge(unread, muted = true)
                    }
                }
            }
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = (14 + size.avatar + 10).dp)
    }
}

@Composable
private fun StoriesRow(repo: TelegramRepository, fraction: Float, modifier: Modifier, onOpen: (Long) -> Unit) {
    val c = TgTheme.colors
    val withStories = repo.storyUsers
    LazyRow(
        modifier.fillMaxWidth().graphicsLayer { alpha = fraction; scaleX = 0.85f + 0.15f * fraction; scaleY = 0.85f + 0.15f * fraction; transformOrigin = TransformOrigin(0.5f, 0f) },
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "me") {
            val me = repo.me
            Column(Modifier.width(68.dp).fadeClickable { if (me.hasStory) onOpen(me.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    if (me.hasStory) Avatar(me.name, 3, 68.dp, photoPeer = me.id, storyRing = if (me.storySeen) StoryRing.Seen else StoryRing.Unseen)
                    else Avatar(me.name, 3, 64.dp, photoPeer = me.id)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(c.background)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(IosIcons.Plus, Color.White, 14.dp) }
                }
                Spacer(Modifier.height(5.dp))
                T("My Story", TgTheme.type.caption1, c.secondaryText, maxLines = 1, align = TextAlign.Center)
            }
        }
        items(withStories, key = { it.id }) { u ->
            Column(Modifier.width(68.dp).fadeClickable { onOpen(u.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(u.name, u.id, 68.dp, storyRing = if (u.storySeen) StoryRing.Seen else StoryRing.Unseen)
                Spacer(Modifier.height(3.dp))
                T(u.firstName, TgTheme.type.caption1, if (u.storySeen) c.secondaryText else c.text, maxLines = 1, align = TextAlign.Center)
            }
        }
    }
}

/** Spec §9 search: recent peers, chats and messages. */
private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(repo: TelegramRepository, query: String, global: com.abtin.tglass.data.GlobalResults?, onOpen: (Long) -> Unit) {
    val q = query.trim()
    item(key = "filters") {
        var sel by remember { mutableIntStateOf(0) }
        ChipTabs(listOf("Chats", "Channels", "Media", "Links", "Files", "Music", "Voice"), sel, { sel = it }, Modifier.padding(vertical = 6.dp))
    }
    if (q.isEmpty()) {
        item(key = "recentHeader") { SectionHeader("Recent") }
        item(key = "recent") {
            val c = TgTheme.colors
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(repo.chats.filter { it.type != ChatType.Saved }.take(10), key = { it.id }) { chat ->
                    Column(Modifier.width(72.dp).fadeClickable { onOpen(chat.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        ChatAvatar(chat, repo, 58.dp)
                        Spacer(Modifier.height(4.dp))
                        T(chat.title.substringBefore(' '), TgTheme.type.caption1, c.text, maxLines = 1)
                    }
                }
            }
        }
        return
    }
    val chats = repo.chats.filter { it.title.contains(q, ignoreCase = true) || it.username?.contains(q.removePrefix("@"), ignoreCase = true) == true }
    val localMessages = repo.chats.flatMap { chat ->
        repo.messages(chat.id).filter { m ->
            m.content !is MessageContent.Service && m.preview.contains(q, ignoreCase = true)
        }.map { chat to it }
    }
    val serverMessages = global?.messages.orEmpty().mapNotNull { m -> repo.chat(m.chatId)?.let { it to m } }
    val messages = (localMessages + serverMessages).distinctBy { it.first.id to it.second.id }.sortedByDescending { it.second.date }
    val publicChats = global?.chats.orEmpty().filter { pc -> chats.none { it.id == pc.id } }
    if (chats.isEmpty() && messages.isEmpty() && publicChats.isEmpty()) {
        if (repo.isLive && global == null && q.length >= 2) {
            item(key = "searching") { Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { com.abtin.tglass.ui.components.ActivityIndicator(24.dp) } }
        } else {
            item(key = "none") { EmptyState("🔍", "No Results", "There were no results for \"$q\".\nTry a new search.") }
        }
        return
    }
    if (chats.isNotEmpty()) {
        item(key = "chatsHeader") { SectionHeader("Chats") }
        items(chats, key = { "c${it.id}" }) { chat -> ChatRow(chat, repo, onClick = { onOpen(chat.id) }) }
    }
    if (publicChats.isNotEmpty()) {
        item(key = "globalHeader") { SectionHeader("Global Search") }
        items(publicChats, key = { "g${it.id}" }) { chat ->
            val c = TgTheme.colors
            Row(
                Modifier.fillMaxWidth().iosClickable { onOpen(chat.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatAvatar(chat, repo, 48.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    T(chat.title, TgTheme.type.headline, c.text, maxLines = 1)
                    val sub = listOfNotNull(
                        chat.username?.let { "@$it" },
                        chat.members.takeIf { it > 0 }?.let { "$it ${if (chat.type == ChatType.Channel) "subscribers" else "members"}" },
                    ).joinToString(", ")
                    if (sub.isNotEmpty()) T(sub, TgTheme.type.subheadline, c.secondaryText, maxLines = 1)
                }
            }
        }
    }
    if (messages.isNotEmpty()) {
        item(key = "msgHeader") { SectionHeader("Messages") }
        items(messages, key = { "m${it.second.id}" }) { (chat, m) ->
            val c = TgTheme.colors
            Row(
                Modifier.fillMaxWidth().iosClickable { onOpen(chat.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatAvatar(chat, repo, 48.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        T(chat.title, TgTheme.type.headline, c.text, maxLines = 1, modifier = Modifier.weight(1f))
                        T(formatListDate(m.date), TgTheme.type.footnote, c.secondaryText)
                    }
                    T(m.preview, TgTheme.type.subheadline, c.secondaryText, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val c = TgTheme.colors
    Box(Modifier.fillMaxWidth().background(c.groupedBackground).padding(horizontal = 16.dp, vertical = 6.dp)) {
        T(text.uppercase(), TgTheme.type.footnote, c.secondaryText, weight = FontWeight.Medium)
    }
}

/** Spec §42: Archived chats list. */
@Composable
fun ArchiveScreen() {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val sheet = LocalActionSheet.current
    val c = TgTheme.colors
    val backdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val archived = repo.chats.filter { it.archived }
    androidx.compose.runtime.CompositionLocalProvider(com.abtin.tglass.core.glass.LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            LazyColumn(Modifier.fillMaxSize().layerBackdrop(backdrop), contentPadding = PaddingValues(top = top + 62.dp, bottom = 40.dp)) {
                if (archived.isEmpty()) item { EmptyState("🗄", "No Archived Chats", "Swipe left on a chat and tap Archive to move it here.") }
                items(archived, key = { it.id }) { chat ->
                    ChatListItem(chat, repo, editing = false, selected = false, modifier = Modifier.animateItem(), onDelete = {
                        sheet.show(SheetRequest(actions = listOf(SheetAction("Delete Chat", destructive = true) { repo.deleteChat(chat.id) })))
                    }, onClick = { nav.push(Route.Chat(chat.id)) })
                }
            }
            GlassTopBar("Archived Chats")
        }
    }
}
