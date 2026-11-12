package com.abtin.tglass.data.td

import android.content.Context
import com.abtin.tglass.BuildConfig

/**
 * Telegram API credentials (https://my.telegram.org → API development tools).
 * They can be baked in at build time (`TG_API_ID` / `TG_API_HASH`) or entered once inside the app.
 */
class TdConfig(context: Context) {
    private val prefs = context.getSharedPreferences("tglass_td", Context.MODE_PRIVATE)

    val apiId: Int
        get() = prefs.getInt("apiId", 0).takeIf { it != 0 } ?: BuildConfig.TG_API_ID.takeIf { it != 0 } ?: DEFAULT_API_ID

    val apiHash: String
        get() = prefs.getString("apiHash", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.TG_API_HASH.ifBlank { DEFAULT_API_HASH }

    val hasCredentials: Boolean get() = apiId != 0 && apiHash.length >= 16

    fun save(apiId: Int, apiHash: String) {
        prefs.edit().putInt("apiId", apiId).putString("apiHash", apiHash.trim()).apply()
    }

    companion object {
        /**
         * Default credentials, chosen by the owner because my.telegram.org would not create an app for them:
         * the public ones of the open-source Telegram Desktop client. Telegram's API terms reserve these for the
         * official app, so replace them with your own (Settings → "Change API ID" on the phone screen) when possible.
         */
        const val DEFAULT_API_ID = 2040
        const val DEFAULT_API_HASH = "b18441a1ff607e10a989891a5462e627"
    }
}
