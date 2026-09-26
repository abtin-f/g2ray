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
import com.abtin.tglass.ui.components.SearchField
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
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
    val c = TgTheme.colors
    var query by remember { mutableStateOf("") }
    var byName by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contacts = repo.users.values
        .filter { it.id != 0L && it.id != 10L && (query.isBlank() || it.name.contains(query, true)) }
        .let { list -> if (byName) list.sortedBy { it.name } else list.sortedWith(compareByDescending<User> { it.online }.thenBy { it.name }) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            Modifier.fillMaxSize().layerBackdrop(backdrop),
            contentPadding = PaddingValues(top = top + 62.dp, bottom = bottom + if (isTab) 110.dp else 20.dp),
        ) {
            item { SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) }
            item { ActionRow(Icons.Rounded.Share, "Invite Friends") { toast.show("Invite link copied") } }
            item { ActionRow(Icons.Rounded.PersonAdd, "Add Contact") { toast.show("Add contact") } }
            items(contacts, key = { it.id }) { u -> ContactRow(u) { nav.push(Route.Chat(repo.privateChatWith(u.id))) } }
        }
        GlassTopBar(
            title = "Contacts",
            left = { GlassTextButton("Sort", { byName = !byName; toast.show(if (byName) "Sorted by name" else "Sorted by last seen") }) },
            right = { GlassIconButton(Icons.Rounded.Add, { toast.show("New contact") }) },
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box {
        Row(
            Modifier.fillMaxWidth().iosClickable(onClick = onClick).height(50.dp).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, c.accent, 26.dp)
            Spacer(Modifier.width(22.dp))
            T(title, TgTheme.type.body, c.accent)
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = 70.dp)
    }
}

@Composable
fun ContactRow(u: User, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box {
        Row(
            Modifier.fillMaxWidth().iosClickable(onClick = onClick).height(58.dp).padding(horizontal = 14.dp),
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
    val contacts = repo.users.values.filter { it.id != 0L && (query.isBlank() || it.name.contains(query, true)) }.sortedBy { it.name }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            LazyColumn(Modifier.fillMaxSize().layerBackdrop(backdrop), contentPadding = PaddingValues(top = top + 62.dp, bottom = 30.dp)) {
                item { SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) }
                item { ActionRow(Icons.Rounded.Group, "New Group") { toast.show("New group") } }
                item { ActionRow(Icons.Rounded.PersonAdd, "New Contact") { toast.show("New contact") } }
                item { ActionRow(Icons.Rounded.Campaign, "New Channel") { toast.show("New channel") } }
                var letter = ' '
                contacts.forEach { u ->
                    val l = u.name.first().uppercaseChar()
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
