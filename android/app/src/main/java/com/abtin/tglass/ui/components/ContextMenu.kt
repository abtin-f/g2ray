package com.abtin.tglass.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
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
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

class MenuAction(
    val title: String,
    /** A [TgIcons] drawable id or an [ImageVector]. */
    val icon: Any,
    val destructive: Boolean = false,
    val groupStart: Boolean = false,
    /** Shown as an icon-over-label button in the row at the top of the menu (Telegram iOS 26: Select · Copy · Delete). */
    val quick: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Reactions offered above a long-pressed message. Filled asynchronously (TDLib getMessageAvailableReactions),
 * so the bar can appear at once with a guess and settle on the chat's real list a moment later.
 */
class MenuReactions(top: List<String>, all: List<String> = top, chosen: Set<String> = emptySet()) {
    /** Shown in the capsule (about seven). */
    var top by mutableStateOf(top)
    /** Everything the chat allows, shown when the bar is expanded. */
    var all by mutableStateOf(all)
    /** Reactions the user already put on the message. */
    var chosen by mutableStateOf(chosen)
    /** False when the chat has reactions turned off: the bar is hidden. */
    var available by mutableStateOf(true)

    fun isChosen(emoji: String) = chosen.any { sameReaction(it, emoji) }
}

/** "❤" and "❤️" are the same reaction (TDLib drops the emoji variation selector). */
fun sameReaction(a: String, b: String): Boolean = a.replace("️", "") == b.replace("️", "")

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
    /** Live reaction list (takes precedence over [reactions]). */
    val menuReactions: MenuReactions? = null,
    /** Actions read from snapshot state (e.g. refined once message permissions load); overrides [actions]. */
    val dynamicActions: (() -> List<MenuAction>)? = null,
    val preview: @Composable () -> Unit,
)

class ContextMenuState {
    var request by mutableStateOf<ContextMenuRequest?>(null)
        internal set
    var visible by mutableStateOf(false)
        internal set

    /** The whole app content recorded for glass; overlays drawn outside it (alerts, sheets) can refract it. */
    var backdrop by mutableStateOf<Backdrop?>(null)
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

/** Telegram's default reaction order (used before / without the chat's own list). */
val DefaultReactions: List<String> = com.abtin.tglass.data.TopReactions

/** All free emoji reactions Telegram offers (the expanded grid in demo mode / before the chat's list loads). */
val AllFreeReactions: List<String> = com.abtin.tglass.data.FreeReactions

private const val VisibleReactions = 6
private val ReactionSlot = 46.dp
private val ReactionBarHeight = 56.dp

/**
 * Hosts the app content and the context-menu layer (spec §6 layer order):
 * Z0 content → Z1 blur/dim → Z2 selected content (sharp) → Z3 menu.
 */
@Composable
fun ContextMenuHost(state: ContextMenuState, content: @Composable () -> Unit) {
    val animations = LocalAppSettings.current.animations
    val blur by animateDpAsState(
        if (state.visible) 18.dp else 0.dp,
        if (animations) tween(240) else tween(0),
        label = "menuBlur",
    )
    // The whole screen is recorded (unblurred) so the menu and reaction bar can be real refracting glass.
    val rootBackdrop = rememberLayerBackdrop()
    LaunchedEffect(rootBackdrop) { state.backdrop = rootBackdrop }
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
    var expanded by remember(req) { mutableStateOf(false) }
    val expand by animateFloatAsState(if (expanded) 1f else 0f, if (animations) spring(0.8f, 420f) else tween(0), label = "reactExpand")

    LaunchedEffect(req, state.visible) {
        if (state.visible) {
            if (animations) progress.animateTo(1f, spring(0.78f, 380f)) else progress.snapTo(1f)
        } else {
            if (animations) progress.animateTo(0f, tween(170)) else progress.snapTo(0f)
            if (state.request === req) state.request = null
        }
    }
    BackHandler(enabled = state.visible) { if (expanded) expanded = false else state.dismiss() }

    val reactions = req.menuReactions ?: remember(req) { req.reactions?.let { MenuReactions(it, (it + AllFreeReactions).distinct()) } }
    val showReactions = reactions != null && req.onReact != null && reactions.available && reactions.top.isNotEmpty()
    val actions = req.dynamicActions?.invoke() ?: req.actions

    val safeTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp
    val safeBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp

    Box(
        Modifier
            .fillMaxSize()
            .background(c.scrim.copy(alpha = c.scrim.alpha * progress.value.coerceIn(0f, 1f)))
            .pointerInput(req) { detectTapGestures { if (expanded) expanded = false else state.dismiss() } }
    )

    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            // 0: reactions (capsule, or the full grid when expanded)
            if (showReactions && reactions != null) {
                ReactionsControl(
                    reactions = reactions,
                    expanded = expanded,
                    expandProgress = expand,
                    backdrop = rootBackdrop,
                    onExpand = { Haptics.tap(view); expanded = !expanded },
                ) { emoji ->
                    Haptics.tap(view)
                    req.onReact?.invoke(emoji)
                    state.dismiss()
                }
            } else {
                Box(Modifier.size(0.dp))
            }
            // 1: preview (sharp copy of the pressed content)
            Box(Modifier.pointerInput(req) { detectTapGestures { if (expanded) expanded = false else state.dismiss() } }) { req.preview() }
            // 2: menu
            MenuList(actions, rootBackdrop, enabled = !expanded) { action ->
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
        val react = measurables[0].measure(loose.copy(maxWidth = (w - margin * 2).coerceAtLeast(0), maxHeight = (bottom - top).coerceAtLeast(0)))
        val previewConstraints = req.previewSize?.let { (pw, ph) ->
            Constraints.fixed(pw.roundToPx().coerceAtMost(w - margin * 2), ph.roundToPx().coerceAtMost(h / 2))
        } ?: Constraints.fixed(req.anchor.width.roundToInt().coerceAtLeast(1), req.anchor.height.roundToInt().coerceAtLeast(1))
        val preview = measurables[1].measure(previewConstraints)

        // Layout uses the collapsed bar height so expanding the grid does not move the message.
        val barH = if (react.height > 0) ReactionBarHeight.roundToPx() else 0
        val reactH = if (barH > 0) barH + gap else 0
        val menuMax = (bottom - top - reactH - gap - minOf(preview.height, 96.dp.roundToPx())).coerceAtLeast(160.dp.roundToPx())
        val menu = measurables[2].measure(loose.copy(maxHeight = menuMax))
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
        val reactTop = (previewTop - gap - barH).coerceAtLeast(top).coerceAtMost((bottom - react.height).coerceAtLeast(top))

        layout(w, h) {
            val p = progress.value
            val pc = p.coerceIn(0f, 1f)
            val startTop = req.anchor.top.roundToInt()
            val startLeft = req.anchor.left.roundToInt()
            preview.placeWithLayer(
                x = lerp(startLeft.toFloat(), previewLeft.toFloat(), pc).roundToInt(),
                y = lerp(startTop.toFloat(), previewTop.toFloat(), pc).roundToInt(),
            ) {
                val s = if (req.previewSize != null) lerp(0.9f, 1f, p) else lerp(1f, 1.03f, pc)
                scaleX = s
                scaleY = s
                alpha = if (req.previewSize != null) pc else 1f
            }
            menu.placeWithLayer(menuLeft, menuTop) {
                alpha = pc * (1f - expand)
                val s = lerp(0.6f, 1f, p) * lerp(1f, 0.85f, expand)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(if (req.alignEnd) 1f else 0f, 0f)
            }
            if (react.height > 0) {
                react.placeWithLayer(reactLeft, reactTop, zIndex = 1f) {
                    alpha = pc
                    val s = lerp(0.4f, 1f, p)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(if (req.alignEnd) 0.85f else 0.15f, 1f)
                }
            }
        }
    }
}

@Composable
private fun MenuList(actions: List<MenuAction>, backdrop: Backdrop, enabled: Boolean, onClick: (MenuAction) -> Unit) {
    val c = TgTheme.colors
    val quick = actions.filter { it.quick }.take(4)
    val rows = actions.filterNot { it in quick }
    val highlight = if (c.isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    val hairline = if (c.isDark) Color.White.copy(0.16f) else Color.Black.copy(0.12f)
    Column(
        Modifier
            .width(250.dp)
            .glass(
                shape = RoundedRectangle(26.dp),
                backdrop = backdrop,
                surface = if (c.isDark) Color(0xFF1C1C1C).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.62f),
                blurRadius = 12.dp,
                lensHeight = 14.dp,
                lensAmount = 20.dp,
            )
            .verticalScroll(rememberScrollState())
            .padding(vertical = 6.dp)
    ) {
        if (quick.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                quick.forEach { a ->
                    val color = if (a.destructive) c.destructive else c.text
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedRectangle(18.dp))
                            .then(if (enabled) Modifier.iosClickable(highlight = highlight) { onClick(a) } else Modifier)
                            .padding(top = 9.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                            when (val icon = a.icon) {
                                is Int -> Icon(icon, color, 24.dp)
                                is ImageVector -> Icon(icon, color, 22.dp)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        T(a.title, TgTheme.type.footnote.copy(fontSize = 13.sp), color, maxLines = 1, align = TextAlign.Center)
                    }
                }
            }
            if (rows.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(0.5.dp).background(hairline))
            }
        }
        rows.forEachIndexed { i, a ->
            if (i > 0 && a.groupStart) {
                // iOS 26 menus separate groups with a hairline and a little air; items inside a group have none.
                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).height(0.5.dp).background(hairline))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .clip(Capsule())
                    .then(if (enabled) Modifier.iosClickable(highlight = highlight) { onClick(a) } else Modifier)
                    .height(44.dp)
                    .padding(start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Telegram-iOS ContextControllerActionsStackNode: icon slot 32pt at x=20, title at x=60.
                val color = if (a.destructive) c.destructive else c.text
                Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                    when (val icon = a.icon) {
                        is Int -> Icon(icon, color, 24.dp)
                        is ImageVector -> Icon(icon, color, 22.dp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                T(a.title, TgTheme.type.body, color, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

/** iOS 26 reaction picker: a glass capsule with ~6 reactions and a chevron that opens the full grid. */
@Composable
private fun ReactionsControl(
    reactions: MenuReactions,
    expanded: Boolean,
    expandProgress: Float,
    backdrop: Backdrop,
    onExpand: () -> Unit,
    onReact: (String) -> Unit,
) {
    val c = TgTheme.colors
    val surface = if (c.isDark) Color(0xFF1C1C1C).copy(alpha = 0.62f) else Color.White.copy(alpha = 0.7f)
    // About six fit next to the chevron; fewer on narrow screens.
    val fit = ((LocalConfiguration.current.screenWidthDp - 16 - 10 - 46) / ReactionSlot.value).toInt().coerceIn(3, VisibleReactions)
    val top = reactions.top.take(fit)
    val all = reactions.all.ifEmpty { reactions.top }
    val canExpand = all.size > top.size
    if (!expanded) {
        Row(
            Modifier
                .height(ReactionBarHeight)
                .glass(shape = Capsule(), backdrop = backdrop, surface = surface, blurRadius = 12.dp, lensHeight = 12.dp, lensAmount = 18.dp)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            top.forEachIndexed { i, e ->
                ReactionItem(e, reactions.isChosen(e), popDelay = 22L * i, onClick = { onReact(e) })
            }
            if (canExpand) {
                Box(
                    Modifier
                        .padding(start = 2.dp, end = 2.dp)
                        .size(40.dp)
                        .clip(Capsule())
                        .background(c.accent.copy(alpha = if (c.isDark) 0.3f else 0.18f))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onExpand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(IosIcons.ChevronDown, if (c.isDark) Color.White.copy(0.9f) else c.accent.copy(alpha = 0.85f), 20.dp)
                }
            }
        }
    } else {
        val cols = 7
        val rows = (all.size + cols - 1) / cols
        Column(
            Modifier
                .width(ReactionSlot * cols + 12.dp)
                .glass(
                    shape = RoundedRectangle(26.dp), backdrop = backdrop, surface = surface, blurRadius = 14.dp, lensHeight = 12.dp, lensAmount = 18.dp,
                    layerBlock = {
                        val s = lerp(0.92f, 1f, expandProgress)
                        scaleX = s; scaleY = s
                        alpha = expandProgress.coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    },
                )
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Reactions", TgTheme.type.footnote, c.secondaryText, modifier = Modifier.weight(1f), maxLines = 1)
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(Capsule())
                        .background(if (c.isDark) Color.White.copy(0.12f) else Color.Black.copy(0.06f))
                        .clickable(remember { MutableInteractionSource() }, null, onClick = onExpand),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(IosIcons.ChevronDown, c.secondaryText, 16.dp, Modifier.graphicsLayer { rotationZ = 180f })
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                modifier = Modifier.fillMaxWidth().heightIn(max = ReactionSlot * minOf(rows, 7) + 12.dp),
                contentPadding = PaddingValues(6.dp),
            ) {
                itemsIndexed(all, key = { i, e -> "$i-$e" }) { i, e ->
                    Box(Modifier.height(ReactionSlot), contentAlignment = Alignment.Center) {
                        ReactionItem(e, reactions.isChosen(e), popDelay = (8L * i).coerceAtMost(240L), onClick = { onReact(e) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactionItem(emoji: String, chosen: Boolean, popDelay: Long, onClick: () -> Unit) {
    val c = TgTheme.colors
    val animations = LocalAppSettings.current.animations
    val pop = remember { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (pop.value < 1f) {
            kotlinx.coroutines.delay(popDelay)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 520f))
        }
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 1.35f else 1f, spring(0.55f, 700f), label = "reactPress")
    Box(
        Modifier
            .size(ReactionSlot)
            .graphicsLayer {
                val s = pop.value * pressScale
                scaleX = s; scaleY = s
                alpha = pop.value.coerceIn(0f, 1f)
            }
            .clickable(source, null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (chosen) {
            Box(Modifier.size(42.dp).clip(Capsule()).background(if (c.isDark) Color.White.copy(0.2f) else c.accent.copy(alpha = 0.16f)))
        }
        com.abtin.tglass.core.emoji.EmojiGlyph(emoji, 34.dp)
    }
}
