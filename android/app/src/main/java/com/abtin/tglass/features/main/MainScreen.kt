package com.abtin.tglass.features.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LiquidBottomTab
import com.abtin.tglass.core.glass.LiquidBottomTabs
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.features.calls.CallsScreen
import com.abtin.tglass.features.chatlist.ChatListScreen
import com.abtin.tglass.features.contacts.ContactsScreen
import com.abtin.tglass.features.settings.SettingsScreen
import com.abtin.tglass.ui.components.Badge
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.T
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

val LocalRepository = staticCompositionLocalOf<TelegramRepository> { error("Repository not provided") }

/** Lets a tab temporarily hide the floating tab bar (search / edit mode). */
class TabBarController {
    var hidden by mutableStateOf(false)
    /** Incremented by the tab bar's search button; the chat list opens search when it changes. */
    var searchRequests by mutableIntStateOf(0)
}

@Composable
fun MainScreen() {
    val repo = LocalRepository.current
    var tab by rememberSaveable {
        mutableIntStateOf(
            when (com.abtin.tglass.DebugLaunch.screen) {
                "contacts" -> 0
                "calls_tab" -> 1
                "settings_tab" -> 3
                else -> 2
            }
        )
    }
    val backdrop = rememberLayerBackdrop()
    val holder = rememberSaveableStateHolder()
    val tabBar = remember { TabBarController() }
    val c = TgTheme.colors

    Box(Modifier.fillMaxSize().background(c.background)) {
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            holder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> ContactsScreen(backdrop, isTab = true)
                    1 -> CallsScreen(backdrop, isTab = true)
                    2 -> ChatListScreen(backdrop, tabBar)
                    else -> SettingsScreen(backdrop)
                }
            }

            AnimatedVisibility(
                visible = !tabBar.hidden,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val unread = repo.chats.filter { !it.archived && !it.muted }.sumOf { it.unread }
                // Telegram-iOS TabBarComponent: 64pt capsule (56 + 2×4), max width 500, then an 8pt gap and a 64pt search circle.
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .widthIn(max = 500.dp + 72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidBottomTabs(
                        selectedTabIndex = { tab },
                        onTabSelected = { tab = it },
                        backdrop = backdrop,
                        tabsCount = 4,
                        modifier = Modifier.weight(1f),
                    ) {
                        TabItem(Icons.Rounded.AccountCircle, "Contacts") { tab = 0 }
                        TabItem(Icons.Rounded.Call, "Calls") { tab = 1 }
                        TabItem(Icons.Rounded.Forum, "Chats", badge = unread) { tab = 2 }
                        TabItem(Icons.Rounded.Settings, "Settings") { tab = 3 }
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(
                        Icons.Rounded.Search,
                        onClick = { tab = 2; tabBar.searchRequests++ },
                        size = 64.dp,
                        iconSize = 26.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(icon: ImageVector, label: String, badge: Int = 0, onClick: () -> Unit) {
    val c = TgTheme.colors
    LiquidBottomTab(onClick = onClick) {
        Box {
            Icon(icon, c.text, 26.dp)
            if (badge > 0) {
                Badge(badge, modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-4).dp).size(width = 26.dp, height = 18.dp))
            }
        }
        T(label, TgTheme.type.caption2.copy(fontSize = 10.sp, lineHeight = 12.sp), c.text, maxLines = 1, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}
