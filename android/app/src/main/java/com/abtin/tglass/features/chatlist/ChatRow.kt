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
import androidx.compose.ui.platform.LocalView
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
import com.abtin.tglass.ui.components.formatListDate
import com.abtin.tglass.ui.components.iosClickable
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

class SwipeAction(val label: String, val icon: ImageVector, val color: Color, val onClick: () -> Unit)

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
    val last = repo.lastMessage(chat.id)
    val bg = if (chat.pinned) (if (c.isDark) Color(0xFF111112) else Color(0xFFF7F7F7)) else c.background
    Box(modifier.fillMaxWidth().background(bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .iosClickable(onLongClick = onLongClick, onClick = onClick)
                .height(78.dp)
                .padding(start = 10.dp, end = 14.dp),
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
                ) { if (selected) Icon(Icons.Rounded.Check, Color.White, 16.dp) }
            }
            ChatAvatar(chat, repo, 62.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = 9.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val typeIcon = chatIcon(chat)
                    if (typeIcon != null && chat.type != ChatType.Bot) {
                        Icon(typeIcon, c.text, 16.dp)
                        Spacer(Modifier.width(3.dp))
                    }
                    T(chat.title, TgTheme.type.headline, c.text, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (chat.verified) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Rounded.Verified, c.accent, 16.dp)
                    }
                    if (chat.muted) {
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Rounded.VolumeOff, c.secondaryText, 15.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    if (last != null && last.outgoing && chat.type != ChatType.Saved) {
                        val (icon, tint) = when (last.status) {
                            MessageStatus.Sending -> Icons.Rounded.Schedule to c.secondaryText
                            MessageStatus.Sent -> Icons.Rounded.Check to c.green
                            MessageStatus.Read -> Icons.Rounded.DoneAll to c.green
                            MessageStatus.Failed -> Icons.Rounded.ErrorOutline to c.destructive
                        }
                        Icon(icon, tint, 17.dp)
                        Spacer(Modifier.width(3.dp))
                    }
                    if (last != null) T(formatListDate(last.date), TgTheme.type.subheadline.copy(fontSize = 14.sp), c.secondaryText, maxLines = 1)
                }
                Spacer(Modifier.height(1.dp))
                Row(Modifier.weight(1f)) {
                    Column(Modifier.weight(1f)) {
                        ChatPreviewText(chat, repo)
                    }
                    Column(Modifier.padding(start = 6.dp, top = 3.dp), horizontalAlignment = Alignment.End) {
                        when {
                            chat.mentions > 0 -> Row {
                                Badge(0, mention = true)
                                Spacer(Modifier.width(4.dp))
                                Badge(chat.unread, muted = chat.muted)
                            }
                            chat.unread > 0 -> Badge(chat.unread, muted = chat.muted)
                            chat.markedUnread -> Box(Modifier.size(20.dp).clip(CircleShape).background(if (chat.muted) c.mutedBadge else c.accent))
                            chat.pinned -> Icon(Icons.Rounded.PushPin, c.tertiaryText.copy(alpha = 1f), 18.dp, Modifier.graphicsLayer { rotationZ = 45f })
                        }
                    }
                }
            }
        }
        Separator(Modifier.align(Alignment.BottomStart), startPadding = if (editing) 116.dp else 82.dp)
    }
}

@Composable
private fun ChatPreviewText(chat: Chat, repo: TelegramRepository) {
    val c = TgTheme.colors
    val last = repo.lastMessage(chat.id)
    val style = TgTheme.type.subheadline
    when {
        chat.typing != null -> T("${chat.typing}…", style, c.accent, maxLines = 2)
        chat.draft != null -> Row {
            T("Draft: ", style, c.destructive, maxLines = 1)
            T(chat.draft ?: "", style, c.secondaryText, maxLines = 2)
        }
        last == null -> T("", style)
        chat.type == ChatType.Group && last.content !is MessageContent.Service -> {
            val sender = if (last.outgoing) "You" else repo.user(last.senderId)?.firstName ?: ""
            T(sender, style, c.text, maxLines = 1)
            T(last.preview, style, c.secondaryText, maxLines = 1)
        }
        else -> T(last.preview, style, c.secondaryText, maxLines = 2)
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
        val o = offset.value
        if (o < 0f) {
            Row(Modifier.matchParentSize(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                val w = with(density) { (-o / trailing.size.coerceAtLeast(1)).toDp() }
                trailing.forEach { a -> ActionButton(a, w) { scope.launch { offset.animateTo(0f) }; a.onClick() } }
            }
        } else if (o > 0f) {
            Row(Modifier.matchParentSize()) {
                val w = with(density) { (o / leading.size.coerceAtLeast(1)).toDp() }
                leading.forEach { a -> ActionButton(a, w) { scope.launch { offset.animateTo(0f) }; a.onClick() } }
            }
        }
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
        Icon(a.icon, Color.White, 26.dp)
        Spacer(Modifier.height(4.dp))
        T(a.label, TgTheme.type.caption1.copy(fontSize = 13.sp), Color.White, maxLines = 1, weight = FontWeight.Medium)
    }
}

/** Standard swipe actions for a chat. */
@Composable
fun chatSwipeActions(chat: Chat, repo: TelegramRepository, onDelete: () -> Unit): Pair<List<SwipeAction>, List<SwipeAction>> {
    val c = TgTheme.colors
    val unread = chat.unread > 0 || chat.markedUnread
    val leading = listOf(
        SwipeAction(if (unread) "Read" else "Unread", if (unread) Icons.Rounded.MarkChatRead else Icons.Rounded.MarkChatUnread, c.accent) { repo.toggleRead(chat.id) },
        SwipeAction(if (chat.pinned) "Unpin" else "Pin", Icons.Rounded.PushPin, c.green) { repo.togglePin(chat.id) },
    )
    val trailing = listOf(
        SwipeAction(if (chat.muted) "Unmute" else "Mute", if (chat.muted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, c.orange) { repo.toggleMute(chat.id) },
        SwipeAction("Delete", Icons.Outlined.Delete, c.destructive, onDelete),
        SwipeAction(if (chat.archived) "Unarchive" else "Archive", if (chat.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, Color(0xFFAAAAAF)) { repo.toggleArchive(chat.id) },
    )
    return leading to trailing
}
