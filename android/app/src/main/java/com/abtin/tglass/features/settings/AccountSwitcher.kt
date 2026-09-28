package com.abtin.tglass.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.core.glass.glass
import com.abtin.tglass.data.AccountInfo
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActivityIndicator
import com.abtin.tglass.ui.components.Haptics
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.LocalContextMenu
import com.abtin.tglass.ui.components.Separator
import com.abtin.tglass.ui.components.T
import com.abtin.tglass.ui.components.iosClickable
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Telegram-iOS account switch: the whole app fades out and scales down slightly, the target account's avatar
 * with a spinner shows on the plain background while TDLib restarts on its database, then the new account's UI
 * fades back in (and the old one comes back the same way if the switch failed). The background is the theme's,
 * so there is never a black flash.
 */
@Composable
fun AccountSwitchTransition(repo: TelegramRepository, content: @Composable () -> Unit) {
    val target = repo.switchingAccount
    // The overlay keeps showing the last target while it fades out.
    val lastTarget = remember { arrayOf<AccountInfo?>(null) }
    if (target != null) lastTarget[0] = target
    val animations = LocalAppSettings.current.animations
    val progress = animateFloatAsState(
        targetValue = if (target != null) 1f else 0f,
        animationSpec = when {
            !animations -> tween<Float>(0)
            target != null -> tween<Float>(220, easing = FastOutSlowInEasing)
            else -> tween<Float>(340, easing = LinearOutSlowInEasing)
        },
        label = "accountSwitch",
    )
    val c = TgTheme.colors
    Box(Modifier.fillMaxSize().background(c.background)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = progress.value
                alpha = 1f - p
                val s = 1f - 0.04f * p
                scaleX = s
                scaleY = s
            }
        ) { content() }
        val shown = lastTarget[0]
        // Recompose only when the overlay appears / goes, not on every animation frame.
        val overlayOn by remember { derivedStateOf { progress.value > 0.001f } }
        if (overlayOn && shown != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    // Nothing underneath may be tapped while the accounts change.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                    .graphicsLayer { alpha = progress.value },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier.graphicsLayer {
                        val s = 0.86f + 0.14f * progress.value
                        scaleX = s
                        scaleY = s
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AccountAvatar(shown, 76.dp)
                    Spacer(Modifier.height(12.dp))
                    T(shown.name, TgTheme.type.headline, c.text, maxLines = 1)
                    Spacer(Modifier.height(14.dp))
                    ActivityIndicator(22.dp)
                }
            }
        }
    }
}

/** Visibility of the account switcher opened by long-pressing the Settings tab. */
object AccountSwitcherState {
    var visible by mutableStateOf(false)
}

/**
 * Long press on the Settings tab (Telegram iOS): a glass menu above the tab with the signed-in accounts (checkmark
 * on the active one) and "Add Account". Tapping another account moves the checkmark to it, then the menu closes
 * and the app cross-fades to that account ([AccountSwitchTransition]).
 */
@Composable
fun AccountSwitcherHost() {
    val repo = LocalRepository.current
    val state = AccountSwitcherState
    BackHandler(enabled = state.visible) { state.visible = false }
    AnimatedVisibility(state.visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.2f))
                .clickable(remember { MutableInteractionSource() }, null) { state.visible = false }
        )
    }
    // Outside the context-menu host's recorded content (like the action sheet), so its glass can refract it.
    val backdrop = LocalContextMenu.current.backdrop
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            state.visible && repo.isLive,
            enter = fadeIn(tween(160)) + scaleIn(spring(0.78f, 520f), initialScale = 0.5f, transformOrigin = TransformOrigin(0.85f, 1f)),
            exit = fadeOut(tween(150)) + scaleOut(tween(170), targetScale = 0.6f, transformOrigin = TransformOrigin(0.85f, 1f)),
            modifier = Modifier.navigationBarsPadding().padding(end = 16.dp, bottom = 84.dp),
        ) {
            AccountMenu(repo, backdrop, onClose = { state.visible = false })
        }
    }
}

@Composable
private fun AccountMenu(repo: TelegramRepository, backdrop: com.kyant.backdrop.Backdrop?, onClose: () -> Unit) {
    val c = TgTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val accounts = repo.accounts
    val canAdd = repo.canAddAccount
    // Slot with the checkmark: the active account until another one is tapped.
    var checked by remember { mutableIntStateOf(accounts.firstOrNull { it.active }?.slot ?: -1) }
    var busy by remember { mutableStateOf(false) }
    val surface = if (c.isDark) Color(0xFF1E1E20).copy(alpha = 0.74f) else Color(0xFFF7F7F9).copy(alpha = 0.78f)
    Column(
        Modifier
            .width(264.dp)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .glass(shape = RoundedRectangle(24.dp), backdrop = backdrop, surface = surface, blurRadius = 18.dp, lensHeight = 14.dp, lensAmount = 20.dp),
    ) {
        accounts.forEachIndexed { i, a ->
            if (i > 0) Separator(startPadding = 56.dp)
            AccountMenuRow(a, checked = a.slot == checked, index = i) {
                if (busy) return@AccountMenuRow
                if (a.slot == checked) { onClose(); return@AccountMenuRow }
                busy = true
                checked = a.slot
                Haptics.tick(view)
                scope.launch {
                    // Let the checkmark move before the menu closes and the app fades to the other account.
                    delay(260)
                    onClose()
                    repo.switchAccount(a.slot)
                }
            }
        }
        if (canAdd) {
            if (accounts.isNotEmpty()) Box(Modifier.fillMaxWidth().height(8.dp).background(c.text.copy(alpha = if (c.isDark) 0.06f else 0.04f)))
            Row(
                Modifier
                    .fillMaxWidth()
                    .iosClickable(highlight = if (c.isDark) Color.White.copy(0.1f) else Color.Black.copy(0.06f)) {
                        if (busy) return@iosClickable
                        onClose()
                        repo.addAccount()
                    }
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("Add Account", TgTheme.type.body.copy(fontSize = 17.sp), c.text, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(IosIcons.Plus, c.text, 20.dp)
            }
        }
    }
}

@Composable
private fun AccountMenuRow(a: AccountInfo, checked: Boolean, index: Int, onClick: () -> Unit) {
    val c = TgTheme.colors
    // Rows arrive one after another, sliding up a little (staggered like Telegram's context menus).
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(30L * index)
        appear.animateTo(1f, spring(0.8f, 420f))
    }
    val check by animateFloatAsState(if (checked) 1f else 0f, spring(0.55f, 520f), label = "accountCheck")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 10.dp.toPx()
            }
            .iosClickable(highlight = if (c.isDark) Color.White.copy(0.1f) else Color.Black.copy(0.06f), onClick = onClick)
            .height(52.dp)
            .padding(start = 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(a, 30.dp)
        Spacer(Modifier.width(12.dp))
        T(
            a.name, TgTheme.type.body.copy(fontSize = 17.sp), c.text, maxLines = 1,
            weight = if (checked) FontWeight.SemiBold else null,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (check > 0.01f) {
                Icon(
                    IosIcons.Checkmark, c.accent, 20.dp,
                    modifier = Modifier.graphicsLayer {
                        alpha = check.coerceIn(0f, 1f)
                        scaleX = 0.4f + 0.6f * check
                        scaleY = 0.4f + 0.6f * check
                    },
                )
            }
        }
    }
}
