package com.abtin.tglass.features.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.emoji.ReactionGlyph
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.AddedReactionInfo
import com.abtin.tglass.data.AddedReactionsPage
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageSeenInfo
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.MenuAction
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.formatCount
import com.abtin.tglass.ui.components.formatDay
import com.abtin.tglass.ui.components.formatTime
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

// Telegram-iOS long-press info: "Read at 14:32" / "N Seen" and "N Reactions" rows with stacked avatars, each opening a
// list of people (with the reaction they chose, filter tabs per reaction, and when).

/** Which list is open: the people who reacted or the members who saw the message. */
enum class MessageInfoMode { Reactions, Seen }

data class MessageInfoTarget(val chatId: Long, val messageId: Long, val mode: MessageInfoMode)

/** State of the info rows of one open menu; filled asynchronously (rows appear when data arrives, hidden on errors). */
class MessageInfoState {
    var seen by mutableStateOf<MessageSeenInfo?>(null)
    var reactions by mutableStateOf<AddedReactionsPage?>(null)
}

/** Loads what the menu's info rows need for [m]; nothing is requested for service messages or messages without info. */
fun loadMessageInfo(repo: TelegramRepository, m: Message, chatType: ChatType): MessageInfoState {
    val st = MessageInfoState()
    if (chatType == ChatType.Channel || chatType == ChatType.Saved) {
        // Channels show the view count only; reactions can still be listed for admins.
    } else if (m.outgoing) {
        repo.loadMessageSeenInfo(m.chatId, m.id) { st.seen = it }
    }
    if (m.reactions.isNotEmpty()) {
        repo.loadAddedReactions(m.chatId, m.id, null, "", 3) { st.reactions = it?.takeIf { p -> p.items.isNotEmpty() } }
    }
    return st
}

/** The info rows for the top of the menu card. Empty until the data is there. */
fun messageInfoActions(repo: TelegramRepository, m: Message, chatType: ChatType, info: MessageInfoState, open: (MessageInfoMode) -> Unit): List<MenuAction> {
    val rows = ArrayList<MenuAction>(3)
    if (chatType == ChatType.Channel) {
        val v = m.views
        if (v != null && v > 0) rows += MenuAction("${formatCount(v)} ${if (v == 1) "view" else "views"}", IosIcons.Eye) {}
    }
    val seen = info.seen
    if (seen != null) {
        val readAt = seen.readAt
        val viewers = seen.viewers
        if (readAt != null) {
            rows += MenuAction("Read at ${formatTime(readAt)}", TgIcons.CtxRead) {}
        } else if (!viewers.isNullOrEmpty()) {
            rows += MenuAction(
                "${viewers.size} Seen", TgIcons.CtxRead,
                trailing = { StackedAvatars(repo, viewers.take(3).map { it.userId }) },
            ) { open(MessageInfoMode.Seen) }
        }
    }
    val rx = info.reactions
    if (rx != null && m.reactions.isNotEmpty()) {
        val total = m.reactions.sumOf { it.count }.coerceAtLeast(rx.total)
        rows += MenuAction(
            "$total ${if (total == 1) "Reaction" else "Reactions"}", TgIcons.CtxSmile,
            trailing = { StackedAvatars(repo, rx.items.map { it.senderId }.distinct().take(3)) },
        ) { open(MessageInfoMode.Reactions) }
    }
    if (rows.isEmpty()) return rows
    val last = rows.lastIndex
    // The hairline goes under the last info row.
    rows[last] = MenuAction(
        rows[last].title, rows[last].icon, trailing = rows[last].trailing, groupEnd = true, onClick = rows[last].onClick,
    )
    return rows
}

private fun nameOf(repo: TelegramRepository, id: Long): String =
    repo.user(id)?.name ?: repo.chat(id)?.title ?: "Unknown"

/** Small overlapping avatars (Telegram iOS shows up to three next to the info rows). */
@Composable
private fun StackedAvatars(repo: TelegramRepository, ids: List<Long>) {
    if (ids.isEmpty()) return
    val c = TgTheme.colors
    val size = 22.dp
    val step = 14.dp
    Box(Modifier.width(size + step * (ids.size - 1)).height(size)) {
        ids.forEachIndexed { i, id ->
            Box(
                Modifier
                    .offset(x = step * i)
                    .size(size)
                    .border(1.5.dp, if (c.isDark) androidx.compose.ui.graphics.Color(0xFF2C2C2E) else androidx.compose.ui.graphics.Color.White, CircleShape),
            ) {
                val name = nameOf(repo, id)
                Avatar(name, id, size)
            }
        }
    }
}

private fun whenText(ms: Long): String? = if (ms <= 0L) null else "${formatDay(ms)} at ${formatTime(ms)}"

/** Full-screen page listing who reacted (with tabs per reaction) or who saw the message. */
@Composable
fun MessageInfoPage(target: MessageInfoTarget?, onDismiss: () -> Unit) {
    BackHandler(enabled = target != null) { onDismiss() }
    val last = remember { arrayOfNulls<MessageInfoTarget>(1) }
    if (target != null) last[0] = target
    AnimatedVisibility(target != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        val t = target ?: last[0] ?: return@AnimatedVisibility
        MessageInfoContent(t, onDismiss)
    }
}

@Composable
private fun MessageInfoContent(t: MessageInfoTarget, onDismiss: () -> Unit) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val c = TgTheme.colors
    val msg = repo.findMessage(t.chatId, t.messageId)
    // "" = all reactions; otherwise the emoji filter. Pages are cached per filter.
    var filter by remember(t) { mutableStateOf("") }
    val pages = remember(t) { mutableStateMapOf<String, AddedReactionsPage>() }
    val items = remember(t) { mutableStateMapOf<String, List<AddedReactionInfo>>() }
    val loading = remember(t) { mutableStateMapOf<String, Boolean>() }
    var seen by remember(t) { mutableStateOf<MessageSeenInfo?>(null) }
    var seenFailed by remember(t) { mutableStateOf(false) }

    fun load(key: String) {
        if (loading[key] == true) return
        loading[key] = true
        val have = items[key].orEmpty()
        val offset = pages[key]?.nextOffset ?: ""
        repo.loadAddedReactions(t.chatId, t.messageId, key.ifEmpty { null }, offset, 30) { page ->
            loading[key] = false
            if (page != null) {
                items[key] = have + page.items
                pages[key] = page
            }
        }
    }
    LaunchedEffect(t) {
        if (t.mode == MessageInfoMode.Seen) {
            repo.loadMessageSeenInfo(t.chatId, t.messageId) { if (it == null) seenFailed = true else seen = it }
        } else load("")
    }
    LaunchedEffect(filter) { if (t.mode == MessageInfoMode.Reactions && items[filter] == null) load(filter) }

    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.groupedBackground)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 32.dp),
            ) {
                if (t.mode == MessageInfoMode.Reactions) {
                    val reactions = msg?.reactions.orEmpty()
                    if (reactions.size > 1) {
                        item(key = "tabs") {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            ) {
                                FilterTab(selected = filter.isEmpty(), onClick = { filter = "" }) {
                                    T("All ${formatCount(reactions.sumOf { it.count })}", TgTheme.type.footnote, if (filter.isEmpty()) androidx.compose.ui.graphics.Color.White else c.text, weight = FontWeight.SemiBold)
                                }
                                reactions.forEach { r ->
                                    FilterTab(selected = filter == r.emoji, onClick = { filter = r.emoji }) {
                                        ReactionGlyph(r.emoji, 16.dp)
                                        Spacer(Modifier.width(4.dp))
                                        T(formatCount(r.count), TgTheme.type.footnote, if (filter == r.emoji) androidx.compose.ui.graphics.Color.White else c.text, weight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    item(key = "list-$filter") {
                        val list = items[filter]
                        Spacer(Modifier.height(10.dp))
                        Section {
                            if (list == null) {
                                Box(Modifier.padding(16.dp)) { ActivityIndicator() }
                            } else {
                                val more = pages[filter]?.nextOffset?.isNotEmpty() == true
                                list.forEachIndexed { idx, a ->
                                    PersonCell(repo, a.senderId, whenText(a.date), divider = idx != list.lastIndex || more,
                                        trailing = { ReactionGlyph(a.emoji, 24.dp) }, nav = nav, onDismiss = onDismiss)
                                }
                                if (more) {
                                    Cell(
                                        if (loading[filter] == true) "Loading…" else "Show More",
                                        titleColor = c.accent, chevron = false, divider = false,
                                        onClick = { load(filter) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item(key = "seen") {
                        val list = seen?.viewers
                        Section(header = "Seen by") {
                            if (list == null) {
                                Box(Modifier.padding(16.dp)) { if (!seenFailed) ActivityIndicator() }
                            } else {
                                list.forEachIndexed { idx, v ->
                                    PersonCell(repo, v.userId, whenText(v.date), divider = idx != list.lastIndex, trailing = null, nav = nav, onDismiss = onDismiss)
                                }
                            }
                        }
                    }
                }
            }
            GlassTopBar(
                title = if (t.mode == MessageInfoMode.Reactions) "Reactions" else "Seen",
                fade = c.groupedBackground,
                onBack = onDismiss,
            )
        }
    }
}

@Composable
private fun FilterTab(selected: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = TgTheme.colors
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (selected) c.accent else c.secondaryText.copy(alpha = 0.16f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun PersonCell(
    repo: TelegramRepository,
    id: Long,
    subtitle: String?,
    divider: Boolean,
    trailing: (@Composable () -> Unit)?,
    nav: com.abtin.tglass.core.navigation.Navigator,
    onDismiss: () -> Unit,
) {
    val name = nameOf(repo, id)
    Cell(
        name,
        subtitle = subtitle,
        chevron = false,
        divider = divider,
        leading = { Avatar(name, id, 40.dp) },
        trailing = trailing,
        onClick = {
            onDismiss()
            if (repo.user(id) != null) nav.push(Route.UserProfile(id)) else nav.push(Route.Chat(id))
        },
    )
}
