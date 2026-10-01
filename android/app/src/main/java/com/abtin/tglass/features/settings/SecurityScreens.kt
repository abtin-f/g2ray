package com.abtin.tglass.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.EmptyState
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.PrimaryButton
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T

// ---- Blocked Users ----

internal fun LazyListScope.blockedUsers() {
    item {
        val repo = LocalRepository.current
        val sheet = LocalActionSheet.current
        val toast = LocalToast.current
        val nav = LocalNavigator.current
        LaunchedEffect(Unit) { repo.loadBlocked() }
        val list = repo.blockedPeers
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { ActivityIndicator(26.dp) }
            list.isEmpty() -> EmptyState("🙅", "No Blocked Users", "Blocked users can't send you messages or add you to groups.")
            else -> {
                val total = repo.blockedCount ?: list.size
                Section(
                    header = if (total == 1) "1 blocked user" else "$total blocked users",
                    footer = "Blocked users can't send you messages or add you to groups. They will not see your profile photos, stories, online and last seen status.",
                ) {
                    list.forEachIndexed { i, p ->
                        Cell(
                            p.name,
                            subtitle = p.subtitle.ifBlank { null },
                            leading = { Avatar(p.name, p.id, 38.dp, photoPeer = p.id) },
                            chevron = false,
                            divider = i != list.lastIndex,
                            onClick = {
                                sheet.show(SheetRequest(
                                    title = p.name,
                                    actions = listOf(
                                        SheetAction("Unblock", bold = true) {
                                            repo.unblock(p) { err -> if (err != null) toast.show(err, Icons.Rounded.ErrorOutline) else toast.show("${p.name} unblocked") }
                                        },
                                        SheetAction(if (p.isUser) "Send Message" else "Open Chat") {
                                            nav.push(Route.Chat(if (p.isUser) repo.privateChatWith(p.id) else p.id))
                                        },
                                    ),
                                ))
                            },
                        )
                    }
                }
            }
        }
    }
}

// ---- Two-Step Verification ----

private enum class PasswordMode { None, Set, Change, Remove, Email }

internal fun LazyListScope.twoStep() {
    item { TwoStepPage() }
}

@Composable
private fun TwoStepPage() {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    val toast = LocalToast.current
    LaunchedEffect(Unit) { repo.loadPasswordInfo() }
    val info = repo.passwordInfo
    var mode by rememberSaveable { mutableStateOf(PasswordMode.None) }
    var current by rememberSaveable { mutableStateOf("") }
    var new1 by rememberSaveable { mutableStateOf("") }
    var new2 by rememberSaveable { mutableStateOf("") }
    var hint by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun reset() {
        mode = PasswordMode.None
        current = ""; new1 = ""; new2 = ""; hint = ""; email = ""; code = ""
    }
    fun fail(message: String) = toast.show(message, Icons.Rounded.ErrorOutline)
    val done: (String?, String) -> Unit = { err, ok ->
        busy = false
        if (err != null) fail(err) else {
            val pending = repo.passwordInfo?.pendingEmailPattern
            toast.show(if (pending != null) "Check $pending for the confirmation code" else ok)
            reset()
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(90.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF72D5FD), Color(0xFF2A9EF1)))),
            contentAlignment = Alignment.Center,
        ) { Icon(IosIcons.Lock, Color.White, 50.dp) }
        Spacer(Modifier.height(14.dp))
        T(
            when {
                info == null -> "Loading…"
                info.hasPassword -> "Two-Step Verification is on. Your account is protected with an additional password, required when you log in on a new device."
                else -> "You can set a password that will be required when you log in on a new device in addition to the code you get via SMS or Telegram."
            },
            TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
    if (info == null) {
        Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) { ActivityIndicator(26.dp) }
        return
    }

    val pending = info.pendingEmailPattern
    if (pending != null && mode == PasswordMode.None) {
        Section(header = "Recovery Email", footer = "Please enter the code we sent to $pending to confirm your recovery email.") {
            FormField(code, { code = it.filter { ch -> ch.isDigit() }.take(12) }, "Code", divider = false, keyboard = KeyboardType.Number)
        }
        Spacer(Modifier.height(24.dp))
        Section {
            Cell(if (busy) "Checking…" else "Confirm", titleColor = if (code.isNotEmpty() && !busy) c.accent else c.secondaryText, chevron = false,
                onClick = if (code.isEmpty() || busy) null else ({
                    busy = true
                    repo.confirmRecoveryEmail(code) { err -> done(err, "Recovery email confirmed") }
                }))
            Cell("Resend Code", titleColor = c.accent, chevron = false, divider = false, onClick = {
                repo.resendRecoveryEmailCode { err -> if (err != null) fail(err) else toast.show("Code sent to $pending") }
            })
        }
        Spacer(Modifier.height(24.dp))
    }

    when (mode) {
        PasswordMode.None -> {
            if (!info.hasPassword) {
                PrimaryButton("Set Password", { mode = PasswordMode.Set }, Modifier.padding(horizontal = 32.dp))
            } else {
                Section(footer = if (info.hint.isNotBlank()) "Hint: ${info.hint}" else null) {
                    Cell("Change Password", onClick = { mode = PasswordMode.Change })
                    Cell(if (info.hasRecoveryEmail) "Change Recovery Email" else "Set Recovery Email", onClick = { mode = PasswordMode.Email })
                    Cell("Turn Password Off", titleColor = c.destructive, chevron = false, divider = false, onClick = { mode = PasswordMode.Remove })
                }
            }
        }
        PasswordMode.Set, PasswordMode.Change -> {
            if (mode == PasswordMode.Change) {
                Section(header = "Current Password") {
                    FormField(current, { current = it }, "Current Password", divider = false, secret = true)
                }
                Spacer(Modifier.height(24.dp))
            }
            Section(header = "New Password") {
                FormField(new1, { new1 = it }, "Enter a password", secret = true)
                FormField(new2, { new2 = it }, "Re-enter password", divider = false, secret = true)
            }
            Spacer(Modifier.height(24.dp))
            Section(header = "Hint", footer = "Optional. The hint is shown when you are asked for this password.") {
                FormField(hint, { hint = it.take(64) }, "Password hint", divider = false)
            }
            if (mode == PasswordMode.Set) {
                Spacer(Modifier.height(24.dp))
                Section(header = "Recovery Email", footer = "Optional, but recommended: it lets you reset the password if you forget it.") {
                    FormField(email, { email = it.trim() }, "Your email", divider = false, keyboard = KeyboardType.Email)
                }
            }
            Spacer(Modifier.height(24.dp))
            Section {
                Cell(if (busy) "Saving…" else "Save Password", titleColor = if (busy) c.secondaryText else c.accent, chevron = false, onClick = if (busy) null else ({
                    when {
                        mode == PasswordMode.Change && current.isEmpty() -> fail("Please enter your current password.")
                        new1.isEmpty() -> fail("Please enter a new password.")
                        new1 != new2 -> fail("Passwords don't match.")
                        hint.isNotBlank() && hint.trim() == new1 -> fail("The hint must be different from the password.")
                        email.isNotBlank() && ('@' !in email || '.' !in email.substringAfter('@')) -> fail("Please enter a valid email address.")
                        else -> {
                            busy = true
                            val old = if (mode == PasswordMode.Change) current else ""
                            val recovery = if (mode == PasswordMode.Set) email.ifBlank { null } else null
                            repo.setPassword(old, new1, hint.trim(), recovery) { err -> done(err, "Password saved") }
                        }
                    }
                }))
                Cell("Cancel", titleColor = c.accent, chevron = false, divider = false, onClick = { reset() })
            }
        }
        PasswordMode.Remove -> {
            Section(header = "Current Password", footer = "After turning the password off, your account will be protected only by the login code.") {
                FormField(current, { current = it }, "Current Password", divider = false, secret = true)
            }
            Spacer(Modifier.height(24.dp))
            Section {
                Cell(if (busy) "Turning Off…" else "Turn Password Off", titleColor = if (busy) c.secondaryText else c.destructive, chevron = false, onClick = if (busy) null else ({
                    if (current.isEmpty()) fail("Please enter your current password.")
                    else {
                        busy = true
                        repo.setPassword(current, "", "", null) { err -> done(err, "Password turned off") }
                    }
                }))
                Cell("Cancel", titleColor = c.accent, chevron = false, divider = false, onClick = { reset() })
            }
        }
        PasswordMode.Email -> {
            Section(header = "Current Password") {
                FormField(current, { current = it }, "Current Password", divider = false, secret = true)
            }
            Spacer(Modifier.height(24.dp))
            Section(header = "Recovery Email", footer = "We will send a code to this address to confirm it.") {
                FormField(email, { email = it.trim() }, "Your email", divider = false, keyboard = KeyboardType.Email)
            }
            Spacer(Modifier.height(24.dp))
            Section {
                Cell(if (busy) "Saving…" else "Save Email", titleColor = if (busy) c.secondaryText else c.accent, chevron = false, onClick = if (busy) null else ({
                    when {
                        current.isEmpty() -> fail("Please enter your current password.")
                        '@' !in email || '.' !in email.substringAfter('@') -> fail("Please enter a valid email address.")
                        else -> {
                            busy = true
                            repo.setRecoveryEmail(current, email) { err -> done(err, "Recovery email saved") }
                        }
                    }
                }))
                Cell("Cancel", titleColor = c.accent, chevron = false, divider = false, onClick = { reset() })
            }
        }
    }
}

/** Text field row inside a [Section]. */
@Composable
internal fun FormField(
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    divider: Boolean = true,
    secret: Boolean = false,
    keyboard: KeyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
) {
    val c = TgTheme.colors
    Box {
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) T(placeholder, TgTheme.type.body, c.tertiaryText)
                BasicTextField(
                    value, onValue, Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TgTheme.type.body.copy(color = c.text),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                )
            }
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = 16.dp)
    }
}
