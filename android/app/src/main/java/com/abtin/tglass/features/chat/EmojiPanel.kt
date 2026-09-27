package com.abtin.tglass.features.chat

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassBox
import com.abtin.tglass.data.GifItem
import com.abtin.tglass.data.StickerItem
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgImage
import com.abtin.tglass.ui.components.avatarColors
import com.abtin.tglass.ui.components.bounceClickable
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** SF Symbols–like glyphs of the composer and the emoji keyboard (24×24 grid, tint with [Icon]). */
internal object ComposerIcons {
    private fun glyph(name: String, vararg strokes: String, width: Float = 1.8f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        strokes.forEach { d ->
            b.addPath(
                addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private const val Circle = "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z"

    val Smiley = glyph("face.smiling", Circle, "M8.3 14.2c.9 1.4 2.2 2.1 3.7 2.1s2.8-.7 3.7-2.1", "M9.2 9.3v1.2", "M14.8 9.3v1.2", width = 1.7f)
    val Leaf = glyph("leaf", "M5 19C5 10.5 10.5 5 19 5c0 8.5-5.5 14-14 14z", "M5 19l8.5-8.5")
    val Cup = glyph("cup", "M5 10h11v4a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z", "M16 11h1.5a2.5 2.5 0 0 1 0 5H16", "M9 4.5V7", "M12.5 4.5V7")
    val Ball = glyph("ball", Circle, "M3.6 9.6c3 1.4 5 4.6 5.4 10.9", "M20.4 9.6c-3 1.4-5 4.6-5.4 10.9", "M6.8 4.7C8.3 6.4 10 7.3 12 7.3s3.7-.9 5.2-2.6")
    val Car = glyph(
        "car",
        "M4 16.5V13l1.8-4.7A2 2 0 0 1 7.7 7h8.6a2 2 0 0 1 1.9 1.3L20 13v3.5z",
        "M4 13h16", "M6.5 16.5v2", "M17.5 16.5v2", "M7.5 14.8h.01", "M16.5 14.8h.01",
    )
    val Bulb = glyph("bulb", "M9.5 17.5h5", "M10.5 20.5h3", "M12 3.5a5.8 5.8 0 0 0-3.4 10.5c.6.5.9 1.2.9 2v1.5h5V16c0-.8.3-1.5.9-2A5.8 5.8 0 0 0 12 3.5z")
    val Hash = glyph("number", "M10 4 8 20", "M16 4l-2 16", "M4.5 9h15", "M4 15h15")
    val Flag = glyph("flag", "M5.5 21V4", "M5.5 4.5h12l-2.5 4 2.5 4h-12")
    val Globe = glyph("globe", Circle, "M3 12h18", "M12 3c2.4 2.5 3.6 5.5 3.6 9s-1.2 6.5-3.6 9c-2.4-2.5-3.6-5.5-3.6-9S9.6 5.5 12 3z", width = 1.6f)
    val Backspace = glyph("delete.left", "M8.5 5.5h10a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-10L3 12z", "M11.5 9.5l5 5", "M16.5 9.5l-5 5", width = 1.7f)
    val Search = glyph("magnifyingglass", "M10.5 4a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13z", "M15.5 15.5 20 20", width = 2f)
    val Plus = glyph("plus", "M12 5v14", "M5 12h14", width = 2f)
    val Trash = glyph("trash", "M4.5 6.5h15", "M9.5 6.5V4.8c0-.7.5-1.3 1.2-1.3h2.6c.7 0 1.2.6 1.2 1.3v1.7", "M6.5 6.5l.9 12.2c.1 1 .9 1.8 1.9 1.8h5.4c1 0 1.8-.8 1.9-1.8l.9-12.2", "M10 10.5v6", "M14 10.5v6")
}

/**
 * Height of the software keyboard (above the navigation bar), remembered across launches, so the emoji panel takes
 * exactly the keyboard's place like on iOS.
 */
internal object KeyboardHeight {
    private const val Prefs = "tglass_composer"
    private const val Key = "keyboard_dp"
    const val DefaultDp = 300f

    var dp by mutableFloatStateOf(0f)
        private set

    fun load(context: Context) {
        if (dp > 0f) return
        dp = runCatching { context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getFloat(Key, 0f) }.getOrDefault(0f)
    }

    fun update(context: Context, value: Float) {
        if (abs(value - dp) < 1f) return
        dp = value
        runCatching { context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putFloat(Key, value).apply() }
    }
}

/** Watches the IME inset and stores the keyboard's height once it settles (call from the composer). */
@Composable
internal fun TrackKeyboardHeight() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val nav = WindowInsets.navigationBars
    LaunchedEffect(Unit) {
        KeyboardHeight.load(context)
        snapshotFlow { ime.getBottom(density) - nav.getBottom(density) }.collectLatest { px ->
            if (px <= 0) return@collectLatest
            delay(350) // wait for the show animation to end
            val d = px / density.density
            if (d in 150f..600f) KeyboardHeight.update(context, d)
        }
    }
}

private const val TabGifs = 0
private const val TabStickers = 1
private const val TabEmoji = 2

private val DemoStickers = listOf("🥳", "😎", "🤩", "😂", "😍", "🙏", "👍", "🔥", "💯", "🎉", "😭", "🤯", "🥰", "😴", "🤔", "👻", "🐱", "🐶", "🦊", "🐼", "🐸", "🦄", "🍕", "☕️")

/**
 * Emoji / Stickers / GIFs panel in place of the keyboard (Telegram-iOS 26): a strip of section icons on top, the
 * grid with section headers, and a floating bar at the bottom with the keyboard globe, the GIFs · Stickers · Emoji
 * switcher and backspace. Exactly as tall as the keyboard it replaces.
 */
@Composable
fun EmojiPanel(
    onEmoji: (String) -> Unit,
    onSticker: (String) -> Unit,
    onGif: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onStickerItem: (StickerItem) -> Unit = {},
    onGifItem: (GifItem) -> Unit = {},
    onBackspace: () -> Unit = {},
    onSwitchKeyboard: () -> Unit = {},
    chatId: Long = 0L,
) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        KeyboardHeight.load(context)
        repo.loadStickers()
    }
    var tab by rememberSaveable { mutableIntStateOf(TabEmoji) }
    val height = (KeyboardHeight.dp.takeIf { it > 0f } ?: KeyboardHeight.DefaultDp).dp
    GlassBox(
        onClick = null,
        shape = RoundedRectangle(26.dp),
        modifier = modifier.fillMaxWidth().height(height).padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (tab) {
            TabEmoji -> EmojiTab(onEmoji)
            TabStickers -> StickersTab(onSticker, onStickerItem)
            else -> GifsTab(chatId, onGif, onGifItem)
        }
        PanelBar(tab, { tab = it }, onSwitchKeyboard, onBackspace, Modifier.align(Alignment.BottomCenter))
    }
}

/** Room left under the grids for the floating bar. */
private val BarSpace = 60.dp

@Composable
private fun SectionHeader(title: String) {
    T(
        title,
        TgTheme.type.caption1.copy(fontSize = 12.sp, letterSpacing = 0.3.sp),
        TgTheme.colors.secondaryText,
        weight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 10.dp, bottom = 4.dp),
        align = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** Top strip entry: a glyph or custom content, highlighted with a soft capsule when its section is on screen. */
@Composable
private fun StripItem(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val c = TgTheme.colors
    val bg by animateColorAsState(if (selected) c.text.copy(alpha = 0.09f) else Color.Transparent, label = "strip")
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(bg).fadeClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Index of the section whose header is at or above the first visible grid item. */
private fun currentSection(grid: LazyGridState, headers: List<Int>): Int {
    val first = grid.firstVisibleItemIndex
    return headers.indexOfLast { it <= first }.coerceAtLeast(0)
}

@Composable
private fun EmojiTab(onEmoji: (String) -> Unit) {
    val c = TgTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Recents are shown as they were when the panel opened (the grid doesn't jump while tapping), but saved at once.
    val shownRecent = remember { RecentEmoji.load(context) }
    var savedRecent by remember { mutableStateOf(shownRecent) }
    val sections = remember(shownRecent) { listOf(EmojiCategory("recent", "FREQUENTLY USED", shownRecent)) + EmojiCategories }
    val headers = remember(sections) {
        var i = 0
        sections.map { s -> i.also { i += 1 + s.emoji.size } }
    }
    val grid = rememberLazyGridState()
    val current by remember(headers) { derivedStateOf { currentSection(grid, headers) } }
    val icons = listOf(IosIcons.Clock, ComposerIcons.Smiley, ComposerIcons.Leaf, ComposerIcons.Cup, ComposerIcons.Ball, ComposerIcons.Car, ComposerIcons.Bulb, ComposerIcons.Hash, ComposerIcons.Flag)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            sections.forEachIndexed { i, _ ->
                StripItem(current == i, { scope.launch { grid.scrollToItem(headers[i]) } }) {
                    Icon(icons.getOrElse(i) { ComposerIcons.Smiley }, if (current == i) c.text.copy(alpha = 0.75f) else c.secondaryText, 21.dp)
                }
            }
        }
        LazyVerticalGrid(
            GridCells.Fixed(8),
            state = grid,
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = BarSpace),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            sections.forEach { s ->
                item(key = "h:${s.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { SectionHeader(s.title) }
                items(s.emoji.size, key = { i -> "${s.key}:$i" }, contentType = { "emoji" }) { i ->
                    val e = s.emoji[i]
                    Box(
                        Modifier.aspectRatio(1f).bounceClickable {
                            savedRecent = RecentEmoji.push(context, savedRecent, e)
                            onEmoji(e)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        T(e, TgTheme.type.body.copy(fontSize = 29.sp, lineHeight = 34.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** One block of the stickers grid: recent stickers, an installed pack, or the demo set. */
private class StickerSection(val key: String, val title: String, val items: List<StickerItem>, val demo: List<String> = emptyList())

@Composable
private fun StickersTab(onDemoSticker: (String) -> Unit, onSticker: (StickerItem) -> Unit) {
    val c = TgTheme.colors
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val packs = repo.stickerPacks
    val recent = repo.recentStickers
    val sections = remember(packs.size, recent.size, repo.isLive) {
        if (!repo.isLive) listOf(StickerSection("demo", "STICKERS", emptyList(), DemoStickers))
        else buildList {
            if (recent.isNotEmpty()) add(StickerSection("recent", "RECENTLY USED", recent.toList()))
            packs.forEach { p -> if (p.stickers.isNotEmpty()) add(StickerSection("p${p.id}", p.title.uppercase(), p.stickers)) }
        }
    }
    val headers = remember(sections) {
        var i = 0
        sections.map { s -> i.also { i += 1 + maxOf(s.items.size, s.demo.size) } }
    }
    val grid = rememberLazyGridState()
    val current by remember(headers) { derivedStateOf { currentSection(grid, headers) } }
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(44.dp),
        ) {
            items(sections.size) { i ->
                val s = sections[i]
                StripItem(current == i, { scope.launch { grid.scrollToItem(headers[i]) } }) {
                    when {
                        s.key == "recent" -> Icon(IosIcons.Clock, if (current == i) c.text.copy(alpha = 0.75f) else c.secondaryText, 21.dp)
                        s.items.firstOrNull()?.image != null ->
                            TgImage(s.items.first().image, Modifier.size(26.dp), maxPx = 96, contentScale = ContentScale.Fit)
                        else -> T(s.items.firstOrNull()?.emoji ?: s.demo.firstOrNull() ?: "🙂", TgTheme.type.body.copy(fontSize = 20.sp, lineHeight = 24.sp))
                    }
                }
            }
        }
        if (sections.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = BarSpace), contentAlignment = Alignment.Center) {
                ActivityIndicator(24.dp)
            }
        } else LazyVerticalGrid(
            GridCells.Fixed(5),
            state = grid,
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = BarSpace),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            sections.forEach { s ->
                item(key = "h:${s.key}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { SectionHeader(s.title) }
                if (s.demo.isNotEmpty()) {
                    items(s.demo.size, key = { i -> "${s.key}:$i" }, contentType = { "sticker" }) { i ->
                        Box(Modifier.aspectRatio(1f).bounceClickable { onDemoSticker(s.demo[i]) }, contentAlignment = Alignment.Center) {
                            T(s.demo[i], TgTheme.type.body.copy(fontSize = 44.sp, lineHeight = 50.sp))
                        }
                    }
                } else {
                    items(s.items.size, key = { i -> "${s.key}:$i" }, contentType = { "sticker" }) { i ->
                        val st = s.items[i]
                        Box(Modifier.aspectRatio(1f).bounceClickable { onSticker(st) }.padding(4.dp), contentAlignment = Alignment.Center) {
                            if (st.image != null) TgImage(st.image, Modifier.fillMaxSize(), maxPx = 192, contentScale = ContentScale.Fit)
                            else T(st.emoji, TgTheme.type.body.copy(fontSize = 34.sp, lineHeight = 40.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GifsTab(chatId: Long, onDemoGif: (Int) -> Unit, onGif: (GifItem) -> Unit) {
    val c = TgTheme.colors
    val repo = LocalRepository.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GifItem>?>(null) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) {
            results = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(400)
        repo.searchGifs(chatId, q) { found ->
            if (query.trim() == q) {
                results = found
                searching = false
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        // Search field
        Row(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp).fillMaxWidth().height(36.dp)
                .clip(Capsule()).background(c.searchField).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(ComposerIcons.Search, c.secondaryText, 17.dp)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) T("Search GIFs", TgTheme.type.body, c.secondaryText, maxLines = 1)
                BasicTextField(
                    query, { query = it },
                    Modifier.fillMaxWidth(),
                    textStyle = TgTheme.type.body.copy(color = c.text, textDirection = TextDirection.Content),
                    singleLine = true,
                    cursorBrush = SolidColor(c.accent),
                )
            }
            if (query.isNotEmpty()) {
                Box(Modifier.size(24.dp).fadeClickable { query = "" }, contentAlignment = Alignment.Center) {
                    Icon(IosIcons.Close, c.secondaryText, 14.dp)
                }
            }
        }
        val shown = results ?: repo.savedGifs
        when {
            searching && results == null -> Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = BarSpace), contentAlignment = Alignment.Center) { ActivityIndicator(24.dp) }
            !repo.isLive && results == null -> LazyVerticalGrid(
                GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = BarSpace),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                items(12) { i ->
                    val (a, b) = avatarColors(i.toLong() + 3)
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedRectangle(10.dp)).background(Brush.linearGradient(listOf(a, b))).bounceClickable { onDemoGif(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        T(DemoStickers[i % DemoStickers.size], TgTheme.type.body.copy(fontSize = 36.sp, lineHeight = 42.sp))
                        GifBadge(Modifier.align(Alignment.BottomStart))
                    }
                }
            }
            shown.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f).padding(bottom = BarSpace), contentAlignment = Alignment.Center) {
                T(if (results != null) "No GIFs found" else "Saved GIFs will appear here", TgTheme.type.subheadline, c.secondaryText)
            }
            else -> {
                if (results == null) SectionHeader("SAVED GIFS")
                LazyVerticalGrid(
                    GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = BarSpace),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    items(shown.size) { i ->
                        val g = shown[i]
                        Box(Modifier.aspectRatio(1f).clip(RoundedRectangle(10.dp)).background(c.searchField).bounceClickable { onGif(g) }) {
                            TgImage(g.thumb, Modifier.fillMaxSize(), maxPx = 256)
                            GifBadge(Modifier.align(Alignment.BottomStart))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GifBadge(modifier: Modifier) {
    T(
        "GIF", TgTheme.type.caption2, Color.White, weight = FontWeight.Bold,
        modifier = modifier.padding(5.dp).clip(Capsule()).background(Color.Black.copy(0.35f)).padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** The floating bar at the bottom of the panel: globe · GIFs / Stickers / Emoji · backspace (Telegram-iOS 26). */
@Composable
private fun PanelBar(tab: Int, onTab: (Int) -> Unit, onSwitchKeyboard: () -> Unit, onBackspace: () -> Unit, modifier: Modifier) {
    val c = TgTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val surface = if (c.isDark) Color(0xFF3A3A3C).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f)
    val currentBackspace by rememberUpdatedState(onBackspace)
    Row(
        modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.1f), spotColor = Color.Black.copy(0.14f))
                .clip(CircleShape).background(surface).fadeClickable(onClick = onSwitchKeyboard),
            contentAlignment = Alignment.Center,
        ) { Icon(ComposerIcons.Globe, c.text.copy(alpha = 0.8f), 22.dp) }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.height(40.dp).shadow(6.dp, Capsule(), ambientColor = Color.Black.copy(0.1f), spotColor = Color.Black.copy(0.14f))
                .clip(Capsule()).background(surface).padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(TabGifs to "GIFs", TabStickers to "Stickers", TabEmoji to "Emoji").forEach { (t, label) ->
                val selected = tab == t
                val bg by animateColorAsState(if (selected) c.text.copy(alpha = 0.1f) else Color.Transparent, label = "panelTab")
                Box(
                    Modifier.height(34.dp).clip(Capsule()).background(bg)
                        .fadeClickable { if (!selected) { Haptics.tap(view); onTab(t) } }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    T(label, TgTheme.type.subheadline, if (selected) c.text else c.text.copy(alpha = 0.7f), weight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // Backspace repeats while held, like the keyboard's.
        Box(
            Modifier.size(40.dp).shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(0.1f), spotColor = Color.Black.copy(0.14f))
                .clip(CircleShape).background(surface)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        val job = scope.launch {
                            currentBackspace()
                            Haptics.tap(view)
                            delay(450)
                            while (isActive) {
                                currentBackspace()
                                delay(70)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    })
                },
            contentAlignment = Alignment.Center,
        ) { Image(ComposerIcons.Backspace, null, Modifier.size(22.dp), colorFilter = ColorFilter.tint(c.text.copy(alpha = 0.8f))) }
    }
}

/** [text] without its last user-perceived character (a whole emoji, flag or ZWJ sequence). */
internal fun dropLastGrapheme(text: String): String {
    if (text.isEmpty()) return text
    val it = android.icu.text.BreakIterator.getCharacterInstance()
    it.setText(text)
    it.last()
    val start = it.previous()
    return if (start == android.icu.text.BreakIterator.DONE) "" else text.substring(0, start)
}
