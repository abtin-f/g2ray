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
    val c = TgTheme.colors
    val chat = repo.chat(chatId) ?: return
    val user = chat.peerUserId?.let { repo.user(it) }
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val isGroup = chat.type == ChatType.Group
    val tabs = (if (isGroup) listOf("Members") else emptyList()) + listOf("Media", "Files", "Links", "Voice", "GIFs")

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 60.dp, bottom = 40.dp),
            ) {
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ChatAvatar(chat, repo, 110.dp, showOnline = false)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(chat.title, TgTheme.type.title2.copy(fontWeight = FontWeight.SemiBold), c.text, maxLines = 1)
                            if (chat.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Verified, c.accent, 22.dp) }
                        }
                        val (sub, active) = chatSubtitle(chat, repo)
                        if (sub != null) T(sub, TgTheme.type.subheadline, if (active || user?.online == true) c.accent else c.secondaryText)
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ActionTile(Icons.Rounded.ChatBubble, "Message", Modifier.weight(1f)) { nav.pop() }
                            ActionTile(if (chat.muted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, if (chat.muted) "Unmute" else "Mute", Modifier.weight(1f)) { repo.toggleMute(chat.id) }
                            when (chat.type) {
                                ChatType.Private, ChatType.Saved -> {
                                    ActionTile(Icons.Rounded.Call, "Call", Modifier.weight(1f)) { user?.let { nav.push(Route.ActiveCall(it.id, false)) } }
                                    ActionTile(Icons.Rounded.Videocam, "Video", Modifier.weight(1f)) { user?.let { nav.push(Route.ActiveCall(it.id, true)) } }
                                }
                                else -> {
                                    ActionTile(Icons.Rounded.Search, "Search", Modifier.weight(1f)) { toast.show("Search in chat") }
                                    ActionTile(Icons.AutoMirrored.Rounded.ExitToApp, "Leave", Modifier.weight(1f)) {
                                        sheet.show(SheetRequest(actions = listOf(SheetAction(if (chat.type == ChatType.Channel) "Leave Channel" else "Leave Group", destructive = true) {
                                            repo.deleteChat(chat.id); nav.resetTo(Route.Main)
                                        })))
                                    }
                                }
                            }
                            ActionTile(Icons.Rounded.MoreHoriz, "More", Modifier.weight(1f)) {
                                sheet.show(SheetRequest(actions = listOf(
                                    SheetAction("Share Contact") { toast.show("Link copied") },
                                    SheetAction("Clear History", destructive = true) { repo.deleteMessages(chat.id, repo.messages(chat.id).map { it.id }.toSet()) },
                                )))
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }
                item {
                    Section {
                        if (user != null) {
                            InfoRow("mobile", user.phone, accent = true)
                            if (user.username != null) {
                                InfoRow("username", "@${user.username}", accent = true)
                            }
                            if (user.bio != null) InfoRow("bio", user.bio)
                        } else {
                            chat.description?.let { InfoRow("info", it) }
                            chat.username?.let { InfoRow("link", "t.me/$it", accent = true) }
                        }
                        Cell("Notifications", chevron = false, trailing = { IOSSwitch(!chat.muted, { repo.toggleMute(chat.id) }) }, divider = false)
                    }
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    ChipTabs(tabs, tab, { tab = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
                val tabName = tabs[tab]
                if (tabName == "Members") {
                    val members = repo.users.values.filter { it.id != 0L }.take(8)
                    item {
                        Section {
                            Cell("Add Members", icon = Icons.Rounded.PersonAdd, titleColor = c.accent, chevron = false, onClick = { toast.show("Invite link copied") })
                            members.forEachIndexed { i, u ->
                                Cell(
                                    u.name,
                                    subtitle = u.status,
                                    leading = { Avatar(u.name, u.id, 40.dp) },
                                    chevron = false,
                                    divider = i != members.lastIndex,
                                    value = if (i == 0) "owner" else if (i < 3) "admin" else null,
                                    onClick = { nav.push(Route.UserProfile(u.id)) },
                                )
                            }
                        }
                    }
                } else if (tabName == "Media" || tabName == "GIFs") {
                    val photos = repo.messages(chat.id).mapNotNull { it.content as? MessageContent.Photo }
                    val tiles = (photos.map { it.seed to it.emoji } + (0 until 14).map { (it + chat.id.toInt()) to listOf("🏔", "🌅", "🐈", "🍜", "🌸", "🎨", "🌊")[it % 7] })
                    tiles.chunked(3).forEachIndexed { r, row ->
                        item(key = "row$r") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 1.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                row.forEach { (seed, emoji) ->
                                    val (a, b) = avatarColors(seed.toLong())
                                    Box(
                                        Modifier.weight(1f).aspectRatio(1f).background(Brush.linearGradient(listOf(a, b))),
                                        contentAlignment = Alignment.Center,
                                    ) { T(emoji, TgTheme.type.body.copy(fontSize = 34.sp, lineHeight = 40.sp)) }
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(1.dp))
                        }
                    }
                } else {
                    item {
                        Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            T("No ${tabName.lowercase()} yet", TgTheme.type.headline, c.text, align = TextAlign.Center)
                            T("Shared ${tabName.lowercase()} from this chat will appear here.", TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center)
                        }
                    }
                }
            }
            GlassTopBar(title = null, fade = c.groupedBackground, right = { GlassTextButton("Edit", { toast.show("Edit profile") }) })
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
