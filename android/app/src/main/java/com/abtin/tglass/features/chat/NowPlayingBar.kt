package com.abtin.tglass.features.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassStyle
import com.abtin.tglass.core.media.VoicePlayer
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.data.senderName
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/** What the now-playing bar shows (a voice / music item of [VoicePlayer], or the round video playing with sound). */
@Immutable
private data class NowPlaying(
    val key: String,
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val subtitle: String?,
    val video: Boolean,
)

private fun nowPlaying(repo: TelegramRepository, includeVideo: Boolean = true): NowPlaying? {
    VoicePlayer.track?.let { t ->
        return NowPlaying(t.key, t.chatId, t.messageId, t.title.ifBlank { "Audio" }, t.subtitle, video = false)
    }
    if (!includeVideo) return null
    val key = VideoNotePlayback.activeKey ?: return null
    val parts = key.split(':')
    val chatId = parts.getOrNull(0)?.toLongOrNull() ?: return null
    val messageId = parts.getOrNull(1)?.toLongOrNull() ?: return null
    val m = repo.findMessage(chatId, messageId)
    val title = when {
        m == null -> "Video Message"
        m.outgoing -> "You"
        else -> repo.senderName(m).ifBlank { repo.chat(chatId)?.title ?: "Video Message" }
    }
    return NowPlaying(key, chatId, messageId, title, "Video Message", video = true)
}

/**
 * Telegram-iOS media player panel under the chat header (same place and style as the pinned-message bar):
 * play/pause, title (sender / song – performer), speed, close, and a progress line along the bottom.
 * It follows the global player, so it stays while the user switches chats. Tapping it opens the message.
 */
@Composable
fun NowPlayingBar(
    repo: TelegramRepository,
    onOpen: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    includeVideo: Boolean = true,
    /** False: a bare 40 pt row for the panel it shares with the pinned-message row (iOS 26 chat header). */
    container: Boolean = true,
) {
    val current = nowPlaying(repo, includeVideo)
    val last = remember { arrayOfNulls<NowPlaying>(1) }
    if (current != null) last[0] = current
    AnimatedVisibility(
        visible = current != null,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        modifier = modifier,
    ) {
        val np = current ?: last[0] ?: return@AnimatedVisibility
        NowPlayingContent(np, onOpen, container)
    }
}

/** Whether [NowPlayingBar] has something to show (for screens that make room for it). */
fun hasNowPlaying(repo: TelegramRepository, includeVideo: Boolean = true): Boolean = nowPlaying(repo, includeVideo) != null

@Composable
private fun NowPlayingContent(np: NowPlaying, onOpen: (Long, Long) -> Unit, container: Boolean) {
    val open = { if (np.chatId != 0L) onOpen(np.chatId, np.messageId) }
    if (container) {
        Column {
            Spacer(Modifier.height(6.dp))
            GlassBox(
                onClick = open,
                shape = RoundedRectangle(ChatPanelRadius),
                style = GlassStyle.Blur,
                modifier = Modifier.fillMaxWidth().height(ChatBarHeight),
                contentAlignment = Alignment.CenterStart,
            ) { NowPlayingRow(np, inset = 18.dp) }
        }
    } else {
        Box(Modifier.fillMaxWidth().height(ChatBarHeight).fadeClickable(onClick = open), contentAlignment = Alignment.CenterStart) {
            NowPlayingRow(np, inset = 0.dp)
        }
    }
}

/** Play/pause, title, speed, close and the progress line ([inset] from the sides) of the player panel. */
@Composable
private fun BoxScope.NowPlayingRow(np: NowPlaying, inset: Dp) {
    val c = TgTheme.colors
    val playing = if (np.video) VideoNotePlayback.playing else VoicePlayer.playing
    val t by animateFloatAsState(if (playing) 1f else 0f, label = "barPlay")
    run {
        run {
            Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                // Play / pause (white glyph; 40 dp hit area)
                Box(
                    Modifier.size(ChatBarHeight).clip(CircleShape).fadeClickable {
                        if (np.video) VideoNotePlayback.togglePause?.invoke() else VoicePlayer.playPause()
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(ChatBarHeight)) { drawPlayPause(t, c.text, scale = 0.8f) }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val style = TgTheme.type.footnote.copy(fontSize = 13.sp, lineHeight = 16.sp, textDirection = TextDirection.Content, textAlign = TextAlign.Center)
                    T(np.title, style, c.text, weight = FontWeight.SemiBold, maxLines = 1)
                    if (np.subtitle != null) T(np.subtitle, style.copy(fontSize = 12.sp, lineHeight = 14.sp), c.secondaryText, maxLines = 1)
                }
                if (!np.video) {
                    val speed = VoicePlayer.speed
                    val label = if (speed == 1.5f) "1.5X" else "${speed.roundToInt()}X"
                    Box(
                        Modifier
                            .height(ChatBarHeight)
                            .clip(Capsule())
                            .fadeClickable { VoicePlayer.cycleSpeed() }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedRectangle(5.dp))
                                .background(if (speed != 1f) c.accent else c.secondaryText.copy(alpha = 0.9f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            T(label, TgTheme.type.caption2.copy(fontSize = 10.sp, lineHeight = 12.sp), TgTheme.colors.background, weight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
                Box(
                    Modifier.size(ChatBarHeight).clip(CircleShape).fadeClickable {
                        if (np.video) VideoNotePlayback.activeKey = null else VoicePlayer.stop()
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(IosIcons.Close, c.text, 14.dp) }
            }
            // Progress line along the bottom edge of the capsule.
            Canvas(Modifier.matchParentSize().padding(horizontal = inset)) {
                val y = size.height - 1.dp.toPx()
                val w = 2.dp.toPx()
                drawLine(c.text.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), strokeWidth = w, cap = StrokeCap.Round)
                // Read while drawing: progress ticks redraw this line only. iOS 26: a white (control color) line.
                val progress = if (np.video) VideoNotePlayback.progress else VoicePlayer.progress
                if (progress > 0f) drawLine(c.text, Offset(0f, y), Offset(size.width * progress.coerceIn(0f, 1f), y), strokeWidth = w, cap = StrokeCap.Round)
            }
        }
    }
}
