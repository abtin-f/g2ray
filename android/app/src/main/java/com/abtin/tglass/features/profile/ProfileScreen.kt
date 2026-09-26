package com.abtin.tglass.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.MediaKind
import com.abtin.tglass.data.senderName
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.features.chat.chatSubtitle
import com.abtin.tglass.features.chatlist.ChatAvatar
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.ChipTabs
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.IOSSwitch
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.CollapsedTitle
import com.abtin.tglass.ui.components.HeroAction
import com.abtin.tglass.ui.components.ProfileHero
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.VerifiedBadge
import com.abtin.tglass.ui.components.rememberHeroCollapse
import androidx.compose.foundation.lazy.rememberLazyListState
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.bounceClickable
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle

/** Spec §27: user / group / channel info page. */
@Composable
fun ProfileScreen(chatId: Long) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val sheet = LocalActionSheet.current
    val toast = LocalToast.current
    val callContext = androidx.compose.ui.platform.LocalContext.current
    val c = TgTheme.colors
    val chat = repo.chat(chatId) ?: return
    val user = chat.peerUserId?.let { repo.user(it) }
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val collapse = rememberHeroCollapse(listState)
    /** Opens the chat with its search bar (back to it when we came from there). */
    fun searchInChat() {
        com.abtin.tglass.features.chat.ChatSearchRequest.chatId = chat.id
        val below = nav.stack.getOrNull(nav.stack.size - 2)?.route
        if (below == Route.Chat(chat.id)) nav.pop() else nav.replaceTop(Route.Chat(chat.id))
    }
    val isGroup = chat.type == ChatType.Group
    val isChannel = chat.type == ChatType.Channel
    val rights = chat.rights
    val canInvite = rights?.inviteUsers == true
    fun leaveOrDelete() {
        val noun = if (isChannel) "Channel" else "Group"
        sheet.show(SheetRequest(actions = listOfNotNull(
            SheetAction("Leave $noun", destructive = true) { repo.deleteChat(chat.id); nav.resetTo(Route.Main) },
            if (rights?.owner == true) SheetAction("Delete $noun for everyone", destructive = true) {
                repo.deleteChatForAll(chat.id) { err ->
                    if (err != null) toast.show(err) else nav.resetTo(Route.Main)
                }
            } else null,
        )))
    }
    // ---- Chat features: block / unblock, clear history, report spam ----
    val isUserChat = chat.type == ChatType.Private || chat.type == ChatType.Bot
    val isBot = chat.type == ChatType.Bot
    fun toggleBlock() {
        val name = chat.title
        if (repo.isBlocked(chat.id)) {
            repo.setBlocked(chat.id, false) { err ->
                if (err != null) toast.show(err)
                else if (isBot) { repo.startBot(chat.id); toast.show("Bot restarted") }
                else toast.show("$name unblocked")
            }
        } else {
            sheet.show(SheetRequest(
                title = if (isBot) "Stop and block $name?" else "Block $name?",
                message = if (isBot) "The bot won't be able to send you messages." else "$name won't be able to message or call you.",
                actions = listOf(SheetAction(if (isBot) "Stop Bot" else "Block User", destructive = true) {
                    repo.setBlocked(chat.id, true) { err -> toast.show(err ?: "$name blocked") }
                }),
            ))
        }
    }
    fun confirmClearHistory() {
        val o = repo.clearHistoryOptions(chat.id)
        val first = chat.title.substringBefore(' ')
        val done = { toast.show("History cleared") }
        val actions = listOfNotNull(
            if (o.forEveryone) SheetAction(if (chat.type == ChatType.Private) "Delete for me and $first" else "Delete for Everyone", destructive = true) {
                repo.clearHistory(chat.id, true); done()
            } else null,
            if (o.forMe) SheetAction(if (o.forEveryone) "Delete just for me" else "Clear History", destructive = true) {
                repo.clearHistory(chat.id, false); done()
            } else null,
        )
        if (actions.isEmpty()) {
            toast.show("History can't be cleared in this chat")
            return
        }
        sheet.show(SheetRequest(title = "Are you sure you want to delete all messages in this chat?", message = "This action cannot be undone.", actions = actions))
    }
    fun confirmReportSpam() {
        sheet.show(SheetRequest(
            title = "Report this ${if (isChannel) "channel" else "group"} as spam?",
            actions = listOf(SheetAction("Report Spam", destructive = true) {
                repo.reportSpam(chat.id) { err -> toast.show(err ?: "Thank you! Your report will be reviewed by our team.") }
            }),
        ))
    }
    // ---- end Chat features ----
    val tabs = (if (isGroup) listOf("Members") else emptyList()) + listOf("Media", "Files", "Links", "Voice", "GIFs")
    androidx.compose.runtime.LaunchedEffect(chatId) { repo.loadChatInfo(chatId) }
    val info = repo.chatInfo(chatId)
    androidx.compose.runtime.LaunchedEffect(chatId, tab) {
        when (tabs.getOrNull(tab)) {
            "Media" -> repo.loadSharedMedia(chatId, MediaKind.Media)
            "Files" -> repo.loadSharedMedia(chatId, MediaKind.Files)
            "Links" -> repo.loadSharedMedia(chatId, MediaKind.Links)
            "Voice" -> repo.loadSharedMedia(chatId, MediaKind.Voice)
            "GIFs" -> repo.loadSharedMedia(chatId, MediaKind.Gifs)
        }
    }

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                state = listState,
                contentPadding = PaddingValues(bottom = 40.dp),
            ) {
                item {
                    val (sub, active) = chatSubtitle(chat, repo)
                    ProfileHero(
                        name = chat.title,
                        seed = chat.id,
                        subtitle = sub,
                        collapse = collapse,
                        saved = chat.type == ChatType.Saved,
                        subtitleAccent = active || user?.online == true,
                        badge = { if (chat.verified) VerifiedBadge(22.dp) },
                    ) {
                        HeroAction(TgIcons.PiMessage, "Message") { nav.pop() }
                        HeroAction(if (chat.muted) TgIcons.PiUnmute else TgIcons.PiMute, if (chat.muted) "Unmute" else "Mute") { com.abtin.tglass.features.groups.toggleMuteWithOptions(sheet, repo, chat.id) }
                        when (chat.type) {
                            ChatType.Private, ChatType.Saved -> {
                                HeroAction(TgIcons.PiCall, "Call") { user?.let { com.abtin.tglass.features.calls.requestCall(callContext, repo, nav, sheet, toast, it.id, video = false) } }
                                HeroAction(TgIcons.PiVideo, "Video") { user?.let { com.abtin.tglass.features.calls.requestCall(callContext, repo, nav, sheet, toast, it.id, video = true) } }
                            }
                            else -> {
                                HeroAction(TgIcons.PiSearch, "Search") { searchInChat() }
                                HeroAction(TgIcons.PiLeave, "Leave") { leaveOrDelete() }
                            }
                        }
                        HeroAction(TgIcons.PiMore, "More") {
                            sheet.show(SheetRequest(actions = listOfNotNull(
                                if (rights?.changeInfo == true) SheetAction(if (isChannel) "Edit Channel" else "Edit Group") { nav.push(Route.EditChat(chat.id)) } else null,
                                if (canInvite && (isGroup || isChannel)) SheetAction(if (isChannel) "Add Subscribers" else "Add Members") { nav.push(Route.AddMembers(chat.id)) } else null,
                                SheetAction("Search Messages") { searchInChat() },
                                SheetAction("Share Contact") { toast.show("Link copied") },
                                if (isUserChat) {
                                    val blocked = repo.isBlocked(chat.id)
                                    SheetAction(
                                        when {
                                            blocked && isBot -> "Restart Bot"
                                            blocked -> "Unblock User"
                                            isBot -> "Stop Bot"
                                            else -> "Block User"
                                        },
                                        destructive = !blocked,
                                    ) { toggleBlock() }
                                } else null,
                                if ((isGroup || isChannel) && repo.canReportSpam(chat.id)) SheetAction("Report Spam", destructive = true) { confirmReportSpam() } else null,
                                SheetAction("Clear History", destructive = true) { confirmClearHistory() },
                            )))
                        }
                    }
                }
                item {
                    Section {
                        val about = info?.about
                        if (user != null) {
                            if (user.phone.isNotBlank()) InfoRow("mobile", user.phone, accent = true)
                            if (user.username != null) InfoRow("username", "@${user.username}", accent = true)
                            (about ?: user.bio)?.let { InfoRow("bio", it) }
                        } else {
                            (about ?: chat.description)?.let { InfoRow("info", it) }
                            (info?.link ?: chat.username)?.let { l -> InfoRow("link", if (l.startsWith("http")) l.removePrefix("https://") else "t.me/$l", accent = true) }
                        }
                        Cell("Notifications", chevron = false, trailing = {
                            IOSSwitch(!chat.muted, { on -> if (on) repo.muteFor(chat.id, 0) else com.abtin.tglass.features.groups.showMuteOptions(sheet, repo, chat.id) })
                        }, divider = false)
                    }
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    ChipTabs(tabs, tab, { tab = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
                val tabName = tabs[tab]
                val kind = when (tabName) {
                    "Media" -> MediaKind.Media
                    "Files" -> MediaKind.Files
                    "Links" -> MediaKind.Links
                    "Voice" -> MediaKind.Voice
                    "GIFs" -> MediaKind.Gifs
                    else -> null
                }
                val shared = kind?.let { repo.sharedMedia(chat.id, it) }.orEmpty()
                if (tabName == "Members") {
                    val members = info?.members.orEmpty().mapNotNull { m -> repo.user(m.userId)?.let { it to m.role } }
                    item {
                        Section(footer = info?.memberCount?.takeIf { it > members.size }?.let { "$it members" }) {
                            Cell("Add Members", icon = TgIcons.PiAddMember, iconColor = c.accent, titleColor = c.accent, chevron = false, divider = members.isNotEmpty(), onClick = {
                                if (canInvite) nav.push(Route.AddMembers(chat.id)) else toast.show("Invite link copied")
                            })
                            members.forEachIndexed { i, (u, role) ->
                                // Owners remove anyone; admins with the ban right remove regular members.
                                val removable = rights?.banMembers == true && u.id != repo.me.id && role != "owner" && (role != "admin" || rights?.owner == true)
                                com.abtin.tglass.features.groups.MemberCell(
                                    u,
                                    role = role,
                                    divider = i != members.lastIndex,
                                    onClick = { nav.push(Route.UserProfile(u.id)) },
                                    onLongClick = if (!removable) null else ({
                                        sheet.show(SheetRequest(title = u.name, actions = listOf(
                                            SheetAction("Remove from Group", destructive = true) {
                                                repo.removeMember(chat.id, u.id) { err -> toast.show(err ?: "${u.name} removed") }
                                            },
                                        )))
                                    }),
                                )
                            }
                        }
                    }
                } else if (kind == MediaKind.Media || kind == MediaKind.Gifs) {
                    val tiles = shared.mapNotNull { m -> (m.content as? MessageContent.Photo)?.let { m to it } }
                    val fillers = if (repo.isLive || tiles.isNotEmpty()) emptyList()
                    else (0 until 14).map { null to MessageContent.Photo(it + chat.id.toInt(), 1f, emoji = listOf("🏔", "🌅", "🐈", "🍜", "🌸", "🎨", "🌊")[it % 7]) }
                    val all: List<Pair<com.abtin.tglass.data.Message?, MessageContent.Photo>> = tiles + fillers
                    if (all.isEmpty()) emptyTab(tabName)
                    all.chunked(3).forEachIndexed { r, row ->
                        item(key = "row$r") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                row.forEach { (m, p) ->
                                    val (a, b) = avatarColors(p.seed.toLong())
                                    Box(
                                        Modifier.weight(1f).aspectRatio(1f).background(Brush.linearGradient(listOf(a, b)))
                                            .then(if (m != null) Modifier.bounceClickable { nav.push(Route.Media(chat.id, m.id)) } else Modifier),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (p.image != null) com.abtin.tglass.ui.components.TgImage(p.image, Modifier.matchParentSize(), maxPx = 360)
                                        else T(p.emoji, TgTheme.type.body.copy(fontSize = 34.sp, lineHeight = 40.sp))
                                        if (p.video && !p.loop && p.duration > 0) {
                                            T(
                                                com.abtin.tglass.ui.components.formatDuration(p.duration), TgTheme.type.caption2, androidx.compose.ui.graphics.Color.White,
                                                weight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                            )
                                        }
                                    }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(1.dp))
                        }
                    }
                } else if (shared.isEmpty()) {
                    emptyTab(tabName)
                } else {
                    item {
                        Section {
                            shared.forEachIndexed { i, m -> SharedRow(m, divider = i != shared.lastIndex) }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
            GlassTopBar(title = null, fade = c.groupedBackground, center = { CollapsedTitle(chat.title, chat.id, collapse, saved = chat.type == ChatType.Saved) }, right = {
                GlassTextButton("Edit", {
                    if (rights?.changeInfo == true && (isGroup || isChannel)) nav.push(Route.EditChat(chat.id)) else toast.show("Edit profile")
                })
            })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, accent: Boolean = false) {
    val c = TgTheme.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        T(label, TgTheme.type.subheadline, c.secondaryText)
        T(value, TgTheme.type.body, if (accent) c.accent else c.text)
    }
    com.abtin.tglass.ui.components.Separator(startPadding = 16.dp)
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = TgTheme.colors
    Column(
        modifier
            .height(64.dp)
            .clip(RoundedRectangle(16.dp))
            .background(c.cell)
            .bounceClickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, c.accent, 24.dp)
        Spacer(Modifier.height(3.dp))
        T(label, TgTheme.type.caption1, c.accent, maxLines = 1)
    }
}

/** Opens (or creates) the private chat's profile for a user. */
@Composable
fun UserProfileScreen(userId: Long) {
    val repo = LocalRepository.current
    ProfileScreen(repo.privateChatWith(userId))
}


private fun androidx.compose.foundation.lazy.LazyListScope.emptyTab(tabName: String) {
    item {
        val c = TgTheme.colors
        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("No ${tabName.lowercase()} yet", TgTheme.type.headline, c.text, align = TextAlign.Center)
            T("Shared ${tabName.lowercase()} from this chat will appear here.", TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center)
        }
    }
}

/** A row of the Files / Links / Voice tabs. Files download and open on tap, voice notes play. */
@Composable
private fun SharedRow(m: com.abtin.tglass.data.Message, divider: Boolean) {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val toast = LocalToast.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val player = com.abtin.tglass.core.media.VoicePlayer
    val key = "${m.chatId}:${m.id}"
    val content = m.content
    val ref = when (content) {
        is MessageContent.File -> content.file
        is MessageContent.Voice -> content.media
        is MessageContent.VideoNote -> content.video
        else -> null
    }
    val path = ref?.let { repo.filePath(it) }
    var pending by androidx.compose.runtime.remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    fun act(p: String) {
        when (content) {
            is MessageContent.File -> if (content.music) player.toggle(context, key, p, content.duration)
                else if (!com.abtin.tglass.core.media.Files.open(context, p, content.mime)) toast.show("No app can open this file")
            is MessageContent.Voice -> player.toggle(context, key, p, content.seconds)
            is MessageContent.VideoNote -> player.toggle(context, key, p, content.seconds)
            else -> {}
        }
    }
    androidx.compose.runtime.LaunchedEffect(pending, path) { if (pending && path != null) { pending = false; act(path) } }
    val (title, subtitle) = when (content) {
        is MessageContent.File -> content.name to listOfNotNull(content.performer, content.size).joinToString(" · ")
        is MessageContent.Voice -> repo.senderName(m) to com.abtin.tglass.ui.components.formatDuration(content.seconds)
        is MessageContent.VideoNote -> repo.senderName(m) to "Video message · ${com.abtin.tglass.ui.components.formatDuration(content.seconds)}"
        is MessageContent.Link -> content.title.ifBlank { content.site } to content.text
        else -> m.preview to ""
    }
    Cell(
        title,
        subtitle = listOf(subtitle, com.abtin.tglass.ui.components.formatDay(m.date)).filter { it.isNotBlank() }.joinToString(" · "),
        chevron = false,
        divider = divider,
        leading = {
            Box(Modifier.height(40.dp).aspectRatio(1f).clip(RoundedRectangle(10.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                val playing = player.currentKey == key && player.playing
                when {
                    pending && path == null -> com.abtin.tglass.ui.components.ActivityIndicator(18.dp, androidx.compose.ui.graphics.Color.White)
                    content is MessageContent.Voice || content is MessageContent.VideoNote || (content is MessageContent.File && content.music) ->
                        Icon(if (playing) com.abtin.tglass.ui.components.IosIcons.Pause else com.abtin.tglass.ui.components.IosIcons.Play, androidx.compose.ui.graphics.Color.White, 20.dp)
                    content is MessageContent.Link -> T(content.site.take(1).uppercase(), TgTheme.type.headline, androidx.compose.ui.graphics.Color.White)
                    else -> Icon(TgIcons.AttFile, androidx.compose.ui.graphics.Color.White, 22.dp)
                }
            }
        },
        onClick = {
            when {
                content is MessageContent.Link -> {
                    val url = com.abtin.tglass.features.chat.detectEntities(content.text).firstOrNull { it.type == com.abtin.tglass.data.EntityType.Url }
                        ?.let { content.text.substring(it.start, it.end) }
                    if (url != null) runCatching { uri.openUri(if (url.startsWith("http")) url else "https://$url") }
                }
                ref == null -> {}
                path != null -> act(path)
                else -> { pending = true; repo.requestImage(ref) }
            }
        },
    )
}
