package com.abtin.tglass.features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abtin.tglass.core.design.TgTheme
import com.abtin.tglass.data.AccountInfo
import com.abtin.tglass.data.ImageRef
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.features.main.LocalRepository
import com.abtin.tglass.ui.components.ActionSheetState
import com.abtin.tglass.ui.components.Avatar
import com.abtin.tglass.ui.components.Cell
import com.abtin.tglass.ui.components.Icon
import com.abtin.tglass.ui.components.IosIcons
import com.abtin.tglass.ui.components.Section
import com.abtin.tglass.ui.components.TgImage

/**
 * Round avatar of a signed-in account. The active one uses its live profile photo; the others (whose TDLib
 * is not running) show the photo cached while they were active, over their initials.
 */
@Composable
fun AccountAvatar(account: AccountInfo, size: Dp) {
    if (account.active) {
        Avatar(account.name, account.userId, size, photoPeer = account.userId)
        return
    }
    Box(Modifier.size(size)) {
        // A peer id no chat uses: initials only, never another account's cached photo of the same id.
        Avatar(account.name, account.userId, size, photoPeer = Long.MIN_VALUE)
        val path = account.photoPath
        if (path != null) {
            TgImage(ImageRef(0, path), Modifier.size(size).clip(CircleShape), maxPx = 192)
        }
    }
}

/**
 * Telegram iOS Settings: the other accounts (tap to switch) and "Add Account", in their own section under the
 * profile. Hidden in the demo, which has a single account.
 */
@Composable
internal fun AccountsSection() {
    val repo = LocalRepository.current
    if (!repo.isLive) return
    val others = repo.accounts.filter { !it.active }
    val canAdd = repo.canAddAccount
    if (others.isEmpty() && !canAdd) return
    val c = TgTheme.colors
    Section {
        others.forEachIndexed { i, a ->
            Cell(
                a.name,
                leading = { AccountAvatar(a, 29.dp) },
                chevron = false,
                divider = i != others.lastIndex || canAdd,
                onClick = { repo.switchAccount(a.slot) },
            )
        }
        if (canAdd) {
            Cell(
                "Add Account",
                leading = { Box(Modifier.size(29.dp), contentAlignment = Alignment.Center) { Icon(IosIcons.Plus, c.accent, 22.dp) } },
                titleColor = c.accent,
                chevron = false,
                divider = false,
                onClick = { repo.addAccount() },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

/** Long press on the Settings tab (Telegram iOS): pick another signed-in account or add one ([AccountSwitcherHost]). */
@Suppress("UNUSED_PARAMETER")
fun showAccountSwitcher(repo: TelegramRepository, sheet: ActionSheetState) {
    if (!repo.isLive) return
    if (repo.accounts.isEmpty() && !repo.canAddAccount) return
    AccountSwitcherState.visible = true
}
