package com.abtin.tglass.features.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.media.RecordedVoice
import com.abtin.tglass.core.media.VoicePlayer
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatDuration
import com.kyant.shapes.Capsule
import java.util.Locale
import kotlin.math.roundToInt

/** Diameter of the blue record button while recording (Telegram-iOS). */
private val RecordCircle = 92.dp

/**
 * Status inside the field while recording (Telegram-iOS): blinking red dot and a 0:03,45 timer on the left,
 * "‹ Slide to cancel" in the middle that follows the finger and fades out; "Cancel" once locked.
 */
@Composable
internal fun RecordingStatus(elapsedMs: Long, locked: Boolean, dragX: Float, cancelDistance: Dp, onCancel: () -> Unit) {
    val c = TgTheme.colors
    val blink = rememberInfiniteTransition(label = "rec")
    val dot by blink.animateFloat(1f, 0.15f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot")
    val cancelPx = with(LocalDensity.current) { cancelDistance.toPx() }
    val secs = elapsedMs / 1000
    val hundredths = (elapsedMs % 1000) / 10
    Row(Modifier.fillMaxWidth().height(ComposerHeight).padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).graphicsLayer { alpha = dot }.clip(CircleShape).background(c.destructive))
        Spacer(Modifier.width(8.dp))
        T(
            String.format(Locale.US, "%d:%02d,%02d", secs / 60, secs % 60, hundredths),
            TgTheme.type.body.copy(fontFeatureSettings = "tnum"),
            c.text,
            maxLines = 1,
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (locked) {
                T("Cancel", TgTheme.type.body, c.accent, weight = FontWeight.Medium, modifier = Modifier.fadeClickable(onClick = onCancel).padding(horizontal = 8.dp, vertical = 6.dp))
            } else {
                val shimmer = rememberInfiniteTransition(label = "slide")
                val nudge by shimmer.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "nudge")
                Row(
                    Modifier.graphicsLayer {
                        val progress = (-dragX / cancelPx).coerceIn(0f, 1f)
                        translationX = dragX * 0.55f - nudge * 3.dp.toPx()
                        alpha = (1f - progress * 1.3f).coerceIn(0f, 1f)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(IosIcons.ChevronLeft, c.secondaryText, 15.dp)
                    Spacer(Modifier.width(2.dp))
                    T("Slide to cancel", TgTheme.type.subheadline, c.secondaryText, maxLines = 1)
                }
            }
        }
        // Room for the big record circle that overlaps the field's end.
        Spacer(Modifier.width(34.dp))
    }
}

/**
 * The big blue record circle that follows the finger, with a soft ring pulsing to the voice level, and the lock
 * capsule above it (Telegram-iOS). Once locked the circle becomes the send button and the capsule a stop button.
 * Drawn centered on the mic button, overflowing it.
 */
@Composable
internal fun RecordingControls(
    level: Float,
    dragX: Float,
    dragY: Float,
    locked: Boolean,
    video: Boolean,
    lockDistance: Dp,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val c = TgTheme.colors
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f)) }
    val voiceLevel by animateFloatAsState(level.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 220f), label = "level")
    val breathe = rememberInfiniteTransition(label = "breathe")
    val idle by breathe.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "idle")
    val lockPx = with(density) { lockDistance.toPx() }
    val lockProgress = if (locked) 1f else (-dragY / lockPx).coerceIn(0f, 1f)

    // Lock capsule (becomes a round stop button when locked).
    val capsuleHeight by animateDpAsState(if (locked) 40.dp else 72.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "lockH")
    val capsuleLift by animateDpAsState(if (locked) 96.dp else 118.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "lockY")
    GlassBox(
        onClick = if (locked && !video) onStop else null,
        shape = Capsule(),
        modifier = Modifier
            .offset {
                val follow = if (locked) 0f else dragY * 0.35f
                IntOffset(0, (-capsuleLift.toPx() + follow).roundToInt())
            }
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 40.dp.toPx()
            }
            .requiredSize(40.dp, capsuleHeight),
    ) {
        if (locked) {
            if (video) Icon(IosIcons.Lock, c.secondaryText, 18.dp)
            else Icon(IosIcons.Stop, c.destructive, 18.dp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(IosIcons.Lock, c.secondaryText.copy(alpha = 0.6f + 0.4f * lockProgress), 18.dp)
                Spacer(Modifier.height(4.dp))
                Icon(
                    IosIcons.ChevronDown,
                    c.secondaryText,
                    14.dp,
                    Modifier.graphicsLayer {
                        rotationZ = 180f
                        alpha = 1f - lockProgress
                        translationY = -lockProgress * 6.dp.toPx() - idle * 2.dp.toPx()
                    },
                )
            }
        }
    }

    // Volume ring + circle
    Box(
        Modifier
            .graphicsLayer {
                translationX = if (locked) 0f else dragX
                translationY = if (locked) 0f else dragY
                val s = appear.value
                scaleX = s
                scaleY = s
            }
            .requiredSize(RecordCircle * 1.6f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .requiredSize(RecordCircle)
                .graphicsLayer {
                    val grow = 1f + 0.5f * voiceLevel + 0.05f * idle
                    scaleX = grow
                    scaleY = grow
                }
                .clip(CircleShape)
                .background(c.accent.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .requiredSize(RecordCircle)
                .clip(CircleShape)
                .background(c.accent)
                .bounceClickable { if (locked) onSend() },
            contentAlignment = Alignment.Center,
        ) {
            val icon = when {
                locked -> IosIcons.ArrowUp
                video -> IosIcons.Video
                else -> IosIcons.Mic
            }
            Icon(icon, Color.White, if (locked) 34.dp else 36.dp)
        }
    }
}

/** A stopped voice recording in the field: play / pause, waveform with progress and the duration. */
@Composable
internal fun VoiceDraft(voice: RecordedVoice, key: String) {
    val c = TgTheme.colors
    val context = LocalContext.current
    val current = VoicePlayer.currentKey == key
    val playing = current && VoicePlayer.playing
    val shownSecs by androidx.compose.runtime.remember(current, voice) {
        androidx.compose.runtime.derivedStateOf { if (current) (VoicePlayer.positionMs / 1000).toInt() else voice.seconds }
    }
    Row(
        Modifier.fillMaxWidth().height(ComposerHeight).padding(4.dp).clip(Capsule()).background(c.accent).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color.White).fadeClickable {
                VoicePlayer.toggle(context, key, voice.path, voice.seconds)
            },
            contentAlignment = Alignment.Center,
        ) { Icon(if (playing) IosIcons.Pause else IosIcons.Play, c.accent, 16.dp) }
        Spacer(Modifier.width(8.dp))
        val wave = voice.waveform
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 7.dp)) {
            if (wave.isEmpty()) return@Canvas
            val progress = if (current) VoicePlayer.progress else 0f
            val barW = 2.dp.toPx()
            val gap = 1.5.dp.toPx()
            val count = ((size.width + gap) / (barW + gap)).toInt().coerceAtLeast(1)
            for (i in 0 until count) {
                val v = wave[(i * wave.size / count).coerceIn(0, wave.size - 1)]
                val h = (size.height * v).coerceAtLeast(barW)
                val x = i * (barW + gap)
                val played = (i + 0.5f) / count <= progress
                drawRoundRect(
                    color = if (played) Color.White else Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(x, (size.height - h) / 2),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(barW / 2),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        T(formatDuration(shownSecs), TgTheme.type.footnote.copy(fontFeatureSettings = "tnum"), Color.White, weight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(6.dp))
    }
}
