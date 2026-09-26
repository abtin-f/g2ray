package com.abtin.tglass.features.chatlist

import androidx.compose.animation.AnimatedVisibility
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
    val menu = LocalContextMenu.current
    val sheet = LocalActionSheet.current
    val c = TgTheme.colors
    val focus = LocalFocusManager.current
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
    var handledSearch by rememberSaveable { mutableIntStateOf(tabBar.searchRequests) }
    LaunchedEffect(tabBar.searchRequests) {
        if (tabBar.searchRequests > handledSearch) {
            handledSearch = tabBar.searchRequests
            searching = true
        }
    }

    val all = repo.chats.filter { !it.archived }
    val chats = when (repo.folders.getOrNull(folder)) {
        "Personal" -> all.filter { it.folder == "Personal" || it.type == ChatType.Private }
        "Work" -> all.filter { it.folder == "Work" }
        "Unread" -> all.filter { it.unread > 0 || it.markedUnread }
        else -> all
    }
    val archived = repo.chats.filter { it.archived }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
            contentPadding = PaddingValues(top = top + 62.dp, bottom = bottom + 110.dp),
        ) {
            if (searching) {
                searchResults(repo, query, onOpen = { focus.clearFocus(); nav.push(Route.Chat(it)) })
            } else {
                item(key = "search") {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SearchField("", {}, Modifier.fillMaxWidth())
                        Box(Modifier.matchParentSize().fadeClickable { searching = true })
                    }
                }
                item(key = "stories") { StoriesRow(repo) { nav.push(Route.Stories(it)) } }
                item(key = "folders") {
                    ChipTabs(repo.folders, folder, { folder = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Separator()
                }
                if (archived.isNotEmpty() && folder == 0 && !editing) {
                    item(key = "archive") { ArchiveRow(archived, repo) { nav.push(Route.Archive) } }
                }
                if (chats.isEmpty()) {
                    item(key = "empty") { EmptyState("💬", "No Chats", "There are no chats in this folder yet.") }
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

        // Top bar
        if (searching) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.background)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(query, { query = it }, Modifier.weight(1f), focusRequester = searchFocus)
                Spacer(Modifier.width(12.dp))
                TextButton("Cancel", { searching = false; query = ""; focus.clearFocus() })
            }
        } else {
            GlassTopBar(
                title = null,
                left = {
                    GlassTextButton(if (editing) "Done" else "Edit", {
                        editing = !editing
                        selected.clear()
                    }, bold = editing)
                },
                center = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("Chats", TgTheme.type.headline, c.text)
                    }
                },
                right = {
                    GlassIconButton(Icons.Outlined.Edit, { nav.push(Route.NewMessage) }, tint = c.text, iconSize = 21.dp)
                },
            )
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
                                MenuAction(if (unread) "Mark as Read" else "Mark as Unread", if (unread) Icons.Outlined.MarkChatRead else Icons.Outlined.MarkChatUnread) { repo.toggleRead(chat.id) },
                                if (!chat.archived) MenuAction(if (chat.pinned) "Unpin" else "Pin", Icons.Outlined.PushPin) { repo.togglePin(chat.id) } else null,
                                MenuAction(if (chat.muted) "Unmute" else "Mute", if (chat.muted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff) { repo.toggleMute(chat.id) },
                                MenuAction(if (chat.archived) "Unarchive" else "Archive", if (chat.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive) { repo.toggleArchive(chat.id) },
                                MenuAction("Delete", Icons.Outlined.Delete, destructive = true, groupStart = true, onClick = onDelete),
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

@Composable
private fun ArchiveRow(archived: List<Chat>, repo: TelegramRepository, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .iosClickable(onClick = onClick)
                .height(72.dp)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar("Archive", 0, 60.dp, icon = Icons.Rounded.Archive, iconColors = Color(0xFFDEDEE5) to Color(0xFFC5C6CC))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Archived Chats", TgTheme.type.headline, c.text, modifier = Modifier.weight(1f))
                    repo.lastMessage(archived.first().id)?.let { T(formatListDate(it.date), TgTheme.type.subheadline.copy(fontSize = 14.sp), c.secondaryText) }
                }
                T(archived.joinToString(", ") { it.title }, TgTheme.type.subheadline, c.secondaryText, maxLines = 2)
            }
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = 86.dp)
    }
}

@Composable
private fun StoriesRow(repo: TelegramRepository, onOpen: (Long) -> Unit) {
    val c = TgTheme.colors
    val withStories = repo.users.values.filter { it.hasStory }.sortedBy { it.storySeen }
    LazyRow(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "me") {
            Column(Modifier.width(70.dp).fadeClickable { }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    Avatar(repo.me.name, repo.me.id, 62.dp)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(c.background)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(c.accent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, Color.White, 16.dp) }
                }
                Spacer(Modifier.height(4.dp))
                T("My Story", TgTheme.type.caption1, c.secondaryText, maxLines = 1, align = TextAlign.Center)
            }
        }
        items(withStories, key = { it.id }) { u ->
            Column(Modifier.width(70.dp).fadeClickable { onOpen(u.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(u.name, u.id, 66.dp, storyRing = if (u.storySeen) StoryRing.Seen else StoryRing.Unseen)
                Spacer(Modifier.height(2.dp))
                T(u.firstName, TgTheme.type.caption1, if (u.storySeen) c.secondaryText else c.text, maxLines = 1, align = TextAlign.Center)
            }
        }
    }
}

/** Spec §9 search: recent peers, chats and messages. */
private fun androidx.compose.foundation.lazy.LazyListScope.searchResults(repo: TelegramRepository, query: String, onOpen: (Long) -> Unit) {
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
    val chats = repo.chats.filter { it.title.contains(q, ignoreCase = true) }
    val messages = repo.chats.flatMap { chat ->
        repo.messages(chat.id).filter { m ->
            m.content !is MessageContent.Service && m.preview.contains(q, ignoreCase = true)
        }.map { chat to it }
    }.sortedByDescending { it.second.date }
    if (chats.isEmpty() && messages.isEmpty()) {
        item(key = "none") { EmptyState("🔍", "No Results", "There were no results for \"$q\".\nTry a new search.") }
        return
    }
    if (chats.isNotEmpty()) {
        item(key = "chatsHeader") { SectionHeader("Chats") }
        items(chats, key = { "c${it.id}" }) { chat -> ChatRow(chat, repo, onClick = { onOpen(chat.id) }) }
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
