package com.abtin.tglass.core.design

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Design tokens modelled after Telegram for iOS (spec §2). */
@Immutable
data class TgColors(
    val isDark: Boolean,
    /** Grouped background (Settings, Profile). */
    val groupedBackground: Color,
    /** Plain list background (Chats, Contacts). */
    val background: Color,
    /** Cells inside grouped sections. */
    val cell: Color,
    val cellPressed: Color,
    val text: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val separator: Color,
    val accent: Color,
    val destructive: Color,
    val green: Color,
    val orange: Color,
    val mutedBadge: Color,
    val searchField: Color,
    val bubbleIn: Color,
    val bubbleOut: Color,
    /** Night theme paints outgoing bubbles with a gradient (#61BCF9 → #0088FF). */
    val bubbleOutGradient: List<Color>? = null,
    val bubbleInText: Color,
    val bubbleOutText: Color,
    val bubbleInMeta: Color,
    val bubbleOutMeta: Color,
    val bubbleOutAccent: Color,
    val glassSurface: Color,
    val glassOpaque: Color,
    val menuSurface: Color,
    val scrim: Color,
    val wallpaper: List<Color>,
    val wallpaperPattern: Color,
    val serviceBubble: Color,
    val onlineDot: Color = Color(0xFF4CC91F),
    val listCheckmark: Color = Color(0xFF0088FF),
    val pinnedRow: Color = Color(0xFFF7F7F7),
)

val LightColors = TgColors(
    isDark = false,
    groupedBackground = Color(0xFFEFEFF4),
    background = Color(0xFFFFFFFF),
    cell = Color(0xFFFFFFFF),
    cellPressed = Color(0xFFD9D9D9),
    text = Color(0xFF000000),
    secondaryText = Color(0xFF8E8E93),
    tertiaryText = Color(0xFFC7C7CC),
    separator = Color(0xFFC8C7CC),
    accent = Color(0xFF0088FF),
    destructive = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    mutedBadge = Color(0xFFB6B6BB),
    searchField = Color(0x1F767680),
    bubbleIn = Color(0xFFFFFFFF),
    bubbleOut = Color(0xFFE1FFC7),
    bubbleInText = Color(0xFF000000),
    bubbleOutText = Color(0xFF000000),
    bubbleInMeta = Color(0xFF525252).copy(alpha = 0.6f),
    bubbleOutMeta = Color(0xFF008C09).copy(alpha = 0.8f),
    bubbleOutAccent = Color(0xFF00A700),
    glassSurface = Color(0xFFFAFAFA).copy(alpha = 0.45f),
    glassOpaque = Color(0xFFFFFFFF).copy(alpha = 0.92f),
    menuSurface = Color(0xFFF7F7F7).copy(alpha = 0.72f),
    scrim = Color(0xFF000000).copy(alpha = 0.18f),
    wallpaper = listOf(Color(0xFFDBDDBB), Color(0xFF6BA587), Color(0xFFD5D88D), Color(0xFF88B884)),
    wallpaperPattern = Color(0xFF000000).copy(alpha = 0.09f),
    serviceBubble = Color(0xFF000000).copy(alpha = 0.2f),
)

val DarkColors = TgColors(
    isDark = true,
    groupedBackground = Color(0xFF000000),
    background = Color(0xFF000000),
    cell = Color(0xFF1C1C1D),
    cellPressed = Color(0xFF2C2C2E),
    text = Color(0xFFFFFFFF),
    secondaryText = Color(0xFF98989E),
    tertiaryText = Color(0xFF48484A),
    separator = Color(0xFF545458).copy(alpha = 0.55f),
    accent = Color(0xFF3E88F7),
    destructive = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    mutedBadge = Color(0xFF666666),
    searchField = Color(0x3D767680),
    bubbleIn = Color(0xFF1D1D1D),
    bubbleOut = Color(0xFF0088FF),
    bubbleOutGradient = listOf(Color(0xFF61BCF9), Color(0xFF0088FF)),
    bubbleInText = Color(0xFFFFFFFF),
    bubbleOutText = Color(0xFFFFFFFF),
    bubbleInMeta = Color(0xFFFFFFFF).copy(alpha = 0.5f),
    bubbleOutMeta = Color(0xFFFFFFFF).copy(alpha = 0.5f),
    bubbleOutAccent = Color(0xFFFFFFFF),
    glassSurface = Color(0xFF121212).copy(alpha = 0.45f),
    glassOpaque = Color(0xFF1C1C1C).copy(alpha = 0.85f),
    menuSurface = Color(0xFF252525).copy(alpha = 0.78f),
    scrim = Color(0xFF000000).copy(alpha = 0.35f),
    wallpaper = listOf(Color(0xFF0B1A2B), Color(0xFF1C2B3E), Color(0xFF0F2031), Color(0xFF223246)),
    wallpaperPattern = Color(0xFFFFFFFF).copy(alpha = 0.06f),
    serviceBubble = Color(0xFFFFFFFF).copy(alpha = 0.12f),
    listCheckmark = Color(0xFF3E88F7),
    pinnedRow = Color(0xFF1C1C1D),
)

/** iOS text styles. Letter spacing mimics SF Pro tracking. */
@Immutable
data class TgTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption1: TextStyle,
    val caption2: TextStyle,
)

private fun ios(size: Float, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.sp, lineHeight: Float = size * 1.2f) =
    TextStyle(
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = tracking,
        lineHeight = lineHeight.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

fun typography(scale: Float) = TgTypography(
    largeTitle = ios(34f * scale, FontWeight.Bold, 0.01.em),
    title1 = ios(28f * scale, FontWeight.Bold, 0.01.em),
    title2 = ios(22f * scale, FontWeight.Bold, (-0.01).em),
    title3 = ios(20f * scale, FontWeight.SemiBold, (-0.02).em),
    headline = ios(17f * scale, FontWeight.SemiBold, (-0.025).em, 22f * scale),
    body = ios(17f * scale, FontWeight.Normal, (-0.025).em, 22f * scale),
    callout = ios(16f * scale, FontWeight.Normal, (-0.02).em, 21f * scale),
    subheadline = ios(15f * scale, FontWeight.Normal, (-0.015).em, 20f * scale),
    footnote = ios(13f * scale, FontWeight.Normal, (-0.005).em, 18f * scale),
    caption1 = ios(12f * scale, FontWeight.Normal, 0.sp, 16f * scale),
    caption2 = ios(11f * scale, FontWeight.Normal, 0.005.em, 13f * scale),
)

enum class ThemeMode { System, Light, Dark }

/** Spec §5 / §39: effect levels, wired to Settings → Power Saving. */
enum class GlassLevel(val title: String) {
    Full("Liquid Glass"),
    Medium("Blur Only"),
    Low("Translucent"),
    Off("Off (Opaque)"),
}

/** User-tunable appearance settings, persisted in SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("tglass", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(ThemeMode.entries[prefs.getInt("theme", 0)])
        private set
    var glassLevel by mutableStateOf(GlassLevel.entries[prefs.getInt("glass", 0)])
        private set
    var animations by mutableStateOf(prefs.getBoolean("anim", true))
        private set
    var textScale by mutableFloatStateOf(prefs.getFloat("textScale", 1f))
        private set
    var bubbleRadius by mutableFloatStateOf(prefs.getFloat("bubbleRadius", 16f))
        private set
    var wallpaperIndex by mutableIntStateOf(prefs.getInt("wallpaper", 0))
        private set
    var loggedIn by mutableStateOf(prefs.getBoolean("loggedIn", false))
        private set
    var autoplayVideo by mutableStateOf(prefs.getBoolean("autoplayVideo", true))
        private set
    var autoplayGif by mutableStateOf(prefs.getBoolean("autoplayGif", true))
        private set

    fun updateTheme(v: ThemeMode) { themeMode = v; prefs.edit().putInt("theme", v.ordinal).apply() }
    fun updateGlass(v: GlassLevel) { glassLevel = v; prefs.edit().putInt("glass", v.ordinal).apply() }
    fun updateAnimations(v: Boolean) { animations = v; prefs.edit().putBoolean("anim", v).apply() }
    fun updateTextScale(v: Float) { textScale = v; prefs.edit().putFloat("textScale", v).apply() }
    fun updateBubbleRadius(v: Float) { bubbleRadius = v; prefs.edit().putFloat("bubbleRadius", v).apply() }
    fun updateWallpaper(v: Int) { wallpaperIndex = v; prefs.edit().putInt("wallpaper", v).apply() }
    fun updateLoggedIn(v: Boolean) { loggedIn = v; prefs.edit().putBoolean("loggedIn", v).apply() }
    fun updateAutoplayVideo(v: Boolean) { autoplayVideo = v; prefs.edit().putBoolean("autoplayVideo", v).apply() }
    fun updateAutoplayGif(v: Boolean) { autoplayGif = v; prefs.edit().putBoolean("autoplayGif", v).apply() }
}

val LocalAppSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }
private val LocalTgColors = staticCompositionLocalOf { LightColors }
private val LocalTgTypography = staticCompositionLocalOf { typography(1f) }

object TgTheme {
    val colors: TgColors
        @Composable @ReadOnlyComposable get() = LocalTgColors.current
    val type: TgTypography
        @Composable @ReadOnlyComposable get() = LocalTgTypography.current
}

/** Alternative chat wallpapers (4-point gradients like Telegram's default ones). */
val WallpaperPresets = listOf(
    null,
    listOf(Color(0xFFE8C06E), Color(0xFFF9EDB5), Color(0xFFD0A24F), Color(0xFFF5DDA0)),
    listOf(Color(0xFF7FA381), Color(0xFFFFF5C5), Color(0xFF336F55), Color(0xFFFBE37D)),
    listOf(Color(0xFFA6C4E6), Color(0xFFE3D6F0), Color(0xFF8DB0E3), Color(0xFFC5B6EE)),
    listOf(Color(0xFFF2B6C4), Color(0xFFFCE3D3), Color(0xFFE59AB3), Color(0xFFF7C8A6)),
)

@Composable
fun TgThemeProvider(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val base = if (dark) DarkColors else LightColors
    val wallpaper = WallpaperPresets.getOrNull(settings.wallpaperIndex)
    val colors = if (wallpaper != null && !dark) base.copy(wallpaper = wallpaper) else base
    CompositionLocalProvider(
        LocalTgColors provides colors,
        LocalTgTypography provides typography(settings.textScale),
        content = content,
    )
}
