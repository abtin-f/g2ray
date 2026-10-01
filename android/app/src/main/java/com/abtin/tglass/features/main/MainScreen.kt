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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import com.kyant.shapes.Capsule
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.GlassIconButton
import com.abtin.tglass.core.glass.LiquidBottomTab
import com.abtin.tglass.core.glass.LiquidBottomTabs
import com.abtin.tglass.core.glass.LiquidBottomTabLayer
import com.abtin.tglass.core.glass.LocalLiquidBottomTabLayer
import com.abtin.tglass.core.glass.LocalBackdrop
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.features.calls.CallsScreen
import com.abtin.tglass.features.chatlist.ChatListScreen
import com.abtin.tglass.features.contacts.ContactsScreen
import com.abtin.tglass.features.settings.SettingsScreen
import com.abtin.tglass.ui.components.Badge
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.TgIcons
import com.abtin.tglass.ui.components.LottieIcon
import com.abtin.tglass.ui.components.TgAnimations
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
    val sheet = com.abtin.tglass.ui.components.LocalActionSheet.current
    val c = TgTheme.colors

    Box(Modifier.fillMaxSize().background(c.background)) {
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            val tabFade = remember { androidx.compose.animation.core.Animatable(1f) }
            androidx.compose.runtime.LaunchedEffect(tab) {
                tabFade.snapTo(0f)
                tabFade.animateTo(1f, androidx.compose.animation.core.tween(220))
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = 0.4f + 0.6f * tabFade.value
                val s = 0.985f + 0.015f * tabFade.value
                scaleX = s; scaleY = s
            }) {
            holder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> ContactsScreen(backdrop, isTab = true)
                    1 -> CallsScreen(backdrop, isTab = true)
                    2 -> ChatListScreen(backdrop, tabBar)
                    else -> SettingsScreen(backdrop)
                }
            }
            }

            AnimatedVisibility(
                visible = !tabBar.hidden,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                // Read only where the badge is drawn, so unread-count changes don't recompose the whole tab bar.
                val unread by remember(repo) { androidx.compose.runtime.derivedStateOf { repo.chats.filter { !it.archived && !it.muted }.sumOf { it.unread } } }
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
                        TabItem(TgAnimations.TabContacts, "Contacts", selected = tab == 0) { tab = 0 }
                        TabItem(TgAnimations.TabCalls, "Calls", selected = tab == 1) { tab = 1 }
                        TabItem(TgAnimations.TabChats, "Chats", selected = tab == 2, badge = unread) { tab = 2 }
                        TabItem(
                            TgAnimations.TabSettings, "Settings", selected = tab == 3,
                            onLongClick = if (repo.isLive) ({ com.abtin.tglass.features.settings.showAccountSwitcher(repo, sheet) }) else null,
                        ) { tab = 3 }
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(
                        TgIcons.IcSearch,
                        onClick = { tab = 2; tabBar.searchRequests++ },
                        size = 64.dp,
                        iconSize = 28.dp,
                        contentDescription = "Search",
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    animation: Int,
    label: String,
    selected: Boolean,
    badge: Int = 0,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = TgTheme.colors
    val layer = LocalLiquidBottomTabLayer.current
    val overlay = layer == LiquidBottomTabLayer.Overlay
    LiquidBottomTab(onClick = onClick, onLongClick = onLongClick) {
        // The icon slot is shorter than the Lottie canvas: Telegram's tab animations leave ~25% empty margin
        // around the glyph, so a 44dp canvas gives the ~24pt glyph of the iOS 26 tab bar.
        Box(Modifier.size(width = 44.dp, height = 28.dp), contentAlignment = Alignment.Center) {
            if (!overlay) {
                Box(Modifier.requiredSize(44.dp)) {
                    // Telegram-iOS tab icons are Lottie animations that play when the tab gets selected.
                    LottieIcon(animation, c.text, 44.dp, playKey = if (selected) label else null, play = selected)
                }
            } else if (badge > 0) {
                // Drawn above the selection pill so it stays red instead of being tinted blue.
                TabBadge(badge, Modifier.align(Alignment.TopCenter).offset(x = 15.dp, y = (-4).dp).wrapContentSize(unbounded = true))
            }
        }
        T(
            label,
            TgTheme.type.caption2.copy(fontSize = 10.5.sp, lineHeight = 13.sp),
            if (overlay) Color.Transparent else c.text,
            maxLines = 1,
            weight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}

/** iOS tab bar badge: a red capsule (UITabBarItem.badgeValue), not the blue chat-list counter. */
@Composable
private fun TabBadge(count: Int, modifier: Modifier = Modifier) {
    val c = TgTheme.colors
    Box(
        modifier
            .defaultMinSize(minWidth = 18.dp)
            .height(18.dp)
            .clip(Capsule())
            .background(c.destructive)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(
            com.abtin.tglass.ui.components.formatCount(count),
            TgTheme.type.footnote.copy(fontSize = 12.sp, lineHeight = 14.sp),
            Color.White,
            maxLines = 1,
            weight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}
