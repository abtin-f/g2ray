package com.abtin.tglass.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.GlassLevel
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.ThemeMode
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.design.WallpaperPresets
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.BubbleDemo
import com.abtin.tglass.features.chat.BubbleGroup
import com.abtin.tglass.features.chat.ChatWallpaper
import com.abtin.tglass.features.chat.MessageBubble
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.GlassTextButton
import com.abtin.tglass.ui.components.GlassTopBar
import com.abtin.tglass.ui.components.IOSSlider
import com.abtin.tglass.ui.components.IOSSwitch
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.CollapsedTitle
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.ProfileHero
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.rememberHeroCollapse
import androidx.compose.foundation.lazy.rememberLazyListState
import com.abtin.tglass.ui.components.fadeClickable
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle

enum class Page(val title: String) {
    EditProfile("Edit Profile"),
    Notifications("Notifications and Sounds"),
    Privacy("Privacy and Security"),
    Data("Data and Storage"),
    Appearance("Appearance"),
    PowerSaving("Power Saving"),
    Language("Language"),
    Devices("Devices"),
    Folders("Chat Folders"),
    Premium("Telegram Premium"),
}

private val Red = Color(0xFFFF3B30)
private val Orange = Color(0xFFFF9500)
private val Yellow = Color(0xFFFFCC00)
private val Green = Color(0xFF34C759)
private val Teal = Color(0xFF32ADE6)
private val Blue = Color(0xFF007AFF)
private val Purple = Color(0xFFAF52DE)
private val Gray = Color(0xFF8E8E93)

/** Spec §37: Settings tab. */
@Composable
fun SettingsScreen(backdrop: LayerBackdrop) {
    val repo = LocalRepository.current
    val nav = LocalNavigator.current
    val toast = LocalToast.current
    val sheet = LocalActionSheet.current
    val settings = LocalAppSettings.current
    val c = TgTheme.colors
    val me = repo.me
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val collapse = rememberHeroCollapse(listState)
    fun open(p: Page) = nav.push(Route.SettingsPage(p))

    Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
            contentPadding = PaddingValues(bottom = bottom + 112.dp),
        ) {
            item {
                ProfileHero(
                    name = me.name,
                    seed = 3,
                    photoPeer = me.id,
                    subtitle = listOfNotNull(me.phone.takeIf { it.isNotBlank() }, me.username?.let { "@$it" }).joinToString(" • "),
                    collapse = collapse,
                    badge = { if (me.premium) Icon(TgIcons.IcPremiumPeer, Color(0xFFAF52DE), 22.dp) },
                )
            }
            item {
                Section {
                    Cell("Set Emoji Status", icon = TgIcons.SetStatus, iconColor = Purple, onClick = { toast.show("Emoji status set ✨") })
                    Cell("My Profile", icon = TgIcons.SetProfile, iconColor = Red, divider = false, onClick = { open(Page.EditProfile) })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Section {
                    Cell("Saved Messages", icon = TgIcons.SetSaved, iconColor = Blue, onClick = { nav.push(Route.Chat(100)) })
                    Cell("Recent Calls", icon = TgIcons.SetCalls, iconColor = Green, onClick = { nav.push(Route.Calls) })
                    Cell("Devices", icon = TgIcons.SetDevices, iconColor = Orange, value = "${repo.sessions.size}", onClick = { open(Page.Devices) })
                    Cell("Chat Folders", icon = TgIcons.SetFolders, iconColor = Teal, divider = false, onClick = { open(Page.Folders) })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Section {
                    Cell("Notifications and Sounds", icon = TgIcons.SetNotifications, iconColor = Red, onClick = { open(Page.Notifications) })
                    Cell("Privacy and Security", icon = TgIcons.SetPrivacy, iconColor = Gray, onClick = { open(Page.Privacy) })
                    Cell("Data and Storage", icon = TgIcons.SetData, iconColor = Green, onClick = { open(Page.Data) })
                    Cell("Appearance", icon = TgIcons.SetAppearance, iconColor = Teal, onClick = { open(Page.Appearance) })
                    Cell("Power Saving", icon = TgIcons.SetPower, iconColor = Orange, value = settings.glassLevel.title, onClick = { open(Page.PowerSaving) })
                    Cell("Language", icon = TgIcons.SetLanguage, iconColor = Purple, value = "English", divider = false, onClick = { open(Page.Language) })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Section {
                    Cell("Telegram Premium", icon = TgIcons.SetPremium, iconColor = Color(0xFF8F68FF), onClick = { open(Page.Premium) })
                    Cell("My Stars", icon = TgIcons.SetStars, iconColor = Color(0xFFFFB800), onClick = { toast.show("Stars ⭐️") })
                    Cell("Telegram Business", icon = TgIcons.SetBusiness, iconColor = Color(0xFFFF6B3D), onClick = { toast.show("Business") })
                    Cell("Send a Gift", icon = TgIcons.SetGift, iconColor = Color(0xFF32C1DE), divider = false, onClick = { toast.show("Gifts are coming soon 🎁") })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Section {
                    Cell("Ask a Question", icon = TgIcons.CtxSmile, iconColor = Orange, onClick = { toast.show("Opening support chat…") })
                    Cell("Telegram FAQ", icon = TgIcons.SetFaq, iconColor = Teal, onClick = { toast.show("FAQ") })
                    Cell("Telegram Features", icon = TgIcons.SetTips, iconColor = Yellow, divider = false, onClick = { toast.show("Features") })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Section {
                    Cell("Log Out", titleColor = c.destructive, chevron = false, divider = false, onClick = {
                        sheet.show(SheetRequest(title = "Log out?", message = "You will return to the welcome screen.", alert = true, actions = listOf(SheetAction("Log Out", destructive = true) {
                            repo.logOut()
                            settings.updateLoggedIn(false)
                            settings.updateDemoMode(false)
                            nav.resetTo(Route.Welcome)
                        })))
                    })
                }
                Spacer(Modifier.height(12.dp))
                T("TGlass for Android v${com.abtin.tglass.BuildConfig.VERSION_NAME}", TgTheme.type.footnote, c.secondaryText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        GlassTopBar(
            title = null,
            fade = c.groupedBackground,
            left = { GlassIconButton(IosIcons.QrCode, { toast.show("QR code") }, iconSize = 22.dp) },
            center = { CollapsedTitle(me.name, 3, collapse, photoPeer = me.id) },
            right = { GlassTextButton("Edit", { open(Page.EditProfile) }) },
        )
    }
}

/** Shared scaffold for pushed settings pages. */
@Composable
fun SettingsPageScreen(page: Page) {
    val backdrop = rememberLayerBackdrop()
    val c = TgTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 30.dp),
            ) {
                when (page) {
                    Page.Appearance -> appearance()
                    Page.PowerSaving -> powerSaving()
                    Page.Notifications -> notifications()
                    Page.Privacy -> privacy()
                    Page.Data -> dataStorage()
                    Page.Language -> language()
                    Page.Devices -> devices()
                    Page.Folders -> folders()
                    Page.Premium -> premium()
                    Page.EditProfile -> editProfile()
                }
            }
            GlassTopBar(page.title, fade = c.groupedBackground)
        }
    }
}

private fun LazyListScope.gap() = item { Spacer(Modifier.height(24.dp)) }

private fun LazyListScope.appearance() {
    item {
        val s = LocalAppSettings.current
        val c = TgTheme.colors
        Section(header = "Color Theme") {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val sel = s.themeMode == mode
                    Column(Modifier.weight(1f).fadeClickable { s.updateTheme(mode) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        val dark = mode == ThemeMode.Dark
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedRectangle(14.dp))
                                .border(if (sel) 2.5.dp else 1.dp, if (sel) c.accent else c.separator, RoundedRectangle(14.dp))
                                .background(
                                    if (mode == ThemeMode.System) Brush.linearGradient(listOf(Color(0xFFDBDDBB), Color(0xFF1C2B3E)))
                                    else Brush.linearGradient(if (dark) listOf(Color(0xFF0B1A2B), Color(0xFF223246)) else listOf(Color(0xFFDBDDBB), Color(0xFF88B884)))
                                )
                                .padding(10.dp),
                        ) {
                            Box(Modifier.align(Alignment.TopStart).size(46.dp, 16.dp).clip(RoundedRectangle(8.dp)).background(if (dark) Color(0xFF262628) else Color.White))
                            Box(Modifier.align(Alignment.BottomEnd).size(52.dp, 16.dp).clip(RoundedRectangle(8.dp)).background(if (dark) Color(0xFF313131) else Color(0xFFE1FFC7)))
                        }
                        Spacer(Modifier.height(6.dp))
                        T(when (mode) { ThemeMode.System -> "System"; ThemeMode.Light -> "Day"; ThemeMode.Dark -> "Night" }, TgTheme.type.footnote, if (sel) c.accent else c.text, weight = if (sel) FontWeight.SemiBold else null)
                    }
                }
            }
        }
    }
    gap()
    item {
        val s = LocalAppSettings.current
        val c = TgTheme.colors
        Section(header = "Preview") {
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedRectangle(26.dp))) {
                ChatWallpaper()
                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MessageBubble(BubbleDemo.incoming, BubbleGroup(false, false, false, false), null, 1, null, null, false, 260.dp)
                    Row { Spacer(Modifier.weight(1f)); MessageBubble(BubbleDemo.outgoing, BubbleGroup(false, false, false, false), null, 0, BubbleDemo.incoming, "Sara", false, 260.dp) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Section(header = "Text Size", footer = "Adjusts text size across the whole app.") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("A", TgTheme.type.footnote, c.text)
                IOSSlider(s.textScale, { s.updateTextScale(it) }, 0.85f..1.3f, Modifier.weight(1f).padding(horizontal = 12.dp), steps = 8)
                T("A", TgTheme.type.title3, c.text)
            }
        }
        Spacer(Modifier.height(24.dp))
        Section(header = "Message Corners") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IOSSlider(s.bubbleRadius, { s.updateBubbleRadius(it) }, 6f..22f, Modifier.weight(1f), steps = 7)
            }
        }
        Spacer(Modifier.height(24.dp))
        Section(header = "Chat Wallpaper") {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                WallpaperPresets.forEachIndexed { i, preset ->
                    val colors = preset ?: listOf(Color(0xFFDBDDBB), Color(0xFF6BA587), Color(0xFFD5D88D), Color(0xFF88B884))
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(if (s.wallpaperIndex == i) 3.dp else 0.dp, c.accent, CircleShape)
                            .padding(if (s.wallpaperIndex == i) 4.dp else 0.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(colors))
                            .fadeClickable { s.updateWallpaper(i) }
                    )
                }
            }
        }
    }
}

private fun LazyListScope.powerSaving() {
    item {
        val s = LocalAppSettings.current
        Section(header = "Interface Effects", footer = "Liquid Glass uses real-time blur and lens refraction (Android 13+). Lower levels save battery; Off makes every surface opaque, like Reduce Transparency on iOS.") {
            GlassLevel.entries.forEachIndexed { i, level ->
                Cell(level.title, checked = s.glassLevel == level, chevron = false, divider = i != GlassLevel.entries.lastIndex, onClick = { s.updateGlass(level) })
            }
        }
    }
    gap()
    item {
        val s = LocalAppSettings.current
        Section(header = "Resource-Intensive Processes") {
            Cell("Animations", chevron = false, trailing = { IOSSwitch(s.animations, { s.updateAnimations(it) }) })
            Cell("Autoplay Videos", chevron = false, trailing = { IOSSwitch(s.autoplayVideo, { s.updateAutoplayVideo(it) }) })
            Cell("Autoplay GIFs", chevron = false, divider = false, trailing = { IOSSwitch(s.autoplayGif, { s.updateAutoplayGif(it) }) })
        }
    }
}

@Composable
private fun ToggleCell(title: String, initial: Boolean, divider: Boolean = true, subtitle: String? = null) {
    var on by rememberSaveable(title) { mutableStateOf(initial) }
    Cell(title, subtitle = subtitle, chevron = false, divider = divider, trailing = { IOSSwitch(on, { on = it }) })
}

private fun LazyListScope.notifications() {
    item {
        val s = LocalAppSettings.current
        val context = androidx.compose.ui.platform.LocalContext.current
        Section(
            header = "Background",
            footer = "Keeps TGlass connected while it is closed so new messages arrive as notifications. " +
                "If they stop after a while, allow TGlass to ignore battery optimization.",
        ) {
            Cell("Keep Connected in Background", chevron = false, trailing = { IOSSwitch(s.backgroundConnection, { s.updateBackgroundConnection(it) }) })
            Cell("Battery Optimization", divider = false, onClick = {
                val pm = context.getSystemService(android.os.PowerManager::class.java)
                val intent = if (pm?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                } else {
                    android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}"))
                }
                runCatching { context.startActivity(intent) }
            })
        }
        Spacer(Modifier.height(24.dp))
    }
    listOf("Private Chats", "Group Chats", "Channels").forEachIndexed { i, header ->
        item {
            Section(header = "Message Notifications".takeIf { i == 0 }) {
                ToggleCell("Show Notifications", true, subtitle = header)
                Cell("Sound", value = listOf("Note", "Tri-tone", "Chime")[i], divider = false, onClick = {})
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    item {
        Section(header = "In-App Notifications") {
            ToggleCell("In-App Sounds", true)
            ToggleCell("In-App Vibrate", true)
            val s = LocalAppSettings.current
            Cell("In-App Preview", chevron = false, divider = false, trailing = { IOSSwitch(s.inAppPreview, { s.updateInAppPreview(it) }) })
        }
    }
}

private fun LazyListScope.privacy() {
    item {
        Section {
            Cell("Passcode Lock", icon = TgIcons.SetPrivacy, iconColor = Orange, value = "Off", onClick = {})
            Cell("Two-Step Verification", icon = IosIcons.Lock, iconColor = Blue, value = "Off", onClick = {})
            Cell("Blocked Users", icon = IosIcons.Close, iconColor = Red, value = "3", divider = false, onClick = {})
        }
    }
    gap()
    item {
        Section(header = "Privacy", footer = "You can restrict which users are allowed to add you to groups and channels.") {
            listOf(
                "Phone Number" to "My Contacts",
                "Last Seen & Online" to "Everybody",
                "Profile Photos" to "Everybody",
                "Forwarded Messages" to "Everybody",
                "Calls" to "Everybody",
                "Groups & Channels" to "Everybody",
            ).forEachIndexed { i, (t, v) -> Cell(t, value = v, divider = i != 5, onClick = {}) }
        }
    }
    gap()
    item {
        Section(header = "Automatically delete my account", footer = "If you do not come online at least once within this period, your account will be deleted.") {
            Cell("If Away For", value = "6 months", divider = false, onClick = {})
        }
    }
}

private fun LazyListScope.dataStorage() {
    item {
        Section {
            Cell("Storage Usage", icon = TgIcons.SetData, iconColor = Blue, value = "1.2 GB", onClick = {})
            Cell("Data Usage", icon = TgIcons.SetData, iconColor = Green, value = "3.4 GB", divider = false, onClick = {})
        }
    }
    gap()
    item {
        Section(header = "Automatic Media Download") {
            Cell("Using Cellular", value = "Enabled", onClick = {})
            Cell("Using Wi-Fi", value = "Enabled", onClick = {})
            Cell("Roaming", value = "Disabled", divider = false, onClick = {})
        }
    }
    gap()
    item {
        Section(header = "Save to Photos") {
            ToggleCell("Private Chats", false)
            ToggleCell("Groups", false)
            ToggleCell("Channels", false, divider = false)
        }
    }
}

private val Languages = listOf("English" to "English", "Persian" to "فارسی", "Arabic" to "العربية", "German" to "Deutsch", "Spanish" to "Español", "French" to "Français", "Russian" to "Русский", "Turkish" to "Türkçe")

private fun LazyListScope.language() {
    item {
        var sel by rememberSaveable { mutableIntStateOf(0) }
        Section(header = "Interface Language") {
            Languages.forEachIndexed { i, (en, native) ->
                Cell(native, subtitle = en, checked = sel == i, chevron = false, divider = i != Languages.lastIndex, onClick = { sel = i })
            }
        }
    }
}

private fun LazyListScope.devices() {
    item {
        val repo = LocalRepository.current
        val sheet = LocalActionSheet.current
        val c = TgTheme.colors
        val current = repo.sessions.firstOrNull { it.current }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(90.dp).clip(RoundedRectangle(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF72D5FD), Color(0xFF2A9EF1)))), contentAlignment = Alignment.Center) {
                Icon(TgIcons.SetDevices, Color.White, 64.dp)
            }
            Spacer(Modifier.height(12.dp))
            T("Link other devices to this account.", TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
        }
        if (current != null) {
            Section(header = "This Device") {
                Cell(current.device, subtitle = "${current.app}\n${current.location} • ${current.lastActive}", leading = { SessionIcon() }, chevron = false)
                Cell("Terminate All Other Sessions", titleColor = c.destructive, chevron = false, divider = false, onClick = {
                    sheet.show(SheetRequest(actions = listOf(SheetAction("Terminate Sessions", destructive = true) { repo.terminateOtherSessions() })))
                })
            }
        }
        Spacer(Modifier.height(24.dp))
        val others = repo.sessions.filter { !it.current }
        if (others.isNotEmpty()) {
            Section(header = "Active Sessions", footer = "Tap on a session to terminate it.") {
                others.forEachIndexed { i, s ->
                    Cell(s.device, subtitle = "${s.app}\n${s.location} • ${s.lastActive}", leading = { SessionIcon() }, chevron = false, divider = i != others.lastIndex, onClick = {
                        sheet.show(SheetRequest(title = s.device, actions = listOf(SheetAction("Terminate Session", destructive = true) { repo.terminateSession(s) })))
                    })
                }
            }
        }
    }
}

@Composable
private fun SessionIcon() {
    Box(Modifier.size(30.dp).clip(RoundedRectangle(8.dp)).background(Blue), contentAlignment = Alignment.Center) {
        Icon(TgIcons.SetDevices, Color.White, 30.dp)
    }
}

private fun LazyListScope.folders() {
    item {
        val repo = LocalRepository.current
        val c = TgTheme.colors
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("📁", TgTheme.type.largeTitle.copy(fontSize = 64.sp, lineHeight = 72.sp))
            T("Create folders for different groups of chats and quickly switch between them.", TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        }
        Section(header = "Chat Folders") {
            Cell("Create New Folder", icon = TgIcons.SetFolders, iconColor = c.accent, titleColor = c.accent, chevron = false, onClick = {})
            repo.folders.drop(1).forEachIndexed { i, f ->
                Cell(f, subtitle = when (f) { "Personal" -> "Private chats"; "Work" -> "2 chats"; else -> "Unread chats" }, divider = i != repo.folders.size - 2, onClick = {})
            }
        }
    }
}

private fun LazyListScope.premium() {
    item {
        val c = TgTheme.colors
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(110.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF6B93FF), Color(0xFFB36DF6), Color(0xFFFF7A9C)))),
                contentAlignment = Alignment.Center,
            ) { Icon(TgIcons.SetPremium, Color.White, 84.dp) }
            Spacer(Modifier.height(14.dp))
            T("Telegram Premium", TgTheme.type.title2, c.text)
            T("Go beyond the limits and unlock dozens of exclusive features.", TgTheme.type.subheadline, c.secondaryText, align = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp))
        }
        Section {
            listOf(
                Triple("Doubled Limits", "Up to 1000 channels, 20 folders, 10 pinned chats.", Red),
                Triple("Faster Download Speed", "No more limits on the speed of media downloads.", Orange),
                Triple("Voice-to-Text", "Read the transcripts of any voice message.", Purple),
                Triple("No Ads", "No more ads in public channels.", Blue),
                Triple("Emoji Statuses", "Choose from 1000s of emoji to show next to your name.", Teal),
            ).forEachIndexed { i, (t, d, col) ->
                Cell(t, subtitle = d, icon = TgIcons.SetPremium, iconColor = col, chevron = false, divider = i != 4)
            }
        }
    }
}

private fun LazyListScope.editProfile() {
    item {
        val repo = LocalRepository.current
        val c = TgTheme.colors
        val me = repo.me
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(me.name, 3, 100.dp, photoPeer = me.id)
            Spacer(Modifier.height(8.dp))
            T("Set New Photo", TgTheme.type.body, c.accent)
        }
        Section(footer = "Enter your name and add an optional profile photo.") {
            Cell(me.firstName, chevron = false)
            Cell("Last Name", titleColor = c.secondaryText, chevron = false, divider = false)
        }
        Spacer(Modifier.height(24.dp))
        Section(header = "Bio", footer = "Any details such as age, occupation or city.") {
            Cell(me.bio ?: "", chevron = false, divider = false)
        }
        Spacer(Modifier.height(24.dp))
        Section {
            Cell("Username", value = "@${me.username}", onClick = {})
            Cell("Phone Number", value = me.phone, divider = false, onClick = {})
        }
    }
}
