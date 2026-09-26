package com.abtin.tglass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.LiquidToggle
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.shapes.Capsule
import kotlin.math.roundToInt

/** iOS 26 switch with the Liquid Glass thumb. */
@Composable
fun IOSSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val current = rememberUpdatedState(checked)
    LiquidToggle(
        selected = { current.value },
        onSelect = onCheckedChange,
        backdrop = emptyBackdrop(),
        modifier = modifier,
    )
}

/** UISlider with optional discrete steps. */
@Composable
fun IOSSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val c = TgTheme.colors
    val view = LocalView.current
    val latest = rememberUpdatedState(value)
    BoxWithConstraints(modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.CenterStart) {
        val thumb = 28.dp
        val widthPx = constraints.maxWidth.toFloat()
        val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { thumb.toPx() }
        val track = (widthPx - thumbPx).coerceAtLeast(1f)
        fun snap(v: Float): Float {
            val clamped = v.coerceIn(range.start, range.endInclusive)
            if (steps <= 0) return clamped
            val step = (range.endInclusive - range.start) / (steps + 1)
            return range.start + ((clamped - range.start) / step).roundToInt() * step
        }
        fun fromX(x: Float) = range.start + ((x - thumbPx / 2) / track).coerceIn(0f, 1f) * (range.endInclusive - range.start)
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val input = Modifier
            .pointerInput(range, steps) {
                detectTapGestures { o ->
                    val v = snap(fromX(o.x))
                    if (v != latest.value) { Haptics.tick(view); onValueChange(v) }
                }
            }
            .pointerInput(range, steps) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val v = snap(fromX(change.position.x))
                    if (v != latest.value) { if (steps > 0) Haptics.tick(view); onValueChange(v) }
                }
            }
        Box(Modifier.fillMaxWidth().height(32.dp).then(input), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(Capsule()).background(c.searchField))
            Box(Modifier.fillMaxWidth(((thumbPx / 2 + fraction * track) / widthPx).coerceIn(0f, 1f)).height(4.dp).clip(Capsule()).background(c.accent))
            Box(
                Modifier
                    .offset { IntOffset((fraction * track).roundToInt(), 0) }
                    .size(thumb)
                    .shadow(4.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(0.15f), spotColor = Color.Black.copy(0.2f))
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}
