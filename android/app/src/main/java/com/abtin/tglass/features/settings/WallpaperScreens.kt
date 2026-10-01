package com.abtin.tglass.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.ColorTheme
import com.abtin.tglass.core.design.AppSettings
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.LocalThemeState
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.design.UserBackground
import com.abtin.tglass.core.design.buildColors
import com.abtin.tglass.core.design.hsb
import com.abtin.tglass.core.design.hsbColor
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.features.chat.WallpaperStore
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.IOSSlider
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.fadeClickable

/** The background being edited on the Chat Background editor page (set by the gallery page before it pushes the editor). */
internal object WallpaperDraft {
    /** Which side is edited: false = day themes, true = night themes. */
    var night by mutableStateOf(false)
    var draft by mutableStateOf(UserBackground())

    fun open(night: Boolean, draft: UserBackground) {
        this.night = night
        this.draft = draft
    }
}

/** Drops the custom background of one side and deletes photos nothing refers to any more. */
internal fun clearCustomBackground(context: android.content.Context, s: AppSettings, night: Boolean) {
    s.updateBackground(night, UserBackground())
    pruneWallpaperFiles(context, s)
}

/** Deletes wallpaper photos not used by the day or night background (off the main thread), plus [extraKeep]. */
internal fun pruneWallpaperFiles(context: android.content.Context, s: AppSettings, extraKeep: String? = null) {
    val keep = listOf(s.bgDay.photo, s.bgNight.photo, extraKeep)
    val app = context.applicationContext
    Thread { WallpaperStore.prune(app, keep) }.start()
}

/** Settings -> Appearance -> Chat Background -> editor: preview with sample bubbles, options, Set / Cancel. */
internal fun LazyListScope.wallpaperEditorPage() {
    item {
        val s = LocalAppSettings.current
        val state = LocalThemeState.current
        val nav = LocalNavigator.current
        val context = LocalContext.current
        val c = TgTheme.colors
        val night = WallpaperDraft.night
        val d = WallpaperDraft.draft
        val theme = if (night) (if (state.active.dark) state.active else s.nightTheme) else (if (!state.active.dark) state.active else ColorTheme.Classic)
        val spec = s.themeSpec(theme)
        val colors = remember(spec) { buildColors(spec) }

        Section { Box(Modifier.padding(10.dp)) { ChatPreview(colors = colors, minHeight = 340, background = d) } }
        Spacer(Modifier.height(24.dp))

        if (d.photo != null) {
            Section(
                header = "Photo",
                footer = "Blurred softens the photo like in Telegram for iPhone. Dimming darkens it so messages stay easy to read. Motion (parallax) is not available.",
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Blurred", d.blur) { WallpaperDraft.draft = d.copy(blur = !d.blur) }
                }
                DimRow(d.dim) { WallpaperDraft.draft = d.copy(dim = it) }
            }
        } else {
            Section(header = "Color", footer = "Add a second color for a gradient and rotate it. Dimming darkens the background.") {
                var slot by remember { mutableIntStateOf(0) }
                val index = slot.coerceIn(0, (d.colors.size - 1).coerceAtLeast(0))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    d.colors.forEachIndexed { i, col ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(col))
                                .border(if (i == index) 3.dp else 0.5.dp, if (i == index) c.accent else c.separator, CircleShape)
                                .fadeClickable { slot = i },
                        )
                    }
                    if (d.colors.size == 1) Pill("Add Color", false) {
                        val (h, sa, br) = Color(d.colors[0]).hsb().let { Triple(it[0], it[1], it[2]) }
                        WallpaperDraft.draft = d.copy(colors = d.colors + hsbColor((h + 0.12f) % 1f, sa, br).toArgb())
                        slot = 1
                    }
                    if (d.colors.size == 2) {
                        Pill("Rotate", false) { WallpaperDraft.draft = d.copy(rotation = (d.rotation + 45) % 360) }
                        Pill("Remove", false) {
                            WallpaperDraft.draft = d.copy(colors = listOf(d.colors[0]))
                            slot = 0
                        }
                    }
                }
                if (d.colors.isNotEmpty()) {
                    ColorEditor(key = index, color = d.colors[index]) { col ->
                        WallpaperDraft.draft = d.copy(colors = d.colors.toMutableList().also { it[index] = col })
                    }
                }
                DimRow(d.dim) { WallpaperDraft.draft = d.copy(dim = it) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Section {
            Cell("Set Background", titleColor = c.accent, chevron = false, onClick = {
                s.updateBackground(night, WallpaperDraft.draft)
                pruneWallpaperFiles(context, s)
                nav.pop()
            })
            Cell("Cancel", chevron = false, divider = false, onClick = {
                pruneWallpaperFiles(context, s)
                nav.pop()
            })
        }
    }
}

@Composable
private fun DimRow(dim: Float, onChange: (Float) -> Unit) {
    val c = TgTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        T("Dimming", TgTheme.type.body, c.text)
        IOSSlider(dim, onChange, 0f..0.7f, Modifier.weight(1f).padding(start = 16.dp))
    }
}

/** iOS-style option capsule (Blurred, Motion, ...). */
@Composable
private fun Pill(label: String, on: Boolean, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (on) c.accent else c.searchField)
            .fadeClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(label, TgTheme.type.subheadline, if (on) Color.White else c.accent, weight = FontWeight.Medium, maxLines = 1)
    }
}

private val HueStops = List(7) { hsbColor(it / 6f, 1f, 1f) }

private fun hexOf(argb: Int) = String.format(java.util.Locale.US, "%06X", argb and 0xFFFFFF)

private fun parseHex(s: String): Int? =
    if (s.length == 6) s.toIntOrNull(16)?.let { 0xFF000000.toInt() or it } else null

/** Hue / saturation / brightness sliders and a hex field for one color. [key] resets the sliders when another color is picked. */
@Composable
private fun ColorEditor(key: Int, color: Int, onChange: (Int) -> Unit) {
    val c = TgTheme.colors
    var h by remember(key) { mutableFloatStateOf(Color(color).hsb()[0]) }
    var sa by remember(key) { mutableFloatStateOf(Color(color).hsb()[1]) }
    var br by remember(key) { mutableFloatStateOf(Color(color).hsb()[2]) }
    var hex by remember(key) { mutableStateOf(hexOf(color)) }
    LaunchedEffect(color) { if (parseHex(hex) != color) hex = hexOf(color) }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        ColorSlider(h, { h = it; onChange(hsbColor(h, sa, br).toArgb()) }, Brush.horizontalGradient(HueStops))
    }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        ColorSlider(sa, { sa = it; onChange(hsbColor(h, sa, br).toArgb()) }, Brush.horizontalGradient(listOf(hsbColor(h, 0f, br), hsbColor(h, 1f, br))))
    }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        ColorSlider(br, { br = it; onChange(hsbColor(h, sa, br).toArgb()) }, Brush.horizontalGradient(listOf(Color.Black, hsbColor(h, sa, 1f))))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        T("Hex", TgTheme.type.body, c.text)
        Spacer(Modifier.weight(1f))
        T("#", TgTheme.type.body, c.secondaryText)
        Spacer(Modifier.width(2.dp))
        BasicTextField(
            value = hex,
            onValueChange = { v ->
                val t = v.filter { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' }.take(6).uppercase()
                hex = t
                parseHex(t)?.let { argb ->
                    val hs = Color(argb).hsb()
                    h = hs[0]; sa = hs[1]; br = hs[2]
                    onChange(argb)
                }
            },
            singleLine = true,
            textStyle = TgTheme.type.body.copy(color = c.text),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier.width(84.dp),
        )
    }
}

/** A capsule track filled with [brush] and a round thumb; [value] and the callback are 0..1. */
@Composable
private fun ColorSlider(value: Float, onChange: (Float) -> Unit, brush: Brush) {
    val cb by rememberUpdatedState(onChange)
    Box(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(brush)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val r = size.height / 2f
                    cb(((p.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val r = size.height / 2f
                    cb(((change.position.x - r) / (size.width - 2 * r)).coerceIn(0f, 1f))
                }
            }
            .drawWithContent {
                drawContent()
                val r = size.height / 2f
                val center = Offset(r + (size.width - 2 * r) * value, r)
                drawCircle(Color.White, r - 2.dp.toPx(), center)
                drawCircle(Color.Black.copy(alpha = 0.25f), r - 2.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            },
    )
}
