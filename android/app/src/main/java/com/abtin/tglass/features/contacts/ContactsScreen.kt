package com.abtin.tglass.features.contacts

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.User
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.core.media.Sharing
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.ErrorOutline
import com.abtin.tglass.ui.components.SearchField
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Spec §29. */
@Composable
fun ContactsScreen(backdrop: LayerBackdrop, isTab: Boolean) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val context = LocalContext.current
    val c = TgTheme.colors
    var query by remember { mutableStateOf("") }
    var byName by rememberSaveable { mutableStateOf(false) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contacts = repo.contacts
        .filter { query.isBlank() || it.name.contains(query.trim(), true) }
        .let { list ->
            if (byName) list.sortedBy { it.name.lowercase() }
            else list.sortedWith(compareByDescending<User> { repo.lastSeenOrder(it.id) }.thenBy { it.name.lowercase() })
        }

    fun sortOptions() {
        sheet.show(SheetRequest(title = "Sort Contacts", actions = listOf(
            SheetAction("by Last Seen Time", bold = !byName) { byName = false },
            SheetAction("by Name", bold = byName) { byName = true },
        )))
    }

    fun contactOptions(u: User) {
        sheet.show(SheetRequest(title = u.name, actions = listOf(
            SheetAction("Send Message") { nav.push(Route.Chat(repo.privateChatWith(u.id))) },
            SheetAction("Delete Contact", destructive = true) {
                sheet.show(SheetRequest(
                    title = "Delete Contact",
                    message = "Are you sure you want to delete ${u.name} from your contacts?",
                    actions = listOf(SheetAction("Delete", destructive = true) {
                        repo.removeContact(u.id) { error ->
                            if (error != null) toast.show(error, Icons.Rounded.ErrorOutline)
                            else toast.show("${u.name} deleted from contacts")
                        }
                    }),
                    alert = true,
                ))
            },
        )))
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize().layerBackdrop(backdrop),
            contentPadding = PaddingValues(top = top + 62.dp, bottom = bottom + if (isTab) 110.dp else 20.dp),
        ) {
            item { SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) }
            item {
                ActionRow(TgIcons.CtInvite, "Invite Friends") {
                    if (!Sharing.shareText(context, Sharing.InviteText, "Invite Friends")) toast.show("No app to share the invitation", Icons.Rounded.ErrorOutline)
                }
            }
            item { ActionRow(TgIcons.CtAddMember, "Add Contact") { nav.push(Route.NewContact) } }
            items(contacts, key = { it.id }) { u ->
                ContactRow(u, onLongClick = { contactOptions(u) }) { nav.push(Route.Chat(repo.privateChatWith(u.id))) }
            }
        }
        GlassTopBar(
            title = "Contacts",
            left = { GlassTextButton("Sort", { sortOptions() }) },
            right = { GlassIconButton(IosIcons.Plus, { nav.push(Route.NewContact) }, iconSize = 22.dp) },
        )
    }
}

@Composable
private fun ActionRow(icon: Int, title: String, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box {
        Row(
            Modifier.fillMaxWidth().iosClickable(onClick = onClick).height(50.dp).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, c.accent, 30.dp)
            Spacer(Modifier.width(22.dp))
            T(title, TgTheme.type.body, c.accent)
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = 70.dp)
    }
}

@Composable
fun ContactRow(u: User, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box {
        Row(
            Modifier.fillMaxWidth().iosClickable(onLongClick = onLongClick, onClick = onClick).height(58.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(u.name, u.id, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                T(u.name, TgTheme.type.headline, c.text, maxLines = 1)
                T(u.status, TgTheme.type.subheadline, if (u.online) c.accent else c.secondaryText, maxLines = 1)
            }
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = 70.dp)
    }
}

/** Spec §10: New Message (presented modally). */
@Composable
fun NewMessageScreen() {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val c = TgTheme.colors
    val backdrop = rememberLayerBackdrop()
    var query by remember { mutableStateOf("") }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val contacts = repo.contacts.filter { query.isBlank() || it.name.contains(query, true) }.sortedBy { it.name }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            LazyColumn(Modifier.fillMaxSize().layerBackdrop(backdrop), contentPadding = PaddingValues(top = top + 62.dp, bottom = 30.dp)) {
                item { SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) }
                item { ActionRow(TgIcons.CtCreateGroup, "New Group") { nav.push(Route.NewGroup) } }
                item { ActionRow(TgIcons.CtAddMember, "New Contact") { nav.push(Route.NewContact) } }
                item { ActionRow(TgIcons.CtCreateChannel, "New Channel") { nav.push(Route.NewChannel) } }
                var letter = ' '
                contacts.forEach { u ->
                    val l = u.name.firstOrNull()?.uppercaseChar() ?: '#' 
                    if (l != letter) {
                        letter = l
                        item(key = "h$l") {
                            Box(Modifier.fillMaxWidth().background(c.groupedBackground).padding(horizontal = 16.dp, vertical = 4.dp)) {
                                T(l.toString(), TgTheme.type.footnote, c.secondaryText)
                            }
                        }
                    }
                    item(key = u.id) { ContactRow(u) { nav.replaceTop(Route.Chat(repo.privateChatWith(u.id))) } }
                }
            }
            GlassTopBar(
                title = "New Message",
                left = { GlassTextButton("Cancel", { nav.pop() }) },
            )
        }
    }
}
