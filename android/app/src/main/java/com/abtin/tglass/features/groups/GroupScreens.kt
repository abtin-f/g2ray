package com.abtin.tglass.features.groups

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.media.MediaPrep
import com.abtin.tglass.core.media.PickedMedia
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.User
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.SearchField
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

// =====================================================================================
// New Group / Add Members: contact picker
// =====================================================================================

/**
 * Contact multi-picker with chips and search. With [chatId] == null it is step 1 of New Group ("Next" goes to
 * the name step); otherwise it adds the picked users to that group/channel.
 */
@Composable
fun MemberPickerScreen(chatId: Long?) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val c = TgTheme.colors
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var query by rememberSaveable { mutableStateOf("") }
    val selected: SnapshotStateList<Long> = rememberSaveable(
        saver = listSaver<SnapshotStateList<Long>, Long>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<Long>() }
    var busy by remember { mutableStateOf(false) }

    if (chatId != null) LaunchedEffect(chatId) { repo.loadChatInfo(chatId) }
    val chat = chatId?.let { repo.chat(it) }
    val isChannel = chat?.type == ChatType.Channel
    val existing: Set<Long> = if (chatId != null) repo.chatInfo(chatId)?.members.orEmpty().map { it.userId }.toSet() else emptySet()
    val meId = repo.me.id
    val contacts = repo.contacts
        .filter { it.id != meId }
        .filter { u -> query.isBlank() || u.name.contains(query, true) || u.username?.contains(query.removePrefix("@"), true) == true }
        .sortedBy { it.name.lowercase() }
    val chips = selected.mapNotNull { repo.user(it) }

    fun toggle(u: User) {
        if (u.id in existing) return
        if (u.id in selected) selected.remove(u.id) else selected.add(u.id)
    }

    fun done() {
        if (busy) return
        if (chatId == null) {
            nav.push(Route.NewGroupInfo(selected.toList()))
            return
        }
        if (selected.isEmpty()) return
        busy = true
        val count = selected.size
        repo.addMembers(chatId, selected.toList()) { err ->
            busy = false
            if (err != null) toast.show(err, Icons.Rounded.ErrorOutline)
            else toast.show(if (count == 1) "1 member added" else "$count members added")
            nav.pop()
        }
    }

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.background)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 62.dp, bottom = bottom + 30.dp),
            ) {
                if (chips.isNotEmpty()) {
                    item(key = "chips") {
                        LazyRow(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(chips, key = { it.id }) { u -> UserChip(u) { selected.remove(u.id) } }
                        }
                    }
                }
                item(key = "search") {
                    SearchField(
                        query, { query = it },
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        placeholder = if (chatId == null) "Who would you like to add?" else "Search",
                    )
                }
                if (contacts.isEmpty()) {
                    item(key = "empty") {
                        T(
                            if (query.isBlank()) "You have no contacts yet." else "No contacts found.",
                            TgTheme.type.subheadline, c.secondaryText,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
                items(contacts, key = { it.id }) { u ->
                    val already = u.id in existing
                    SelectableUserRow(
                        user = u,
                        checked = already || u.id in selected,
                        enabled = !already,
                        subtitle = if (already) (if (isChannel) "already subscribed" else "already in the group") else null,
                    ) { toggle(u) }
                }
            }
            val title = when {
                chatId == null -> "New Group"
                isChannel -> "Add Subscribers"
                else -> "Add Members"
            }
            val canContinue = chatId == null || selected.isNotEmpty()
            GlassTopBar(
                title = title,
                subtitle = if (selected.isEmpty()) null else if (selected.size == 1) "1 selected" else "${selected.size} selected",
                right = {
                    if (busy) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { ActivityIndicator(20.dp) }
                    else GlassTextButton(
                        if (chatId == null) "Next" else "Add",
                        { if (canContinue) done() },
                        color = if (canContinue) c.accent else c.tertiaryText,
                        bold = true,
                    )
                },
            )
        }
    }
}

@Composable
private fun UserChip(u: User, onRemove: () -> Unit) {
    val c = TgTheme.colors
    Row(
        Modifier
            .height(30.dp)
            .clip(Capsule())
            .background(c.accent.copy(alpha = if (c.isDark) 0.28f else 0.12f))
            .fadeClickable(onClick = onRemove)
            .padding(start = 3.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(u.name, u.id, 24.dp)
        Spacer(Modifier.width(6.dp))
        T(u.firstName.ifBlank { u.name }, TgTheme.type.subheadline, c.accent, maxLines = 1, weight = FontWeight.Medium)
    }
}

/** Contact row with an iOS check circle on the left. */
@Composable
private fun SelectableUserRow(user: User, checked: Boolean, enabled: Boolean, subtitle: String?, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .iosClickable(onClick = onClick)
                .height(58.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckCircle(checked, enabled)
            Spacer(Modifier.width(12.dp))
            Avatar(user.name, user.id, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(user.name, TgTheme.type.headline, if (enabled) c.text else c.secondaryText, maxLines = 1)
                T(subtitle ?: user.status, TgTheme.type.subheadline, if (subtitle == null && user.online) c.accent else c.secondaryText, maxLines = 1)
            }
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = 104.dp)
    }
}

@Composable
private fun CheckCircle(checked: Boolean, enabled: Boolean, size: Dp = 24.dp) {
    val c = TgTheme.colors
    if (checked) {
        Box(
            Modifier.size(size).clip(CircleShape).background(if (enabled) c.accent else c.tertiaryText),
            contentAlignment = Alignment.Center,
        ) { Icon(IosIcons.Checkmark, Color.White, size * 0.7f) }
    } else {
        Box(Modifier.size(size).clip(CircleShape).border(1.5.dp, c.tertiaryText, CircleShape))
    }
}

// =====================================================================================
// New Group (name + photo) and New Channel
// =====================================================================================

/** Step 2 of New Group: name and optional photo, then creates the group and opens it. */
@Composable
fun NewGroupInfoScreen(userIds: List<Long>) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    var name by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val pickPhoto = rememberChatPhotoPicker { photo = it }
    val members = userIds.mapNotNull { repo.user(it) }

    fun create() {
        if (name.isBlank() || creating) return
        creating = true
        repo.createGroup(name.trim(), userIds, photo) { id, err ->
            creating = false
            if (id != null) nav.popToRootAndPush(Route.Chat(id))
            else toast.show(err ?: "Couldn't create the group", Icons.Rounded.ErrorOutline)
        }
    }

    FormScaffold(
        title = "New Group",
        action = "Create",
        actionEnabled = name.isNotBlank(),
        busy = creating,
        onAction = { create() },
    ) {
        item {
            Section(footer = "Enter a name for the group and optionally set a photo.") {
                NameWithPhoto(name, { name = it.take(128) }, "Group Name", photo, onPickPhoto = pickPhoto)
            }
            Spacer(Modifier.height(24.dp))
        }
        if (members.isNotEmpty()) {
            item {
                Section(header = if (members.size == 1) "1 member" else "${members.size} members") {
                    members.forEachIndexed { i, u ->
                        Cell(
                            u.name,
                            subtitle = u.status,
                            leading = { Avatar(u.name, u.id, 40.dp) },
                            chevron = false,
                            divider = i != members.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

/** New Channel: name, description and optional photo, then creates the channel and opens it. */
@Composable
fun NewChannelScreen() {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    var name by rememberSaveable { mutableStateOf("") }
    var about by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val pickPhoto = rememberChatPhotoPicker { photo = it }

    fun create() {
        if (name.isBlank() || creating) return
        creating = true
        repo.createChannel(name.trim(), about.trim(), photo) { id, err ->
            creating = false
            if (id != null) nav.popToRootAndPush(Route.Chat(id))
            else toast.show(err ?: "Couldn't create the channel", Icons.Rounded.ErrorOutline)
        }
    }

    FormScaffold(
        title = "New Channel",
        action = "Create",
        actionEnabled = name.isNotBlank(),
        busy = creating,
        onAction = { create() },
    ) {
        item {
            Section {
                NameWithPhoto(name, { name = it.take(128) }, "Channel Name", photo, onPickPhoto = pickPhoto)
            }
            Spacer(Modifier.height(24.dp))
            Section(footer = "You can provide an optional description for your channel.") {
                FormField(about, { about = it.take(255) }, "Description", divider = false, singleLine = false)
            }
        }
    }
}

// =====================================================================================
// Edit group / channel
// =====================================================================================

/** Edit a group or channel: photo, title, description; the owner can also delete it. */
@Composable
fun EditChatScreen(chatId: Long) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val c = TgTheme.colors
    val chat = repo.chat(chatId) ?: return
    LaunchedEffect(chatId) { repo.loadChatInfo(chatId) }
    val isChannel = chat.type == ChatType.Channel
    val currentAbout = repo.chatInfo(chatId)?.about ?: chat.description ?: ""
    // Null until the user types, so late-arriving info still fills the fields.
    var titleEdit by rememberSaveable { mutableStateOf<String?>(null) }
    var aboutEdit by rememberSaveable { mutableStateOf<String?>(null) }
    val title = titleEdit ?: chat.title
    val about = aboutEdit ?: currentAbout
    var saving by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val canEdit = chat.rights?.changeInfo == true
    val hasPhoto = repo.avatar(chatId) != null

    val pickPhoto = rememberChatPhotoPicker { path ->
        uploading = true
        repo.updateChatPhoto(chatId, path) { err ->
            uploading = false
            if (err != null) toast.show(err, Icons.Rounded.ErrorOutline) else toast.show("Photo updated")
        }
    }
    fun photoOptions() {
        if (!canEdit || uploading) return
        if (!hasPhoto) { pickPhoto(); return }
        sheet.show(SheetRequest(actions = listOf(
            SheetAction("Set New Photo") { pickPhoto() },
            SheetAction("Remove Photo", destructive = true) {
                uploading = true
                repo.updateChatPhoto(chatId, null) { err -> uploading = false; if (err != null) toast.show(err, Icons.Rounded.ErrorOutline) }
            },
        )))
    }
    fun save() {
        if (saving) return
        val changed = title.trim() != chat.title || about.trim() != currentAbout
        if (!changed || !canEdit) { nav.pop(); return }
        if (title.isBlank()) { toast.show("Please enter a name", Icons.Rounded.ErrorOutline); return }
        saving = true
        repo.editChat(chatId, title.trim(), about.trim()) { err ->
            saving = false
            if (err != null) toast.show(err, Icons.Rounded.ErrorOutline) else nav.pop()
        }
    }
    fun delete() {
        val what = if (isChannel) "Channel" else "Group"
        sheet.show(SheetRequest(
            title = "Delete $what?",
            message = if (isChannel) "This will delete the channel and all its posts for all subscribers." else "This will delete the group and all its messages for all members.",
            alert = true,
            actions = listOf(SheetAction("Delete", destructive = true) {
                repo.deleteChatForAll(chatId) { err ->
                    if (err != null) toast.show(err, Icons.Rounded.ErrorOutline)
                    else { toast.show("$what deleted"); nav.resetTo(Route.Main) }
                }
            }),
        ))
    }

    FormScaffold(
        title = "Edit",
        action = "Done",
        actionEnabled = true,
        busy = saving,
        onAction = { save() },
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fadeClickable(enabled = canEdit) { photoOptions() }, contentAlignment = Alignment.Center) {
                    Avatar(chat.title, chat.id, 100.dp, photoPeer = chat.id)
                    if (uploading) ActivityIndicator(28.dp, Color.White)
                }
                if (canEdit) {
                    Spacer(Modifier.height(8.dp))
                    T(if (hasPhoto) "Edit Photo" else "Set New Photo", TgTheme.type.body, c.accent, modifier = Modifier.fadeClickable { photoOptions() })
                }
            }
            Section {
                FormField(title, { if (canEdit) titleEdit = it.take(128) }, if (isChannel) "Channel Name" else "Group Name", enabled = canEdit)
                FormField(about, { if (canEdit) aboutEdit = it.take(255) }, "Description (optional)", divider = false, singleLine = false, enabled = canEdit)
            }
            if (!canEdit) {
                T(
                    "Only admins with the right to change info can edit this.",
                    TgTheme.type.footnote, c.secondaryText,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            if (chat.rights?.owner == true) {
                Section {
                    Cell(
                        if (isChannel) "Delete Channel" else "Delete Group",
                        titleColor = c.destructive,
                        chevron = false,
                        divider = false,
                        onClick = { delete() },
                    )
                }
            }
        }
    }
}

// =====================================================================================
// Profile helpers
// =====================================================================================

/** A member row on the profile's Members tab; long press offers admin actions. */
@Composable
fun MemberCell(user: User, role: String?, divider: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val c = TgTheme.colors
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .iosClickable(onLongClick = onLongClick, onClick = onClick)
                .heightIn(min = 58.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user.name, user.id, 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                T(user.name, TgTheme.type.body, c.text, maxLines = 1)
                T(user.status, TgTheme.type.subheadline, if (user.online) c.accent else c.secondaryText, maxLines = 1)
            }
            if (role != null) T(role, TgTheme.type.subheadline, c.secondaryText, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = 70.dp)
    }
}

// =====================================================================================
// Building blocks
// =====================================================================================

/** Grouped-background page with a glass top bar and one text action on the right. */
@Composable
private fun FormScaffold(
    title: String,
    action: String,
    actionEnabled: Boolean,
    busy: Boolean,
    onAction: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val c = TgTheme.colors
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 30.dp),
                content = content,
            )
            GlassTopBar(
                title = title,
                fade = c.groupedBackground,
                right = {
                    if (busy) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { ActivityIndicator(20.dp) }
                    else GlassTextButton(action, { if (actionEnabled) onAction() }, color = if (actionEnabled) c.accent else c.tertiaryText, bold = true)
                },
            )
        }
    }
}

/** iOS "photo + name" cell of the New Group / New Channel forms. */
@Composable
private fun NameWithPhoto(name: String, onName: (String) -> Unit, placeholder: String, photo: String?, onPickPhoto: () -> Unit) {
    val c = TgTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(c.accent.copy(alpha = if (c.isDark) 0.25f else 0.12f)).fadeClickable(onClick = onPickPhoto),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) TgImage(ImageRef(0, photo), Modifier.fillMaxSize(), maxPx = 256)
            else Icon(IosIcons.Camera, c.accent, 30.dp)
        }
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (name.isEmpty()) T(placeholder, TgTheme.type.body, c.tertiaryText, maxLines = 1)
            BasicTextField(
                name, onName, Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TgTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
            )
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    divider: Boolean = true,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    val c = TgTheme.colors
    Box {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 13.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) T(placeholder, TgTheme.type.body, c.tertiaryText, maxLines = 1)
            BasicTextField(
                value, onValue, Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 6,
                textStyle = TgTheme.type.body.copy(color = if (enabled) c.text else c.secondaryText),
                cursorBrush = SolidColor(c.accent),
            )
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = 16.dp)
    }
}

/** Gallery photo picker; the picked image is prepared (upright, re-encoded JPEG) and its local path handed to [onPicked]. */
@Composable
fun rememberChatPhotoPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = MediaPrep.prepare(context, listOf(PickedMedia(uri, false)), null).firstOrNull()?.image?.path
            if (path == null) toast.show("Can't read this photo", Icons.Rounded.ErrorOutline) else callback(path)
        }
    }
    return remember(launcher) {
        val open: () -> Unit = { runCatching { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
        open
    }
}
