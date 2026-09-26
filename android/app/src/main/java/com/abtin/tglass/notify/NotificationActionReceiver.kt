package com.abtin.tglass.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.abtin.tglass.data.td.Td

/** Handles "Reply" and "Mark as Read" from message notifications. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra(Notifier.EXTRA_CHAT_ID, 0L)
        if (chatId == 0L) return
        val td = Td.get(context)
        when (intent.action) {
            Notifier.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim()
                if (!text.isNullOrEmpty()) td.sendText(chatId, text, null)
                td.markChatRead(chatId)
                Notifier.cancel(context, chatId)
            }
            Notifier.ACTION_READ -> {
                td.markChatRead(chatId)
                Notifier.cancel(context, chatId)
            }
        }
    }
}
