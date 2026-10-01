package com.abtin.tglass.features.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.User
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.EmptyState
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.IOSSwitch
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
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private const val MaxPollOptions = 10

/** A page that slides up over the chat like an iOS page sheet; blocks touches to the chat below. */
@Composable
private fun ChatModalPage(
    visible: Boolean,
    onDismiss: () -> Unit,
    background: Color,
    content: @Composable (LayerBackdrop) -> Unit,
) {
    BackHandler(enabled = visible) { onDismiss() }
    AnimatedVisibility(visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        val backdrop = rememberLayerBackdrop()
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(background)
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                content(backdrop)
            }
        }
    }
}

private class PollOptionField(val id: Int, text: String) {
    var text by mutableStateOf(text)
}

/**
 * Telegram iOS "New Poll": question, 2–10 options, Anonymous Voting / Multiple Answers / Quiz Mode;
 * a quiz needs one correct answer and may carry an explanation. [onSend] gets a ready poll (trimmed, no empty options).
 */
@Composable
fun NewPollSheet(visible: Boolean, onDismiss: () -> Unit, onSend: (MessageContent.Poll) -> Unit) {
    val c = TgTheme.colors
    val toast = LocalToast.current
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf(PollOptionField(0, ""), PollOptionField(1, "")) }
    var nextId by remember { mutableIntStateOf(2) }
    var anonymous by remember { mutableStateOf(true) }
    var multiple by remember { mutableStateOf(false) }
    var quiz by remember { mutableStateOf(false) }
    var correctId by remember { mutableStateOf<Int?>(null) }
    var explanation by remember { mutableStateOf("") }

    fun reset() {
        question = ""
        options.clear()
        options.add(PollOptionField(0, ""))
        options.add(PollOptionField(1, ""))
        nextId = 2
        anonymous = true
        multiple = false
        quiz = false
        correctId = null
        explanation = ""
    }

    val filled = options.filter { it.text.isNotBlank() }
    val valid = question.isNotBlank() && filled.size >= 2 && (!quiz || filled.any { it.id == correctId })

    fun send() {
        when {
            question.isBlank() -> toast.show("Please enter a question")
            filled.size < 2 -> toast.show("Please enter at least two options")
            quiz && filled.none { it.id == correctId } -> toast.show("Please choose the correct answer")
            else -> {
                val poll = MessageContent.Poll(
                    question = question.trim(),
                    options = filled.map { it.text.trim() },
                    votes = List(filled.size) { 0 },
                    quiz = quiz,
                    anonymous = anonymous,
                    multiple = multiple && !quiz,
                    correctOption = if (quiz) filled.indexOfFirst { it.id == correctId } else null,
                    explanation = if (quiz) explanation.trim().ifBlank { null } else null,
                )
                reset()
                onSend(poll)
            }
        }
    }

    fun cancel() {
        reset()
        onDismiss()
    }

    ChatModalPage(visible, { cancel() }, c.groupedBackground) { backdrop ->
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop).imePadding(),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 40.dp),
            ) {
                item {
                    Section(header = "Question") {
                        TextFieldRow(question, { question = it.take(255) }, "Ask a question", divider = false, singleLine = false)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
                item {
                    val left = MaxPollOptions - options.size
                    val footer = buildString {
                        if (quiz) append("Tap the circle to mark the correct answer. ")
                        append(
                            when (left) {
                                0 -> "You have added the maximum number of options."
                                1 -> "You can add 1 more option."
                                else -> "You can add $left more options."
                            },
                        )
                    }
                    Section(header = if (quiz) "Quiz Answers" else "Poll Options", footer = footer) {
                        options.forEachIndexed { i, o ->
                            key(o.id) {
                                PollOptionRow(
                                    value = o.text,
                                    onValue = { o.text = it.replace("\n", " ").take(100) },
                                    quiz = quiz,
                                    correct = o.id == correctId,
                                    onMarkCorrect = { correctId = o.id },
                                    removable = options.size > 2,
                                    onRemove = {
                                        options.remove(o)
                                        if (correctId == o.id) correctId = null
                                    },
                                    divider = i != options.lastIndex || left > 0,
                                )
                            }
                        }
                        if (left > 0) {
                            Cell("Add an Option", titleColor = c.accent, chevron = false, divider = false, onClick = {
                                options.add(PollOptionField(nextId, ""))
                                nextId += 1
                            })
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
                item {
                    Section(footer = if (quiz) "Polls in Quiz Mode have one correct answer. Users can't revoke their answers." else null) {
                        Cell("Anonymous Voting", chevron = false, trailing = { IOSSwitch(anonymous, { anonymous = it }) })
                        Cell("Multiple Answers", chevron = false, trailing = {
                            IOSSwitch(multiple, { on -> multiple = on; if (on) quiz = false })
                        })
                        Cell("Quiz Mode", chevron = false, divider = false, trailing = {
                            IOSSwitch(quiz, { on -> quiz = on; if (on) multiple = false })
                        })
                    }
                }
                if (quiz) {
                    item { Spacer(Modifier.height(24.dp)) }
                    item {
                        Section(header = "Explanation", footer = "Users will see this text after choosing a wrong answer, good for educational purposes.") {
                            TextFieldRow(
                                explanation,
                                { v -> explanation = v.take(200).let { t -> if (t.count { ch -> ch == '\n' } > 2) explanation else t } },
                                "Add a Comment (Optional)",
                                divider = false,
                                singleLine = false,
                            )
                        }
                    }
                }
            }
            GlassTopBar(
                title = if (quiz) "New Quiz" else "New Poll",
                fade = c.groupedBackground,
                left = { GlassTextButton("Cancel", { cancel() }) },
                right = { GlassTextButton("Send", { send() }, color = if (valid) c.accent else c.secondaryText, bold = true) },
            )
        }
    }
}

@Composable
private fun TextFieldRow(value: String, onValue: (String) -> Unit, placeholder: String, divider: Boolean, singleLine: Boolean = true) {
    val c = TgTheme.colors
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) T(placeholder, TgTheme.type.body, c.tertiaryText)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                modifier = Modifier.fillMaxWidth(),
                singleLine = singleLine,
                textStyle = TgTheme.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
            )
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = 16.dp)
    }
}

@Composable
private fun PollOptionRow(
    value: String,
    onValue: (String) -> Unit,
    quiz: Boolean,
    correct: Boolean,
    onMarkCorrect: () -> Unit,
    removable: Boolean,
    onRemove: () -> Unit,
    divider: Boolean,
) {
    val c = TgTheme.colors
    Box(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (quiz) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(if (correct) Modifier.background(c.green) else Modifier.border(1.5.dp, c.tertiaryText, CircleShape))
                        .fadeClickable(onClick = onMarkCorrect),
                    contentAlignment = Alignment.Center,
                ) {
                    if (correct) Icon(IosIcons.Checkmark, Color.White, 16.dp)
                }
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f).padding(vertical = 13.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) T("Option", TgTheme.type.body, c.tertiaryText)
                BasicTextField(
                    value = value,
                    onValueChange = onValue,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TgTheme.type.body.copy(color = c.text),
                    cursorBrush = SolidColor(c.accent),
                )
            }
            if (removable) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.RemoveCircle, c.destructive, 22.dp, Modifier.fadeClickable(onClick = onRemove))
            }
        }
        if (divider) Separator(Modifier.align(Alignment.BottomStart), startPadding = if (quiz) 52.dp else 16.dp)
    }
}

/** Attach → Contact: pick one of the account's contacts to share in the chat. */
@Composable
fun ContactPickerSheet(visible: Boolean, onDismiss: () -> Unit, onPick: (User) -> Unit) {
    val repo = LocalRepository.current
    val sheet = LocalActionSheet.current
    val c = TgTheme.colors
    var query by remember { mutableStateOf("") }
    ChatModalPage(visible, { query = ""; onDismiss() }, c.background) { backdrop ->
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val q = query.trim()
        val digits = q.filter { it.isDigit() }
        val contacts = repo.contacts
            .filter { u ->
                q.isEmpty() || u.name.contains(q, true) || u.username?.contains(q.removePrefix("@"), true) == true ||
                    (digits.isNotEmpty() && u.phone.filter { it.isDigit() }.contains(digits))
            }
            .sortedBy { it.name.lowercase() }
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop).imePadding(),
                contentPadding = PaddingValues(top = top + 62.dp, bottom = bottom + 30.dp),
            ) {
                item { SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) }
                if (contacts.isEmpty()) {
                    item {
                        EmptyState(
                            "👤",
                            if (q.isEmpty()) "No Contacts" else "No Results",
                            if (q.isEmpty()) "Your Telegram contacts will appear here." else "There were no results for \"$q\".",
                        )
                    }
                }
                items(contacts, key = { it.id }) { u ->
                    SharedContactRow(u) {
                        sheet.show(
                            SheetRequest(
                                title = u.name,
                                message = u.phone.ifBlank { null },
                                actions = listOf(SheetAction("Send Contact", bold = true) { query = ""; onPick(u) }),
                            ),
                        )
                    }
                }
            }
            GlassTopBar(
                title = "Share Contact",
                left = { GlassTextButton("Cancel", { query = ""; onDismiss() }) },
            )
        }
    }
}

@Composable
private fun SharedContactRow(u: User, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box {
        Row(
            Modifier.fillMaxWidth().iosClickable(onClick = onClick).height(58.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(u.name, u.id, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                com.abtin.tglass.core.emoji.EmojiText(u.name, TgTheme.type.headline, c.text, maxLines = 1)
                T(u.phone.ifBlank { u.status }, TgTheme.type.subheadline, c.secondaryText, maxLines = 1)
            }
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = 70.dp)
    }
}
