package com.abtin.tglass.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.R
import com.abtin.tglass.core.design.AccentPresets
import com.abtin.tglass.core.design.AutoNight
import com.abtin.tglass.core.design.ChatListSize
import com.abtin.tglass.core.design.ColorTheme
import com.abtin.tglass.core.design.DayWallpapers
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.LocalThemeState
import com.abtin.tglass.core.design.NightWallpapers
import com.abtin.tglass.core.design.ProvideColors
import com.abtin.tglass.core.design.ProvideTextScale
import com.abtin.tglass.core.design.TgColors
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.design.WallpaperOption
import com.abtin.tglass.core.design.buildColors
import com.abtin.tglass.core.design.themeAccent
import com.abtin.tglass.core.design.themeWallpaper
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.BubbleDemo
import com.abtin.tglass.data.NameColors
import com.abtin.tglass.features.chat.BubbleGroup
import com.abtin.tglass.features.chat.ChatWallpaper
import com.abtin.tglass.features.chat.MessageBubble
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.IOSSlider
import com.abtin.tglass.ui.components.IOSSwitch
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.SegmentedControl
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.shapes.RoundedRectangle
import java.util.Locale
import kotlin.math.roundToInt

// Settings → Appearance, in the order of Telegram-iOS ThemeSettingsController:
// COLOR THEME (chat preview, theme carousel, accent colors, Chat Wallpaper, Your Color) · Night Mode ·
// Text Size / Message Corners · Animations · APP ICON · OTHER · then TGlass's own extras.

private fun LazyListScope.appearanceGap() = item { Spacer(Modifier.height(24.dp)) }

/** Chat wallpaper with an incoming and an outgoing message, drawn with the current (or given) palette. */
@Composable
internal fun ChatPreview(
    modifier: Modifier = Modifier,
    scale: Float? = null,
    corners: Float? = null,
    colors: TgColors? = null,
    minHeight: Int = 170,
) {
    val s = LocalAppSettings.current
    val body: @Composable () -> Unit = {
        Box(modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(RoundedRectangle(26.dp))) {
            Box(Modifier.matchParentSize()) { ChatWallpaper() }
            ProvideTextScale(scale ?: s.textScale) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MessageBubble(BubbleDemo.incoming, BubbleGroup(false, false, false, false), null, 1, null, null, false, 260.dp, radiusOverride = corners ?: s.bubbleRadius)
                    Row {
                        Spacer(Modifier.weight(1f))
                        MessageBubble(BubbleDemo.outgoing, BubbleGroup(false, false, false, false), null, 0, BubbleDemo.incoming, "Sara", false, 260.dp, radiusOverride = corners ?: s.bubbleRadius)
                    }
                }
            }
        }
    }
    if (colors != null) ProvideColors(colors, body) else body()
}

/** Main Appearance page. */
internal fun LazyListScope.appearancePage() {
    item {
        val s = LocalAppSettings.current
        val state = LocalThemeState.current
        val nav = LocalNavigator.current
        val repo = LocalRepository.current
        val c = TgTheme.colors
        val themes = if (state.nightShowing) listOf(ColorTheme.Tinted, ColorTheme.Night)
        else listOf(ColorTheme.Classic, ColorTheme.Tinted, ColorTheme.Day, ColorTheme.Night)
        Section(header = if (state.nightShowing) "Color Theme — Night Mode" else "Color Theme") {
            Box(Modifier.padding(10.dp)) { ChatPreview() }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                themes.forEach { t ->
                    ThemeThumb(t, selected = state.active == t) { s.selectTheme(t, state.nightShowing) }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccentPresets.indices.forEach { i ->
                    val color = themeAccent(state.active, i)
                    val sel = s.accentIndex(state.active) == i
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(if (sel) 2.dp else 0.dp, if (sel) color else Color.Transparent, CircleShape)
                            .padding(if (sel) 5.dp else 0.dp)
                            .clip(CircleShape)
                            .background(color)
                            .fadeClickable { s.updateAccent(state.active, i) },
                    )
                }
            }
            Separator(startPadding = 16.dp)
            Cell("Chat Wallpaper", onClick = { nav.push(Route.SettingsPage(Page.Wallpaper)) })
            Cell("Your Color", divider = false, trailing = {
                Box(Modifier.size(22.dp).clip(CircleShape).background(NameColors.color(repo.myNameColorId, c.isDark)))
                Spacer(Modifier.width(8.dp))
                Icon(IosIcons.ChevronRight, c.tertiaryText, 14.dp)
            }, onClick = { nav.push(Route.SettingsPage(Page.NameColor)) })
        }
    }
    appearanceGap()
    item {
        val s = LocalAppSettings.current
        val state = LocalThemeState.current
        val nav = LocalNavigator.current
        Section {
            Cell("Night Mode", chevron = false, trailing = {
                IOSSwitch(s.nightForced || s.colorTheme.dark, { s.updateNightForced(it) })
            })
            Cell("Auto-Night Mode", value = s.autoNight.title, divider = false, onClick = { nav.push(Route.SettingsPage(Page.AutoNight)) })
        }
        if (state.autoNightTriggered && !s.nightForced && !s.colorTheme.dark) {
            T(
                "Auto-Night Mode is on: the ${s.nightTheme.title} theme is used now.",
                TgTheme.type.footnote, TgTheme.colors.secondaryText,
                modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp),
            )
        }
    }
    appearanceGap()
    item {
        val s = LocalAppSettings.current
        val nav = LocalNavigator.current
        Section {
            Cell("Text Size", value = "${(17f * s.textScale).roundToInt()}pt", onClick = { nav.push(Route.SettingsPage(Page.TextSize)) })
            Cell("Message Corners", value = "${s.bubbleRadius.roundToInt()}pt", divider = false, onClick = { nav.push(Route.SettingsPage(Page.MessageCorners)) })
        }
    }
    appearanceGap()
    item {
        val s = LocalAppSettings.current
        val nav = LocalNavigator.current
        Section {
            Cell("Animations", value = if (s.animations) "On" else "Off", divider = false, onClick = { nav.push(Route.SettingsPage(Page.PowerSaving)) })
        }
    }
    appearanceGap()
    item {
        val s = LocalAppSettings.current
        val context = LocalContext.current
        val toast = LocalToast.current
        val c = TgTheme.colors
        Section(header = "App Icon", footer = "Your home screen may take a few seconds to show the new icon.") {
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                AppIcons.options.forEachIndexed { i, o ->
                    val sel = s.appIcon == i
                    Column(
                        Modifier.fadeClickable {
                            if (!sel) {
                                if (AppIcons.apply(context, i)) s.updateAppIcon(i) else toast.error("Can't change the icon on this device")
                            }
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(66.dp)
                                .border(if (sel) 2.5.dp else 0.dp, if (sel) c.accent else Color.Transparent, RoundedRectangle(18.dp))
                                .padding(4.dp)
                                .clip(RoundedRectangle(14.dp))
                                .background(Brush.verticalGradient(o.background)),
                        ) {
                            Image(
                                painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = o.title,
                                colorFilter = ColorFilter.tint(o.glyph),
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        T(o.title, TgTheme.type.caption1, if (sel) c.accent else c.text, weight = if (sel) FontWeight.SemiBold else null)
                    }
                }
            }
        }
    }
    appearanceGap()
    item {
        val s = LocalAppSettings.current
        Section(header = "Other", footer = "Messages that only contain a few emoji are shown big, without a bubble.") {
            Cell("Large Emoji", chevron = false, divider = false, trailing = { IOSSwitch(s.largeEmoji, { s.updateLargeEmoji(it) }) })
        }
    }
    appearanceGap()
    item {
        val context = LocalContext.current
        com.abtin.tglass.core.emoji.appleEmojiKey()
        val apple = com.abtin.tglass.core.emoji.AppleEmoji.appleStyle
        Section(
            header = "Emoji Style",
            footer = "Apple emoji look like Telegram for iPhone. They are downloaded once as you see them and kept on this device; until then, and with System, your phone's emoji are used.",
        ) {
            Box(Modifier.padding(12.dp)) {
                SegmentedControl(
                    listOf("Apple", "System"), if (apple) 0 else 1,
                    { com.abtin.tglass.core.emoji.AppleEmoji.setAppleStyle(context, it == 0) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
    appearanceGap()
    item {
        val s = LocalAppSettings.current
        val nav = LocalNavigator.current
        Section(header = "Chat List", footer = "Compact matches Telegram for iPhone. Larger sizes show bigger photos and text.") {
            Box(Modifier.padding(12.dp)) {
                SegmentedControl(ChatListSize.entries.map { it.title }, s.chatListSize.ordinal, { s.updateChatListSize(ChatListSize.entries[it]) }, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(24.dp))
        Section(header = "TGlass", footer = "Bubble Gradient paints outgoing messages of the Day, Night and Tinted themes with a soft gradient. Tinted Glass colors bars and buttons slightly with the accent color.") {
            Cell("Liquid Glass", value = s.glassLevel.title, onClick = { nav.push(Route.SettingsPage(Page.PowerSaving)) })
            Cell("Bubble Gradient", chevron = false, trailing = { IOSSwitch(s.bubbleGradient, { s.updateBubbleGradient(it) }) })
            Cell("Tinted Glass", chevron = false, divider = false, trailing = { IOSSwitch(s.glassTint, { s.updateGlassTint(it) }) })
        }
    }
}

/** A carousel item: a tiny chat in [theme]'s colors with the theme name under it. */
@Composable
private fun ThemeThumb(theme: ColorTheme, selected: Boolean, onClick: () -> Unit) {
    val s = LocalAppSettings.current
    val c = TgTheme.colors
    val spec = s.themeSpec(theme)
    val colors = remember(spec) { buildColors(spec) }
    Column(Modifier.width(84.dp).fadeClickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(84.dp, 108.dp)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) c.accent else c.separator, RoundedRectangle(16.dp))
                .padding(if (selected) 4.dp else 1.dp)
                .clip(RoundedRectangle(13.dp))
                .background(Brush.linearGradient(colors.wallpaper))
                .padding(8.dp),
        ) {
            Box(Modifier.align(Alignment.TopStart).size(46.dp, 16.dp).clip(RoundedRectangle(8.dp)).background(colors.bubbleIn))
            val out = colors.bubbleOutGradient
            Box(
                Modifier.align(Alignment.Center).padding(start = 18.dp).size(50.dp, 16.dp).clip(RoundedRectangle(8.dp))
                    .then(if (out != null) Modifier.background(Brush.verticalGradient(out)) else Modifier.background(colors.bubbleOut))
            )
            Box(Modifier.align(Alignment.BottomStart).size(34.dp, 16.dp).clip(RoundedRectangle(8.dp)).background(colors.bubbleIn))
        }
        Spacer(Modifier.height(6.dp))
        T(theme.title, TgTheme.type.footnote, if (selected) c.accent else c.text, weight = if (selected) FontWeight.SemiBold else null)
    }
}

/** Settings → Appearance → Text Size. */
internal fun LazyListScope.textSizePage() {
    item {
        val s = LocalAppSettings.current
        val c = TgTheme.colors
        // Only the preview follows the finger; the app is re-laid out once, when it lifts.
        var scale by remember { mutableFloatStateOf(s.textScale) }
        Section { Box(Modifier.padding(10.dp)) { ChatPreview(scale = scale) } }
        Spacer(Modifier.height(24.dp))
        Section(header = "Text Size", footer = "${(17f * scale).roundToInt()}pt. Adjusts text size across the whole app.") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("A", TextStyle(fontSize = 13.sp), c.text)
                IOSSlider(scale, { scale = it }, 0.85f..1.3f, Modifier.weight(1f).padding(horizontal = 12.dp), steps = 8, onValueChangeFinished = { if (scale != s.textScale) s.updateTextScale(scale) })
                T("A", TextStyle(fontSize = 20.sp), c.text)
            }
        }
        Spacer(Modifier.height(24.dp))
        Section {
            Cell("Reset to Default", titleColor = c.accent, chevron = false, divider = false, onClick = { scale = 1f; s.updateTextScale(1f) })
        }
    }
}

/** Settings → Appearance → Message Corners. */
internal fun LazyListScope.messageCornersPage() {
    item {
        val s = LocalAppSettings.current
        val c = TgTheme.colors
        var corners by remember { mutableFloatStateOf(s.bubbleRadius) }
        Section { Box(Modifier.padding(10.dp)) { ChatPreview(corners = corners) } }
        Spacer(Modifier.height(24.dp))
        Section(header = "Message Corners", footer = "Adjust the corner radius of message bubbles.") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).border(2.dp, c.secondaryText, RoundedRectangle(2.dp)))
                IOSSlider(corners, { corners = it }, 6f..22f, Modifier.weight(1f).padding(horizontal = 12.dp), steps = 7, onValueChangeFinished = { if (corners != s.bubbleRadius) s.updateBubbleRadius(corners) })
                Box(Modifier.size(16.dp).border(2.dp, c.secondaryText, RoundedRectangle(7.dp)))
            }
        }
        Spacer(Modifier.height(24.dp))
        Section {
            Cell("Reset to Default", titleColor = c.accent, chevron = false, divider = false, onClick = { corners = 16f; s.updateBubbleRadius(16f) })
        }
    }
}

/** Settings → Appearance → Chat Wallpaper: gradient and color galleries for day and night themes. */
internal fun LazyListScope.wallpaperPage() {
    item {
        val s = LocalAppSettings.current
        val state = LocalThemeState.current
        var night by rememberSaveable { mutableStateOf(state.active.dark) }
        val theme = if (night) (if (state.active.dark) state.active else s.nightTheme) else (if (!state.active.dark) state.active else ColorTheme.Classic)
        val spec = s.themeSpec(theme)
        val colors = remember(spec) { buildColors(spec) }
        val list = if (night) NightWallpapers else DayWallpapers
        val selected = if (night) s.wallpaperNightIndex else s.wallpaperIndex
        fun pick(i: Int) = if (night) s.updateWallpaperNight(i) else s.updateWallpaper(i)
        val c = TgTheme.colors

        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 14.dp)) {
            SegmentedControl(listOf("Day Themes", "Night Themes"), if (night) 1 else 0, { night = it == 1 }, Modifier.fillMaxWidth())
        }
        Section { Box(Modifier.padding(10.dp)) { ChatPreview(colors = colors, minHeight = 200) } }
        Spacer(Modifier.height(24.dp))
        Section(header = "Pattern", footer = "The doodle pattern is drawn over gradient wallpapers.") {
            Cell("Show Pattern", chevron = false, trailing = { IOSSwitch(s.wallpaperPattern, { s.updateWallpaperPattern(it) }) })
            var intensity by remember { mutableFloatStateOf(s.patternIntensity) }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Intensity", TgTheme.type.body, if (s.wallpaperPattern) c.text else c.secondaryText)
                IOSSlider(intensity, { intensity = it }, 0f..1f, Modifier.weight(1f).padding(start = 16.dp), onValueChangeFinished = { s.updatePatternIntensity(intensity) })
            }
        }
        Spacer(Modifier.height(24.dp))
        val gradients = list.indices.filter { list[it]?.solid != true }
        val solids = list.indices.filter { list[it]?.solid == true }
        Section(header = "Gradients") {
            WallpaperGrid(gradients, list, selected, theme, s.accentIndex(theme)) { pick(it) }
        }
        Spacer(Modifier.height(24.dp))
        Section(header = "Colors") {
            WallpaperGrid(solids, list, selected, theme, s.accentIndex(theme)) { pick(it) }
        }
        Spacer(Modifier.height(24.dp))
        Section {
            Cell("Reset to Default", titleColor = c.accent, chevron = false, divider = false, onClick = {
                pick(0); s.updateWallpaperPattern(true); s.updatePatternIntensity(0.5f)
            })
        }
    }
}

@Composable
private fun WallpaperGrid(indices: List<Int>, list: List<WallpaperOption?>, selected: Int, theme: ColorTheme, accent: Int, onPick: (Int) -> Unit) {
    val c = TgTheme.colors
    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        indices.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    val option = list[i] ?: themeWallpaper(theme, accent)
                    val sel = i == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.72f)
                            .clip(RoundedRectangle(12.dp))
                            .background(Brush.linearGradient(option.colors))
                            .border(if (sel) 3.dp else 0.5.dp, if (sel) c.accent else c.separator, RoundedRectangle(12.dp))
                            .fadeClickable { onPick(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (i == 0) T("Default", TgTheme.type.caption1, if (option.colors.first().let { it.red + it.green + it.blue } > 1.6f) Color.Black.copy(0.6f) else Color.White.copy(0.8f), weight = FontWeight.SemiBold)
                        if (sel) Box(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                            Icon(IosIcons.Checkmark, Color.White, 14.dp)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun formatMinute(m: Int): String = String.format(Locale.US, "%02d:%02d", m / 60, m % 60)

/** Settings → Appearance → Auto-Night Mode. */
internal fun LazyListScope.autoNightPage() {
    item {
        val s = LocalAppSettings.current
        Section(footer = when (s.autoNight) {
            AutoNight.Disabled -> "Night Mode is only used when you switch it on."
            AutoNight.System -> "The app follows the dark appearance of Android."
            AutoNight.Scheduled -> "The night theme is used during the hours below."
        }) {
            AutoNight.entries.forEachIndexed { i, v ->
                Cell(v.title, checked = s.autoNight == v, chevron = false, divider = i != AutoNight.entries.lastIndex, onClick = { s.updateAutoNight(v) })
            }
        }
    }
    item {
        val s = LocalAppSettings.current
        if (s.autoNight == AutoNight.Scheduled) {
            var editing by rememberSaveable { mutableStateOf(-1) } // 0 = From, 1 = To
            Spacer(Modifier.height(24.dp))
            Section(header = "Schedule") {
                Cell("From", value = formatMinute(s.nightFrom), chevron = false, onClick = { editing = if (editing == 0) -1 else 0 })
                if (editing == 0) TimeChips(s.nightFrom) { s.updateNightSchedule(it, s.nightTo) }
                Cell("To", value = formatMinute(s.nightTo), chevron = false, divider = editing == 1, onClick = { editing = if (editing == 1) -1 else 1 })
                if (editing == 1) TimeChips(s.nightTo) { s.updateNightSchedule(s.nightFrom, it) }
            }
        }
    }
    item {
        val s = LocalAppSettings.current
        Spacer(Modifier.height(24.dp))
        Section(header = "Preferred Theme", footer = "The theme used by Night Mode and Auto-Night Mode.") {
            listOf(ColorTheme.Night, ColorTheme.Tinted).forEachIndexed { i, t ->
                Cell(t.title, checked = s.nightTheme == t, chevron = false, divider = i == 0, onClick = { s.updateNightTheme(t) })
            }
        }
    }
}

/** Inline hour / minute chooser (a compact stand-in for the iOS time wheel). */
@Composable
private fun TimeChips(value: Int, onChange: (Int) -> Unit) {
    val hour = value / 60
    val minute = value % 60
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until 24).forEach { h ->
                Chip(String.format(Locale.US, "%02d", h), h == hour) { onChange(h * 60 + minute) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0, 15, 30, 45).forEach { m ->
                Chip(":" + String.format(Locale.US, "%02d", m), m == minute) { onChange(hour * 60 + m) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Separator(startPadding = 16.dp)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = TgTheme.colors
    Box(
        Modifier
            .clip(RoundedRectangle(10.dp))
            .background(if (selected) c.accent else c.searchField)
            .fadeClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        T(text, TgTheme.type.subheadline, if (selected) Color.White else c.text, weight = if (selected) FontWeight.SemiBold else null)
    }
}

/** Settings → Your Color: the color of your name in messages and replies. */
internal fun LazyListScope.nameColorPage() {
    item {
        val repo = LocalRepository.current
        val toast = LocalToast.current
        val c = TgTheme.colors
        androidx.compose.runtime.LaunchedEffect(Unit) { repo.loadProfileExtras() }
        val current = repo.myNameColorId
        var pending by remember { mutableStateOf<Int?>(null) }
        val shown = pending ?: current
        val nameColor = NameColors.color(shown, c.isDark)
        Section {
            Box(Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(10.dp).clip(RoundedRectangle(20.dp))) {
                Box(Modifier.matchParentSize()) { ChatWallpaper() }
                Column(Modifier.padding(12.dp).clip(RoundedRectangle(16.dp)).background(c.bubbleIn).padding(10.dp)) {
                    T(repo.me.name, TgTheme.type.subheadline, nameColor, weight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.clip(RoundedRectangle(6.dp)).background(nameColor.copy(alpha = 0.12f))) {
                        Box(Modifier.width(3.dp).height(38.dp).background(nameColor))
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                            T(repo.me.name, TgTheme.type.footnote, nameColor, weight = FontWeight.SemiBold, maxLines = 1)
                            T("Reply to your message", TgTheme.type.footnote, c.bubbleInText, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    T("Your name and replies to you have this color.", TgTheme.type.body, c.bubbleInText)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Section(header = "Name Color", footer = "Choose a color for your name, the replies to your messages and link previews.") {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                (0 until NameColors.count).forEach { id ->
                    val col = NameColors.color(id, c.isDark)
                    val sel = id == shown
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(if (sel) 2.5.dp else 0.dp, if (sel) col else Color.Transparent, CircleShape)
                            .padding(if (sel) 5.dp else 0.dp)
                            .clip(CircleShape)
                            .background(col)
                            .fadeClickable {
                                if (id == shown) return@fadeClickable
                                pending = id
                                repo.setNameColor(id) { err ->
                                    if (err != null) toast.error(err)
                                    pending = null
                                }
                            },
                    )
                }
            }
        }
        T(
            "Profile colors, background icons and collectible colors need Telegram Premium; set them in the official app.",
            TgTheme.type.footnote, c.secondaryText, align = TextAlign.Start,
            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 7.dp),
        )
    }
}
