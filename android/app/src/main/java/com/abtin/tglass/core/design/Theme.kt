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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.abtin.tglass.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

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
    /** Outgoing bubbles painted with a vertical gradient (Day / Night / Tinted themes). */
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

/** Telegram's default "Classic" day theme: green outgoing bubbles on the green pattern wallpaper. */
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

/** iOS `defaultDarkPresentationTheme` wallpaper gradient (shown through a dark pattern, intensity −34). */
private val NightWallpaperGradient = listOf(Color(0xFF598BF6), Color(0xFF7A5EEF), Color(0xFFD67CFF), Color(0xFFF38B58))

/**
 * Telegram-iOS "Night" (DefaultDarkPresentationTheme.swift): true black backgrounds, #1C1C1D grouped cells,
 * #313135 highlight, #545458 @ 55 % separators, #98989E secondary text, blue-gradient outgoing bubbles.
 */
val DarkColors = TgColors(
    isDark = true,
    groupedBackground = Color(0xFF000000),
    background = Color(0xFF000000),
    cell = Color(0xFF1C1C1D),
    cellPressed = Color(0xFF313135),
    text = Color(0xFFFFFFFF),
    secondaryText = Color(0xFF98989E),
    // disclosureArrowColor: white @ 28 % over the cell.
    tertiaryText = Color(0xFF5C5C5F),
    separator = Color(0xFF545458).copy(alpha = 0.55f),
    accent = Color(0xFF3E88F7),
    destructive = Color(0xFFEB5545),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    mutedBadge = Color(0xFF666666),
    searchField = Color(0xFF767680).copy(alpha = 0.3f),
    bubbleIn = Color(0xFF1D1D1D),
    bubbleOut = Color(0xFF0088FF),
    bubbleOutGradient = listOf(Color(0xFF61BCF9), Color(0xFF0088FF)),
    bubbleInText = Color(0xFFFFFFFF),
    bubbleOutText = Color(0xFFFFFFFF),
    bubbleInMeta = Color(0xFFFFFFFF).copy(alpha = 0.5f),
    bubbleOutMeta = Color(0xFFFFFFFF).copy(alpha = 0.5f),
    bubbleOutAccent = Color(0xFFFFFFFF),
    glassSurface = Color(0xFF121212).copy(alpha = 0.45f),
    glassOpaque = Color(0xFF1C1C1C).copy(alpha = 0.9f),
    menuSurface = Color(0xFF252525).copy(alpha = 0.8f),
    scrim = Color(0xFF000000).copy(alpha = 0.45f),
    wallpaper = NightWallpaperGradient.map { it.mixedWith(Color.Black, 0.84f) },
    wallpaperPattern = Color(0xFF8C7CF0).copy(alpha = 0.22f),
    serviceBubble = Color(0xFF1F1F1F).copy(alpha = 0.85f),
    listCheckmark = Color(0xFF3E88F7),
    pinnedRow = Color(0xFF1C1C1D),
)

// ---- Color math (UIColor.withMultiplied / lightness as used by Telegram-iOS themes) ----

/** [amount] 0 = this color, 1 = [other]. */
fun Color.mixedWith(other: Color, amount: Float): Color = Color(
    red + (other.red - red) * amount,
    green + (other.green - green) * amount,
    blue + (other.blue - blue) * amount,
    alpha + (other.alpha - alpha) * amount,
)

/** Hue (0..1), saturation, brightness. */
internal fun Color.hsb(): FloatArray {
    val out = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), out)
    out[0] = out[0] / 360f
    return out
}

internal fun hsbColor(h: Float, s: Float, b: Float): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 0.9999f) * 360f, s.coerceIn(0f, 1f), b.coerceIn(0f, 1f))))

/** UIColor.withMultiplied(hue:saturation:brightness:). */
internal fun Color.multiplied(hue: Float, saturation: Float, brightness: Float): Color {
    val (h, s, b) = hsb()
    return hsbColor(min(1f, h * hue), min(1f, s * saturation), min(1f, b * brightness))
}

/** UIColor.lightness (Rec. 709 luma). */
internal val Color.lightness: Float get() = 0.2126f * red + 0.7152f * green + 0.0722f * blue

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

/**
 * Inter (variable, SIL OFL) with optical sizing — the closest open equivalent of SF Pro Text / Display.
 * Each weight is pinned via font variations so the system never fakes bold.
 */
@OptIn(ExperimentalTextApi::class)
private fun interFamily(opticalSize: Float) = FontFamily(
    listOf(400, 500, 600, 700).flatMap { w ->
        listOf(
            Font(
                R.font.inter,
                weight = FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opticalSize)),
            ),
            // Real italic faces (Inter Italic variable), so italic text is never synthesized.
            Font(
                R.font.inter_italic,
                weight = FontWeight(w),
                style = androidx.compose.ui.text.font.FontStyle.Italic,
                variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("opsz", opticalSize)),
            ),
        )
    }
)

val InterText = interFamily(14f)
val InterDisplay = interFamily(32f)

private fun ios(size: Float, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.sp, lineHeight: Float = size * 1.2f) =
    TextStyle(
        fontFamily = if (size >= 20f) InterDisplay else InterText,
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
    body = ios(17f * scale, FontWeight.Normal, (-0.022).em, 22f * scale),
    callout = ios(16f * scale, FontWeight.Normal, (-0.02).em, 21f * scale),
    subheadline = ios(15f * scale, FontWeight.Normal, (-0.015).em, 20f * scale),
    footnote = ios(13f * scale, FontWeight.Normal, (-0.005).em, 18f * scale),
    caption1 = ios(12f * scale, FontWeight.Normal, 0.sp, 16f * scale),
    caption2 = ios(11f * scale, FontWeight.Normal, 0.005.em, 13f * scale),
)

/** Kept for callers that only know light / dark (CI screenshot launches). */
enum class ThemeMode { System, Light, Dark }

/** Spec §5 / §39: effect levels, wired to Settings → Power Saving. */
enum class GlassLevel(val title: String) {
    Full("Liquid Glass"),
    Medium("Blur Only"),
    Low("Translucent"),
    Off("Off (Opaque)"),
}

/** Chat list density (Settings → Appearance → Chat List). Compact matches Telegram iOS 26. */
enum class ChatListSize(val title: String, val row: Int, val avatar: Int, val titleSp: Float, val previewSp: Float) {
    Compact("Compact", 64, 52, 15f, 14f),
    Regular("Regular", 72, 60, 16f, 15f),
    Large("Large", 80, 64, 17f, 16f),
}

/** The built-in themes of the Appearance carousel (Telegram-iOS: dayClassic, nightAccent, day, night). */
enum class ColorTheme(val title: String, val dark: Boolean) {
    Classic("Classic", false),
    Day("Day", false),
    Night("Night", true),
    Tinted("Tinted", true),
}

/** Auto-Night Mode (ThemeAutoNightSettingsController): when the night theme replaces the day theme. */
enum class AutoNight(val title: String) {
    Disabled("Disabled"),
    System("System"),
    Scheduled("Scheduled"),
}

/** Accent colors offered under the carousel (PresentationThemeBaseColor) with the Tinted wallpaper of each. */
@Immutable
data class AccentPreset(val name: String, val color: Color, val tintedWallpaper: List<Color>)

private fun rgb(vararg c: Long) = c.map { Color(0xFF000000 or it) }

val AccentPresets = listOf(
    AccentPreset("Blue", Color(0xFF0088FF), rgb(0x1e3557, 0x182036, 0x1c4352, 0x16263a)),
    AccentPreset("Cyan", Color(0xFF00C2ED), rgb(0x1e3557, 0x151a36, 0x1c4352, 0x2a4541)),
    AccentPreset("Green", Color(0xFF29B327), rgb(0x2d4836, 0x172b19, 0x364331, 0x103231)),
    AccentPreset("Pink", Color(0xFFEB6CA4), rgb(0x2c0b22, 0x290020, 0x160a22, 0x3b1834)),
    AccentPreset("Orange", Color(0xFFF08200), rgb(0x2c211b, 0x442917, 0x22191f, 0x3b2714)),
    AccentPreset("Purple", Color(0xFF9472EE), rgb(0x3a1c3a, 0x24193c, 0x392e3e, 0x1a1632)),
    AccentPreset("Red", Color(0xFFD33213), rgb(0x2c211b, 0x44332a, 0x22191f, 0x3b2d36)),
    AccentPreset("Yellow", Color(0xFFEDB400), rgb(0x2c2512, 0x45360b, 0x221d08, 0x3b2f13)),
    AccentPreset("Gray", Color(0xFF6D839E), rgb(0x1c2731, 0x1a1c25, 0x27303b, 0x1b1b21)),
)

/** The accent a theme uses for [index] in [AccentPresets] (index 0 = the theme's own blue). */
fun themeAccent(theme: ColorTheme, index: Int): Color {
    val preset = AccentPresets.getOrNull(index)
    if (preset == null || index == 0) return when (theme) {
        ColorTheme.Classic, ColorTheme.Day -> Color(0xFF0088FF)
        ColorTheme.Night -> Color(0xFF3E88F7)
        ColorTheme.Tinted -> Color(0xFF2EA6FF)
    }
    val c = preset.color
    return when (theme) {
        ColorTheme.Classic, ColorTheme.Day -> if (c.lightness > 0.705f) {
            val (h, s, b) = c.hsb(); hsbColor(h, min(1f, s * 1.1f), min(b, 0.6f))
        } else c
        ColorTheme.Night -> { val (h, s, b) = c.hsb(); hsbColor(h, s, max(b, 0.55f)) }
        ColorTheme.Tinted -> c
    }
}

/** A chat wallpaper from the gallery: 4 gradient colors (all equal for a plain color). */
@Immutable
data class WallpaperOption(val colors: List<Color>, val solid: Boolean = false)

/**
 * The user's own chat background for the day or night side (Settings -> Appearance -> Chat Background):
 * a photo (file name inside filesDir/wallpapers), or one/two colors (a gradient rotated by [rotation] degrees).
 * Neither = the theme's wallpaper. "Motion" (parallax) from iOS is not implemented.
 */
@Immutable
data class UserBackground(
    val photo: String? = null,
    val blur: Boolean = false,
    /** 0..1 black overlay. */
    val dim: Float = 0f,
    /** ARGB ints, 0, 1 or 2 entries. */
    val colors: List<Int> = emptyList(),
    val rotation: Int = 0,
) {
    val isCustom: Boolean get() = photo != null || colors.isNotEmpty()

    fun serialize(): String = "${photo ?: ""}|$blur|$dim|${colors.joinToString(",")}|$rotation"

    companion object {
        fun parse(s: String?): UserBackground {
            if (s.isNullOrEmpty()) return UserBackground()
            return runCatching {
                val p = s.split("|")
                UserBackground(
                    photo = p[0].ifEmpty { null },
                    blur = p[1].toBoolean(),
                    dim = p[2].toFloat().coerceIn(0f, 1f),
                    colors = p[3].split(",").filter { it.isNotEmpty() }.map { it.toInt() }.take(2),
                    rotation = p[4].toInt(),
                )
            }.getOrDefault(UserBackground())
        }
    }
}

private fun gradient(vararg c: Long) = WallpaperOption(rgb(*c).let { if (it.size == 3) it + it[1] else it })
private fun solid(c: Long) = WallpaperOption(List(4) { Color(0xFF000000 or c) }, solid = true)

/**
 * Day wallpapers. Index 0 = the theme's own wallpaper; 1–4 keep the order of earlier versions so a saved
 * choice survives the update. The rest are Telegram-iOS accent wallpapers and plain colors.
 */
val DayWallpapers: List<WallpaperOption?> = listOf(
    null,
    gradient(0xE8C06E, 0xF9EDB5, 0xD0A24F, 0xF5DDA0),
    gradient(0x7FA381, 0xFFF5C5, 0x336F55, 0xFBE37D),
    gradient(0xA6C4E6, 0xE3D6F0, 0x8DB0E3, 0xC5B6EE),
    gradient(0xF2B6C4, 0xFCE3D3, 0xE59AB3, 0xF7C8A6),
    gradient(0xDBDDBB, 0x6BA587, 0xD5D88D, 0x88B884),
    gradient(0xA4DBFF, 0x009FDD, 0x527BDD),
    gradient(0xE4B2EA, 0x8376C2, 0xEAB9D9, 0xB493E6),
    gradient(0xFEC496, 0xDD6CB9, 0x962FBF, 0x4F5BD5),
    gradient(0x8ADBF2, 0x888DEC, 0xE39FEA, 0x679CED),
    gradient(0xEAA36E, 0xF0E486, 0xF29EBF, 0xE8C06E),
    solid(0xFFFFFF), solid(0xE4E7EB), solid(0xD4DFEA), solid(0xB3CDE1), solid(0x8FB4D6), solid(0xD3E2DA),
    solid(0xBFD9B7), solid(0xF2E7C4), solid(0xF7D9C4), solid(0xF2C6D3), solid(0xDCCFEF), solid(0x2E3A48),
)

/** Night wallpapers (index 0 = the theme's own). */
val NightWallpapers: List<WallpaperOption?> = listOf(
    null,
    gradient(0x1E3557, 0x182036, 0x1C4352, 0x16263A),
    gradient(0x0B1A2B, 0x1C2B3E, 0x0F2031, 0x223246),
    gradient(0x2D4836, 0x172B19, 0x364331, 0x103231),
    gradient(0x2C0B22, 0x290020, 0x160A22, 0x3B1834),
    gradient(0x2C211B, 0x442917, 0x22191F, 0x3B2714),
    gradient(0x3A1C3A, 0x24193C, 0x392E3E, 0x1A1632),
    gradient(0x2C2512, 0x45360B, 0x221D08, 0x3B2F13),
    gradient(0x1C2731, 0x1A1C25, 0x27303B, 0x1B1B21),
    solid(0x000000), solid(0x1C1C1E), solid(0x121B26), solid(0x17212B), solid(0x1E2A22), solid(0x2A1E26),
    solid(0x26221A), solid(0x23212E),
)

/** The wallpaper a theme uses when the user did not pick one. */
fun themeWallpaper(theme: ColorTheme, accentIndex: Int): WallpaperOption = when (theme) {
    ColorTheme.Classic -> WallpaperOption(LightColors.wallpaper)
    ColorTheme.Day -> WallpaperOption(List(4) { Color.White }, solid = true)
    ColorTheme.Night -> WallpaperOption(DarkColors.wallpaper)
    ColorTheme.Tinted -> WallpaperOption((AccentPresets.getOrNull(accentIndex) ?: AccentPresets[0]).tintedWallpaper)
}

/** Everything that decides the palette; [buildColors] turns it into [TgColors]. */
@Immutable
data class ThemeSpec(
    val theme: ColorTheme,
    val accentIndex: Int = 0,
    /** null = the theme's own wallpaper. */
    val wallpaper: WallpaperOption? = null,
    val pattern: Boolean = true,
    /** 0..1, 0.5 = Telegram's default intensity. */
    val patternIntensity: Float = 0.5f,
    val bubbleGradient: Boolean = true,
    val glassTint: Boolean = false,
)

/** Builds the palette of a theme like Telegram-iOS `customizeDefault…Theme(accentColor:)` does. */
fun buildColors(spec: ThemeSpec): TgColors {
    val theme = spec.theme
    val custom = spec.accentIndex != 0 && spec.accentIndex in AccentPresets.indices
    val acc = themeAccent(theme, spec.accentIndex)
    val base: TgColors = when (theme) {
        ColorTheme.Classic -> if (!custom) LightColors else {
            val (h, s, b) = AccentPresets[spec.accentIndex].color.hsb()
            val bubble = hsbColor(h, if (s > 0f && b > 0f) 0.14f else 0f, 0.79f + b * 0.21f)
            LightColors.copy(
                accent = acc, listCheckmark = acc,
                bubbleOut = bubble, bubbleOutMeta = acc.copy(alpha = 0.8f), bubbleOutAccent = acc,
            )
        }
        ColorTheme.Day -> {
            val grad = listOf(acc.multiplied(0.966f, 0.61f, 0.98f), acc)
            LightColors.copy(
                accent = acc, listCheckmark = acc,
                bubbleOut = acc, bubbleOutGradient = if (spec.bubbleGradient) grad else null,
                bubbleOutText = Color.White, bubbleOutMeta = Color.White.copy(alpha = 0.7f), bubbleOutAccent = Color.White,
                wallpaperPattern = Color.Transparent,
            )
        }
        ColorTheme.Night -> {
            val grad = if (!custom) listOf(Color(0xFF61BCF9), Color(0xFF0088FF)) else listOf(acc.multiplied(0.966f, 0.61f, 0.98f), acc)
            DarkColors.copy(
                accent = acc, listCheckmark = acc,
                bubbleOut = grad.last(), bubbleOutGradient = if (spec.bubbleGradient) grad else null,
            )
        }
        ColorTheme.Tinted -> {
            val mainBackground = acc.multiplied(1.024f, 0.585f, 0.25f)
            val selection = acc.multiplied(1.03f, 0.585f, 0.12f)
            val additionalBackground = acc.multiplied(1.024f, 0.573f, 0.18f)
            val separator = acc.multiplied(1.033f, 0.426f, 0.34f)
            val secondary = acc.multiplied(1.019f, 0.109f, 0.59f)
            val secondaryText = acc.multiplied(0.956f, 0.17f, 1.0f)
            val input = acc.multiplied(1.029f, 0.609f, 0.19f)
            val outgoing = acc.multiplied(1.019f, 0.731f, 0.59f)
            val grad = listOf(outgoing.multiplied(0.966f, 0.61f, 0.98f), outgoing)
            DarkColors.copy(
                groupedBackground = additionalBackground,
                background = additionalBackground,
                cell = mainBackground,
                cellPressed = selection,
                secondaryText = secondaryText.copy(alpha = 0.55f),
                tertiaryText = secondary,
                separator = separator,
                accent = acc,
                listCheckmark = acc,
                mutedBadge = secondary,
                searchField = input,
                bubbleIn = mainBackground,
                bubbleOut = outgoing,
                bubbleOutGradient = if (spec.bubbleGradient) grad else null,
                bubbleInMeta = secondaryText.copy(alpha = 0.5f),
                glassSurface = additionalBackground.copy(alpha = 0.45f),
                glassOpaque = mainBackground.copy(alpha = 0.92f),
                menuSurface = mainBackground.copy(alpha = 0.82f),
                pinnedRow = mainBackground,
                serviceBubble = additionalBackground.copy(alpha = 0.8f),
                wallpaperPattern = Color.Black.copy(alpha = 0.3f),
            )
        }
    }
    // Wallpaper and its pattern.
    val picked = spec.wallpaper
    val wall = picked ?: themeWallpaper(theme, spec.accentIndex)
    val avg = wall.colors.map { it.lightness }.average().toFloat()
    val patternBase = when {
        wall.solid -> Color.Transparent
        picked == null -> base.wallpaperPattern
        avg < 0.35f -> Color.White.copy(alpha = 0.07f)
        else -> Color.Black.copy(alpha = 0.09f)
    }
    val pattern = if (!spec.pattern || patternBase.alpha == 0f) Color.Transparent
    else patternBase.copy(alpha = (patternBase.alpha * spec.patternIntensity * 2f).coerceIn(0f, 1f))
    var colors = base.copy(wallpaper = wall.colors, wallpaperPattern = pattern)
    // White incoming bubbles would vanish on a white wallpaper (Telegram-iOS day: #F1F1F4 without wallpaper).
    if (!colors.isDark && avg > 0.93f) colors = colors.copy(bubbleIn = Color(0xFFF1F1F4))
    if (!colors.isDark && avg < 0.3f) colors = colors.copy(serviceBubble = Color.White.copy(alpha = 0.18f))
    if (spec.glassTint) colors = colors.copy(
        glassSurface = colors.glassSurface.mixedWith(colors.accent.copy(alpha = colors.glassSurface.alpha), 0.16f),
        glassOpaque = colors.glassOpaque.mixedWith(colors.accent.copy(alpha = colors.glassOpaque.alpha), 0.08f),
        menuSurface = colors.menuSurface.mixedWith(colors.accent.copy(alpha = colors.menuSurface.alpha), 0.08f),
    )
    return colors
}

/** User-tunable appearance settings, persisted in SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("tglass", Context.MODE_PRIVATE)

    var glassLevel by mutableStateOf(GlassLevel.entries[prefs.getInt("glass", 0)])
        private set
    var animations by mutableStateOf(prefs.getBoolean("anim", true))
        private set
    var textScale by mutableFloatStateOf(prefs.getFloat("textScale", 1f))
        private set
    var bubbleRadius by mutableFloatStateOf(prefs.getFloat("bubbleRadius", 16f))
        private set
    /** Day wallpaper: index into [DayWallpapers]. */
    var wallpaperIndex by mutableIntStateOf(prefs.getInt("wallpaper", 0))
        private set
    /** Night wallpaper: index into [NightWallpapers]. */
    var wallpaperNightIndex by mutableIntStateOf(prefs.getInt("wallpaperNight", 0))
        private set
    /** Custom chat background of the day themes / night themes (see [UserBackground]). */
    var bgDay by mutableStateOf(UserBackground.parse(prefs.getString("bgDay", null)))
        private set
    var bgNight by mutableStateOf(UserBackground.parse(prefs.getString("bgNight", null)))
        private set
    var loggedIn by mutableStateOf(prefs.getBoolean("loggedIn", false))
        private set
    /** Local sample data instead of a real account. */
    var demoMode by mutableStateOf(prefs.getBoolean("demoMode", false))
        private set
    /** Keep TDLib connected by a foreground service so notifications arrive while the app is closed. */
    var backgroundConnection by mutableStateOf(prefs.getBoolean("bgConnection", true))
        private set
    /** Glass banner at the top for messages in other chats while the app is open. */
    var inAppPreview by mutableStateOf(prefs.getBoolean("inAppPreview", true))
        private set
    var chatListSize by mutableStateOf(ChatListSize.entries.getOrElse(prefs.getInt("chatListSize", 0)) { ChatListSize.Compact })
        private set
    var autoplayVideo by mutableStateOf(prefs.getBoolean("autoplayVideo", true))
        private set
    var autoplayGif by mutableStateOf(prefs.getBoolean("autoplayGif", true))
        private set

    // ---- Appearance ----
    private val legacyTheme = prefs.getInt("theme", 0) // 0 System, 1 Light, 2 Dark (before color themes)
    /** Theme picked in the carousel. */
    var colorTheme by mutableStateOf(
        ColorTheme.entries.getOrElse(prefs.getInt("colorTheme", if (legacyTheme == 2) ColorTheme.Night.ordinal else ColorTheme.Classic.ordinal)) { ColorTheme.Classic }
    )
        private set
    /** Last day theme, to return to when Night Mode is switched off. */
    private var lastDayTheme = ColorTheme.entries.getOrElse(prefs.getInt("dayTheme", 0)) { ColorTheme.Classic }.takeIf { !it.dark } ?: ColorTheme.Classic
    /** Theme used at night (Night Mode switch or Auto-Night Mode). */
    var nightTheme by mutableStateOf(ColorTheme.entries.getOrElse(prefs.getInt("nightTheme", ColorTheme.Night.ordinal)) { ColorTheme.Night }.takeIf { it.dark } ?: ColorTheme.Night)
        private set
    /** "Night Mode" switch: the night theme regardless of Auto-Night Mode. */
    var nightForced by mutableStateOf(prefs.getBoolean("nightForced", false))
        private set
    var autoNight by mutableStateOf(
        AutoNight.entries.getOrElse(prefs.getInt("autoNight", if (legacyTheme == 0) AutoNight.System.ordinal else AutoNight.Disabled.ordinal)) { AutoNight.System }
    )
        private set
    /** Scheduled auto-night, minutes after midnight. */
    var nightFrom by mutableIntStateOf(prefs.getInt("nightFrom", 22 * 60))
        private set
    var nightTo by mutableIntStateOf(prefs.getInt("nightTo", 7 * 60))
        private set
    /** Accent index (into [AccentPresets]) of each [ColorTheme]. */
    private val accents = mutableStateListOf(*Array(ColorTheme.entries.size) { i -> prefs.getInt("accent_${ColorTheme.entries[i].name}", 0) })
    var wallpaperPattern by mutableStateOf(prefs.getBoolean("pattern", true))
        private set
    var patternIntensity by mutableFloatStateOf(prefs.getFloat("patternIntensity", 0.5f))
        private set
    /** Messages with only a few emoji are shown big, without a bubble. */
    var largeEmoji by mutableStateOf(prefs.getBoolean("largeEmoji", true))
        private set
    /** Outgoing bubbles of the Day / Night / Tinted themes use their gradient. */
    var bubbleGradient by mutableStateOf(prefs.getBoolean("bubbleGradient", true))
        private set
    /** Glass surfaces pick up a little of the accent color. */
    var glassTint by mutableStateOf(prefs.getBoolean("glassTint", false))
        private set
    /** Launcher icon (see features/settings/AppIcons.kt). */
    var appIcon by mutableIntStateOf(prefs.getInt("appIcon", 0))
        private set

    fun accentIndex(theme: ColorTheme): Int = accents[theme.ordinal]

    /** Light / dark summary, for callers that predate color themes. */
    val themeMode: ThemeMode
        get() = when {
            nightForced || colorTheme.dark -> ThemeMode.Dark
            autoNight == AutoNight.System -> ThemeMode.System
            else -> ThemeMode.Light
        }

    fun updateTheme(v: ThemeMode) {
        when (v) {
            ThemeMode.Dark -> updateNightForced(true)
            ThemeMode.Light -> { updateNightForced(false); updateAutoNight(AutoNight.Disabled) }
            ThemeMode.System -> { updateNightForced(false); updateAutoNight(AutoNight.System) }
        }
    }

    /** Whether Auto-Night Mode currently asks for the night theme. */
    fun autoNightTriggered(systemDark: Boolean, minuteOfDay: Int): Boolean = when (autoNight) {
        AutoNight.Disabled -> false
        AutoNight.System -> systemDark
        AutoNight.Scheduled -> if (nightFrom <= nightTo) minuteOfDay in nightFrom until nightTo else minuteOfDay >= nightFrom || minuteOfDay < nightTo
    }

    fun activeTheme(autoNightTriggered: Boolean): ColorTheme = when {
        colorTheme.dark -> colorTheme
        nightForced || autoNightTriggered -> nightTheme
        else -> colorTheme
    }

    /** Carousel tap. While the night theme is showing (not picked as the main theme) this picks the night theme. */
    fun selectTheme(t: ColorTheme, nightShowing: Boolean) {
        if (nightShowing && !colorTheme.dark) {
            if (t.dark) storeNightTheme(t)
            return
        }
        colorTheme = t
        prefs.edit().putInt("colorTheme", t.ordinal).apply()
        if (t.dark) storeNightTheme(t) else {
            lastDayTheme = t
            prefs.edit().putInt("dayTheme", t.ordinal).apply()
            updateNightForced(false)
        }
    }

    private fun storeNightTheme(t: ColorTheme) { nightTheme = t; prefs.edit().putInt("nightTheme", t.ordinal).apply() }

    /** "Night Mode" switch. Off also leaves a night theme picked in the carousel. */
    fun updateNightForced(v: Boolean) {
        nightForced = v
        prefs.edit().putBoolean("nightForced", v).apply()
        if (!v && colorTheme.dark) {
            colorTheme = lastDayTheme
            prefs.edit().putInt("colorTheme", lastDayTheme.ordinal).apply()
        }
    }

    fun updateNightTheme(t: ColorTheme) { if (t.dark) storeNightTheme(t) }
    fun updateAutoNight(v: AutoNight) { autoNight = v; prefs.edit().putInt("autoNight", v.ordinal).apply() }
    fun updateNightSchedule(from: Int, to: Int) {
        nightFrom = from.mod(24 * 60); nightTo = to.mod(24 * 60)
        prefs.edit().putInt("nightFrom", nightFrom).putInt("nightTo", nightTo).apply()
    }
    fun updateAccent(theme: ColorTheme, index: Int) { accents[theme.ordinal] = index; prefs.edit().putInt("accent_${theme.name}", index).apply() }
    fun updateWallpaper(v: Int) { wallpaperIndex = v; prefs.edit().putInt("wallpaper", v).apply() }
    fun updateWallpaperNight(v: Int) { wallpaperNightIndex = v; prefs.edit().putInt("wallpaperNight", v).apply() }
    fun backgroundFor(dark: Boolean): UserBackground = if (dark) bgNight else bgDay
    fun updateBackground(dark: Boolean, v: UserBackground) {
        if (dark) { bgNight = v; prefs.edit().putString("bgNight", v.serialize()).apply() }
        else { bgDay = v; prefs.edit().putString("bgDay", v.serialize()).apply() }
    }
    fun updateWallpaperPattern(v: Boolean) { wallpaperPattern = v; prefs.edit().putBoolean("pattern", v).apply() }
    fun updatePatternIntensity(v: Float) { patternIntensity = v; prefs.edit().putFloat("patternIntensity", v).apply() }
    fun updateLargeEmoji(v: Boolean) { largeEmoji = v; prefs.edit().putBoolean("largeEmoji", v).apply() }
    fun updateBubbleGradient(v: Boolean) { bubbleGradient = v; prefs.edit().putBoolean("bubbleGradient", v).apply() }
    fun updateGlassTint(v: Boolean) { glassTint = v; prefs.edit().putBoolean("glassTint", v).apply() }
    fun updateAppIcon(v: Int) { appIcon = v; prefs.edit().putInt("appIcon", v).apply() }

    /** The palette inputs for [theme] with the current wallpaper / pattern / bubble settings. */
    fun themeSpec(theme: ColorTheme): ThemeSpec = ThemeSpec(
        theme = theme,
        accentIndex = accentIndex(theme),
        wallpaper = if (theme.dark) NightWallpapers.getOrNull(wallpaperNightIndex) else DayWallpapers.getOrNull(wallpaperIndex),
        pattern = wallpaperPattern,
        patternIntensity = patternIntensity,
        bubbleGradient = bubbleGradient,
        glassTint = glassTint,
    )

    fun updateGlass(v: GlassLevel) { glassLevel = v; prefs.edit().putInt("glass", v.ordinal).apply() }
    fun updateAnimations(v: Boolean) { animations = v; prefs.edit().putBoolean("anim", v).apply() }
    fun updateTextScale(v: Float) { textScale = v; prefs.edit().putFloat("textScale", v).apply() }
    fun updateBubbleRadius(v: Float) { bubbleRadius = v; prefs.edit().putFloat("bubbleRadius", v).apply() }
    fun updateLoggedIn(v: Boolean) { loggedIn = v; prefs.edit().putBoolean("loggedIn", v).apply() }
    fun updateChatListSize(v: ChatListSize) { chatListSize = v; prefs.edit().putInt("chatListSize", v.ordinal).apply() }
    fun updateDemoMode(v: Boolean) { demoMode = v; prefs.edit().putBoolean("demoMode", v).apply() }
    fun updateBackgroundConnection(v: Boolean) { backgroundConnection = v; prefs.edit().putBoolean("bgConnection", v).apply() }
    fun updateInAppPreview(v: Boolean) { inAppPreview = v; prefs.edit().putBoolean("inAppPreview", v).apply() }
    fun updateAutoplayVideo(v: Boolean) { autoplayVideo = v; prefs.edit().putBoolean("autoplayVideo", v).apply() }
    fun updateAutoplayGif(v: Boolean) { autoplayGif = v; prefs.edit().putBoolean("autoplayGif", v).apply() }
}

/** Which theme is showing right now and why (for the Appearance page). */
@Immutable
data class ThemeState(val active: ColorTheme, val autoNightTriggered: Boolean, val nightShowing: Boolean)

val LocalAppSettings = staticCompositionLocalOf<AppSettings> { error("AppSettings not provided") }
private val LocalTgColors = staticCompositionLocalOf { LightColors }
private val LocalTgTypography = staticCompositionLocalOf { typography(1f) }
val LocalThemeState = staticCompositionLocalOf { ThemeState(ColorTheme.Classic, autoNightTriggered = false, nightShowing = false) }

/** Shows [content] with another text size (the live preview while the Text Size slider is dragged). */
@Composable
fun ProvideTextScale(scale: Float, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTgTypography provides typography(scale), content = content)
}

/** Shows [content] with another palette (theme thumbnails, previews). */
@Composable
fun ProvideColors(colors: TgColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTgColors provides colors, content = content)
}

object TgTheme {
    val colors: TgColors
        @Composable @ReadOnlyComposable get() = LocalTgColors.current
    val type: TgTypography
        @Composable @ReadOnlyComposable get() = LocalTgTypography.current
}

private fun minuteOfDay(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

@Composable
fun TgThemeProvider(settings: AppSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val scheduled = settings.autoNight == AutoNight.Scheduled
    // Scheduled Auto-Night: re-check the clock twice a minute.
    val minute by produceState(minuteOfDay(), scheduled) {
        value = minuteOfDay()
        while (scheduled) {
            delay(30_000)
            value = minuteOfDay()
        }
    }
    val triggered = settings.autoNightTriggered(systemDark, minute)
    val active = settings.activeTheme(triggered)
    val spec = settings.themeSpec(active)
    val colors = remember(spec) { buildColors(spec) }
    val state = ThemeState(active, triggered, nightShowing = active.dark && !settings.colorTheme.dark)
    CompositionLocalProvider(
        LocalTgColors provides colors,
        LocalTgTypography provides typography(settings.textScale),
        LocalThemeState provides state,
        content = content,
    )
}

/** Shows [content] with the dark palette regardless of the app theme (full-screen media viewer and editor). */
@Composable
fun ProvideDarkColors(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTgColors provides DarkColors, content = content)
}
