package com.abtin.tglass.core.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.zIndex
import com.abtin.tglass.core.design.TgTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

sealed interface Route {
    data object Welcome : Route
    data object Phone : Route
    data class Code(val phone: String) : Route
    data object ApiSetup : Route
    /** Settings → Proxy. */
    data object Proxy : Route
    data class ProxyEdit(val id: Int?) : Route
    /** Confirmation for an opened proxy link. */
    data class ProxyLink(val link: String) : Route
    data object Password : Route
    data object Register : Route
    data object Main : Route
    data class Chat(val chatId: Long) : Route
    data class Profile(val chatId: Long) : Route
    data class UserProfile(val userId: Long) : Route
    data object Archive : Route
    data object NewMessage : Route
    /** Story viewer; with [postsOf] it shows that chat's profile Posts (see profileStories) from [startIndex]. */
    data class Stories(val startUserId: Long, val postsOf: Long = 0L, val startIndex: Int = 0) : Route
    data class ActiveCall(val userId: Long, val video: Boolean) : Route
    data class Media(val chatId: Long, val messageId: Long) : Route
    data class SettingsPage(val page: com.abtin.tglass.features.settings.Page) : Route
    data object Calls : Route

    // ---- Groups & channels ----
    /** Member picker of the New Group flow. */
    data object NewGroup : Route
    /** Name + photo step of the New Group flow. */
    data class NewGroupInfo(val userIds: List<Long>) : Route
    data object NewChannel : Route
    data class EditChat(val chatId: Long) : Route
    data class AddMembers(val chatId: Long) : Route
    /** Settings → Chat Folders editor; [folderId] null creates a new folder. */
    data class FolderEdit(val folderId: Int?) : Route

    // ---- Contacts, media, calls ----
    /** iOS New Contact form (first / last name, phone). */
    data object NewContact : Route
}

/** Routes presented modally (slide up) instead of pushed (slide from right). */
private val Route.isModal: Boolean
    get() = this is Route.Stories || this is Route.ActiveCall || this is Route.NewMessage || this is Route.Media

/** Routes whose chrome is always dark (status bar icons light). */
val Route.isDarkChrome: Boolean
    get() = this is Route.Stories || this is Route.ActiveCall || this is Route.Media

class Entry(val route: Route, val id: Long, initialOffset: Float = 1f) {
    /** 0 = fully on screen, 1 = fully off screen (to the right, or below for modals). */
    val offset = Animatable(initialOffset)
    val modal = route.isModal
}

/** A UINavigationController-like stack (spec §49: push from right, pop to right, sheets from bottom). */
class Navigator(initial: Route, private val scope: CoroutineScope) {
    private var ids = 0L
    val stack = mutableStateListOf(Entry(initial, ids++, 0f))
    private var busy = false

    val top: Route get() = stack.last().route

    fun push(route: Route) {
        val e = Entry(route, ids++)
        stack.add(e)
        scope.launch { e.offset.animateTo(0f, PushSpec) }
    }

    fun pop() {
        if (stack.size <= 1 || busy) return
        val e = stack.last()
        busy = true
        scope.launch {
            e.offset.animateTo(1f, PushSpec)
            stack.remove(e)
            busy = false
        }
    }

    /** Replace the whole stack, e.g. after login. */
    fun resetTo(route: Route) {
        val e = Entry(route, ids++, 0f)
        stack.clear()
        stack.add(e)
    }

    /** Instantly shows [route] on top without animation (used for deep links / screenshots). */
    fun pushInstant(route: Route) {
        stack.add(Entry(route, ids++, 0f))
    }

    fun replaceTop(route: Route) {
        stack[stack.lastIndex] = Entry(route, ids++, 0f)
    }

    /** Closes everything above the root (e.g. a finished New Group flow) and shows [route] on top of it. */
    fun popToRootAndPush(route: Route) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        push(route)
    }

    internal fun dragTo(fraction: Float) {
        scope.launch { stack.last().offset.snapTo(fraction.coerceIn(0f, 1f)) }
    }

    internal fun settle(pop: Boolean) {
        val e = stack.last()
        if (pop) {
            busy = true
            scope.launch {
                e.offset.animateTo(1f, tween(220, easing = IOSEase))
                stack.remove(e)
                busy = false
            }
        } else {
            scope.launch { e.offset.animateTo(0f, tween(220, easing = IOSEase)) }
        }
    }

    companion object {
        val IOSEase = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)
        val PushSpec = tween<Float>(380, easing = IOSEase)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/**
 * Renders the top two entries with the iOS parallax push transition and an interactive
 * swipe-from-left-edge back gesture.
 */
@Composable
fun IOSNavHost(navigator: Navigator, content: @Composable (Route) -> Unit) {
    val holder = rememberSaveableStateHolder()
    val stack = navigator.stack
    val density = LocalDensity.current
    val edge = with(density) { 28.dp.toPx() }
    val dim = if (TgTheme.colors.isDark) 0.5f else 0.12f

    BackHandler(enabled = stack.size > 1) { navigator.pop() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pointerInput(navigator) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val topEntry = stack.lastOrNull() ?: return@awaitEachGesture
                    if (stack.size < 2 || topEntry.modal || down.position.x > edge) return@awaitEachGesture
                    val width = size.width.toFloat()
                    val tracker = VelocityTracker()
                    var dragging = false
                    var total = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dx = change.positionChange().x
                        total += dx
                        if (!dragging && total > viewConfiguration.touchSlop) dragging = true
                        if (dragging) {
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            navigator.dragTo(total / width)
                        } else if (abs(change.positionChange().y) > viewConfiguration.touchSlop) {
                            break
                        }
                    }
                    if (dragging) {
                        val v = tracker.calculateVelocity().x
                        navigator.settle(pop = v > 800f || (total / width > 0.4f && v > -300f))
                    }
                }
            }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val visible = stack.takeLast(2)
        visible.forEachIndexed { index, entry ->
            key(entry.id) {
                val isTop = index == visible.lastIndex
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(index.toFloat())
                        .graphicsLayer {
                            if (isTop) {
                                val o = entry.offset.value
                                if (entry.modal) translationY = o * h else translationX = o * w
                            } else {
                                val topEntry = visible.last()
                                val progress = 1f - topEntry.offset.value
                                if (!topEntry.modal) translationX = -0.3f * w * progress
                            }
                        }
                        .then(
                            if (isTop && !entry.modal && stack.size > 1) Modifier.shadow(12.dp, clip = false, ambientColor = Color.Black.copy(0.15f), spotColor = Color.Black.copy(0.2f))
                            else Modifier
                        )
                        .drawWithContent {
                            drawContent()
                            if (!isTop) {
                                val topEntry = visible.last()
                                val progress = 1f - topEntry.offset.value
                                if (progress > 0f) drawRect(Color.Black.copy(alpha = dim * progress))
                            }
                        }
                ) {
                    holder.SaveableStateProvider(entry.id) {
                        Box(Modifier.fillMaxSize().background(TgTheme.colors.background)) {
                            content(entry.route)
                        }
                    }
                }
            }
        }
    }
}
