package com.abtin.tglass.features.contacts

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.media.Sharing
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
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
 * iOS "New Contact" form: first name, last name and phone number. Adds the contact on Telegram and opens the chat;
 * numbers that are not on Telegram get an SMS invitation instead.
 */
@Composable
fun NewContactScreen() {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val context = LocalContext.current
    val c = TgTheme.colors
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("+") }
    var saving by remember { mutableStateOf(false) }
    val valid = first.isNotBlank() && phone.count { it.isDigit() } >= 5
    val name = "${first.trim()} ${last.trim()}".trim()

    fun create() {
        if (!valid || saving) return
        saving = true
        val number = phone.trim()
        repo.addContact(first.trim(), last.trim(), number) { userId, error ->
            saving = false
            when {
                error != null -> toast.show(error, Icons.Rounded.ErrorOutline)
                userId != null -> {
                    toast.show("$name added to contacts")
                    nav.popToRootAndPush(Route.Chat(repo.privateChatWith(userId)))
                }
                else -> {
                    toast.show("$name is not on Telegram", Icons.Rounded.ErrorOutline)
                    sheet.show(
                        SheetRequest(
                            title = "Not on Telegram",
                            message = "$name isn't using Telegram yet. Send an invitation by SMS?",
                            actions = listOf(SheetAction("Invite") {
                                if (!Sharing.smsInvite(context, number)) toast.show("No app to send the invitation", Icons.Rounded.ErrorOutline)
                            }),
                            alert = true,
                        )
                    )
                }
            }
        }
    }

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 30.dp),
            ) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Avatar(name.ifBlank { " " }, 0L, 96.dp, icon = if (name.isBlank()) Icons.Rounded.Person else null)
                    }
                    Spacer(Modifier.height(12.dp))
                    Section {
                        ContactField(first, { first = it.take(64) }, "First name (required)", capitalize = true)
                        ContactField(last, { last = it.take(64) }, "Last name (optional)", capitalize = true, divider = false)
                    }
                    Spacer(Modifier.height(24.dp))
                    Section(footer = "Enter the number with its country code. If the person is on Telegram, you can start chatting right away.") {
                        ContactField(
                            phone,
                            { v -> phone = "+" + v.filter { it.isDigit() || it == ' ' }.trimStart().take(20) },
                            "+1 234 567 8900",
                            phone = true,
                            divider = false,
                        )
                    }
                }
            }
            GlassTopBar(
                title = "New Contact",
                fade = c.groupedBackground,
                left = { GlassTextButton("Cancel", { nav.pop() }) },
                right = {
                    if (saving) Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { ActivityIndicator(20.dp) }
                    else GlassTextButton("Create", { create() }, color = if (valid) c.accent else c.tertiaryText, bold = true)
                },
            )
        }
    }
}

@Composable
private fun ContactField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    divider: Boolean = true,
    capitalize: Boolean = false,
    phone: Boolean = false,
) {
    val c = TgTheme.colors
    Box {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 13.dp), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() || (phone && value == "+")) T(placeholder, TgTheme.type.body, c.tertiaryText, maxLines = 1)
            BasicTextField(
                value, onValue, Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TgTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
                    keyboardType = if (phone) KeyboardType.Phone else KeyboardType.Text,
                ),
            )
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = 16.dp)
    }
}
