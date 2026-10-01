package com.abtin.tglass.features.groups

import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.ui.components.ActionSheetState
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest

/** Telegram's mute durations as an action sheet (plus Unmute when the chat is muted). */
fun showMuteOptions(sheet: ActionSheetState, repo: TelegramRepository, chatId: Long) {
    val muted = repo.chat(chatId)?.muted == true
    sheet.show(
        SheetRequest(
            title = "Mute notifications",
            actions = listOfNotNull(
                if (muted) SheetAction("Unmute", bold = true) { repo.muteFor(chatId, 0) } else null,
                SheetAction("Mute for 1 hour") { repo.muteFor(chatId, 60 * 60) },
                SheetAction("Mute for 8 hours") { repo.muteFor(chatId, 8 * 60 * 60) },
                SheetAction("Mute for 2 days") { repo.muteFor(chatId, 2 * 24 * 60 * 60) },
                SheetAction("Mute forever", destructive = true) { repo.muteFor(chatId, TelegramRepository.MUTE_FOREVER) },
            ),
        )
    )
}

/** The Mute / Unmute button behaviour: unmute right away, or ask for how long to mute. */
fun toggleMuteWithOptions(sheet: ActionSheetState, repo: TelegramRepository, chatId: Long) {
    if (repo.chat(chatId)?.muted == true) repo.muteFor(chatId, 0) else showMuteOptions(sheet, repo, chatId)
}
