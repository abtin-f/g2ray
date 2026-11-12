package com.abtin.tglass.core.design

import androidx.compose.ui.graphics.Color

/*
 * Derived design colors that are not separate per-theme slots in Telegram-iOS: they are the same translucent
 * white (dark themes) or black (light themes) overlays everywhere. Kept here so screens never hand-roll
 * `if (isDark) Color.White.copy(..) else Color.Black.copy(..)`.
 */

/** Pressed / highlighted row inside glass surfaces (context menu rows). */
val TgColors.pressOverlay: Color get() = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)

/** Lighter press highlight for rows in sheets / switchers. */
val TgColors.pressLight: Color get() = if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.06f)

/** Hairline separators inside glass menus. */
val TgColors.glassHairline: Color get() = if (isDark) Color.White.copy(0.16f) else Color.Black.copy(0.12f)

/** Subtle chip / round-button fill. */
val TgColors.subtleFill: Color get() = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.06f)

/** Alert surface (the glass veil over the blurred backdrop) of centered alerts. */
val TgColors.alertSurface: Color get() = if (isDark) Color(0xFF1C1C1D).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.8f)

/** Selected segment thumb of a segmented control. */
val TgColors.segmentThumb: Color get() = if (isDark) Color(0xFF636366) else Color.White

/** Toast pill fill. */
val TgColors.toastSurface: Color get() = if (isDark) Color(0xFF2C2C2E) else Color(0xFF333333).copy(alpha = 0.92f)

/** In-app notification banner veil. */
val TgColors.bannerSurface: Color get() = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.72f) else Color(0xFFF9F9FB).copy(alpha = 0.74f)

/**
 * iOS system colors used for Settings icon tiles, folder-type icons and storage charts. These are fixed in
 * Telegram-iOS (they do not change with the theme), so they live in one object instead of per-file privates.
 */
object TgPalette {
    val Red = Color(0xFFFF3B30)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFCC00)
    val Green = Color(0xFF34C759)
    val Teal = Color(0xFF32ADE6)
    val Blue = Color(0xFF007AFF)
    val Purple = Color(0xFFAF52DE)
    val Gray = Color(0xFF8E8E93)
    /** Telegram's light-blue avatar gradient (Saved Messages, devices, privacy hero). */
    val SavedTop = Color(0xFF72D5FD)
    val SavedBottom = Color(0xFF2A9EF1)
}
