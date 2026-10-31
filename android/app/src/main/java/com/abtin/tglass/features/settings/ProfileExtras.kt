package com.abtin.tglass.features.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.data.PersonalChannel
import com.abtin.tglass.data.UsernameCheck
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay

/**
 * Settings → Username (Telegram-iOS UsernameSetupController): the field, a live availability check while
 * typing, the rules and the t.me link. Done saves; an empty field removes the username.
 */
@Composable
fun UsernameScreen() {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val sheet = LocalActionSheet.current
    val c = TgTheme.colors
    val current = repo.me.username ?: ""
    var text by rememberSaveable { mutableStateOf(current) }
    var check by remember { mutableStateOf<UsernameCheck?>(null) }
    var saving by remember { mutableStateOf(false) }
    val changed = text != current

    LaunchedEffect(text) {
        check = null
        if (text.isEmpty() || text.equals(current, ignoreCase = false)) return@LaunchedEffect
        check = UsernameCheck.Checking
        delay(350)
        repo.checkUsername(text) { result -> if (result != UsernameCheck.Checking) check = result }
    }
    val canSave = changed && !saving && (text.isEmpty() || check == UsernameCheck.Available)
    fun done() {
        if (!changed) { nav.pop(); return }
        if (!canSave) return
        saving = true
        repo.updateUsername(text) { err ->
            saving = false
            if (err != null) sheet.show(SheetRequest(title = "Username", message = err, alert = true, actions = emptyList(), cancel = "OK"))
            else nav.pop()
        }
    }

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val backdrop = rememberLayerBackdrop()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 30.dp),
            ) {
                item {
                    Section(header = "Username") {
                        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("@", TgTheme.type.body, c.secondaryText)
                            Box(Modifier.weight(1f)) {
                                if (text.isEmpty()) T("Username", TgTheme.type.body, c.tertiaryText)
                                BasicTextField(
                                    text,
                                    { v -> text = v.filter { ch -> ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_' }.take(32) },
                                    Modifier.fillMaxWidth().focusRequester(focus),
                                    singleLine = true,
                                    textStyle = TgTheme.type.body.copy(color = c.text, textDirection = TextDirection.Ltr),
                                    cursorBrush = SolidColor(c.accent),
                                )
                            }
                            if (check == UsernameCheck.Checking) ActivityIndicator(16.dp)
                        }
                    }
                    val status = check
                    Column(Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp)) {
                        if (status != null) {
                            T(
                                if (status == UsernameCheck.Available) "$text ${status.message}" else status.message,
                                TgTheme.type.footnote,
                                when {
                                    status == UsernameCheck.Checking -> c.secondaryText
                                    status.ok -> c.green
                                    else -> c.destructive
                                },
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        T(
                            "You can choose a username on Telegram. If you do, people will be able to find you by this username and contact you without needing your phone number.\n\n" +
                                "You can use a–z, 0–9 and underscores. Minimum length is 5 characters.",
                            TgTheme.type.footnote, c.secondaryText,
                        )
                        if (text.length >= 5) {
                            Spacer(Modifier.height(8.dp))
                            T("This link opens a chat with you:", TgTheme.type.footnote, c.secondaryText)
                            T("https://t.me/$text", TgTheme.type.footnote, c.accent)
                        }
                    }
                }
            }
            GlassTopBar(
                "Username",
                fade = c.groupedBackground,
                right = {
                    if (saving) {
                        GlassBox(onClick = null, modifier = Modifier.height(44.dp), shape = Capsule()) {
                            Box(Modifier.padding(horizontal = 22.dp), contentAlignment = Alignment.Center) { ActivityIndicator(20.dp) }
                        }
                    } else {
                        GlassTextButton("Done", { done() }, color = if (canSave) c.accent else c.secondaryText, bold = true)
                    }
                },
            )
        }
    }
}

/** Settings → Edit Profile → Personal Channel: one of the user's public channels shown on the profile. */
internal fun LazyListScope.personalChannelPage() {
    item {
        val repo = LocalRepository.current
        val nav = LocalNavigator.current
        val toast = LocalToast.current
        val c = TgTheme.colors
        var candidates by remember { mutableStateOf<List<PersonalChannel>?>(null) }
        var busy by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            repo.loadProfileExtras()
            repo.loadPersonalChannelCandidates { candidates = it }
        }
        val current = repo.myPersonalChannel
        fun set(id: Long?) {
            if (busy) return
            busy = true
            repo.setPersonalChannel(id) { err ->
                busy = false
                if (err != null) toast.error(err) else nav.pop()
            }
        }
        val list = candidates
        Section(footer = "Your personal channel is shown on your profile, with its latest post.") {
            Cell("Don't Show", checked = current == null, chevron = false, divider = !list.isNullOrEmpty(), onClick = { set(null) })
            when {
                list == null -> Box(Modifier.fillMaxWidth().height(50.dp), contentAlignment = Alignment.Center) { ActivityIndicator(18.dp) }
                else -> list.forEachIndexed { i, ch ->
                    Cell(
                        ch.title,
                        leading = { Avatar(ch.title, ch.chatId, 30.dp, photoPeer = ch.chatId) },
                        checked = current?.chatId == ch.chatId,
                        chevron = false,
                        divider = i != list.lastIndex,
                        onClick = { set(ch.chatId) },
                    )
                }
            }
        }
        if (list != null && list.isEmpty()) {
            T(
                "You don't have public channels that can be shown on your profile.",
                TgTheme.type.footnote, c.secondaryText,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 12.dp),
            )
        }
    }
}
