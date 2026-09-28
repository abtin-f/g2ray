package com.abtin.tglass.features.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.Chat
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.features.chat.chatSubtitle
import com.abtin.tglass.features.chatlist.ChatAvatar
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.SearchField
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

/**
 * "Forward to…" picker (Telegram iOS PeerSelectionController as a sheet): searchable chat list, recent chats first,
 * Saved Messages on top, tap to select several (checkmarks), optional comment and a Send button.
 * Kept for existing callers: [onPick] is called for every selected chat (no comment field).
 */
@Composable
fun ChatPickerSheet(title: String = "Forward to…", onDismiss: () -> Unit, onPick: (Chat) -> Unit) {
    ChatPickerSheet(title = title, allowComment = false, onDismiss = onDismiss) { targets, _ -> targets.forEach(onPick) }
}

/** Forwards [messageIds] of [fromChatId] to the chats picked in the sheet (with the optional comment sent first). */
@Composable
fun ForwardSheet(fromChatId: Long, messageIds: List<Long>, onDismiss: () -> Unit, onDone: () -> Unit = {}) {
    val repo = LocalRepository.current
    val toast = LocalToast.current
    ChatPickerSheet(allowComment = true, onDismiss = onDismiss) { targets, comment ->
        targets.forEach { t ->
            if (comment.isNotBlank()) repo.sendText(t.id, comment.trim(), null)
            repo.forward(fromChatId, messageIds, t.id)
        }
        toast.show(
            when {
                targets.size == 1 && targets[0].type == ChatType.Saved -> "Forwarded to Saved Messages"
                targets.size == 1 -> "Forwarded to ${targets[0].title}"
                else -> "Forwarded to ${targets.size} chats"
            }
        )
        onDone()
    }
}

@Composable
fun ChatPickerSheet(
    title: String = "Forward to…",
    allowComment: Boolean,
    onDismiss: () -> Unit,
    onSend: (targets: List<Chat>, comment: String) -> Unit,
) {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    val view = LocalView.current
    var query by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Long>() }
    val q = query.trim()
    val base = repo.chats.filter { it.joined && (it.type != ChatType.Channel || it.canPost) }
    // Saved Messages first, then the chat list order (most recent first).
    val chats = (base.filter { it.type == ChatType.Saved } + base.filter { it.type != ChatType.Saved })
        .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.username?.contains(q.removePrefix("@"), ignoreCase = true) == true }
    val picked = selected.mapNotNull { id -> repo.chat(id) }
    val shown = remember { MutableTransitionState(false) }.apply { targetState = true }
    BackHandler(onBack = onDismiss)

    fun send() {
        if (picked.isEmpty()) return
        Haptics.tap(view)
        onSend(picked, comment)
        onDismiss()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
        )
        AnimatedVisibility(
            shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(0.86f, 380f)) { it } + fadeIn(),
        ) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp))
                    .background(c.groupedBackground)
                    // Swallow taps so they don't reach the dimmed background.
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .imePadding(),
            ) {
                // Grabber + header: close circle, centered title
                Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 5.dp).clip(Capsule()).background(c.secondaryText.copy(alpha = 0.35f)))
                }
                Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp)) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .size(36.dp)
                            .clip(Capsule())
                            .background(if (c.isDark) Color.White.copy(0.12f) else Color.Black.copy(0.06f))
                            .fadeClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) { Icon(IosIcons.Close, c.text, 16.dp) }
                    T(title, TgTheme.type.headline, c.text, weight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                    if (selected.isNotEmpty()) {
                        T(
                            "${selected.size} selected", TgTheme.type.footnote, c.secondaryText,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
                SearchField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp))
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(chats, key = { it.id }) { chat ->
                        val isSel = chat.id in selected
                        Box(Modifier.padding(horizontal = 16.dp).background(c.cell)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .iosClickable {
                                        Haptics.tick(view)
                                        if (isSel) selected.remove(chat.id) else selected.add(chat.id)
                                    }
                                    .height(62.dp)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ChatAvatar(chat, repo, 44.dp, showOnline = false)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    com.abtin.tglass.core.emoji.EmojiText(
                                        if (chat.type == ChatType.Saved) "Saved Messages" else chat.title,
                                        TgTheme.type.body.copy(textDirection = TextDirection.Content), c.text,
                                        weight = FontWeight.Medium, maxLines = 1,
                                    )
                                    val sub = if (chat.type == ChatType.Saved) null else chatSubtitle(chat, repo).first
                                    if (!sub.isNullOrBlank()) T(sub, TgTheme.type.footnote, c.secondaryText, maxLines = 1)
                                }
                                Spacer(Modifier.width(8.dp))
                                SelectCircle(isSel)
                            }
                            Separator(Modifier.align(Alignment.BottomStart), startPadding = 70.dp)
                        }
                    }
                    if (chats.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            T("No chats found", TgTheme.type.subheadline, c.secondaryText)
                        }
                    }
                }
                // Bottom: names of the picked chats, comment field and the Send button.
                AnimatedVisibility(selected.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(c.cell)
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        T(
                            picked.joinToString(", ") { if (it.type == ChatType.Saved) "Saved Messages" else it.title },
                            TgTheme.type.footnote.copy(textDirection = TextDirection.Content), c.secondaryText,
                            maxLines = 1, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (allowComment) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .clip(RoundedRectangle(20.dp))
                                        .background(c.searchField)
                                        .border(0.5.dp, c.separator, RoundedRectangle(20.dp))
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    if (comment.isEmpty()) T("Add a comment…", TgTheme.type.body, c.secondaryText, maxLines = 1)
                                    BasicTextField(
                                        comment, { comment = it },
                                        Modifier.fillMaxWidth(),
                                        maxLines = 4,
                                        textStyle = TgTheme.type.body.copy(color = c.text, textDirection = TextDirection.Content),
                                        cursorBrush = SolidColor(c.accent),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(Capsule())
                                    .background(c.accent)
                                    .bounceClickable { send() },
                                contentAlignment = Alignment.Center,
                            ) { Icon(IosIcons.ArrowUp, Color.White, 22.dp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectCircle(selected: Boolean) {
    val c = TgTheme.colors
    Box(
        Modifier
            .size(24.dp)
            .clip(Capsule())
            .then(if (selected) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.secondaryText.copy(alpha = 0.5f), Capsule())),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(IosIcons.Checkmark, Color.White, 15.dp)
    }
}
