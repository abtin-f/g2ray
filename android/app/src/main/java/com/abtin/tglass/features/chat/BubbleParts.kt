package com.abtin.tglass.features.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.Message
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatCount
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// =====================================================================================
// Bubbles v2: Telegram-iOS metrics, forward header, comments bar, download ring
// =====================================================================================

/**
 * Text styles of the bubble parts, Telegram-iOS sizes at the 17 pt base (`baseDisplaySize * n / 17`) scaled
 * with Settings → Text Size, like the message text itself.
 */
internal class BubbleText(private val scale: Float) {
    private fun style(size: Float, line: Float) = TextStyle(
        fontFamily = com.abtin.tglass.core.design.InterText,
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        textDirection = TextDirection.Content,
    )

    /** Sender name, forward and reply headers (14 / 17). */
    val header = style(14f, 17f)
    /** Time, views, "edited" (11 / 17). */
    val meta = style(11f, 13f)
    /** Reaction counters. */
    val reaction = style(12f, 14f)
    /** File name (16 / 17). */
    val fileTitle = style(16f, 20f)
    /** File size / status line (13 / 17). */
    val fileInfo = style(13f, 16f)
    /** "N Comments" footer. */
    val comments = style(15f, 19f)
}

@Composable
internal fun bubbleText(): BubbleText {
    val scale = LocalAppSettings.current.textScale
    return remember(scale) { BubbleText(scale) }
}

/**
 * Hosts one child that fills the width it is given but reports at most [cap] as its preferred (max intrinsic)
 * width, so a long reply preview / name / link preview never stretches the bubble — the bubble hugs its content.
 */
@Composable
internal fun CapWidth(cap: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val policy = remember(cap) { CapWidthPolicy(cap) }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

private class CapWidthPolicy(private val cap: Dp) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val placeables = measurables.map { it.measure(constraints) }
        val w = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
        val h = placeables.maxOfOrNull { it.height } ?: constraints.minHeight
        return layout(w, h) { placeables.forEach { it.place(0, 0) } }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        min(measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0, cap.roundToPx())

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        min(measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0, cap.roundToPx())

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicHeight(width) } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicHeight(width) } ?: 0
}

/**
 * Telegram-iOS forward header (ChatMessageForwardInfoNode): "Forwarded from" and below it the origin's
 * 16–18 pt avatar + name. Tapping opens the channel (at the original post) or the user's profile.
 */
@Composable
internal fun ForwardHeader(m: Message, colors: BubbleColors, modifier: Modifier = Modifier) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val peer = m.forwardPeerId
    val origin = if (peer < 0L) repo.originChat(peer) else null
    val name = origin?.title ?: m.forwardedFrom ?: ""
    val text = bubbleText()
    val scale = LocalAppSettings.current.textScale
    val open: (() -> Unit)? = when {
        // Only chats TDLib could load (a private channel the user can't access stays unclickable).
        peer < 0L && origin != null -> ({
            if (m.forwardMessageId != 0L) ChatJumpRequest.request(peer, m.forwardMessageId)
            nav.push(Route.Chat(peer))
        })
        peer > 0L && repo.user(peer) != null -> ({ nav.push(Route.UserProfile(peer)) })
        else -> null
    }
    Column(modifier.then(if (open != null) Modifier.fadeClickable(onClick = open) else Modifier)) {
        T("Forwarded from", text.header, colors.accent, maxLines = 1)
        Row(Modifier.padding(top = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            if (peer != 0L) {
                Avatar(name.ifBlank { "?" }, peer, (17f * scale).dp)
                Spacer(Modifier.width(4.dp))
            }
            com.abtin.tglass.core.emoji.EmojiText(name, text.header, colors.accent, weight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * Telegram-iOS channel comments footer (ChatMessageCommentFooterContentNode): hairline, comment icon,
 * "N Comments" in the accent color and a chevron. Opens the post's thread in the discussion group.
 */
@Composable
internal fun CommentsBar(m: Message, colors: BubbleColors) {
    val count = m.comments ?: return
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = com.abtin.tglass.ui.components.LocalToast.current
    val text = bubbleText()
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .fadeClickable {
                    repo.loadCommentsTarget(m.chatId, m.id) { target ->
                        if (target == null) toast.show("Comments aren't available")
                        else {
                            if (target.messageId != 0L) ChatJumpRequest.request(target.chatId, target.messageId)
                            nav.push(Route.Chat(target.chatId))
                        }
                    }
                }
                .padding(start = 11.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(TgIcons.PiMessage, colors.accent, 20.dp)
            Spacer(Modifier.width(9.dp))
            T(
                when (count) {
                    0 -> "Leave a Comment"
                    1 -> "1 Comment"
                    else -> "${formatCount(count)} Comments"
                },
                text.comments, colors.accent, weight = FontWeight.Medium, maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Icon(IosIcons.ChevronRight, colors.accent.copy(alpha = 0.8f), 13.dp)
        }
    }
}

// ---- Download ring (Telegram-iOS SemanticStatusNode) ----

internal enum class DownloadPhase { Remote, Loading, Done }

/**
 * The progress glyph of a downloadable file: a down arrow before the download starts; while loading a rotating
 * arc (smoothly following the progress) around an X that cancels; when done it fades and shrinks away.
 * [modifier] carries the circle (size / background / click); nothing is drawn once the done animation ended.
 */
@Composable
internal fun DownloadRing(
    phase: DownloadPhase,
    progress: Float,
    glyph: Color,
    modifier: Modifier = Modifier,
    glyphSize: Dp = 16.dp,
    stroke: Dp = 2.dp,
    inset: Dp = 3.dp,
) {
    val shown by animateFloatAsState(if (phase == DownloadPhase.Done) 0f else 1f, tween(260), label = "ringShown")
    if (phase == DownloadPhase.Done && shown <= 0.001f) return
    val arc by animateFloatAsState(if (phase == DownloadPhase.Loading) 1f else 0f, tween(200), label = "ringArc")
    val arrow by animateFloatAsState(if (phase == DownloadPhase.Remote) 1f else 0f, tween(200), label = "ringArrow")
    Box(
        // The layer goes first so the caller's circle (background) fades and shrinks with the glyph.
        Modifier
            .graphicsLayer {
                alpha = shown
                val s = 0.55f + 0.45f * shown
                scaleX = s
                scaleY = s
            }
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (arc > 0.001f) {
            SpinningArc(if (phase == DownloadPhase.Done) 1f else progress, glyph, stroke, Modifier.matchParentSize().padding(inset).graphicsLayer { alpha = arc })
            Icon(IosIcons.Close, glyph, glyphSize * 0.85f, Modifier.graphicsLayer { alpha = arc; scaleX = 0.7f + 0.3f * arc; scaleY = 0.7f + 0.3f * arc })
        }
        if (arrow > 0.001f) {
            Icon(IosIcons.ArrowDown, glyph, glyphSize * 1.3f, Modifier.graphicsLayer { alpha = arrow; scaleX = 0.7f + 0.3f * arrow; scaleY = 0.7f + 0.3f * arrow })
        }
    }
}

@Composable
private fun SpinningArc(progress: Float, color: Color, stroke: Dp, modifier: Modifier) {
    val sweep by animateFloatAsState(progress.coerceIn(0.05f, 1f), tween(420, easing = FastOutSlowInEasing), label = "ringSweep")
    val spin = rememberInfiniteTransition(label = "ringSpin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "ringAngle")
    Canvas(modifier) {
        drawArc(color, -90f + angle, 360f * sweep, false, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * Download state of a picture shown in a bubble (photos load on their own): the 44 pt dark ring while it loads
 * (only if that takes a moment, so cached pictures never flash it), X cancels, the arrow starts it again.
 */
@Composable
internal fun ImageDownloadOverlay(image: ImageRef?, modifier: Modifier = Modifier) {
    if (image == null || image.fileId <= 0) return
    val repo = LocalRepository.current
    val path = repo.filePath(image)
    var cancelled by remember(image.fileId) { mutableStateOf(false) }
    var armed by remember(image.fileId) { mutableStateOf(false) }
    LaunchedEffect(image.fileId) {
        kotlinx.coroutines.delay(300)
        armed = true
    }
    val phase = when {
        path != null -> DownloadPhase.Done
        cancelled -> DownloadPhase.Remote
        else -> DownloadPhase.Loading
    }
    if (!armed && phase == DownloadPhase.Loading) return
    DownloadRing(
        phase, repo.fileProgress(image), Color.White,
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .fadeClickable {
                if (cancelled) {
                    cancelled = false
                    repo.requestImage(image)
                } else {
                    cancelled = true
                    repo.cancelDownload(image)
                }
            },
        glyphSize = 17.dp,
        inset = 4.dp,
    )
}

/** Telegram iOS shows every channel post as an incoming bubble (left, with tail), the admin's own posts included. */
internal fun Message.asChannelPost(): Message = if (outgoing) copy(outgoing = false) else this

/** "2.3 / 14 MB" (Telegram's size pill while a video downloads). */
internal fun progressBytes(done: Long, total: Long): String {
    if (total >= 1_048_576L) {
        fun mb(v: Long): String {
            val x = v / 1_048_576.0
            return if (x >= 10.0) x.roundToInt().toString() else String.format(Locale.US, "%.1f", x)
        }
        return "${mb(max(0L, done))} / ${mb(total)} MB"
    }
    return "${max(0L, done) / 1024} / ${max(1L, total / 1024)} KB"
}
