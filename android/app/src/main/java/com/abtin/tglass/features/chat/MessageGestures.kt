package com.abtin.tglass.features.chat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Long-press that works over the whole message, whatever is inside it.
 *
 * A plain `detectTapGestures(onLongPress)` on the bubble never fires over photos, videos, round videos,
 * stickers, voice/file play buttons etc.: those children have their own `clickable`, which consumes the
 * down event before the parent (Main pass runs child → parent) sees it. Here the gesture is watched in the
 * **Initial** pass (parent → child) without requiring an unconsumed down, so children don't hide it; once the
 * long press fires, the rest of the gesture is consumed so the child's tap / the list scroll don't also run.
 */
@Composable
fun Modifier.messageLongPress(enabled: Boolean, onLongPress: () -> Unit): Modifier {
    val action by rememberUpdatedState(onLongPress)
    if (!enabled) return this
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            val timeout = viewConfiguration.longPressTimeoutMillis
            // true = the finger lifted or moved away before the timeout (not a long press).
            val cancelled = withTimeoutOrNull(timeout) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull true
                    if (!change.pressed) return@withTimeoutOrNull true
                    if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull true
                    if (event.changes.size > 1) return@withTimeoutOrNull true
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            if (cancelled == null) {
                action()
                // Swallow the rest of this touch: no tap on the media below, no scrolling, no swipe-to-reply.
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}
