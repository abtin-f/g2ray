package com.abtin.tglass.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.FolderDraft
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
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Telegram limits folder names to 12 characters. */
private const val FolderNameMax = 12

/** One "chat type" row of the folder editor (Contacts, Groups… / Muted, Read, Archived). */
private class FolderChatType(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val get: (FolderDraft) -> Boolean,
    val set: (FolderDraft, Boolean) -> FolderDraft,
)

private val IncludeTypes = listOf(
    FolderChatType("Contacts", Icons.Rounded.Person, Color(0xFF007AFF), { it.includeContacts }, { d, v -> d.copy(includeContacts = v) }),
    FolderChatType("Non-Contacts", Icons.Rounded.PersonOutline, Color(0xFF32ADE6), { it.includeNonContacts }, { d, v -> d.copy(includeNonContacts = v) }),
    FolderChatType("Groups", Icons.Rounded.Group, Color(0xFF34C759), { it.includeGroups }, { d, v -> d.copy(includeGroups = v) }),
    FolderChatType("Channels", Icons.Rounded.Campaign, Color(0xFFFF9500), { it.includeChannels }, { d, v -> d.copy(includeChannels = v) }),
    FolderChatType("Bots", Icons.Rounded.SmartToy, Color(0xFFAF52DE), { it.includeBots }, { d, v -> d.copy(includeBots = v) }),
)

private val ExcludeTypes = listOf(
    FolderChatType("Muted", Icons.Rounded.NotificationsOff, Color(0xFFFF3B30), { it.excludeMuted }, { d, v -> d.copy(excludeMuted = v) }),
    FolderChatType("Read", Icons.Rounded.DoneAll, Color(0xFF34C759), { it.excludeRead }, { d, v -> d.copy(excludeRead = v) }),
    FolderChatType("Archived", Icons.Rounded.Archive, Color(0xFF8E8E93), { it.excludeArchived }, { d, v -> d.copy(excludeArchived = v) }),
)

/** Settings → Chat Folders for a real account: the account's folders plus "Create New Folder". */
@Composable
fun LiveFolderList() {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val c = TgTheme.colors
    val folders = repo.editableFolders
    Section(header = "Chat Folders", footer = if (folders.isEmpty()) null else "Tap a folder to edit or delete it.") {
        Cell(
            "Create New Folder", icon = TgIcons.SetFolders, iconColor = c.accent, titleColor = c.accent, chevron = false,
            divider = folders.isNotEmpty(), onClick = { nav.push(Route.FolderEdit(null)) },
        )
        folders.forEachIndexed { i, f ->
            Cell(f.title, subtitle = f.subtitle, divider = i != folders.lastIndex, onClick = { nav.push(Route.FolderEdit(f.id)) })
        }
    }
}

/** New Folder / Edit Folder (Telegram iOS ChatListFilterPresetController): name, included and excluded chats. */
@Composable
fun FolderEditScreen(folderId: Int?) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val c = TgTheme.colors
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var draft by remember { mutableStateOf<FolderDraft?>(if (folderId == null) FolderDraft() else null) }
    var initial by remember { mutableStateOf(draft) }
    var saving by remember { mutableStateOf(false) }
    /** true = choosing included chats, false = excluded chats, null = picker closed. */
    var picker by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(folderId) {
        if (folderId != null && draft == null) {
            repo.loadFolder(folderId) { d ->
                if (d == null) {
                    toast.show("Couldn't load this folder")
                    if (nav.top is Route.FolderEdit) nav.pop()
                } else {
                    draft = d
                    initial = d
                }
            }
        }
    }

    fun leave() {
        if (nav.top is Route.FolderEdit) nav.pop()
    }

    fun save() {
        val d = draft ?: return
        if (saving) return
        when {
            d.name.isBlank() -> toast.show("Please enter a folder name")
            !d.hasIncluded -> toast.show("Please add at least one chat or chat type to the folder")
            folderId != null && d == initial -> leave()
            else -> {
                saving = true
                repo.saveFolder(folderId, d.copy(name = d.name.trim())) { err ->
                    saving = false
                    if (err != null) toast.show(err)
                    else {
                        toast.show(if (folderId == null) "Folder created" else "Folder saved")
                        leave()
                    }
                }
            }
        }
    }

    val current = draft
    val canSave = current != null && !saving && current.name.isNotBlank() && current.hasIncluded && (folderId == null || current != initial)

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ActivityIndicator(28.dp) }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().layerBackdrop(backdrop).imePadding(),
                    contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 30.dp),
                ) {
                    item {
                        Section(header = "Folder Name") {
                            NameField(current.name, { v -> draft = draft?.copy(name = v.replace("\n", " ").take(FolderNameMax)) }, "Folder Name")
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                    item {
                        ChosenChatsSection(
                            header = "Included Chats",
                            footer = "Choose chats or types of chats that will appear in this folder.",
                            addTitle = "Add Chats",
                            types = IncludeTypes,
                            draft = current,
                            chatIds = current.includedChatIds,
                            onAdd = { picker = true },
                            onChange = { draft = it },
                            onRemoveChat = { id -> draft = draft?.let { d -> d.copy(includedChatIds = d.includedChatIds - id) } },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                    item {
                        ChosenChatsSection(
                            header = "Excluded Chats",
                            footer = "Choose chats or types of chats that will never appear in this folder.",
                            addTitle = "Remove Chats",
                            types = ExcludeTypes,
                            draft = current,
                            chatIds = current.excludedChatIds,
                            onAdd = { picker = false },
                            onChange = { draft = it },
                            onRemoveChat = { id -> draft = draft?.let { d -> d.copy(excludedChatIds = d.excludedChatIds - id) } },
                        )
                    }
                    if (folderId != null) {
                        item { Spacer(Modifier.height(24.dp)) }
                        item {
                            Section {
                                Cell("Delete Folder", titleColor = c.destructive, chevron = false, divider = false, onClick = {
                                    sheet.show(
                                        SheetRequest(
                                            title = "Delete this folder? The chats in it will not be deleted.",
                                            actions = listOf(
                                                SheetAction("Delete Folder", destructive = true) {
                                                    repo.deleteFolder(folderId) { err ->
                                                        if (err != null) toast.show(err)
                                                        else {
                                                            toast.show("Folder deleted")
                                                            leave()
                                                        }
                                                    }
                                                },
                                            ),
                                        ),
                                    )
                                })
                            }
                        }
                    }
                }
            }
            GlassTopBar(
                title = if (folderId == null) "New Folder" else "Edit Folder",
                fade = c.groupedBackground,
                right = {
                    GlassTextButton(
                        if (saving) "Saving…" else if (folderId == null) "Create" else "Save",
                        { save() },
                        color = if (canSave) c.accent else c.secondaryText,
                        bold = true,
                    )
                },
            )
            if (current != null) {
                FolderChatPicker(
                    visible = picker == true, include = true, draft = current,
                    onDismiss = { picker = null }, onDone = { d -> draft = d; picker = null },
                )
                FolderChatPicker(
                    visible = picker == false, include = false, draft = current,
                    onDismiss = { picker = null }, onDone = { d -> draft = d; picker = null },
                )
            }
        }
    }
}

@Composable
private fun ChosenChatsSection(
    header: String,
    footer: String,
    addTitle: String,
    types: List<FolderChatType>,
    draft: FolderDraft,
    chatIds: List<Long>,
    onAdd: () -> Unit,
    onChange: (FolderDraft) -> Unit,
    onRemoveChat: (Long) -> Unit,
) {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    val chosenTypes = types.filter { it.get(draft) }
    val rows = chosenTypes.size + chatIds.size
    Section(header = header, footer = footer) {
        Cell(
            addTitle, titleColor = c.accent, chevron = false, divider = rows > 0, onClick = onAdd,
            leading = { Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Icon(IosIcons.Plus, c.accent, 24.dp) } },
        )
        chosenTypes.forEachIndexed { i, t ->
            Cell(
                t.title, icon = t.icon, iconColor = t.color, chevron = false, divider = i != rows - 1,
                trailing = { RemoveButton { onChange(t.set(draft, false)) } },
            )
        }
        chatIds.forEachIndexed { i, id ->
            val chat = repo.chat(id)
            val title = chat?.title ?: "Chat"
            Cell(
                title, chevron = false, divider = chosenTypes.size + i != rows - 1,
                leading = { Avatar(title, id, 30.dp, saved = chat?.type == ChatType.Saved) },
                trailing = { RemoveButton { onRemoveChat(id) } },
            )
        }
    }
}

@Composable
private fun RemoveButton(onClick: () -> Unit) {
    Icon(Icons.Rounded.RemoveCircle, TgTheme.colors.destructive, 22.dp, Modifier.fadeClickable(onClick = onClick))
}

@Composable
private fun NameField(value: String, onValue: (String) -> Unit, placeholder: String) {
    val c = TgTheme.colors
    Box(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) T(placeholder, TgTheme.type.body, c.tertiaryText)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TgTheme.type.body.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
        )
    }
}

/** "Include Chats" / "Exclude Chats": chat types plus a searchable list of the account's chats. */
@Composable
private fun FolderChatPicker(
    visible: Boolean,
    include: Boolean,
    draft: FolderDraft,
    onDismiss: () -> Unit,
    onDone: (FolderDraft) -> Unit,
) {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    // A fresh working copy every time the picker opens; Cancel throws it away.
    var work by remember(visible) { mutableStateOf(draft) }
    var query by remember(visible) { mutableStateOf("") }
    val types = if (include) IncludeTypes else ExcludeTypes
    BackHandler(enabled = visible) { onDismiss() }
    AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        val backdrop = rememberLayerBackdrop()
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val chosen = if (include) work.includedChatIds else work.excludedChatIds
        val q = query.trim()
        val chats = repo.chats.filter { q.isEmpty() || it.title.contains(q, true) }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.groupedBackground)
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                LazyColumn(
                    Modifier.fillMaxSize().layerBackdrop(backdrop).imePadding(),
                    contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 30.dp),
                ) {
                    item {
                        Section(header = "Chat Types") {
                            types.forEachIndexed { i, t ->
                                val on = t.get(work)
                                Cell(
                                    t.title, icon = t.icon, iconColor = t.color, chevron = false, checked = on,
                                    divider = i != types.lastIndex, onClick = { work = t.set(work, !on) },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                    item {
                        T("CHATS", TgTheme.type.footnote, c.secondaryText, modifier = Modifier.padding(start = 32.dp, bottom = 7.dp, top = 4.dp))
                    }
                    item {
                        SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp), background = c.cell)
                    }
                    itemsIndexed(chats, key = { _, chat -> chat.id }) { i, chat ->
                        val first = i == 0
                        val last = i == chats.lastIndex
                        val shape = RoundedCornerShape(
                            topStart = if (first) 26.dp else 0.dp, topEnd = if (first) 26.dp else 0.dp,
                            bottomStart = if (last) 26.dp else 0.dp, bottomEnd = if (last) 26.dp else 0.dp,
                        )
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(shape).background(c.cell)) {
                            Cell(
                                chat.title, chevron = false, checked = chat.id in chosen, divider = !last,
                                leading = { Avatar(chat.title, chat.id, 30.dp, saved = chat.type == ChatType.Saved) },
                                onClick = {
                                    val id = chat.id
                                    val list = if (id in chosen) chosen - id else chosen + id
                                    work = if (include) work.copy(includedChatIds = list, excludedChatIds = work.excludedChatIds - id)
                                    else work.copy(excludedChatIds = list, includedChatIds = work.includedChatIds - id)
                                },
                            )
                        }
                    }
                    if (chats.isEmpty()) {
                        item {
                            T(
                                if (q.isEmpty()) "No chats yet" else "No chats found", TgTheme.type.subheadline, c.secondaryText,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                align = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                GlassTopBar(
                    title = if (include) "Include Chats" else "Exclude Chats",
                    fade = c.groupedBackground,
                    left = { GlassTextButton("Cancel", onDismiss) },
                    right = { GlassTextButton("Done", { onDone(work) }, color = c.accent, bold = true) },
                )
            }
        }
    }
}
