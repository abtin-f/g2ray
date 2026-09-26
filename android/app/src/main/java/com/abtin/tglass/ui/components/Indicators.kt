package com.abtin.tglass.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme

/** Telegram's verified badge: accent "flower" background with a white check. */
@Composable
fun VerifiedBadge(size: Dp = 16.dp, color: Color = TgTheme.colors.accent, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(TgIcons.IcVerifiedBg, color, size)
        Icon(TgIcons.IcVerifiedFg, Color.White, size)
    }
}

/** Three bouncing dots used for "typing…" in the chat list and headers. */
@Composable
fun TypingDots(color: Color, dot: Dp = 4.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "typing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(dot * 0.6f), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val p by t.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse, StartOffset(i * 140)),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(dot)
                    .graphicsLayer { alpha = p; scaleX = 0.7f + 0.3f * p; scaleY = 0.7f + 0.3f * p; translationY = -(p - 0.35f) * dot.toPx() * 0.6f }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** "typing" + animated dots on one line. */
@Composable
fun TypingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TypingDots(color, 3.5.dp)
        androidx.compose.foundation.layout.Spacer(Modifier.size(5.dp))
        T(text, style, color, maxLines = 1)
    }
}

/** UIActivityIndicatorView: 8 rounded spokes whose opacity chases around the circle. */
@Composable
fun ActivityIndicator(size: Dp = 20.dp, color: Color = TgTheme.colors.secondaryText, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "spinner")
    val step by t.animateFloat(0f, 8f, infiniteRepeatable(tween(800, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        val head = step.toInt()
        val w = this.size.minDimension
        val stroke = w * 0.09f
        for (i in 0 until 8) {
            val age = ((head - i) % 8 + 8) % 8
            val angle = Math.toRadians(i * 45.0 - 90.0)
            val cx = center.x
            val cy = center.y
            val r0 = w * 0.24f
            val r1 = w * 0.46f
            drawLine(
                color.copy(alpha = 1f - age * 0.1f),
                androidx.compose.ui.geometry.Offset(cx + (r0 * kotlin.math.cos(angle)).toFloat(), cy + (r0 * kotlin.math.sin(angle)).toFloat()),
                androidx.compose.ui.geometry.Offset(cx + (r1 * kotlin.math.cos(angle)).toFloat(), cy + (r1 * kotlin.math.sin(angle)).toFloat()),
                strokeWidth = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
