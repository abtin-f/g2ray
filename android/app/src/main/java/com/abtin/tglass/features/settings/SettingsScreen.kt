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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.GlassLevel
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.ProvideTextScale
import com.abtin.tglass.core.design.ThemeMode
import com.abtin.tglass.core.design.TgTheme
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
import com.abtin.tglass.ui.components.alert
import com.abtin.tglass.ui.components.SegmentedControl
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.CollapsedTitle
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.ProfileHero
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.rememberHeroCollapse
import androidx.compose.foundation.lazy.rememberLazyListState
import com.abtin.tglass.ui.components.fadeClickable
import kotlinx.coroutines.launch
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
    BlockedUsers("Blocked Users"),
    TwoStep("Two-Step Verification"),
    Passcode("Passcode Lock"),
    PasscodeSetup("Passcode"),
    StorageUsage("Storage Usage"),
    NetworkUsage("Data Usage"),
    AutoDownloadCellular("Using Cellular"),
    AutoDownloadWifi("Using Wi-Fi"),
    AutoDownloadRoaming("Roaming"),
    QrCode("QR Code"),
    TextSize("Text Size"),
    MessageCorners("Message Corners"),
    Wallpaper("Chat Wallpaper"),
    AutoNight("Auto-Night Mode"),
    NameColor("Your Color"),
    Username("Username"),
    PersonalChannel("Personal Channel"),
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
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    fun open(p: Page) = nav.push(Route.SettingsPage(p))
    fun openUrl(url: String) {
        runCatching { uri.openUri(url) }.onFailure { toast.error("Can't open $url") }
    }
    val live = repo.isLive

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
                // Telegram-iOS "edit" section: accent-colored actions.
                Section {
                    // Emoji statuses need a status picker (and Premium); the live app does not offer one yet.
                    if (!live) Cell("Set Emoji Status", icon = TgIcons.SetStatus, iconColor = Purple, titleColor = c.accent, chevron = false, onClick = { toast.show("Emoji status set ✨") })
                    val hasPhoto = repo.avatar(me.id) != null
                    Cell(
                        if (hasPhoto) "Change Profile Photo" else "Set Profile Photo",
                        icon = IosIcons.Camera, iconColor = Blue, titleColor = c.accent, chevron = false,
                        divider = me.username.isNullOrBlank(),
                        onClick = { open(Page.EditProfile) },
                    )
                    if (me.username.isNullOrBlank()) {
                        Cell("Set Username", icon = SettingsGlyphs.At, iconColor = Teal, titleColor = c.accent, chevron = false, divider = false, onClick = { open(Page.Username) })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            // Telegram iOS: the other signed-in accounts and "Add Account", right under the profile.
            item(key = "accounts") { AccountsSection() }
            item {
                Section {
                    Cell("My Profile", icon = TgIcons.SetProfile, iconColor = Red, divider = live, onClick = { open(Page.EditProfile) })
                    if (live) Cell("Proxy", icon = SettingsGlyphs.Shield, iconColor = Blue, value = proxySummary(), divider = false, onClick = { nav.push(Route.Proxy) })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Section {
                    Cell("Saved Messages", icon = TgIcons.SetSaved, iconColor = Blue, onClick = { nav.push(Route.Chat(repo.savedChatId)) })
                    Cell("Recent Calls", icon = TgIcons.SetCalls, iconColor = Green, onClick = { nav.push(Route.Calls) })
                    Cell("Devices", icon = TgIcons.SetDevices, iconColor = Orange, value = repo.sessions.size.takeIf { it > 0 }?.toString(), onClick = { open(Page.Devices) })
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
                    Cell("Power Saving", icon = TgIcons.SetPower, iconColor = Orange, value = if (settings.glassLevel == GlassLevel.Full && settings.animations) "Off" else "On", onClick = { open(Page.PowerSaving) })
                    Cell("Language", icon = TgIcons.SetLanguage, iconColor = Purple, value = "English", divider = false, onClick = { open(Page.Language) })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Section {
                    Cell("Telegram Premium", icon = TgIcons.SetPremium, iconColor = Color(0xFF8F68FF), onClick = { open(Page.Premium) })
                    if (live) {
                        // Stars and Business are managed in the official apps; link to what they are.
                        Cell("My Stars", icon = TgIcons.SetStars, iconColor = Color(0xFFFFB800), onClick = { openUrl("https://telegram.org/blog/telegram-stars") })
                        Cell("Telegram Business", icon = TgIcons.SetBusiness, iconColor = Color(0xFFFF6B3D), divider = false, onClick = { openUrl("https://telegram.org/blog/telegram-business") })
                    } else {
                        Cell("My Stars", icon = TgIcons.SetStars, iconColor = Color(0xFFFFB800), onClick = { toast.show("Stars ⭐️") })
                        Cell("Telegram Business", icon = TgIcons.SetBusiness, iconColor = Color(0xFFFF6B3D), onClick = { toast.show("Business") })
                        Cell("Send a Gift", icon = TgIcons.SetGift, iconColor = Color(0xFF32C1DE), divider = false, onClick = { toast.show("Gifts are coming soon 🎁") })
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                Section {
                    Cell("Ask a Question", icon = TgIcons.CtxSmile, iconColor = Orange, onClick = {
                        sheet.show(SheetRequest(
                            title = "Ask a Question",
                            message = "Telegram support is done by volunteers. Please take a look at the Telegram FAQ first: it has answers to most questions.",
                            alert = true,
                            actions = listOf(
                                SheetAction("Ask a Volunteer", bold = true) {
                                    repo.openSupportChat { chatId ->
                                        if (chatId != null) nav.push(Route.Chat(chatId)) else toast.error("Support chat is not available right now")
                                    }
                                },
                                SheetAction("Telegram FAQ") { openUrl("https://telegram.org/faq") },
                            ),
                        ))
                    })
                    Cell("Telegram FAQ", icon = TgIcons.SetFaq, iconColor = Teal, onClick = { openUrl("https://telegram.org/faq") })
                    Cell("Telegram Features", icon = TgIcons.SetTips, iconColor = Yellow, divider = false, onClick = {
                        repo.resolveUsername("TelegramTips") { chatId ->
                            if (chatId != null) nav.push(Route.Chat(chatId)) else openUrl("https://t.me/TelegramTips")
                        }
                    })
                }
                Spacer(Modifier.height(16.dp))
                T("TGlass for Android v${com.abtin.tglass.BuildConfig.VERSION_NAME}", TgTheme.type.footnote, c.secondaryText, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        GlassTopBar(
            title = null,
            fade = c.groupedBackground,
            left = { GlassIconButton(IosIcons.QrCode, { open(Page.QrCode) }, iconSize = 22.dp) },
            center = { CollapsedTitle(me.name, 3, collapse, photoPeer = me.id) },
            right = { GlassTextButton("Edit", { open(Page.EditProfile) }) },
        )
    }
}

/** Pushed settings pages. */
@Composable
fun SettingsPageScreen(page: Page) {
    when (page) {
        Page.Passcode -> PasscodeSettingsScreen()
        Page.PasscodeSetup -> PasscodeSetupScreen()
        Page.EditProfile -> EditProfileScreen()
        Page.Username -> UsernameScreen()
        else -> SettingsScaffold(page.title) {
            when (page) {
                Page.Appearance -> appearancePage()
                Page.TextSize -> textSizePage()
                Page.MessageCorners -> messageCornersPage()
                Page.Wallpaper -> wallpaperPage()
                Page.AutoNight -> autoNightPage()
                Page.NameColor -> nameColorPage()
                Page.PersonalChannel -> personalChannelPage()
                Page.PowerSaving -> powerSaving()
                Page.Notifications -> notifications()
                Page.Privacy -> privacy()
                Page.Data -> dataStorage()
                Page.Language -> language()
                Page.Devices -> devices()
                Page.Folders -> folders()
                Page.Premium -> premium()
                Page.BlockedUsers -> blockedUsers()
                Page.TwoStep -> twoStep()
                Page.StorageUsage -> storageUsage()
                Page.NetworkUsage -> networkUsage()
                Page.AutoDownloadCellular, Page.AutoDownloadWifi, Page.AutoDownloadRoaming -> page.downloadNetwork()?.let { autoDownload(it) }
                Page.QrCode -> myQrCode()
                Page.Passcode, Page.PasscodeSetup, Page.EditProfile, Page.Username -> {}
            }
        }
    }
}

/** Shared scaffold for pushed settings pages: grouped list under a glass top bar. */
@Composable
internal fun SettingsScaffold(title: String, content: LazyListScope.() -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val c = TgTheme.colors
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize().background(c.groupedBackground)) {
            LazyColumn(
                Modifier.fillMaxSize().layerBackdrop(backdrop),
                contentPadding = PaddingValues(top = top + 70.dp, bottom = bottom + 30.dp),
                content = content,
            )
            GlassTopBar(title, fade = c.groupedBackground)
        }
    }
}

private fun LazyListScope.gap() = item { Spacer(Modifier.height(24.dp)) }

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

private fun LazyListScope.notifications() {
    item {
        val s = LocalAppSettings.current
        val context = androidx.compose.ui.platform.LocalContext.current
        val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
        // Re-check the system state whenever the user comes back from Android settings.
        var systemOn by remember { mutableStateOf(com.abtin.tglass.notify.Notifier.systemEnabled(context)) }
        var serviceOn by remember { mutableStateOf(com.abtin.tglass.notify.ConnectionService.running) }
        androidx.compose.runtime.DisposableEffect(lifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
                if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    systemOn = com.abtin.tglass.notify.Notifier.systemEnabled(context)
                    serviceOn = com.abtin.tglass.notify.ConnectionService.running
                }
            }
            lifecycle.lifecycle.addObserver(observer)
            onDispose { lifecycle.lifecycle.removeObserver(observer) }
        }
        Section(
            header = "Status",
            footer = if (!systemOn) "Notifications are turned off for TGlass in Android settings. Tap above to allow them." else null,
        ) {
            Cell("Notifications", value = if (systemOn) "Allowed" else "Off", onClick = { com.abtin.tglass.notify.Notifier.openSystemSettings(context) })
            Cell("Background Connection", value = if (serviceOn) "Running" else "Stopped", chevron = false)
            Cell("Send Test Notification", titleColor = TgTheme.colors.accent, chevron = false, divider = false, onClick = {
                if (systemOn) com.abtin.tglass.notify.Notifier.showTest(context) else com.abtin.tglass.notify.Notifier.openSystemSettings(context)
            })
        }
        Spacer(Modifier.height(24.dp))
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
    com.abtin.tglass.data.NotifyScope.entries.forEach { kind ->
        item {
            val repo = LocalRepository.current
            if (kind == com.abtin.tglass.data.NotifyScope.Private) androidx.compose.runtime.LaunchedEffect(Unit) { repo.loadScopeNotifications() }
            val value = repo.scopeNotifications(kind)
            Section(
                header = kind.title,
                footer = if (value != null && !value.enabled) "Notifications for ${kind.title.lowercase()} are off. Chats with their own settings are not affected." else null,
            ) {
                Cell("Show Notifications", chevron = false, trailing = {
                    if (value != null) IOSSwitch(value.enabled, { repo.setScopeNotifications(kind, value.copy(enabled = it)) })
                    else com.abtin.tglass.ui.components.ActivityIndicator(18.dp)
                })
                Cell("Message Preview", chevron = false, divider = false, trailing = {
                    if (value != null) IOSSwitch(value.preview, { repo.setScopeNotifications(kind, value.copy(preview = it)) })
                    else com.abtin.tglass.ui.components.ActivityIndicator(18.dp)
                })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    item {
        Section(header = "In-App Notifications") {
            val context = androidx.compose.ui.platform.LocalContext.current
            com.abtin.tglass.notify.InAppAlerts.attach(context)
            val alerts = com.abtin.tglass.notify.InAppAlerts
            Cell("In-App Sounds", chevron = false, trailing = { IOSSwitch(alerts.sounds, { alerts.updateSounds(it) }) })
            Cell("In-App Vibrate", chevron = false, trailing = { IOSSwitch(alerts.vibrate, { alerts.updateVibrate(it) }) })
            val s = LocalAppSettings.current
            Cell("In-App Preview", chevron = false, divider = false, trailing = { IOSSwitch(s.inAppPreview, { s.updateInAppPreview(it) }) })
        }
    }
}

private fun LazyListScope.privacy() {
    item {
        val repo = LocalRepository.current
        val nav = LocalNavigator.current
        val context = androidx.compose.ui.platform.LocalContext.current
        PasscodeLock.attach(context)
        androidx.compose.runtime.LaunchedEffect(Unit) {
            repo.loadBlocked()
            repo.loadPasswordInfo()
            repo.loadAccountTtl()
        }
        val blocked = repo.blockedCount
        Section {
            Cell("Passcode Lock", icon = TgIcons.SetPrivacy, iconColor = Orange, value = if (PasscodeLock.enabled) "On" else "Off", onClick = { nav.push(Route.SettingsPage(Page.Passcode)) })
            Cell("Two-Step Verification", icon = IosIcons.Lock, iconColor = Blue, value = repo.passwordInfo?.let { if (it.hasPassword) "On" else "Off" }, onClick = { nav.push(Route.SettingsPage(Page.TwoStep)) })
            Cell("Blocked Users", icon = IosIcons.Close, iconColor = Red, value = blocked?.let { if (it == 0) "None" else "$it" }, divider = false, onClick = { nav.push(Route.SettingsPage(Page.BlockedUsers)) })
        }
    }
    gap()
    item {
        val repo = LocalRepository.current
        val sheet = LocalActionSheet.current
        androidx.compose.runtime.LaunchedEffect(Unit) { repo.loadPrivacy() }
        Section(header = "Privacy", footer = "You can restrict which users are allowed to add you to groups and channels.") {
            val keys = com.abtin.tglass.data.PrivacyKey.entries
            keys.forEachIndexed { i, key ->
                Cell(key.title, value = repo.privacy(key)?.title ?: "…", divider = i != keys.lastIndex, onClick = {
                    sheet.show(SheetRequest(
                        title = "Who can see or use: ${key.title}",
                        actions = com.abtin.tglass.data.PrivacyValue.entries.map { v -> SheetAction(v.title) { repo.setPrivacy(key, v) } },
                    ))
                })
            }
        }
    }
    gap()
    item {
        val repo = LocalRepository.current
        val sheet = LocalActionSheet.current
        val days = repo.accountTtlDays
        Section(header = "Automatically delete my account", footer = "If you do not come online at least once within this period, your account will be deleted along with all messages and contacts.") {
            Cell("If Away For", value = days?.let { com.abtin.tglass.data.accountTtlLabel(it) } ?: "…", divider = false, onClick = {
                sheet.show(SheetRequest(
                    title = "Delete my account if I'm away for",
                    actions = com.abtin.tglass.data.AccountTtlOptions.map { (d, label) -> SheetAction(label, bold = days == d) { repo.setAccountTtl(d) } },
                ))
            })
        }
    }
}

private fun LazyListScope.dataStorage() {
    item {
        val repo = LocalRepository.current
        val nav = LocalNavigator.current
        androidx.compose.runtime.LaunchedEffect(Unit) {
            repo.loadStorage()
            repo.loadDataUsage()
        }
        val usage = repo.dataUsage
        Section {
            Cell("Storage Usage", icon = TgIcons.SetData, iconColor = Blue, value = repo.storageInfo?.let { com.abtin.tglass.data.formatBytes(it.total) }, onClick = { nav.push(Route.SettingsPage(Page.StorageUsage)) })
            Cell("Data Usage", icon = TgIcons.SetData, iconColor = Green, value = usage?.let { com.abtin.tglass.data.formatBytes(it.totalSent + it.totalReceived) }, divider = false, onClick = { nav.push(Route.SettingsPage(Page.NetworkUsage)) })
        }
    }
    gap()
    item {
        val nav = LocalNavigator.current
        val context = androidx.compose.ui.platform.LocalContext.current
        AutoDownloadPrefs.attach(context)
        Section(header = "Automatic Media Download") {
            Cell(com.abtin.tglass.data.DownloadNetwork.Cellular.title, value = AutoDownloadPrefs.summary(com.abtin.tglass.data.DownloadNetwork.Cellular), onClick = { nav.push(Route.SettingsPage(Page.AutoDownloadCellular)) })
            Cell(com.abtin.tglass.data.DownloadNetwork.WiFi.title, value = AutoDownloadPrefs.summary(com.abtin.tglass.data.DownloadNetwork.WiFi), onClick = { nav.push(Route.SettingsPage(Page.AutoDownloadWifi)) })
            Cell(com.abtin.tglass.data.DownloadNetwork.Roaming.title, value = AutoDownloadPrefs.summary(com.abtin.tglass.data.DownloadNetwork.Roaming), divider = false, onClick = { nav.push(Route.SettingsPage(Page.AutoDownloadRoaming)) })
        }
    }
}

private val Languages = listOf("English" to "English", "Persian" to "فارسی", "Arabic" to "العربية", "German" to "Deutsch", "Spanish" to "Español", "French" to "Français", "Russian" to "Русский", "Turkish" to "Türkçe")

private fun LazyListScope.language() {
    item {
        val sheet = LocalActionSheet.current
        // The interface is English-only for now; messages in any language (and RTL) are shown as sent.
        Section(header = "Interface Language", footer = "TGlass is available in English for now. Messages in every language, including right-to-left ones, are shown as they were written.") {
            Languages.forEachIndexed { i, (en, native) ->
                Cell(native, subtitle = en, checked = i == 0, chevron = false, divider = i != Languages.lastIndex, onClick = {
                    if (i != 0) sheet.alert(en, "This language isn't available in TGlass yet.")
                })
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
    Box(Modifier.size(29.dp).clip(RoundedRectangle(7.dp)).background(Blue), contentAlignment = Alignment.Center) {
        Icon(TgIcons.SetDevices, Color.White, 29.dp)
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
        if (repo.isLive) LiveFolderList()
        else Section(header = "Chat Folders") {
            val toast = LocalToast.current
            Cell("Create New Folder", icon = TgIcons.SetFolders, iconColor = c.accent, titleColor = c.accent, chevron = false, onClick = { toast.show("Folders can be created with a real account") })
            repo.folders.drop(1).forEachIndexed { i, f ->
                Cell(f, subtitle = when (f) { "Personal" -> "Private chats"; "Work" -> "2 chats"; else -> "Unread chats" }, divider = i != repo.folders.size - 2)
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
        if (LocalRepository.current.isLive) {
            val uri = androidx.compose.ui.platform.LocalUriHandler.current
            Spacer(Modifier.height(24.dp))
            Section(footer = "Telegram Premium is purchased in the official Telegram apps.") {
                Cell("Learn More", titleColor = c.accent, chevron = false, divider = false, onClick = { runCatching { uri.openUri("https://telegram.org/faq_premium") } })
            }
        }
    }
}

