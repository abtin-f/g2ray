package com.abtin.tglass.features.profile

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.data.User
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.IOSSwitch
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * iOS "Edit Contact" / "Add Contact" page, shown over the profile: first and last name, "Share My Phone
 * Number" when adding, and "Delete Contact" for existing contacts. Has its own backdrop for its glass bar.
 */
@Composable
internal fun EditContactPage(user: User, isContact: Boolean, onClose: () -> Unit, onDeleted: () -> Unit) {
    val repo = LocalRepository.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val c = TgTheme.colors
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var first by rememberSaveable(user.id) { mutableStateOf(user.firstName) }
    var last by rememberSaveable(user.id) { mutableStateOf(user.lastName) }
    var sharePhone by rememberSaveable(user.id) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val valid = first.isNotBlank()
    BackHandler { onClose() }

    fun save() {
        if (!valid || saving) return
        saving = true
        repo.saveContact(user.id, first.trim(), last.trim(), sharePhone && !isContact) { err ->
            saving = false
            if (err != null) toast.show(err, Icons.Rounded.ErrorOutline)
            else {
                toast.show(if (isContact) "Contact updated" else "${first.trim()} added to contacts")
                onClose()
            }
        }
    }

    fun delete() {
        sheet.show(
            SheetRequest(
                title = "Delete ${user.name} from your contacts?",
                actions = listOf(SheetAction("Delete Contact", destructive = true) {
                    repo.removeContact(user.id) { err ->
                        if (err != null) toast.show(err, Icons.Rounded.ErrorOutline)
                        else {
                            toast.show("${user.name} removed from contacts")
                            onDeleted()
                        }
                    }
                }),
            )
        )
    }

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.groupedBackground)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop).background(c.groupedBackground),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 30.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Avatar(user.name, user.id, 96.dp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Section {
                        NameField(first, { first = it.take(64) }, "First name (required)")
                        NameField(last, { last = it.take(64) }, "Last name (optional)", divider = false)
                    }
                    if (user.phone.isNotBlank()) {
                        Spacer(Modifier.height(24.dp))
                        Section { Cell("mobile", value = user.phone, chevron = false, divider = false) }
                    }
                    if (!isContact) {
                        Spacer(Modifier.height(24.dp))
                        Section(footer = "You can make your phone visible to ${user.firstName.ifBlank { user.name }}.") {
                            Cell("Share My Phone Number", chevron = false, divider = false, trailing = {
                                IOSSwitch(sharePhone, { sharePhone = it })
                            })
                        }
                    } else {
                        Spacer(Modifier.height(24.dp))
                        Section {
                            Cell("Delete Contact", titleColor = c.destructive, chevron = false, divider = false, onClick = { delete() })
                        }
                    }
                }
            }
            GlassTopBar(
                title = if (isContact) "Edit Contact" else "Add Contact",
                fade = c.groupedBackground,
                left = { GlassTextButton("Cancel", { onClose() }) },
                right = {
                    if (saving) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { ActivityIndicator(20.dp) }
                    else GlassTextButton("Done", { save() }, color = if (valid) c.accent else c.tertiaryText, bold = true)
                },
            )
        }
    }
}

@Composable
private fun NameField(value: String, onValue: (String) -> Unit, placeholder: String, divider: Boolean = true) {
    val c = TgTheme.colors
    Box {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 13.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) T(placeholder, TgTheme.type.body, c.tertiaryText, maxLines = 1)
            BasicTextField(
                value, onValue, Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TgTheme.type.body.copy(color = c.text, textDirection = TextDirection.Content, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = 16.dp)
    }
}
