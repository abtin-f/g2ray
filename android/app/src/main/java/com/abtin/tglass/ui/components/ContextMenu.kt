package com.abtin.tglass.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.glass
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

class MenuAction(
    val title: String,
    /** A [TgIcons] drawable id or an [ImageVector]. */
    val icon: Any,
    val destructive: Boolean = false,
    val groupStart: Boolean = false,
    val onClick: () -> Unit,
)

/** Spec §56: ContextMenuController state. */
class ContextMenuRequest(
    val key: Any,
    /** Bounds of the pressed content in root coordinates (px). */
    val anchor: Rect,
    val alignEnd: Boolean,
    val actions: List<MenuAction>,
    val reactions: List<String>? = null,
    val onReact: ((String) -> Unit)? = null,
    /** Custom preview size (e.g. chat peek). Null = same size as the anchor. */
    val previewSize: Pair<Dp, Dp>? = null,
    val preview: @Composable () -> Unit,
)

class ContextMenuState {
    var request by mutableStateOf<ContextMenuRequest?>(null)
        internal set
    var visible by mutableStateOf(false)
        internal set

    /** The item that is currently "lifted"; lists hide it so only the sharp copy is visible. */
    val activeKey: Any? get() = request?.key

    fun show(request: ContextMenuRequest) {
        this.request = request
        visible = true
    }

    fun dismiss() {
        visible = false
    }
}

val LocalContextMenu = staticCompositionLocalOf<ContextMenuState> { error("ContextMenuState not provided") }

val DefaultReactions = listOf("👍", "❤️", "🔥", "🥰", "👏", "😁", "🤔", "🤯", "😱", "🎉", "🤩", "🙏", "👌", "😢", "💯")

/**
 * Hosts the app content and the context-menu layer (spec §6 layer order):
 * Z0 content → Z1 blur/dim → Z2 selected content (sharp) → Z3 menu.
 */
@Composable
fun ContextMenuHost(state: ContextMenuState, content: @Composable () -> Unit) {
    val animations = LocalAppSettings.current.animations
    val blur by animateDpAsState(
        if (state.visible) 16.dp else 0.dp,
        if (animations) tween(220) else tween(0),
        label = "menuBlur",
    )
    // The whole screen is recorded (unblurred) so the menu and reaction bar can be real refracting glass.
    val rootBackdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (blur > 0.5.dp) Modifier.blur(blur) else Modifier)) {
            Box(Modifier.fillMaxSize().layerBackdrop(rootBackdrop)) { content() }
        }
        val req = state.request
        if (req != null) ContextMenuOverlay(req, state, rootBackdrop)
    }
}

@Composable
private fun ContextMenuOverlay(req: ContextMenuRequest, state: ContextMenuState, rootBackdrop: Backdrop) {
    val c = TgTheme.colors
    val view = LocalView.current
    val progress = remember(req) { Animatable(0f) }
    val animations = LocalAppSettings.current.animations

    LaunchedEffect(req, state.visible) {
        if (state.visible) {
            if (animations) progress.animateTo(1f, spring(0.78f, 380f)) else progress.snapTo(1f)
        } else {
            if (animations) progress.animateTo(0f, tween(170)) else progress.snapTo(0f)
            if (state.request === req) state.request = null
        }
    }
    BackHandler(enabled = state.visible) { state.dismiss() }

    val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
    val safeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim.copy(alpha = c.scrim.alpha * progress.value.coerceIn(0f, 1f)))
            .pointerInput(req) { detectTapGestures { state.dismiss() } }
    )

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            // 0: reactions
            if (req.reactions != null && req.onReact != null) {
                ReactionBar(req.reactions, rootBackdrop) { emoji ->
                    Haptics.tap(view)
                    req.onReact?.invoke(emoji)
                    state.dismiss()
                }
            } else {
                Box(Modifier.size(0.dp))
            }
            // 1: preview (sharp copy of the pressed content)
            Box(Modifier.pointerInput(req) { detectTapGestures { state.dismiss() } }) { req.preview() }
            // 2: menu
            MenuList(req.actions, rootBackdrop) { action ->
                Haptics.tap(view)
                state.dismiss()
                action.onClick()
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val gap = 8.dp.roundToPx()
        val margin = 8.dp.roundToPx()
        val top = safeTop.roundToPx()
        val bottom = h - safeBottom.roundToPx()

        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val react = measurables[0].measure(loose.copy(maxWidth = w - margin * 2))
        val previewConstraints = req.previewSize?.let { (pw, ph) ->
            Constraints.fixed(pw.roundToPx().coerceAtMost(w - margin * 2), ph.roundToPx().coerceAtMost(h / 2))
        } ?: Constraints.fixed(req.anchor.width.roundToInt().coerceAtLeast(1), req.anchor.height.roundToInt().coerceAtLeast(1))
        val preview = measurables[1].measure(previewConstraints)
        val menu = measurables[2].measure(loose)

        val reactH = if (react.height > 0) react.height + gap else 0
        val block = reactH + preview.height + gap + menu.height

        // Spec §58: keep everything inside the safe area, preferring the original position.
        var blockTop = req.anchor.top.roundToInt() - reactH
        if (req.previewSize != null) blockTop = ((h - block) / 2).coerceAtLeast(top)
        if (blockTop < top) blockTop = top
        if (blockTop + block > bottom) blockTop = bottom - block
        val previewTop: Int
        val menuTop: Int
        if (blockTop < top) {
            // Too tall: pin the menu to the bottom and let the preview slide under the status bar.
            menuTop = bottom - menu.height
            previewTop = menuTop - gap - preview.height
        } else {
            previewTop = blockTop + reactH
            menuTop = previewTop + preview.height + gap
        }
        val previewLeft = if (req.previewSize != null) (w - preview.width) / 2 else req.anchor.left.roundToInt()
        val previewRight = previewLeft + preview.width
        val menuLeft = (if (req.alignEnd) previewRight - menu.width else previewLeft).coerceIn(margin, (w - menu.width - margin).coerceAtLeast(margin))
        val reactLeft = (if (req.alignEnd) previewRight - react.width else previewLeft).coerceIn(margin, (w - react.width - margin).coerceAtLeast(margin))

        layout(w, h) {
            val p = progress.value
            val pc = p.coerceIn(0f, 1f)
            val startTop = req.anchor.top.roundToInt()
            val startLeft = req.anchor.left.roundToInt()
            preview.placeWithLayer(
                x = lerp(startLeft.toFloat(), previewLeft.toFloat(), pc).roundToInt(),
                y = lerp(startTop.toFloat(), previewTop.toFloat(), pc).roundToInt(),
            ) {
                val s = if (req.previewSize != null) lerp(0.9f, 1f, p) else lerp(1f, 1.02f, pc)
                scaleX = s
                scaleY = s
                alpha = if (req.previewSize != null) pc else 1f
            }
            menu.placeWithLayer(menuLeft, menuTop) {
                alpha = pc
                val s = lerp(0.6f, 1f, p)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(if (req.alignEnd) 1f else 0f, 0f)
            }
            if (react.height > 0) {
                react.placeWithLayer(reactLeft, previewTop - gap - react.height) {
                    alpha = pc
                    val s = lerp(0.5f, 1f, p)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (req.alignEnd) 1f else 0f, 1f)
                }
            }
        }
    }
}

@Composable
private fun MenuList(actions: List<MenuAction>, backdrop: Backdrop, onClick: (MenuAction) -> Unit) {
    val c = TgTheme.colors
    Column(
        Modifier
            .width(250.dp)
            .glass(
                shape = RoundedRectangle(24.dp),
                backdrop = backdrop,
                surface = if (c.isDark) Color(0xFF1C1C1C).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.66f),
                blurRadius = 12.dp,
                lensHeight = 14.dp,
                lensAmount = 20.dp,
            )
    ) {
        actions.forEachIndexed { i, a ->
            if (i > 0) {
                if (a.groupStart) Box(Modifier.fillMaxWidth().height(8.dp).background(if (c.isDark) Color.Black.copy(0.35f) else Color.Black.copy(0.07f)))
                else Separator()
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .iosClickable(highlight = if (c.isDark) Color.White.copy(0.1f) else Color.Black.copy(0.08f)) { onClick(a) }
                    .height(44.dp)
                    .padding(start = 20.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Telegram-iOS ContextControllerActionsStackNode: icon slot 32pt at x=20, title at x=60.
                val color = if (a.destructive) c.destructive else c.text
                Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    when (val i = a.icon) {
                        is Int -> Icon(i, color, 24.dp)
                        is ImageVector -> Icon(i, color, 24.dp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                T(a.title, TgTheme.type.body, color, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReactionBar(reactions: List<String>, backdrop: Backdrop, onReact: (String) -> Unit) {
    val c = TgTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .glass(
                shape = Capsule(),
                backdrop = backdrop,
                surface = if (c.isDark) Color(0xFF1C1C1C).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.66f),
                blurRadius = 12.dp,
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(if (expanded) 300.dp else 272.dp).horizontalScroll(rememberScrollState())) {
            reactions.forEachIndexed { i, e ->
                val pop = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(30L * i)
                    pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f))
                }
                Box(
                    Modifier
                        .size(38.dp)
                        .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceIn(0f, 1f) }
                        .clip(Capsule())
                        .bounceClickable { onReact(e) },
                    contentAlignment = Alignment.Center,
                ) {
                    T(e, TgTheme.type.body.copy(fontSize = 26.sp, lineHeight = 30.sp))
                }
            }
        }
        Spacer(Modifier.width(2.dp))
        Box(
            Modifier.size(30.dp).clip(Capsule()).background(c.searchField).fadeClickable { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, c.secondaryText, 22.dp)
        }
    }
}
