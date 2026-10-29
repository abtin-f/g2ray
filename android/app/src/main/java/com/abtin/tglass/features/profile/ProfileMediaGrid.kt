package com.abtin.tglass.features.profile

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.data.Message
import com.abtin.tglass.data.MessageContent
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.formatDuration
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** Column count of the profile media grid, kept for the whole session (all profiles). */
internal object MediaGridZoom {
    const val MIN = 2
    const val MAX = 6
    var columns by mutableIntStateOf(3)
}

/**
 * State of the profile Media grid: pinch zoom (column count 2..6, a live scale preview while the fingers are down,
 * snapped on release) and multi-selection. The preview scale lives in a float state that is only read in
 * graphicsLayer blocks, so pinching does not recompose or allocate.
 */
@Stable
internal class MediaGridState(private val scope: CoroutineScope, private val listState: LazyListState) {
    var columns: Int
        get() = MediaGridZoom.columns
        set(v) { MediaGridZoom.columns = v }

    /** Live pinch scale (1 = idle). */
    var scale by mutableFloatStateOf(1f)
    var settling by mutableStateOf(false)
    var fade by mutableFloatStateOf(1f)
    var fx = 0f
    var fy = 0f
    private var rowTop = FloatArray(0)
    private var rowH = FloatArray(0)
    private var job: Job? = null
    private var rowBase = 0

    // ---- Selection ----
    var selecting by mutableStateOf(false)
    val selected = mutableStateListOf<Long>()
    /** Set by the "Select" menu action; the screen switches to the Media tab and starts selecting. */
    var selectRequest by mutableStateOf(false)
    /** Tile ids in grid order (real message ids, or negative placeholders in the demo). */
    var order: List<Long> = emptyList()

    fun isSelected(id: Long) = selected.contains(id)
    fun toggle(id: Long) { if (!selected.remove(id)) selected.add(id) }
    fun set(id: Long, on: Boolean) { if (on) { if (!selected.contains(id)) selected.add(id) } else selected.remove(id) }
    fun clear() { selecting = false; selected.clear() }

    fun origin(row: Int, widthPx: Float): TransformOrigin {
        val ox = if (widthPx > 0f) fx / widthPx else 0.5f
        if (settling || row >= rowTop.size) return TransformOrigin(ox, 0.5f)
        val top = rowTop[row]
        val h = rowH[row]
        if (top.isNaN() || h <= 0f) return TransformOrigin(ox, 0.5f)
        return TransformOrigin(ox, (fy - top) / h)
    }

    fun beginPinch(x: Float, y: Float) {
        job?.cancel()
        settling = false
        fade = 1f
        fx = x; fy = y
        val n = (order.size + columns - 1) / columns + 1
        if (rowTop.size < n) { rowTop = FloatArray(n); rowH = FloatArray(n) }
        rowTop.fill(Float.NaN)
        rowBase = -1
        for (info in listState.layoutInfo.visibleItemsInfo) {
            val key = info.key as? String ?: continue
            if (!key.startsWith(ROW_PREFIX)) continue
            val r = key.substring(ROW_PREFIX.length).toIntOrNull() ?: continue
            if (r in 0 until n) { rowTop[r] = info.offset.toFloat(); rowH[r] = info.size.toFloat() }
            rowBase = info.index - r
        }
    }

    fun endPinch(cum: Float) {
        val cols = columns
        val target = (cols / cum).roundToInt().coerceIn(MediaGridZoom.MIN, MediaGridZoom.MAX)
        if (target == cols) {
            settle(scale)
            return
        }
        // Keep the tile under the fingers in view: same tile index, new row, centered on the focal point.
        var focalRow = -1
        for (r in rowTop.indices) {
            val t = rowTop[r]
            if (!t.isNaN() && fy >= t && fy < t + rowH[r]) { focalRow = r; break }
        }
        val width = listState.layoutInfo.viewportSize.width.toFloat().coerceAtLeast(1f)
        if (focalRow >= 0 && rowBase >= 0) {
            val col = floor(fx / width * cols).toInt().coerceIn(0, cols - 1)
            val idx = (focalRow * cols + col).coerceAtMost((order.size - 1).coerceAtLeast(0))
            val newTile = width / target
            val top = fy - newTile / 2f
            listState.requestScrollToItem(rowBase + idx / target, -top.roundToInt())
        }
        columns = target
        // The new tiles are (cols / target) times the old size; continue from the visual size at release.
        settle((cum * target / cols).coerceIn(0.8f, 1.25f), crossfade = true)
    }

    private fun settle(from: Float, crossfade: Boolean = false) {
        job?.cancel()
        settling = true
        scale = from
        fade = if (crossfade) 0.6f else 1f
        job = scope.launch {
            animate(0f, 1f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)) { t, _ ->
                scale = from + (1f - from) * t
                if (crossfade) fade = (0.6f + 0.4f * t).coerceIn(0f, 1f)
            }
            scale = 1f; fade = 1f; settling = false
        }
    }

    companion object {
        const val ROW_PREFIX = "Media-row"
    }
}

/**
 * Two-finger pinch on the list: only consumes gestures with two pressed pointers, so vertical scrolling and
 * the tab swipe keep working with one finger.
 */
internal fun Modifier.mediaPinch(state: MediaGridState, enabled: Boolean): Modifier =
    if (!enabled) this else pointerInput(state) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var active = false
            var cum = 1f
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = e.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) {
                    if (!active) {
                        active = true
                        cum = 1f
                        val c = e.calculateCentroid(useCurrent = true)
                        state.beginPinch(c.x, c.y)
                    }
                    cum *= e.calculateZoom()
                    val cols = state.columns
                    state.scale = cum.coerceIn(cols / 7.5f, cols / 1.5f)
                    e.changes.forEach { it.consume() }
                } else if (active) break
            }
            if (active) state.endPinch(cum.coerceIn(state.columns / 7.5f, state.columns / 1.5f))
        }
    }

/** In selection mode, dragging horizontally across tiles selects (or deselects) them, like iOS. */
private fun Modifier.swipeSelect(grid: MediaGridState, rowIndex: Int, cols: Int): Modifier =
    pointerInput(grid, rowIndex, cols) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!grid.selecting) return@awaitEachGesture
            var dragging = false
            var adding = true
            var last = -1
            fun hit(x: Float, y: Float): Int {
                val col = (x / size.width.coerceAtLeast(1) * cols).toInt().coerceIn(0, cols - 1)
                val dr = floor(y / size.height.coerceAtLeast(1)).toInt()
                return (rowIndex + dr) * cols + col
            }
            fun apply(i: Int) {
                if (i == last || i < 0 || i >= grid.order.size) return
                last = i
                grid.set(grid.order[i], adding)
            }
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.changes.count { it.pressed } > 1) return@awaitEachGesture
                val ch = e.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (!ch.pressed) return@awaitEachGesture
                if (!dragging) {
                    val dx = ch.position.x - down.position.x
                    val dy = ch.position.y - down.position.y
                    if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) return@awaitEachGesture
                    if (abs(dx) > viewConfiguration.touchSlop) {
                        dragging = true
                        val i = hit(down.position.x, down.position.y)
                        adding = grid.order.getOrNull(i)?.let { !grid.isSelected(it) } ?: true
                        apply(i)
                    }
                }
                if (dragging) {
                    ch.consume()
                    apply(hit(ch.position.x, ch.position.y))
                }
            }
        }
    }

/**
 * Media grid (photos with video durations, or GIFs) with 1dp gaps. With a [grid] state it zooms (2..6 columns),
 * selects on long press / tap and swipe. [tiles] may contain demo placeholders (null message).
 */
internal fun LazyListScope.mediaGrid(
    prefix: String,
    tiles: List<Pair<Message?, MessageContent.Photo>>,
    itemModifier: Modifier,
    onOpen: (Message) -> Unit,
    grid: MediaGridState? = null,
) {
    val cols = grid?.columns ?: 3
    val ids = tiles.mapIndexed { i, t -> t.first?.id ?: -(i + 1L) }
    grid?.order = ids
    tiles.chunked(cols).forEachIndexed { r, row ->
        item(key = "$prefix-row$r") {
            Row(
                itemModifier
                    .fillMaxWidth()
                    .padding(bottom = 1.dp)
                    .graphicsLayer {
                        if (grid != null) {
                            val s = grid.scale
                            if (s != 1f) {
                                scaleX = s
                                scaleY = s
                                transformOrigin = grid.origin(r, size.width)
                            }
                            if (grid.settling) alpha = grid.fade
                        }
                    }
                    .then(if (grid != null) Modifier.swipeSelect(grid, r, cols) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                row.forEachIndexed { i, t ->
                    MediaTile(t.second, t.first, ids[r * cols + i], grid, cols, onOpen)
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.MediaTile(
    p: MessageContent.Photo,
    m: Message?,
    id: Long,
    grid: MediaGridState?,
    cols: Int,
    onOpen: (Message) -> Unit,
) {
    val view = LocalView.current
    val selecting = grid?.selecting == true
    val sel = grid != null && grid.isSelected(id)
    val inset by animateFloatAsState(if (sel) 0.86f else 1f, spring(0.7f, 500f), label = "tileInset")
    val colors = avatarColors(p.seed.toLong())
    Box(
        Modifier
            .weight(1f)
            .aspectRatio(1f)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = if (grid != null && !selecting) ({
                    Haptics.longPress(view)
                    grid.selecting = true
                    grid.set(id, true)
                }) else null,
                onClick = {
                    if (grid != null && grid.selecting) grid.toggle(id)
                    else if (m != null) onOpen(m)
                },
            ),
    ) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { scaleX = inset; scaleY = inset }
                .background(Brush.linearGradient(listOf(colors.first, colors.second))),
            contentAlignment = Alignment.Center,
        ) {
            if (p.image != null) TgImage(p.image, Modifier.fillMaxSize(), maxPx = if (cols >= 4) 240 else 360)
            else T(p.emoji, TgTheme.type.body.copy(fontSize = (110f / cols).sp, lineHeight = (128f / cols).sp))
            val label = when {
                p.loop -> "GIF"
                p.video && p.duration > 0 -> formatDuration(p.duration)
                else -> null
            }
            if (label != null) {
                T(
                    label, TgTheme.type.caption2, Color.White, weight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                        .clip(Capsule()).background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        if (selecting) {
            val accent = TgTheme.colors.accent
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp)
                    .clip(Capsule())
                    .background(if (sel) accent else Color.Black.copy(alpha = 0.28f))
                    .border(1.5.dp, Color.White, Capsule()),
                contentAlignment = Alignment.Center,
            ) {
                if (sel) Icon(IosIcons.Checkmark, Color.White, 13.dp)
            }
        }
    }
}

/** Bottom glass bar of the selection mode: Forward, Show in Chat, count, Delete. Lives outside the list's backdrop layer. */
@Composable
internal fun MediaSelectionBar(
    count: Int,
    canForward: Boolean,
    canShow: Boolean,
    canDelete: Boolean,
    onForward: () -> Unit,
    onShow: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TgTheme.colors
    val dim = c.secondaryText.copy(alpha = 0.5f)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(TgIcons.CtxForward, { if (canForward) onForward() }, tint = if (canForward) c.text else dim)
        GlassIconButton(TgIcons.PiMessage, { if (canShow) onShow() }, tint = if (canShow) c.text else dim)
        GlassBox(onClick = null, modifier = Modifier.weight(1f).height(44.dp), shape = Capsule()) {
            T(if (count == 0) "Select Items" else "$count Selected", TgTheme.type.body, c.text, weight = FontWeight.Medium, maxLines = 1)
        }
        GlassIconButton(TgIcons.CtxDelete, { if (canDelete) onDelete() }, tint = if (canDelete) c.destructive else dim)
    }
}
