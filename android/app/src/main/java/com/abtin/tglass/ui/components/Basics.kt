package com.abtin.tglass.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme

/** Plain text with iOS defaults. */
@Composable
fun T(
    text: String,
    style: TextStyle,
    color: Color = TgTheme.colors.text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    weight: FontWeight? = null,
    align: TextAlign? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            fontWeight = weight ?: style.fontWeight,
            textAlign = align ?: style.textAlign,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = onTextLayout,
    )
}

@Composable
fun Icon(
    icon: ImageVector,
    tint: Color = TgTheme.colors.text,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

/** Haptics (spec §51). */
object Haptics {
    fun tap(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun longPress(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun tick(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun reject(view: android.view.View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}

/**
 * iOS-like touch feedback: no ripple, a flat highlight while pressed, optional long-press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.iosClickable(
    highlight: Color? = TgTheme.colors.cellPressed,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val view = LocalView.current
    return this
        .then(if (pressed && highlight != null) Modifier.background(highlight) else Modifier)
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onLongClick = onLongClick?.let { lc -> { Haptics.longPress(view); lc() } },
            onClick = onClick,
        )
}

/** Tap feedback for text buttons / icons: dims while pressed like UIButton. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.fadeClickable(enabled: Boolean = true, onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (pressed) 0.35f else 1f, label = "fade")
    return this
        .graphicsLayer { this.alpha = alpha }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/** Scale-down press used by colored action buttons. */
@Composable
fun Modifier.bounceClickable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(0.6f, 600f), label = "bounce")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun Separator(modifier: Modifier = Modifier, startPadding: Dp = 0.dp) {
    Box(
        modifier
            .padding(start = startPadding)
            .fillMaxWidth()
            .height(0.33.dp)
            .background(TgTheme.colors.separator)
    )
}

@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TgTheme.colors.accent,
    bold: Boolean = false,
    enabled: Boolean = true,
) {
    T(
        text = text,
        style = TgTheme.type.body,
        color = if (enabled) color else TgTheme.colors.tertiaryText,
        weight = if (bold) FontWeight.SemiBold else null,
        modifier = modifier.fadeClickable(enabled = enabled, onClick = onClick),
    )
}
