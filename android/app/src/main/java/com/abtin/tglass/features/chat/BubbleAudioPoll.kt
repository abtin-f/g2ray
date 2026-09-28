package com.abtin.tglass.features.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.media.VoicePlayer
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.data.senderName
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatCount
import com.abtin.tglass.ui.components.formatDuration
import kotlin.math.roundToInt

// =====================================================================================
// Voice message (Telegram-iOS ChatMessageInteractiveFileNode, voice layout)
// =====================================================================================

/**
 * Telegram's play ⇄ pause glyph: the triangle splits into two halves that morph into the pause bars ([t] 0 = play,
 * 1 = pause). The triangle is shifted right so it looks centered in the circle (its visual center is its centroid).
 */
internal fun DrawScope.drawPlayPause(t: Float, color: Color, scale: Float = 1f) {
    val u = 1.dp.toPx() * scale
    val cx = size.width / 2f
    val cy = size.height / 2f
    // Play triangle (bounding box 15 × 17, optical offset +1.8)
    val ph = 17f * u
    val pw = 15f * u
    val ox = 1.8f * u
    val l = cx - pw / 2f + ox
    val r = cx + pw / 2f + ox
    val m = (l + r) / 2f
    // Pause bars (4.6 × 16, gap 4.8)
    val bh = 16f * u
    val bw = 4.6f * u
    val g = 4.8f * u
    fun p(ax: Float, ay: Float, bx: Float, by: Float) = Offset(ax + (bx - ax) * t, ay + (by - ay) * t)
    val left = listOf(
        p(l, cy - ph / 2f, cx - g / 2f - bw, cy - bh / 2f),
        p(m, cy - ph / 4f, cx - g / 2f, cy - bh / 2f),
        p(m, cy + ph / 4f, cx - g / 2f, cy + bh / 2f),
        p(l, cy + ph / 2f, cx - g / 2f - bw, cy + bh / 2f),
    )
    val right = listOf(
        p(m, cy - ph / 4f, cx + g / 2f, cy - bh / 2f),
        p(r, cy, cx + g / 2f + bw, cy - bh / 2f),
        p(r, cy, cx + g / 2f + bw, cy + bh / 2f),
        p(m, cy + ph / 4f, cx + g / 2f, cy + bh / 2f),
    )
    val round = Stroke(width = 1.6f * u, join = StrokeJoin.Round)
    for (quad in listOf(left, right)) {
        val path = Path().apply {
            moveTo(quad[0].x, quad[0].y)
            for (i in 1 until quad.size) lineTo(quad[i].x, quad[i].y)
            close()
        }
        drawPath(path, color)
        drawPath(path, color, style = round)
    }
}

/** Round accent play/pause button (44pt) with the morphing glyph, or the download ring while the file loads. */
@Composable
internal fun AudioPlayButton(
    playing: Boolean,
    loading: Boolean,
    loadProgress: Float,
    colors: BubbleColors,
    diameter: Dp = 44.dp,
    onClick: () -> Unit,
) {
    val t by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 520f), label = "playPause")
    Box(
        Modifier.size(diameter).clip(CircleShape).background(colors.control).bounceClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Play / pause glyph, replaced by Telegram's rotating download ring (X cancels) while the file loads.
        val glyphAlpha by animateFloatAsState(if (loading) 0f else 1f, label = "playGlyph")
        val glyphScale = diameter.value / 44f
        Canvas(Modifier.size(diameter).graphicsLayer { alpha = glyphAlpha }) { drawPlayPause(t, colors.controlGlyph, scale = glyphScale) }
        DownloadRing(
            if (loading) DownloadPhase.Loading else DownloadPhase.Done, loadProgress, colors.controlGlyph,
            Modifier.matchParentSize(), glyphSize = 16.dp, inset = 3.5.dp,
        )
    }
}

/** The chat's player entry for a voice or music message (for the now-playing bar and auto-advance). */
internal fun audioTrackOf(repo: com.abtin.tglass.data.TelegramRepository, m: Message): com.abtin.tglass.core.media.VoiceTrack? {
    val key = "${m.chatId}:${m.id}"
    return when (val c = m.content) {
        is MessageContent.Voice -> com.abtin.tglass.core.media.VoiceTrack(
            key, m.chatId, m.id,
            title = if (m.outgoing) "You" else repo.senderName(m).ifBlank { repo.chat(m.chatId)?.title ?: "Voice Message" },
            subtitle = "Voice Message", music = false, seconds = c.seconds,
        )
        is MessageContent.File -> if (!c.music) null else com.abtin.tglass.core.media.VoiceTrack(
            key, m.chatId, m.id,
            title = c.name, subtitle = c.performer ?: "Unknown Artist", music = true, seconds = c.duration,
        )
        else -> null
    }
}

/**
 * Queue of the chat's voice / music messages: when one ends the next newer one plays (downloaded on the way),
 * like Telegram. Starting a voice message marks it as listened.
 */
internal class ChatAudioQueue(private val repo: com.abtin.tglass.data.TelegramRepository) : com.abtin.tglass.core.media.VoiceQueue {
    private fun media(t: com.abtin.tglass.core.media.VoiceTrack): com.abtin.tglass.data.ImageRef? =
        when (val c = repo.findMessage(t.chatId, t.messageId)?.content) {
            is MessageContent.Voice -> c.media
            is MessageContent.File -> c.file
            else -> null
        }

    override fun next(after: com.abtin.tglass.core.media.VoiceTrack): com.abtin.tglass.core.media.VoiceTrack? {
        if (after.chatId == 0L) return null
        val m = repo.nextAudioMessage(after.chatId, after.messageId, after.music) ?: return null
        return audioTrackOf(repo, m)
    }

    override fun hasFile(track: com.abtin.tglass.core.media.VoiceTrack): Boolean = media(track) != null
    override fun path(track: com.abtin.tglass.core.media.VoiceTrack): String? = media(track)?.let { repo.filePath(it) }
    override fun download(track: com.abtin.tglass.core.media.VoiceTrack) {
        media(track)?.let { repo.requestImage(it) }
    }

    override fun onStarted(track: com.abtin.tglass.core.media.VoiceTrack) {
        val m = repo.findMessage(track.chatId, track.messageId) ?: return
        val c = m.content
        if (c is MessageContent.Voice && !c.listened && !m.outgoing) repo.openMessageContent(m.chatId, m.id)
    }
}

/** Plays (or pauses) a voice / music message through the global player with the chat's queue. */
internal fun playAudioMessage(
    context: android.content.Context,
    repo: com.abtin.tglass.data.TelegramRepository,
    m: Message,
    path: String?,
    seconds: Int,
) {
    VoicePlayer.toggle(context, "${m.chatId}:${m.id}", path, seconds, audioTrackOf(repo, m), ChatAudioQueue(repo))
}

@Composable
internal fun VoiceMessageBody(m: Message, v: MessageContent.Voice, colors: BubbleColors, showMeta: Boolean) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val player = VoicePlayer
    val key = "${m.chatId}:${m.id}"
    val current = player.currentKey == key
    val playing = current && player.playing
    val progress = if (current) player.progress else 0f
    val media = v.media
    val path = media?.let { repo.filePath(it) }
    // Tapped before the file was downloaded: start playing as soon as it arrives.
    var pending by remember(key) { mutableStateOf(false) }
    LaunchedEffect(pending, path) {
        if (pending && path != null) {
            pending = false
            playAudioMessage(context, repo, m, path, v.seconds)
        }
    }
    val loading = pending && path == null || (current && !playing && media != null && path == null)
    // Telegram: the waveform grows with the duration (min 120, max ~190 pt).
    val waveWidth = (96 + v.seconds * 3).coerceIn(120, 190).dp
    Row(
        Modifier.padding(start = 8.dp, end = 11.dp, top = 8.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AudioPlayButton(
            playing = playing,
            loading = loading,
            loadProgress = media?.let { repo.fileProgress(it) } ?: 0f,
            colors = colors,
        ) {
            when {
                media == null -> playAudioMessage(context, repo, m, null, v.seconds)
                path != null -> playAudioMessage(context, repo, m, path, v.seconds)
                else -> { pending = !pending; if (pending) repo.requestImage(media) else repo.cancelDownload(media) }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(IntrinsicSize.Max)) {
            Canvas(
                Modifier
                    .width(waveWidth)
                    .height(20.dp)
                    .then(
                        if (current) Modifier.pointerInput(key) {
                            detectTapGestures { o -> VoicePlayer.seekTo(o.x / size.width) }
                        } else Modifier
                    )
            ) {
                val barW = 2.dp.toPx()
                val step = 3.dp.toPx()
                val minH = 2.dp.toPx()
                val bars = ((size.width + (step - barW)) / step).toInt().coerceAtLeast(1)
                val wave = v.waveform
                val played = progress * bars
                for (i in 0 until bars) {
                    val amp = if (wave.isEmpty()) 0.25f else wave[(i * wave.size / bars).coerceIn(0, wave.lastIndex)]
                    val bh = (amp.coerceIn(0f, 1f) * size.height).coerceAtLeast(minH)
                    val f = (played - i).coerceIn(0f, 1f)
                    val color = if (f >= 1f) colors.control else if (f <= 0f) colors.inactive else lerp(colors.inactive, colors.control, f)
                    drawRoundRect(
                        color,
                        topLeft = Offset((i * step).roundToInt().toFloat(), size.height - bh),
                        size = Size(barW, bh),
                        cornerRadius = CornerRadius(barW / 2f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val secs = if (current && player.positionMs > 0) (player.positionMs / 1000).toInt() else v.seconds
                T(formatDuration(secs), bubbleText().meta, colors.meta, maxLines = 1)
                if (!v.listened && !current) {
                    // Not listened yet: Telegram's small dot next to the duration.
                    Spacer(Modifier.width(4.dp))
                    Box(Modifier.size(5.dp).clip(CircleShape).background(colors.control))
                }
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    SpeedPill(player.speed, colors) { player.cycleSpeed() }
                }
                Spacer(Modifier.weight(1f).widthIn(min = 10.dp))
                if (showMeta) MetaRow(m, colors.meta)
            }
        }
    }
}

/** iOS 1x / 1.5x / 2x speed capsule. */
@Composable
internal fun SpeedPill(speed: Float, colors: BubbleColors, onClick: () -> Unit) {
    val label = if (speed == 1.5f) "1.5X" else "${speed.roundToInt()}X"
    Box(
        Modifier
            .height(15.dp)
            .clip(CircleShape)
            .background(colors.control.copy(alpha = 0.16f))
            .fadeClickable(onClick = onClick)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(label, TgTheme.type.caption2.copy(fontSize = 10.sp, lineHeight = 12.sp), colors.control, weight = FontWeight.Bold, maxLines = 1)
    }
}

// =====================================================================================
// Poll (Telegram-iOS ChatMessagePollBubbleContentNode)
// =====================================================================================

/** Poll callbacks of one bubble: vote with the chosen options, open the voters list. */
class PollActions(val vote: (List<Int>) -> Unit, val viewResults: () -> Unit)

@Composable
internal fun PollMessageBody(m: Message, p: MessageContent.Poll, colors: BubbleColors, actions: PollActions, width: Dp, showMeta: Boolean) {
    val repo = LocalRepository.current
    val c = TgTheme.colors
    val chosen = p.chosen.ifEmpty { listOfNotNull(p.voted) }
    val voted = chosen.isNotEmpty()
    // A single-answer tap shows its radio filled until the server confirms the vote.
    var pendingOption by remember(m.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingOption, voted) {
        if (pendingOption != null) {
            if (voted) pendingOption = null
            else { kotlinx.coroutines.delay(8000); pendingOption = null }
        }
    }
    val selection = remember(m.id) { mutableStateListOf<Int>() }
    LaunchedEffect(voted) { if (voted) selection.clear() }
    val canVote = !voted && !p.closed
    val showResults = (voted || p.closed) && p.canSeeResults
    val reveal by animateFloatAsState(if (showResults) 1f else 0f, tween(420), label = "pollReveal")
    val maxVotes = p.votes.maxOrNull()?.coerceAtLeast(1) ?: 1
    val green = c.green
    val red = c.destructive
    val correct = p.correctOption?.takeIf { p.quiz && showResults }

    // Quiz explanation: shown when the user just answered wrong, or from the lightbulb.
    var explanationShown by remember(m.id) { mutableStateOf(false) }
    val wasVoted = remember(m.id) { booleanArrayOf(voted) }
    LaunchedEffect(voted, correct) {
        if (voted && !wasVoted[0] && p.quiz && p.explanation != null && correct != null && correct !in chosen) explanationShown = true
        wasVoted[0] = voted
    }

    val textStyle = TgTheme.type.body.copy(fontSize = 16.sp, lineHeight = 21.sp, textDirection = TextDirection.Content)
    Column(Modifier.width(width).padding(start = 11.dp, end = 11.dp, top = 8.dp, bottom = 7.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(end = if (showResults && p.explanation != null) 26.dp else 0.dp)) {
                T(p.question, textStyle.copy(fontSize = 16.sp), colors.text, weight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(
                        when {
                            p.closed -> "Final Results"
                            p.quiz -> if (p.anonymous) "Anonymous Quiz" else "Quiz"
                            else -> if (p.anonymous) "Anonymous Poll" else "Public Poll"
                        },
                        TgTheme.type.footnote.copy(fontSize = 14.sp), colors.meta, maxLines = 1,
                    )
                    if (!p.anonymous && p.recentVoters.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.height(18.dp).width((16 + 10 * (p.recentVoters.take(3).size - 1)).dp)) {
                            p.recentVoters.take(3).forEachIndexed { i, id ->
                                val name = repo.user(id)?.name ?: repo.chat(id)?.title ?: ""
                                Box(
                                    Modifier
                                        .offset(x = (10 * i).dp, y = 1.dp)
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(colors.fill)
                                        .padding(1.dp),
                                ) { Avatar(name, id, 14.dp) }
                            }
                        }
                    }
                }
            }
            if (showResults && p.explanation != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).fadeClickable { explanationShown = !explanationShown },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Lightbulb, colors.accent, 20.dp) }
            }
            if (explanationShown && p.explanation != null) {
                QuizExplanationPopup(p.explanation) { explanationShown = false }
            }
        }
        Spacer(Modifier.height(5.dp))
        p.options.forEachIndexed { i, opt ->
            val count = p.votes.getOrElse(i) { 0 }
            val pct = p.percentOf(i)
            val isChosen = i in chosen
            val isCorrect = correct == i
            val wrongChoice = correct != null && isChosen && !isCorrect
            val barColor = when {
                correct != null && isCorrect -> green
                wrongChoice -> red
                else -> colors.pollBar
            }
            val frac by animateFloatAsState(
                if (showResults) (count.toFloat() / maxVotes).coerceIn(0f, 1f) else 0f,
                spring(dampingRatio = 0.9f, stiffness = 260f), label = "pollBar",
            )
            val selectedRadio = pendingOption == i || i in selection
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .then(
                        if (canVote && pendingOption == null) Modifier.fadeClickable {
                            if (p.multiple) {
                                if (i in selection) selection.remove(i) else selection.add(i)
                            } else {
                                pendingOption = i
                                actions.vote(listOf(i))
                            }
                        } else Modifier
                    ),
                verticalAlignment = Alignment.Top,
            ) {
                // Left column: radio / check before voting, percentage + result mark after.
                Box(Modifier.width(34.dp).fillMaxHeight()) {
                    if (reveal < 1f) {
                        Box(
                            Modifier
                                .padding(top = 8.dp)
                                .size(22.dp)
                                .graphicsLayer { alpha = 1f - reveal; scaleX = 1f - 0.4f * reveal; scaleY = 1f - 0.4f * reveal }
                                .clip(CircleShape)
                                .then(
                                    if (selectedRadio) Modifier.background(colors.pollBar)
                                    else Modifier.border(1.3.dp, colors.radio, CircleShape)
                                ),
                            contentAlignment = Alignment.Center,
                        ) { if (selectedRadio) Icon(IosIcons.Checkmark, colors.fill, 14.dp) }
                    }
                    if (reveal > 0f) {
                        T(
                            "${(pct * reveal).roundToInt()}%",
                            TgTheme.type.footnote.copy(fontSize = 14.sp, lineHeight = 18.sp),
                            colors.text, weight = FontWeight.Bold, maxLines = 1, align = TextAlign.End,
                            modifier = Modifier.padding(top = 10.dp, end = 6.dp).fillMaxWidth().graphicsLayer { alpha = reveal },
                        )
                        val mark = when {
                            correct != null && isCorrect && (isChosen || p.closed || voted) -> 1 // green check
                            wrongChoice -> 2 // red cross
                            isChosen -> 3 // accent check
                            else -> 0
                        }
                        if (mark != 0) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 6.dp, bottom = 0.dp)
                                    .size(14.dp)
                                    .graphicsLayer { alpha = reveal; scaleX = reveal; scaleY = reveal }
                                    .clip(CircleShape)
                                    .background(if (mark == 1) green else if (mark == 2) red else colors.pollBar),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(if (mark == 2) IosIcons.Close else IosIcons.Checkmark, colors.fill, if (mark == 2) 9.dp else 10.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Spacer(Modifier.height(9.dp))
                    T(opt, textStyle, colors.text)
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.CenterStart) {
                        if (reveal > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth((frac).coerceAtLeast(0.02f))
                                    .height(4.dp)
                                    .graphicsLayer { alpha = reveal }
                                    .clip(CircleShape)
                                    .background(barColor)
                            )
                        }
                        if (reveal < 1f && i != p.options.lastIndex) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(0.6.dp)
                                    .graphicsLayer { alpha = 1f - reveal }
                                    .background(colors.separator)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(22.dp)) {
            val total = p.voterCount
            val noun = if (p.quiz) (if (total == 1) "answer" else "answers") else (if (total == 1) "vote" else "votes")
            when {
                canVote && p.multiple -> {
                    val enabled = selection.isNotEmpty()
                    T(
                        "Vote", TgTheme.type.body.copy(fontSize = 16.sp), if (enabled) colors.accent else colors.meta,
                        weight = FontWeight.SemiBold, maxLines = 1,
                        modifier = Modifier.align(Alignment.Center).then(
                            if (enabled) Modifier.fadeClickable {
                                actions.vote(selection.toList().sorted())
                            } else Modifier
                        ),
                    )
                }
                showResults && p.canGetVoters && !p.anonymous && total > 0 -> T(
                    "View Results", TgTheme.type.body.copy(fontSize = 16.sp), colors.accent, weight = FontWeight.SemiBold, maxLines = 1,
                    modifier = Modifier.align(Alignment.Center).fadeClickable(onClick = actions.viewResults),
                )
                else -> T(
                    if (total == 0) (if (p.quiz) "No answers" else "No votes") else "${formatCount(total)} $noun",
                    TgTheme.type.footnote.copy(fontSize = 14.sp), colors.meta, maxLines = 1, modifier = Modifier.align(Alignment.Center),
                )
            }
            if (showMeta) Box(Modifier.align(Alignment.BottomEnd)) { MetaRow(m, colors.meta) }
        }
    }
}

/** The dark tooltip above a quiz with its explanation (auto-hides after a few seconds, like Telegram). */
@Composable
private fun QuizExplanationPopup(text: String, onDismiss: () -> Unit) {
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(6000)
        onDismiss()
    }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = 500f)) }
    Box(Modifier.fillMaxWidth().height(0.dp)) {
        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(0, -8),
            onDismissRequest = onDismiss,
        ) {
            Row(
                Modifier
                    .widthIn(max = 320.dp)
                    .graphicsLayer { alpha = anim.value; scaleX = 0.9f + 0.1f * anim.value; scaleY = 0.9f + 0.1f * anim.value }
                    .clip(com.kyant.shapes.RoundedRectangle(14.dp))
                    .background(Color(0xF0202022))
                    .fadeClickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lightbulb, Color(0xFFFFD60A), 20.dp)
                Spacer(Modifier.width(8.dp))
                T(text, TgTheme.type.subheadline.copy(textDirection = TextDirection.Content), Color.White)
            }
        }
    }
}
