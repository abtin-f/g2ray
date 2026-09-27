package com.abtin.tglass.features.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.ProvideDarkColors
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.media.VideoSurface
import com.abtin.tglass.core.media.rememberVideoPlayer
import com.abtin.tglass.core.media.rememberVideoState
import com.abtin.tglass.core.media.togglePlay
import com.abtin.tglass.features.media.MediaIcons
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.IOSSlider
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.abtin.tglass.ui.components.formatDuration
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Editing mode of the photo editor. Text is not a mode: it opens the text entry directly. */
enum class EditorTool { None, Crop, Draw, Adjust }

private enum class AdjustKind(val title: String) { Brightness("Brightness"), Contrast("Contrast"), Saturation("Saturation"), Warmth("Warmth") }

private class CropPreset(val label: String, /** Pixel width/height; null = free, -1 = original picture. */ val ratio: Float?)

private val CropPresets = listOf(
    CropPreset("Free", null),
    CropPreset("Original", -1f),
    CropPreset("Square", 1f),
    CropPreset("3:2", 3f / 2f),
    CropPreset("2:3", 2f / 3f),
    CropPreset("4:3", 4f / 3f),
    CropPreset("3:4", 3f / 4f),
    CropPreset("16:9", 16f / 9f),
    CropPreset("9:16", 9f / 16f),
)

private val BrushColors = listOf(
    Color.White, Color.Black, Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A),
    Color(0xFF30D158), Color(0xFF64D2FF), Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF375F),
)

/** Brush widths as fractions of the picture's longer side. */
private val BrushSizes = listOf(0.006f, 0.012f, 0.024f)

private const val MinCrop = 0.08f
private const val DefaultTextSize = 0.07f
private const val PreviewMaxPx = 2048

/**
 * Telegram iOS-style photo/video editor shown before sending from the attachment panel: pages through [items],
 * top bar with close / counter / selection circle, bottom glass tool bar (Crop & Rotate, Draw, Text, Adjust, HD),
 * caption field and Send. Edits live in [edits] (per item Uri) so the panel's own Send uses them too; [onSend]
 * gets the item on screen (the panel sends the selection, or that item when nothing is selected).
 *
 * Glass lives only in overlays outside the recorded pager layer (rule 2).
 */
@Composable
fun MediaEditor(
    visible: Boolean,
    items: List<GalleryItem>,
    startIndex: Int,
    selected: List<GalleryItem>,
    onToggleSelect: (GalleryItem) -> Unit,
    edits: SnapshotStateMap<Uri, PhotoEdits>,
    caption: String,
    onCaption: (String) -> Unit,
    hd: Boolean,
    onHd: (Boolean) -> Unit,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (current: GalleryItem) -> Unit,
) {
    AnimatedVisibility(
        visible = visible && items.isNotEmpty(),
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.94f),
        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f),
    ) {
        if (items.isNotEmpty()) {
            ProvideDarkColors {
                EditorContent(items, startIndex, selected, onToggleSelect, edits, caption, onCaption, hd, onHd, sending, onDismiss, onSend)
            }
        }
    }
}

@Composable
private fun EditorContent(
    items: List<GalleryItem>,
    startIndex: Int,
    selected: List<GalleryItem>,
    onToggleSelect: (GalleryItem) -> Unit,
    edits: SnapshotStateMap<Uri, PhotoEdits>,
    caption: String,
    onCaption: (String) -> Unit,
    hd: Boolean,
    onHd: (Boolean) -> Unit,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (GalleryItem) -> Unit,
) {
    val c = TgTheme.colors
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
    var tool by remember { mutableStateOf(EditorTool.None) }
    var backup by remember { mutableStateOf<EditSpec?>(null) }
    var brushColor by remember { mutableStateOf(BrushColors[2]) }
    var brushSize by remember { mutableIntStateOf(1) }
    var adjustKind by remember { mutableStateOf(AdjustKind.Brightness) }
    var cropRatio by remember { mutableStateOf<Float?>(null) }
    var textDraft by remember { mutableStateOf<EditText?>(null) }

    fun editsFor(item: GalleryItem): PhotoEdits = edits[item.uri] ?: PhotoEdits().also { edits[item.uri] = it }

    fun startTool(t: EditorTool) {
        val e = editsFor(current)
        backup = e.spec()
        cropRatio = null
        tool = t
    }

    fun cancelTool() {
        backup?.let { editsFor(current).restore(it) }
        backup = null
        tool = EditorTool.None
    }

    fun doneTool() {
        backup = null
        tool = EditorTool.None
    }

    fun newText() {
        val e = editsFor(current)
        val crop = e.crop
        textDraft = EditText(
            id = System.nanoTime(),
            text = "",
            color = Color.White,
            filled = false,
            center = crop.center,
            size = DefaultTextSize,
        )
    }

    fun finishText() {
        val d = textDraft ?: return
        textDraft = null
        val e = editsFor(current)
        val exists = e.texts.any { it.id == d.id }
        when {
            d.text.isBlank() -> e.texts.removeAll { it.id == d.id }
            exists -> e.updateText(d.id) { it.copy(text = d.text, color = d.color, filled = d.filled) }
            else -> e.texts.add(d)
        }
    }

    BackHandler {
        when {
            textDraft != null -> textDraft = null
            tool != EditorTool.None -> cancelTool()
            else -> onDismiss()
        }
    }
    LaunchedEffect(current.uri) {
        // Paging away ends any open tool (keeps what was done).
        if (tool != EditorTool.None) doneTool()
    }

    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            // Recorded for the glass bars: nothing below draws glass.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                CompositionLocalProvider(LocalBackdrop provides null) {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 12.dp,
                        userScrollEnabled = tool == EditorTool.None && textDraft == null,
                        key = { i -> items.getOrNull(i)?.uri?.toString() ?: "page$i" },
                    ) { i ->
                        val item = items.getOrNull(i) ?: return@HorizontalPager
                        val isCurrent = item.uri == current.uri
                        val pageTool = if (isCurrent) tool else EditorTool.None
                        Box(
                            Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(top = 60.dp, bottom = if (pageTool == EditorTool.None) 124.dp else 196.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (item.video) {
                                EditorVideo(item, isCurrent)
                            } else {
                                EditorPhoto(
                                    uri = item.uri,
                                    edits = edits[item.uri],
                                    tool = pageTool,
                                    brushColor = brushColor,
                                    brushWidth = BrushSizes[brushSize],
                                    cropRatio = if (isCurrent) cropRatio else null,
                                    editingTextId = if (isCurrent) textDraft?.id else null,
                                    ensureEdits = { editsFor(item) },
                                    onTextTap = { t -> if (tool == EditorTool.None) textDraft = t },
                                )
                            }
                        }
                    }
                }
            }

            // ---- Top bar ----
            if (tool == EditorTool.None && textDraft == null) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(IosIcons.Close, onDismiss, iconSize = 18.dp, tint = Color.White)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (items.size > 1) {
                            GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.height(36.dp)) {
                                T(
                                    "${pager.currentPage + 1} of ${items.size}",
                                    TgTheme.type.subheadline, Color.White, weight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                    val index = selected.indexOfFirst { it.uri == current.uri }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clickable(remember { MutableInteractionSource() }, null) { onToggleSelect(current) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (index >= 0) c.accent else Color.Black.copy(0.25f))
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (index >= 0) T("${index + 1}", TgTheme.type.subheadline, Color.White, weight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ---- Bottom ----
            val currentEdits = edits[current.uri]
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                when (tool) {
                    EditorTool.None -> if (textDraft == null) {
                        if (!current.video) {
                            GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.fillMaxWidth().height(54.dp)) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val e = currentEdits
                                    ToolButton(MediaIcons.Crop, active = e != null && (e.crop != FullCrop || e.quarterTurns != 0)) { startTool(EditorTool.Crop) }
                                    ToolButton(MediaIcons.Brush, active = e != null && e.strokes.isNotEmpty()) { startTool(EditorTool.Draw) }
                                    ToolButton(MediaIcons.Text, active = e != null && e.texts.isNotEmpty()) { newText() }
                                    ToolButton(
                                        MediaIcons.Adjust,
                                        active = e != null && (e.brightness != 0f || e.contrast != 0f || e.saturation != 0f || e.warmth != 0f),
                                    ) { startTool(EditorTool.Adjust) }
                                    HdButton(hd) { onHd(!hd) }
                                }
                            }
                        } else {
                            GlassBox(onClick = null, shape = Capsule(), modifier = Modifier.fillMaxWidth().height(44.dp)) {
                                T(
                                    if (current.durationMs > 0) "Video · ${formatDuration((current.durationMs / 1000).toInt())}" else "Video",
                                    TgTheme.type.subheadline, Color.White.copy(0.85f), weight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            GlassBox(
                                onClick = null,
                                shape = RoundedRectangle(22.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp)) {
                                    if (caption.isEmpty()) T("Add a caption…", TgTheme.type.body, Color.White.copy(0.5f))
                                    BasicTextField(
                                        value = caption,
                                        onValueChange = onCaption,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = TgTheme.type.body.copy(color = Color.White, textDirection = TextDirection.Content),
                                        cursorBrush = SolidColor(c.accent),
                                        maxLines = 5,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(c.accent).bounceClickable { if (!sending) onSend(current) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (sending) ActivityIndicator(20.dp, Color.White)
                                else Icon(IosIcons.ArrowUp, Color.White, 22.dp)
                            }
                        }
                        if (selected.size > 1) {
                            T(
                                "${selected.size} items will be sent",
                                TgTheme.type.caption1, Color.White.copy(0.6f),
                                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                            )
                        }
                    }
                    EditorTool.Crop -> ToolPanel("Crop", onCancel = { cancelTool() }, onDone = { doneTool() }) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CropPresets.forEach { p ->
                                Chip(p.label, selected = cropRatio == p.ratio) { cropRatio = p.ratio }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ToolButton(MediaIcons.Rotate, active = false) {
                                editsFor(current).rotateLeft()
                                cropRatio = null
                            }
                            Spacer(Modifier.weight(1f))
                            T(
                                "Reset", TgTheme.type.body, Color.White, weight = FontWeight.Medium,
                                modifier = Modifier.fadeClickable {
                                    val e = editsFor(current)
                                    while (e.quarterTurns != 0) e.rotateLeft()
                                    e.crop = FullCrop
                                    cropRatio = null
                                }.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                    EditorTool.Draw -> ToolPanel("Draw", onCancel = { cancelTool() }, onDone = { doneTool() }) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BrushColors.forEach { col ->
                                val on = col == brushColor
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .border(if (on) 3.dp else 1.5.dp, Color.White, CircleShape)
                                        .padding(if (on) 5.dp else 2.dp)
                                        .clip(CircleShape)
                                        .background(col)
                                        .clickable(remember { MutableInteractionSource() }, null) { brushColor = col },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BrushSizes.forEachIndexed { i, _ ->
                                val on = i == brushSize
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape)
                                        .background(if (on) Color.White.copy(0.2f) else Color.Transparent)
                                        .clickable(remember { MutableInteractionSource() }, null) { brushSize = i },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.size((6 + i * 6).dp).clip(CircleShape).background(brushColor))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            val canUndo = currentEdits?.strokes?.isNotEmpty() == true
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).fadeClickable(enabled = canUndo) {
                                    currentEdits?.strokes?.let { s -> if (s.isNotEmpty()) s.removeAt(s.lastIndex) }
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(MediaIcons.Undo, Color.White.copy(if (canUndo) 1f else 0.35f), 24.dp)
                            }
                        }
                    }
                    EditorTool.Adjust -> ToolPanel(adjustKind.title, onCancel = { cancelTool() }, onDone = { doneTool() }) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AdjustKind.entries.forEach { k -> Chip(k.title, selected = k == adjustKind) { adjustKind = k } }
                        }
                        Spacer(Modifier.height(6.dp))
                        val e = currentEdits
                        if (e != null) {
                            val value = when (adjustKind) {
                                AdjustKind.Brightness -> e.brightness
                                AdjustKind.Contrast -> e.contrast
                                AdjustKind.Saturation -> e.saturation
                                AdjustKind.Warmth -> e.warmth
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    IOSSlider(
                                        value = value,
                                        onValueChange = { v ->
                                            val snapped = if (kotlin.math.abs(v) < 0.04f) 0f else v
                                            when (adjustKind) {
                                                AdjustKind.Brightness -> e.brightness = snapped
                                                AdjustKind.Contrast -> e.contrast = snapped
                                                AdjustKind.Saturation -> e.saturation = snapped
                                                AdjustKind.Warmth -> e.warmth = snapped
                                            }
                                        },
                                        range = -1f..1f,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                T(
                                    (value * 100).roundToInt().let { if (it > 0) "+$it" else "$it" },
                                    TgTheme.type.subheadline, Color.White, weight = FontWeight.SemiBold,
                                    modifier = Modifier.widthIn(min = 40.dp),
                                    align = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }

            // ---- Text entry ----
            textDraft?.let { draft ->
                TextEntryOverlay(
                    draft = draft,
                    onChange = { textDraft = it },
                    onCancel = { textDraft = null },
                    onDone = { finishText() },
                )
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).fadeClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, if (active) TgTheme.colors.accent else Color.White, 25.dp)
    }
}

@Composable
private fun HdButton(on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).fadeClickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(RoundedCornerShape(5.dp))
                .then(if (on) Modifier.background(Color.White) else Modifier.border(1.8.dp, Color.White, RoundedCornerShape(5.dp)))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            T("HD", TgTheme.type.footnote.copy(fontSize = 12.sp), if (on) Color.Black else Color.White, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(Capsule())
            .background(if (selected) Color.White else Color.White.copy(0.12f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(label, TgTheme.type.subheadline, if (selected) Color.Black else Color.White, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Glass panel of an open tool: its controls, then Cancel · title · Done. */
@Composable
private fun ToolPanel(title: String, onCancel: () -> Unit, onDone: () -> Unit, content: @Composable () -> Unit) {
    GlassBox(onClick = null, shape = RoundedRectangle(28.dp), modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
            content()
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Cancel", TgTheme.type.body, Color.White, modifier = Modifier.fadeClickable(onClick = onCancel).padding(horizontal = 12.dp, vertical = 10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    T(title, TgTheme.type.headline, Color.White, maxLines = 1)
                }
                T(
                    "Done", TgTheme.type.body, TgTheme.colors.accent, weight = FontWeight.SemiBold,
                    modifier = Modifier.fadeClickable(onClick = onDone).padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Geometry of the picture on screen, kept out of snapshot state for gesture code. */
private class PhotoGeometry {
    var fx = 0f
    var fy = 0f
    var fullW = 1f
    var fullH = 1f
    fun toNorm(p: Offset) = Offset((p.x - fx) / fullW, (p.y - fy) / fullH)
}

/** One photo page: the picture with rotation, crop, adjustments, drawings and text; crop handles in Crop mode. */
@Composable
private fun EditorPhoto(
    uri: Uri,
    edits: PhotoEdits?,
    tool: EditorTool,
    brushColor: Color,
    brushWidth: Float,
    cropRatio: Float?,
    editingTextId: Long?,
    ensureEdits: () -> PhotoEdits,
    onTextTap: (EditText) -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodeUpright(context, uri, PreviewMaxPx)?.asImageBitmap() }
    }
    val bmp = bitmap
    if (bmp == null) {
        ActivityIndicator(28.dp, Color.White)
        return
    }
    val density = LocalDensity.current
    val turns = edits?.quarterTurns ?: 0
    val rotW = (if (turns % 2 == 1) bmp.height else bmp.width).toFloat().coerceAtLeast(1f)
    val rotH = (if (turns % 2 == 1) bmp.width else bmp.height).toFloat().coerceAtLeast(1f)

    // Crop presets: fit the largest rectangle of that ratio around the current crop's center.
    LaunchedEffect(cropRatio) {
        val r = cropRatio ?: return@LaunchedEffect
        val e = ensureEdits()
        val rn = if (r < 0f) 1f else r * rotH / rotW
        var w = 1f
        var h = w / rn
        if (h > 1f) { h = 1f; w = rn }
        val cc = e.crop.center
        val x0 = (cc.x - w / 2f).coerceIn(0f, 1f - w)
        val y0 = (cc.y - h / 2f).coerceIn(0f, 1f - h)
        e.crop = Rect(x0, y0, x0 + w, y0 + h)
    }

    val filter = if (edits != null && (edits.brightness != 0f || edits.contrast != 0f || edits.saturation != 0f || edits.warmth != 0f)) {
        ColorFilter.colorMatrix(ColorMatrix(adjustMatrix(edits.brightness, edits.contrast, edits.saturation, edits.warmth)))
    } else null

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val pad = if (tool == EditorTool.Crop) with(density) { 22.dp.toPx() } else 0f
        val availW = (constraints.maxWidth - 2 * pad).coerceAtLeast(1f)
        val availH = (constraints.maxHeight - 2 * pad).coerceAtLeast(1f)
        val crop = if (tool == EditorTool.Crop) FullCrop else edits?.crop ?: FullCrop
        val aspect = (crop.width * rotW) / (crop.height * rotH).coerceAtLeast(0.0001f)
        val dispW = if (availW / availH > aspect) availH * aspect else availW
        val dispH = dispW / aspect
        val fullW = dispW / crop.width
        val fullH = dispH / crop.height
        val fx = -crop.left * fullW
        val fy = -crop.top * fullH
        val geo = remember { PhotoGeometry() }
        geo.fx = fx; geo.fy = fy; geo.fullW = fullW; geo.fullH = fullH
        val live = remember { mutableStateListOf<Offset>() }
        val color by rememberUpdatedState(brushColor)
        val width by rememberUpdatedState(brushWidth)
        val ensure by rememberUpdatedState(ensureEdits)
        val boxSize = Modifier.size(with(density) { dispW.toDp() }, with(density) { dispH.toDp() })

        Box(boxSize.clipToBounds()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (tool == EditorTool.Draw) Modifier.pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                live.clear()
                                live.add(geo.toNorm(down.position))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    live.add(geo.toNorm(change.position))
                                }
                                if (live.isNotEmpty()) ensure().strokes.add(EditStroke(live.toList(), color, width))
                                live.clear()
                            }
                        } else Modifier,
                    ),
            ) {
                val bw = if (turns % 2 == 1) fullH else fullW
                val bh = if (turns % 2 == 1) fullW else fullH
                withTransform({
                    translate(fx + fullW / 2f, fy + fullH / 2f)
                    rotate(-90f * turns, Offset.Zero)
                }) {
                    drawImage(
                        image = bmp,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset((-bw / 2f).roundToInt(), (-bh / 2f).roundToInt()),
                        dstSize = IntSize(bw.roundToInt().coerceAtLeast(1), bh.roundToInt().coerceAtLeast(1)),
                        colorFilter = filter,
                    )
                }
                val long = max(fullW, fullH)
                edits?.strokes?.forEach { s -> drawStroke(s.points, s.color, s.width * long, fx, fy, fullW, fullH) }
                if (live.isNotEmpty()) drawStroke(live, color, width * long, fx, fy, fullW, fullH)
            }
            if (tool != EditorTool.Crop && edits != null) {
                edits.texts.forEach { t ->
                    if (t.id != editingTextId) key(t.id) {
                        EditorTextItem(
                            t = t,
                            fx = fx, fy = fy, fullW = fullW, fullH = fullH,
                            interactive = tool == EditorTool.None,
                            onMove = { d -> edits.updateText(t.id) { it.copy(center = it.center + Offset(d.x / geo.fullW, d.y / geo.fullH)) } },
                            onScale = { z -> edits.updateText(t.id) { it.copy(size = (it.size * z).coerceIn(0.02f, 0.3f)) } },
                            onTap = { onTextTap(t) },
                        )
                    }
                }
            }
        }
        if (tool == EditorTool.Crop && edits != null) {
            CropOverlay(edits, boxSize, if (cropRatio == null) null else if (cropRatio < 0f) 1f else cropRatio * rotH / rotW)
        }
    }
}

private fun DrawScope.drawStroke(points: List<Offset>, color: Color, widthPx: Float, fx: Float, fy: Float, fullW: Float, fullH: Float) {
    if (points.isEmpty()) return
    fun map(p: Offset) = Offset(fx + p.x * fullW, fy + p.y * fullH)
    if (points.size == 1) {
        drawCircle(color, widthPx / 2f, map(points[0]))
        return
    }
    val path = Path()
    val first = map(points[0])
    path.moveTo(first.x, first.y)
    for (i in 1 until points.size) {
        val p = map(points[i])
        path.lineTo(p.x, p.y)
    }
    drawPath(path, color, style = Stroke(width = widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A text overlay: drag to move, pinch to resize, tap to edit. Rendered the same way the saved photo draws it. */
@Composable
private fun EditorTextItem(
    t: EditText,
    fx: Float,
    fy: Float,
    fullW: Float,
    fullH: Float,
    interactive: Boolean,
    onMove: (Offset) -> Unit,
    onScale: (Float) -> Unit,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val move by rememberUpdatedState(onMove)
    val scale by rememberUpdatedState(onScale)
    val tap by rememberUpdatedState(onTap)
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val fontPx = t.size * max(fullW, fullH)
    val cx = fx + t.center.x * fullW
    val cy = fy + t.center.y * fullH
    val corner = with(density) { (fontPx * 0.3f).toDp() }
    Box(
        Modifier
            .offset { IntOffset((cx - measured.width / 2f).roundToInt(), (cy - measured.height / 2f).roundToInt()) }
            .wrapContentSize(align = Alignment.TopStart, unbounded = true)
            .onSizeChanged { measured = it }
            .then(
                if (interactive) Modifier
                    .pointerInput(t.id) { detectTapGestures(onTap = { tap() }) }
                    .pointerInput(t.id) { detectTransformGestures { _, pan, zoom, _ -> move(pan); if (zoom != 1f) scale(zoom) } }
                else Modifier,
            )
            .then(if (t.filled) Modifier.background(t.color, RoundedCornerShape(corner)) else Modifier)
            .padding(horizontal = with(density) { (fontPx * 0.3f).toDp() }, vertical = with(density) { (fontPx * 0.15f).toDp() }),
    ) {
        BasicText(
            text = t.text,
            style = TextStyle(
                color = if (t.filled) filledTextColor(t.color) else t.color,
                fontSize = with(density) { fontPx.toSp() },
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                textDirection = TextDirection.Content,
                shadow = if (t.filled) null else Shadow(Color.Black.copy(0.4f), Offset(0f, fontPx * 0.03f), fontPx * 0.08f),
            ),
            softWrap = false,
        )
    }
}

/** Crop rectangle with dimmed outside, thirds grid and corner handles; drag corners to resize, inside to move. */
@Composable
private fun CropOverlay(edits: PhotoEdits, sizeModifier: Modifier, ratioNorm: Float?) {
    val ratio by rememberUpdatedState(ratioNorm)
    Canvas(
        sizeModifier.pointerInput(edits) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                val r0 = edits.crop
                val corners = listOf(
                    Offset(r0.left * w, r0.top * h),
                    Offset(r0.right * w, r0.top * h),
                    Offset(r0.right * w, r0.bottom * h),
                    Offset(r0.left * w, r0.bottom * h),
                )
                val nearest = corners.indices.minByOrNull { (corners[it] - down.position).getDistance() } ?: 0
                val hit = nearest.takeIf { (corners[it] - down.position).getDistance() < 44.dp.toPx() }
                val inside = r0.contains(Offset(down.position.x / w, down.position.y / h))
                if (hit == null && !inside) return@awaitEachGesture
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    val p = Offset((change.position.x / w).coerceIn(0f, 1f), (change.position.y / h).coerceIn(0f, 1f))
                    edits.crop = if (hit != null) {
                        resizeCrop(r0, hit, p, ratio)
                    } else {
                        val dx = (change.position.x - down.position.x) / w
                        val dy = (change.position.y - down.position.y) / h
                        val x0 = (r0.left + dx).coerceIn(0f, 1f - r0.width)
                        val y0 = (r0.top + dy).coerceIn(0f, 1f - r0.height)
                        Rect(x0, y0, x0 + r0.width, y0 + r0.height)
                    }
                }
            }
        },
    ) {
        val r = edits.crop
        val l = r.left * size.width
        val t = r.top * size.height
        val rr = r.right * size.width
        val b = r.bottom * size.height
        val dim = Color.Black.copy(0.6f)
        drawRect(dim, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, t))
        drawRect(dim, Offset(0f, b), androidx.compose.ui.geometry.Size(size.width, size.height - b))
        drawRect(dim, Offset(0f, t), androidx.compose.ui.geometry.Size(l, b - t))
        drawRect(dim, Offset(rr, t), androidx.compose.ui.geometry.Size(size.width - rr, b - t))
        val line = 1.dp.toPx()
        drawRect(Color.White, Offset(l, t), androidx.compose.ui.geometry.Size(rr - l, b - t), style = Stroke(line))
        val grid = Color.White.copy(0.45f)
        for (i in 1..2) {
            val x = l + (rr - l) * i / 3f
            val y = t + (b - t) * i / 3f
            drawLine(grid, Offset(x, t), Offset(x, b), strokeWidth = line * 0.7f)
            drawLine(grid, Offset(l, y), Offset(rr, y), strokeWidth = line * 0.7f)
        }
        val arm = 20.dp.toPx()
        val thick = 3.dp.toPx()
        val o = thick / 2f
        fun corner(x: Float, y: Float, sx: Float, sy: Float) {
            drawLine(Color.White, Offset(x - sx * o, y - sy * o), Offset(x + sx * arm, y - sy * o), strokeWidth = thick)
            drawLine(Color.White, Offset(x - sx * o, y - sy * o), Offset(x - sx * o, y + sy * arm), strokeWidth = thick)
        }
        corner(l, t, 1f, 1f)
        corner(rr, t, -1f, 1f)
        corner(rr, b, -1f, -1f)
        corner(l, b, 1f, -1f)
    }
}

/** New crop when corner [corner] (0 TL, 1 TR, 2 BR, 3 BL) is dragged to [p]; keeps [ratioNorm] (w/h in normalized units) if set. */
private fun resizeCrop(r0: Rect, corner: Int, p: Offset, ratioNorm: Float?): Rect {
    val anchor = when (corner) {
        0 -> Offset(r0.right, r0.bottom)
        1 -> Offset(r0.left, r0.bottom)
        2 -> Offset(r0.left, r0.top)
        else -> Offset(r0.right, r0.top)
    }
    val sx = if (corner == 0 || corner == 3) -1f else 1f
    val sy = if (corner == 0 || corner == 1) -1f else 1f
    val maxW = if (sx > 0) 1f - anchor.x else anchor.x
    val maxH = if (sy > 0) 1f - anchor.y else anchor.y
    var w = ((p.x - anchor.x) * sx).coerceIn(MinCrop.coerceAtMost(maxW), maxW.coerceAtLeast(MinCrop.coerceAtMost(maxW)))
    var h = ((p.y - anchor.y) * sy).coerceIn(MinCrop.coerceAtMost(maxH), maxH.coerceAtLeast(MinCrop.coerceAtMost(maxH)))
    if (ratioNorm != null && ratioNorm > 0f) {
        if (w / h > ratioNorm) h = w / ratioNorm else w = h * ratioNorm
        if (w > maxW) { w = maxW; h = w / ratioNorm }
        if (h > maxH) { h = maxH; w = h * ratioNorm }
    }
    val x0 = if (sx > 0) anchor.x else anchor.x - w
    val y0 = if (sy > 0) anchor.y else anchor.y - h
    return Rect(x0, y0, x0 + w, y0 + h)
}

/** Full-screen text entry (dimmed picture, big centered text, colors and the filled-box style). */
@Composable
private fun TextEntryOverlay(draft: EditText, onChange: (EditText) -> Unit, onCancel: () -> Unit, onDone: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(draft.id) { runCatching { focus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f))
            .clickable(remember { MutableInteractionSource() }, null) { onDone() },
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTextButton("Cancel", onCancel, color = Color.White)
            Spacer(Modifier.weight(1f))
            GlassTextButton("Done", onDone, color = TgTheme.colors.accent, bold = true)
        }
        Box(
            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = draft.text,
                onValueChange = { onChange(draft.copy(text = it.take(300))) },
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .focusRequester(focus)
                    .then(if (draft.filled) Modifier.background(draft.color, RoundedCornerShape(10.dp)) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                textStyle = TextStyle(
                    color = if (draft.filled) filledTextColor(draft.color) else draft.color,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    textDirection = TextDirection.Content,
                ),
                cursorBrush = SolidColor(if (draft.filled) filledTextColor(draft.color) else draft.color),
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .then(if (draft.filled) Modifier.background(Color.White) else Modifier.border(2.dp, Color.White, RoundedCornerShape(9.dp)))
                    .clickable(remember { MutableInteractionSource() }, null) { onChange(draft.copy(filled = !draft.filled)) },
                contentAlignment = Alignment.Center,
            ) {
                T("A", TgTheme.type.headline, if (draft.filled) Color.Black else Color.White, weight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrushColors.forEach { col ->
                    val on = col == draft.color
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(if (on) 3.dp else 1.5.dp, Color.White, CircleShape)
                            .padding(if (on) 5.dp else 2.dp)
                            .clip(CircleShape)
                            .background(col)
                            .clickable(remember { MutableInteractionSource() }, null) { onChange(draft.copy(color = col)) },
                    )
                }
            }
        }
    }
}

/** A video page: plays (looping, tap to pause) on the current page, thumbnail elsewhere. No glass (inside the layer). */
@Composable
private fun EditorVideo(item: GalleryItem, isCurrent: Boolean) {
    val density = LocalDensity.current
    val thumb = rememberGalleryThumb(item, 720)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val areaW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val areaH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        if (isCurrent) {
            val player = key(item.uri) { rememberVideoPlayer(item.uri, loop = true) }
            val state = rememberVideoState(player)
            val aspect = state.videoAspect.takeIf { it > 0f }
                ?: thumb?.let { it.width.toFloat() / it.height.coerceAtLeast(1) }
                ?: (16f / 9f)
            val w = if (areaW / areaH > aspect) areaH * aspect else areaW
            val h = w / aspect
            Box(
                Modifier
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .clickable(remember { MutableInteractionSource() }, null) { player.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                VideoSurface(player, Modifier.fillMaxSize())
                if (!state.firstFrame && thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (!state.playing) {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
                        Icon(IosIcons.Play, Color.White, 28.dp)
                    }
                }
            }
        } else if (thumb != null) {
            Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}
