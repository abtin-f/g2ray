package com.abtin.tglass.features.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color

/**
 * Alternative launcher icons (Settings → Appearance → App Icon). Each one is an `<activity-alias>` of
 * MainActivity in AndroidManifest.xml; exactly one alias is enabled at a time.
 */
object AppIcons {
    class Option(val title: String, val alias: String, val background: List<Color>, val glyph: Color)

    val options = listOf(
        Option("Default", "com.abtin.tglass.IconDefault", listOf(Color(0xFF37AEE2), Color(0xFF1E96C8)), Color.White),
        Option("Night", "com.abtin.tglass.IconNight", listOf(Color(0xFF2B2B2E), Color(0xFF0B0B0C)), Color.White),
        Option("Light", "com.abtin.tglass.IconLight", listOf(Color(0xFFFFFFFF), Color(0xFFEDEFF3)), Color(0xFF2AABEE)),
        Option("Premium", "com.abtin.tglass.IconPremium", listOf(Color(0xFF6B93FF), Color(0xFFB36DF6), Color(0xFFFF7A9C)), Color.White),
    )

    /** Enables the alias of [index] and disables the others. The launcher may take a moment to refresh. */
    fun apply(context: Context, index: Int): Boolean = runCatching {
        val pm = context.packageManager
        val chosen = options.getOrNull(index) ?: return false
        pm.setComponentEnabledSetting(
            ComponentName(context, chosen.alias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        options.filter { it !== chosen }.forEach {
            pm.setComponentEnabledSetting(
                ComponentName(context, it.alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        true
    }.getOrDefault(false)
}
