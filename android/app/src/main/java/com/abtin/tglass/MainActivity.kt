package com.abtin.tglass

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.abtin.tglass.core.design.AppSettings
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.ThemeMode
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.design.TgThemeProvider
import com.abtin.tglass.core.navigation.IOSNavHost
import com.abtin.tglass.core.navigation.LocalNavigator
import com.abtin.tglass.core.navigation.Navigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.core.navigation.isDarkChrome
import com.abtin.tglass.data.DemoRepository
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.data.td.AuthStep
import com.abtin.tglass.data.td.Td
import com.abtin.tglass.data.td.TdRepository
import com.abtin.tglass.features.auth.ApiSetupScreen
import com.abtin.tglass.features.auth.PasswordScreen
import com.abtin.tglass.features.auth.RegisterScreen
import com.abtin.tglass.features.auth.CodeScreen
import com.abtin.tglass.features.auth.PhoneScreen
import com.abtin.tglass.features.auth.WelcomeScreen
import com.abtin.tglass.features.calls.ActiveCallScreen
import com.abtin.tglass.features.calls.CallsPage
import com.abtin.tglass.features.chat.ChatScreen
import com.abtin.tglass.features.chatlist.ArchiveScreen
import com.abtin.tglass.features.contacts.NewMessageScreen
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.features.main.MainScreen
import com.abtin.tglass.features.media.MediaViewer
import com.abtin.tglass.features.profile.ProfileScreen
import com.abtin.tglass.features.profile.UserProfileScreen
import com.abtin.tglass.features.settings.Page
import com.abtin.tglass.features.settings.SettingsPageScreen
import com.abtin.tglass.features.stories.StoryViewer
import com.abtin.tglass.ui.components.ActionSheetHost
import com.abtin.tglass.ui.components.ActionSheetState
import com.abtin.tglass.ui.components.ContextMenuHost
import com.abtin.tglass.ui.components.ContextMenuState
import com.abtin.tglass.ui.components.LocalActionSheet
import com.abtin.tglass.ui.components.LocalContextMenu
import com.abtin.tglass.ui.components.LocalToast
import com.abtin.tglass.ui.components.ToastHost
import com.abtin.tglass.ui.components.ToastState

/**
 * Optional launch extras (used by CI to capture screenshots):
 * `screen` = welcome | chats | chat | chat_menu | profile | group | channel | settings | appearance | power | contacts | calls | devices
 * `theme`  = light | dark
 */
object DebugLaunch {
    var screen: String? = null
    var theme: String? = null
    var autoMenuMessageId: Long? = null

    fun parse(intent: Intent?) {
        screen = intent?.getStringExtra("screen")
        theme = intent?.getStringExtra("theme")
    }
}

class MainActivity : ComponentActivity() {
    private val demo by lazy { DemoRepository(lifecycleScope) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DebugLaunch.parse(intent)
        val settings = AppSettings(this)
        DebugLaunch.theme?.let { settings.updateTheme(if (it == "dark") ThemeMode.Dark else ThemeMode.Light) }
        if (DebugLaunch.screen != null && DebugLaunch.screen != "welcome") settings.updateLoggedIn(true)
        setContent { App(settings) }
    }

    @Composable
    private fun App(settings: AppSettings) {
        val scope = rememberCoroutineScope()
        // Screenshot runs and "Explore the Demo" use local sample data; everything else is a real account.
        val live: TdRepository? = if (DebugLaunch.screen != null || settings.demoMode) null else Td.get(this)
        val repo: TelegramRepository = live ?: demo
        val nav = remember {
            val start = if (settings.loggedIn && DebugLaunch.screen != "welcome") Route.Main else Route.Welcome
            Navigator(start, scope).also { n ->
                when (DebugLaunch.screen) {
                    "chat" -> n.pushInstant(Route.Chat(101))
                    "chat_menu" -> { n.pushInstant(Route.Chat(101)); DebugLaunch.autoMenuMessageId = repo.messages(101).lastOrNull { !it.outgoing }?.id }
                    "group" -> n.pushInstant(Route.Chat(102))
                    "channel" -> n.pushInstant(Route.Chat(104))
                    "profile" -> n.pushInstant(Route.Profile(101))
                    "appearance" -> n.pushInstant(Route.SettingsPage(Page.Appearance))
                    "power" -> n.pushInstant(Route.SettingsPage(Page.PowerSaving))
                    "devices" -> n.pushInstant(Route.SettingsPage(Page.Devices))
                    "calls" -> n.pushInstant(Route.Calls)
                }
            }
        }
        val menu = remember { ContextMenuState() }
        val sheet = remember { ActionSheetState() }
        val toast = remember { ToastState() }

        if (live != null) {
            // The login flow follows TDLib's authorization state.
            val auth = live.auth
            LaunchedEffect(auth) {
                when (auth) {
                    is AuthStep.WaitCode -> if (nav.top !is Route.Code) nav.push(Route.Code(auth.phone))
                    is AuthStep.WaitPassword -> if (nav.top != Route.Password) nav.push(Route.Password)
                    AuthStep.WaitRegistration -> if (nav.top != Route.Register) nav.push(Route.Register)
                    AuthStep.Ready -> if (!settings.loggedIn || nav.top in AuthRoutes || nav.top is Route.Code) {
                        settings.updateLoggedIn(true)
                        nav.resetTo(Route.Main)
                    }
                    AuthStep.WaitPhone, AuthStep.NeedCredentials -> if (settings.loggedIn) {
                        // Session ended (logged out here or terminated from another device).
                        settings.updateLoggedIn(false)
                        nav.resetTo(Route.Welcome)
                    }
                    is AuthStep.Unsupported -> toast.show(auth.what, androidx.compose.material.icons.Icons.Rounded.ErrorOutline)
                    else -> {}
                }
            }
            LaunchedEffect(live) { live.errors.collect { toast.show(it, androidx.compose.material.icons.Icons.Rounded.ErrorOutline) } }
        }

        CompositionLocalProvider(LocalAppSettings provides settings) {
            TgThemeProvider(settings) {
                val dark = TgTheme.colors.isDark
                val view = LocalView.current
                val top = nav.top
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    val lightBars = !dark && !top.isDarkChrome
                    controller.isAppearanceLightStatusBars = lightBars
                    controller.isAppearanceLightNavigationBars = lightBars
                }
                CompositionLocalProvider(
                    LocalRepository provides repo,
                    LocalNavigator provides nav,
                    LocalContextMenu provides menu,
                    LocalActionSheet provides sheet,
                    LocalToast provides toast,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ContextMenuHost(menu) {
                            IOSNavHost(nav) { route -> Screen(route) }
                        }
                        ActionSheetHost(sheet)
                        ToastHost(toast)
                    }
                }
            }
        }
    }
}

private val AuthRoutes = setOf(Route.Welcome, Route.Phone, Route.ApiSetup, Route.Password, Route.Register)

@Composable
private fun Screen(route: Route) {
    when (route) {
        Route.Welcome -> WelcomeScreen()
        Route.Phone -> PhoneScreen()
        is Route.Code -> CodeScreen(route.phone)
        Route.ApiSetup -> ApiSetupScreen()
        Route.Password -> PasswordScreen()
        Route.Register -> RegisterScreen()
        Route.Main -> MainScreen()
        is Route.Chat -> ChatScreen(route.chatId)
        is Route.Profile -> ProfileScreen(route.chatId)
        is Route.UserProfile -> UserProfileScreen(route.userId)
        Route.Archive -> ArchiveScreen()
        Route.NewMessage -> NewMessageScreen()
        is Route.Stories -> StoryViewer(route.startUserId)
        is Route.ActiveCall -> ActiveCallScreen(route.userId, route.video)
        is Route.Media -> MediaViewer(route.chatId, route.messageId)
        is Route.SettingsPage -> SettingsPageScreen(route.page)
        Route.Calls -> CallsPage()
    }
}
