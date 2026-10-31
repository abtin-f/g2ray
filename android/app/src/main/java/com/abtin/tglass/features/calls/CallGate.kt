package com.abtin.tglass.features.calls

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import com.abtin.tglass.core.media.Sharing
import com.abtin.tglass.core.navigation.Navigator
import com.abtin.tglass.core.navigation.Route
import com.abtin.tglass.data.TelegramRepository
import com.abtin.tglass.ui.components.ActionSheetState
import com.abtin.tglass.ui.components.SheetAction
import com.abtin.tglass.ui.components.SheetRequest
import com.abtin.tglass.ui.components.ToastState

/**
 * Telegram calls need the native tgcalls engine, which TGlass does not ship. The demo keeps its simulated call
 * screen; a real account gets an honest alert that hands the call off to the official Telegram app instead.
 */
fun requestCall(
    context: Context,
    repo: TelegramRepository,
    nav: Navigator,
    sheet: ActionSheetState,
    toast: ToastState,
    userId: Long,
    video: Boolean,
) {
    if (!repo.isLive) {
        nav.push(Route.ActiveCall(userId, video))
        return
    }
    val user = repo.user(userId)
    val who = user?.name?.takeIf { it.isNotBlank() }
    sheet.show(
        SheetRequest(
            title = "Calls aren't available in TGlass yet",
            message = if (who != null) "You can ${if (video) "video call" else "call"} $who from the official Telegram app."
            else "You can make calls from the official Telegram app.",
            actions = listOf(
                SheetAction("Open in Telegram") {
                    if (!Sharing.openInTelegram(context, user?.username, userId)) {
                        toast.show("Telegram isn't installed", Icons.Rounded.ErrorOutline)
                    }
                },
            ),
            alert = true,
        )
    )
}

/** The Calls tab's "new call" button when nobody is chosen yet. */
fun requestNewCall(context: Context, sheet: ActionSheetState, toast: ToastState) {
    sheet.show(
        SheetRequest(
            title = "Calls aren't available in TGlass yet",
            message = "You can make voice and video calls from the official Telegram app.",
            actions = listOf(
                SheetAction("Open Telegram") {
                    if (!Sharing.openTelegramApp(context)) toast.show("Telegram isn't installed", Icons.Rounded.ErrorOutline)
                },
            ),
            alert = true,
        )
    )
}
