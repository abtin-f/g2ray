package com.abtin.tglass.notify

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Settings → Notifications → In-App Sounds / In-App Vibrate, used by the in-app banner. */
object InAppAlerts {
    private var prefs: SharedPreferences? = null

    var sounds by mutableStateOf(true)
        private set
    var vibrate by mutableStateOf(true)
        private set

    fun attach(context: Context) {
        if (prefs != null) return
        val p = runCatching { context.applicationContext.getSharedPreferences("tglass_inapp", Context.MODE_PRIVATE) }.getOrNull() ?: return
        prefs = p
        sounds = p.getBoolean("sounds", true)
        vibrate = p.getBoolean("vibrate", true)
    }

    fun updateSounds(v: Boolean) {
        sounds = v
        prefs?.edit()?.putBoolean("sounds", v)?.apply()
    }

    fun updateVibrate(v: Boolean) {
        vibrate = v
        prefs?.edit()?.putBoolean("vibrate", v)?.apply()
    }

    /** Plays the default notification sound once (in-app banner), if enabled. */
    fun playSound(context: Context) {
        if (!sounds) return
        runCatching {
            val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION) ?: return
            android.media.RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
        }
    }
}
