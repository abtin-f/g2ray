package com.abtin.tglass.features.chatlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.Chat
import com.abtin.tglass.data.ChatType
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.MessageStatus
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Badge
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.StoryRing
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.LottieIcon
import com.abtin.tglass.ui.components.TgAnimations
import com.abtin.tglass.ui.components.TypingText
import com.abtin.tglass.ui.components.VerifiedBadge
import com.abtin.tglass.ui.components.formatListDate
import com.abtin.tglass.ui.components.iosClickable
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class SwipeAction(val label: String, val icon: Int, val color: Color, val animation: Int? = null, val onClick: () -> Unit)

fun chatIcon(chat: Chat): ImageVector? = when (chat.type) {
    ChatType.Group -> Icons.Rounded.Group
    ChatType.Channel -> Icons.Rounded.Campaign
    ChatType.Bot -> Icons.Rounded.SmartToy
    else -> null
}

@Composable
fun ChatAvatar(chat: Chat, repo: TelegramRepository, size: androidx.compose.ui.unit.Dp, showOnline: Boolean = true, showStory: Boolean = false) {
    val user = chat.peerUserId?.let { repo.user(it) }
    Avatar(
        name = chat.title,
        seed = chat.id,
        size = size,
        saved = chat.type == ChatType.Saved,
        online = showOnline && user?.online == true && chat.type == ChatType.Private,
        storyRing = if (showStory && user?.hasStory == true) (if (user.storySeen) StoryRing.Seen else StoryRing.Unseen) else StoryRing.None,
    )
}

/** Spec §7: the chat list row with all its states. */
@Composable
fun ChatRow(
    chat: Chat,
    repo: TelegramRepository,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val c = TgTheme.colors
    val size = com.abtin.tglass.core.design.LocalAppSettings.current.chatListSize
    val last = repo.lastMessage(chat.id)
    val bg = if (chat.pinned) c.pinnedRow else c.background
    // Formatted once per timestamp, not on every recomposition.
    val time = remember(last?.date) { last?.let { formatListDate(it.date) } }
    val dateStyle = TgTheme.type.subheadline.copy(fontSize = size.previewSp.sp)
    // The row layout never mirrors (avatar left, time right) even when the phone language is RTL;
    // the texts themselves follow their own script (TextDirection.Content).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(modifier.fillMaxWidth().background(bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .iosClickable(onLongClick = onLongClick, onClick = onClick)
                .height(size.row.dp)
                .padding(start = 14.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (editing) {
                Box(
                    Modifier
                        .padding(end = 10.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.background(c.accent) else Modifier.border(1.5.dp, c.tertiaryText, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(IosIcons.Checkmark, Color.White, 15.dp) }
            }
            ChatAvatar(chat, repo, size.avatar.dp, showStory = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = if (size == com.abtin.tglass.core.design.ChatListSize.Compact) 6.dp else 8.dp, bottom = 5.dp)) {
                // Top line (ChatListItem): [name verified mute .......... status-check time]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        com.abtin.tglass.core.emoji.EmojiText(chat.title, TgTheme.type.headline.copy(fontSize = size.titleSp.sp, lineHeight = (size.titleSp + 4f).sp, textDirection = TextDirection.Content), c.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        if (chat.verified) {
                            Spacer(Modifier.width(3.dp))
                            VerifiedBadge(16.dp)
                        }
                        if (chat.muted) {
                            Spacer(Modifier.width(2.dp))
                            Icon(TgIcons.IcMutedPeer, c.secondaryText.copy(alpha = 0.8f), 16.dp)
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    if (last != null && last.outgoing && chat.type != ChatType.Saved) {
                        val (icon, tint) = when (last.status) {
                            MessageStatus.Sending -> IosIcons.Clock to c.secondaryText
                            MessageStatus.Sent -> IosIcons.CheckSingle to c.listCheckmark
                            MessageStatus.Read -> IosIcons.CheckDouble to c.listCheckmark
                            MessageStatus.Failed -> Icons.Rounded.ErrorOutline to c.destructive
                        }
                        Icon(icon, tint, 17.dp)
                        Spacer(Modifier.width(3.dp))
                    }
                    if (time != null) T(time, dateStyle, c.secondaryText, maxLines = 1)
                }
                Spacer(Modifier.height(1.dp))
                // Second line: preview (2 lines) with the badges at the trailing edge, level with its first line.
                Row(Modifier.weight(1f)) {
                    Column(Modifier.weight(1f)) {
                        ChatPreviewText(chat, repo, last)
                    }
                    Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            chat.mentions > 0 -> {
                                Badge(0, mention = true)
                                Spacer(Modifier.width(4.dp))
                                Badge(chat.unread, muted = chat.muted)
                            }
                            chat.unread > 0 -> Badge(chat.unread, muted = chat.muted)
                            chat.markedUnread -> Box(Modifier.size(20.dp).clip(CircleShape).background(if (chat.muted) c.mutedBadge else c.accent))
                            chat.pinned -> Icon(TgIcons.MsgPinned, c.mutedBadge, 16.dp)
                        }
                    }
                }
            }
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = (14 + size.avatar + 10 + if (editing) 34 else 0).dp)
    }
    }
}

@Composable
private fun ChatPreviewText(chat: Chat, repo: TelegramRepository, last: com.abtin.tglass.data.Message?) {
    val c = TgTheme.colors
    // Tight lines so the title + two preview lines fit the row (Telegram-iOS ChatListItem).
    val size = com.abtin.tglass.core.design.LocalAppSettings.current.chatListSize
    val style = TgTheme.type.subheadline.copy(fontSize = size.previewSp.sp, lineHeight = (size.previewSp * 1.2f).sp, textDirection = TextDirection.Content)
    when {
        chat.typing != null -> TypingText(chat.typing, style, c.accent, Modifier.padding(top = 2.dp))
        chat.draft != null -> Row {
            T("Draft: ", style, c.destructive, maxLines = 1)
            com.abtin.tglass.core.emoji.EmojiText(chat.draft ?: "", style, c.secondaryText, maxLines = 2)
        }
        last == null -> T("", style)
        chat.type == ChatType.Group && last.content !is MessageContent.Service -> {
            val sender = if (last.outgoing) "You" else repo.user(last.senderId)?.firstName ?: ""
            T(sender, style, c.text, maxLines = 1)
            PreviewLine(last, style, 1)
        }
        else -> PreviewLine(last, style, 2)
    }
}

/** Last-message preview with Telegram's inline media thumbnail / voice glyph. */
@Composable
private fun PreviewLine(m: com.abtin.tglass.data.Message, style: androidx.compose.ui.text.TextStyle, lines: Int) {
    val c = TgTheme.colors
    when (val content = m.content) {
        is MessageContent.Photo -> Row(verticalAlignment = Alignment.CenterVertically) {
            val (a, b) = com.abtin.tglass.ui.components.avatarColors(content.seed.toLong())
            Box(
                Modifier
                    .size(20.dp)
                    .clip(com.kyant.shapes.RoundedRectangle(4.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(a, b))),
                contentAlignment = Alignment.Center,
            ) {
                if (content.image != null) com.abtin.tglass.ui.components.TgImage(content.image, Modifier.matchParentSize(), maxPx = 96)
                else T(content.emoji, style.copy(fontSize = 11.sp, lineHeight = 12.sp))
            }
            Spacer(Modifier.width(5.dp))
            com.abtin.tglass.core.emoji.EmojiText(
                content.caption ?: if (content.video) "Video" else "Photo", style, c.secondaryText, maxLines = 1,
                custom = if (content.caption != null) com.abtin.tglass.core.emoji.customEmojiSpans(content.captionEntities) else emptyList(),
            )
        }
        is MessageContent.Voice -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(TgIcons.IcVoiceSmall, c.secondaryText, 18.dp)
            Spacer(Modifier.width(3.dp))
            T("Voice message", style, c.secondaryText, maxLines = 1)
        }
        is MessageContent.VideoNote -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (content.thumb != null) {
                Box(Modifier.size(20.dp).clip(androidx.compose.foundation.shape.CircleShape)) {
                    com.abtin.tglass.ui.components.TgImage(content.thumb, Modifier.matchParentSize(), maxPx = 96)
                }
                Spacer(Modifier.width(5.dp))
            }
            T("Video message", style, c.secondaryText, maxLines = 1)
        }
        else -> com.abtin.tglass.core.emoji.EmojiText(m.preview, style, c.secondaryText, maxLines = lines, custom = com.abtin.tglass.core.emoji.customEmojiSpans(m))
    }
}

/**
 * Row with iOS swipe actions (spec §7): swipe left reveals [trailing], swipe right reveals [leading].
 * A full swipe triggers the outermost action.
 */
@Composable
fun SwipeableRow(
    leading: List<SwipeAction>,
    trailing: List<SwipeAction>,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidth = with(density) { 74.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val width = remember { floatArrayOf(1f) }

    Box(Modifier.fillMaxWidth().onSizeChanged { width[0] = it.width.toFloat() }) {
        // Only the sign is read here; the per-frame width of the revealed buttons is read inside SwipeActionButtons,
        // so dragging a row doesn't recompose the row content itself.
        val side by remember { androidx.compose.runtime.derivedStateOf { if (offset.value < 0f) -1 else if (offset.value > 0f) 1 else 0 } }
        if (side < 0) SwipeActionButtons(offset, trailing, atEnd = true)
        else if (side > 0) SwipeActionButtons(offset, leading, atEnd = false)
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offset.value.roundToInt(), 0) }
                .then(
                    if (enabled) Modifier.pointerInput(leading.size, trailing.size) {
                        var crossed = false
                        detectHorizontalDragGestures(
                            onDragStart = { crossed = false },
                            onDragEnd = {
                                val v = offset.value
                                val full = abs(v) > width[0] * 0.6f
                                scope.launch {
                                    when {
                                        full && v < 0 && trailing.isNotEmpty() -> { offset.animateTo(0f); trailing.last().onClick() }
                                        full && v > 0 && leading.isNotEmpty() -> { offset.animateTo(0f); leading.first().onClick() }
                                        v < -actionWidth * 0.5f -> offset.animateTo(-actionWidth * trailing.size, spring(0.8f, 500f))
                                        v > actionWidth * 0.5f -> offset.animateTo(actionWidth * leading.size, spring(0.8f, 500f))
                                        else -> offset.animateTo(0f, spring(0.8f, 500f))
                                    }
                                }
                            },
                            onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        ) { change, drag ->
                            change.consume()
                            val min = if (trailing.isEmpty()) 0f else -width[0]
                            val max = if (leading.isEmpty()) 0f else width[0]
                            val next = (offset.value + drag).coerceIn(min, max)
                            val isFull = abs(next) > width[0] * 0.6f
                            if (isFull != crossed) { crossed = isFull; Haptics.tick(view) }
                            scope.launch { offset.snapTo(next) }
                        }
                    } else Modifier
                )
        ) { content() }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.SwipeActionButtons(
    offset: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    actions: List<SwipeAction>,
    atEnd: Boolean,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val w = with(density) { (abs(offset.value) / actions.size.coerceAtLeast(1)).toDp() }
    Row(
        Modifier.matchParentSize(),
        horizontalArrangement = if (atEnd) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start,
    ) {
        actions.forEach { a -> ActionButton(a, w) { scope.launch { offset.animateTo(0f) }; a.onClick() } }
    }
}

@Composable
private fun ActionButton(a: SwipeAction, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(a.color)
            .iosClickable(highlight = Color.Black.copy(0.15f), onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        val revealed = width > 56.dp
        if (a.animation != null) LottieIcon(a.animation, Color.White, 32.dp, playKey = if (revealed) a.label else null, play = revealed)
        else Icon(a.icon, Color.White, 28.dp)
        Spacer(Modifier.height(4.dp))
        T(a.label, TgTheme.type.caption1.copy(fontSize = 13.sp), Color.White, maxLines = 1, weight = FontWeight.Medium)
    }
}

/** Standard swipe actions for a chat. Remembered: the action objects are rebuilt only when what they show changes. */
@Composable
fun chatSwipeActions(chat: Chat, repo: TelegramRepository, onDelete: () -> Unit, folderIndex: Int = 0): Pair<List<SwipeAction>, List<SwipeAction>> {
    val c = TgTheme.colors
    val sheet = com.abtin.tglass.ui.components.LocalActionSheet.current
    val unread = chat.unread > 0 || chat.markedUnread
    val onDeleteNow = androidx.compose.runtime.rememberUpdatedState(onDelete)
    val chatId = chat.id
    val pinned = chat.pinned
    val muted = chat.muted
    val archived = chat.archived
    return remember(chatId, unread, pinned, muted, archived, folderIndex, c, repo, sheet) {
        val leading = listOf(
            SwipeAction(if (unread) "Read" else "Unread", TgIcons.CtxRead, if (unread) Color(0xFFAAAAAF) else c.accent, if (unread) TgAnimations.Read else TgAnimations.Unread) { repo.toggleRead(chatId) },
            SwipeAction(if (pinned) "Unpin" else "Pin", if (pinned) TgIcons.CtxUnpin else TgIcons.CtxPin, c.green, if (pinned) TgAnimations.Unpin else TgAnimations.Pin) { repo.togglePinInFolder(chatId, folderIndex) },
        )
        val trailing = listOf(
            SwipeAction(if (muted) "Unmute" else "Mute", if (muted) TgIcons.CtxUnmute else TgIcons.CtxMuted, c.orange, if (muted) TgAnimations.Unmute else TgAnimations.Mute) { com.abtin.tglass.features.groups.toggleMuteWithOptions(sheet, repo, chatId) },
            SwipeAction("Delete", TgIcons.CtxDelete, c.destructive, TgAnimations.Delete) { onDeleteNow.value() },
            SwipeAction(if (archived) "Unarchive" else "Archive", TgIcons.CtxArchive, Color(0xFFAAAAAF), if (archived) TgAnimations.Unarchive else TgAnimations.Archive) { repo.toggleArchive(chatId) },
        )
        leading to trailing
    }
}
