package com.abtin.tglass.features.chatlist

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether the "Archived Chats" row is hidden above the chat list (revealed by pulling the list down) or pinned
 * as the first row. Telegram-iOS keeps this per device, so it lives in local preferences, not on the server.
 * Hidden by default.
 */
object ArchivePrefs {
    private const val FILE = "tglass_chatlist"
    private const val KEY_HIDDEN = "archive_hidden"

    private var prefs: SharedPreferences? = null

    var hidden by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        hidden = p.getBoolean(KEY_HIDDEN, true)
    }

    fun updateHidden(value: Boolean) {
        hidden = value
        prefs?.edit()?.putBoolean(KEY_HIDDEN, value)?.apply()
    }
}
