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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

/**
 * Design tokens modelled after Telegram for iOS (spec §2). Every value comes from Telegram-iOS
 * `DefaultDayPresentationTheme.swift` (Classic / Day), `DefaultDarkPresentationTheme.swift` (Night) and
 * `DefaultDarkTintedPresentationTheme.swift` (Tinted Night); see TelegramThemes.kt for the mapping.
 */
@Immutable
data class TgColors(
    val isDark: Boolean,
    /** list.blocksBackgroundColor: grouped background (Settings, Profile). */
    val groupedBackground: Color,
    /** list.plainBackgroundColor / chatList.backgroundColor: plain list background (Chats, Contacts). */
    val background: Color,
    /** list.itemBlocksBackgroundColor: cells inside grouped sections. */
    val cell: Color,
    /** list.itemHighlightedBackgroundColor. */
    val cellPressed: Color,
    /** list.itemPrimaryTextColor. */
    val text: Color,
    /** list.itemSecondaryTextColor. */
    val secondaryText: Color,
    /** list.disclosureArrowColor (chevrons, placeholders, empty check circles). */
    val tertiaryText: Color,
    /** list.itemBlocksSeparatorColor / itemPlainSeparatorColor. */
    val separator: Color,
    /** list.itemAccentColor (after the theme's accent customization). */
    val accent: Color,
    /** list.itemDestructiveColor. */
    val destructive: Color,
    val green: Color,
    val orange: Color,
    /** chatList.unreadBadgeInactiveBackgroundColor (muted unread counters). */
    val mutedBadge: Color,
    /** rootController.navigationSearchBar.inputFillColor. */
    val searchField: Color,
    /** Incoming bubble fill (as seen over the theme's wallpaper). */
    val bubbleIn: Color,
    /** Outgoing bubble fill; with [bubbleOutGradient] the bottom color of the gradient. */
    val bubbleOut: Color,
    /** Outgoing bubbles painted with a screen-wide vertical gradient, top to bottom (message.outgoing.bubble.fill). */
    val bubbleOutGradient: List<Color>? = null,
    val bubbleInText: Color,
    val bubbleOutText: Color,
    val bubbleInMeta: Color,
    val bubbleOutMeta: Color,
    val bubbleOutAccent: Color,
    /** Liquid Glass veil over the blurred backdrop (GlassBackgroundComponent `.panel`). */
    val glassSurface: Color,
    /** Glass without a backdrop (glass off): rootNavigationBar.opaqueBackgroundColor. */
    val glassOpaque: Color,
    /** contextMenu.backgroundColor. */
    val menuSurface: Color,
    /** contextMenu.dimColor. */
    val scrim: Color,
    /** Wallpaper gradient colors (1–4). */
    val wallpaper: List<Color>,
    /**
     * Pattern over the wallpaper: black (light wallpapers) or white (dark ones) at the pattern intensity, blended
     * like Telegram-iOS (soft light). Transparent = no pattern.
     */
    val wallpaperPattern: Color,
    /** Service message / date capsule fill (serviceMessage.components.fill). */
    val serviceBubble: Color,
    /** chatList.onlineDotColor. */
    val onlineDot: Color = Color(0xFF4CC91F),
    /** chatList.checkmarkColor (read / sent checks in the chat list). */
    val listCheckmark: Color = Color(0xFF0088FF),
    /** chatList.pinnedItemBackgroundColor. */
    val pinnedRow: Color = Color(0xFFF7F7F7),
    /**
     * Telegram-iOS dark pattern wallpaper (negative intensity): the screen is black and only the pattern lines show
     * the [wallpaper] gradient, at [wallpaperPattern]'s alpha.
     */
    val wallpaperInverted: Boolean = false,
    /** Content on an accent fill (secondaryBadgeTextColor: checkbox checks, send arrow, unread counters). */
    val onAccent: Color = Color.White,
    /** chatList.unreadBadgeActiveTextColor. */
    val badgeText: Color = Color.White,
    /** chatList.unreadBadgeInactiveTextColor. */
    val mutedBadgeText: Color = Color.White,
    /** chatList.messageTextColor / dateTextColor. */
    val chatListSecondary: Color = Color(0xFF8E8E93),
    /** chatList.muteIconColor. */
    val muteIcon: Color = Color(0xFFA7A7AD),
    /** chatList.pinnedBadgeColor. */
    val pinnedIcon: Color = Color(0xFFB6B6BB),
    /** chatList.messageDraftTextColor. */
    val draftText: Color = Color(0xFFDD4B39),
    /** chatList.verifiedIconFillColor / verifiedIconForegroundColor. */
    val verified: Color = Color(0xFF0088FF),
    val verifiedFg: Color = Color.White,
    /** list.sectionHeaderTextColor / freeTextColor (section headers and footers). */
    val sectionHeader: Color = Color(0xFF6D6D72),
    /** list.itemSwitchColors.contentColor. */
    val switchOn: Color = Color(0xFF35C759),
    /** serviceMessage primaryText. */
    val serviceText: Color = Color.White,
    /** serviceMessage.unreadBarFillColor / unreadBarTextColor. */
    val unreadBar: Color = Color.White.copy(alpha = 0.9f),
    val unreadBarText: Color = Color(0xFF86868D),
    /** chat.inputPanel.inputPlaceholderColor / inputControlColor. */
    val inputPlaceholder: Color = Color.Black.copy(alpha = 0.4f),
    val inputIcon: Color = Color.Black.copy(alpha = 0.5f),
    /** chat.message.incoming / outgoing parted colors. */
    val bubbleInPalette: BubblePalette,
    val bubbleOutPalette: BubblePalette,
)

/**
 * Telegram-iOS `PresentationThemePartedColors` of one bubble side. The fill lives in [TgColors.bubbleIn] /
 * [TgColors.bubbleOut]; `.clear` (knockout) colors of the source are resolved to the bubble fill.
 */
@Immutable
data class BubblePalette(
    /** primaryTextColor. */
    val text: Color,
    /** secondaryTextColor: time, "edited", views. */
    val meta: Color,
    /** linkTextColor. */
    val link: Color,
    /** accentTextColor: reply / forward titles, the side's own name color. */
    val accent: Color,
    /** accentControlColor / mediaActiveControlColor: play button, file icon, radio progress. */
    val control: Color,
    /** mediaControlInnerBackgroundColor: the glyph drawn on [control]. */
    val controlGlyph: Color,
    /** mediaInactiveControlColor: waveform not played yet. */
    val inactive: Color,
    val fileTitle: Color,
    val fileDescription: Color,
    /** outgoingCheckColor (read / sent checks). */
    val check: Color,
    val reactionBg: Color,
    val reactionFg: Color,
    val reactionActiveBg: Color,
    val reactionActiveFg: Color,
    /** polls.bar. */
    val pollBar: Color,
    /** polls.radioButton. */
    val radio: Color,
    /** polls.separator. */
    val separator: Color,
    val mediaPlaceholder: Color,
    /** textSelectionColor. */
    val selection: Color,
)

// ---- Color math (UIColor.withMultiplied / lightness as used by Telegram-iOS themes) ----

/** Plain average of [colors] (opaque). */
internal fun averageColor(colors: List<Color>): Color = if (colors.isEmpty()) Color.Black else Color(
    colors.sumOf { it.red.toDouble() }.toFloat() / colors.size,
    colors.sumOf { it.green.toDouble() }.toFloat() / colors.size,
    colors.sumOf { it.blue.toDouble() }.toFloat() / colors.size,
)

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

/** UIColor.withMultipliedBrightnessBy. */
internal fun Color.brightnessBy(factor: Float): Color {
    val (h, s, b) = hsb()
    return hsbColor(h, s, (b * factor).coerceIn(0f, 1f)).copy(alpha = alpha)
}

/** UIColor.withMultipliedAlpha. */
internal fun Color.multipliedAlpha(factor: Float): Color = copy(alpha = (alpha * factor).coerceIn(0f, 1f))

/** UIColor.distance(to:) (the "redmean" color distance Telegram-iOS uses, 0 = equal). */
internal fun Color.distanceTo(other: Color): Int {
    val r1 = (red * 255f).toInt(); val g1 = (green * 255f).toInt(); val b1 = (blue * 255f).toInt()
    val r2 = (other.red * 255f).toInt(); val g2 = (other.green * 255f).toInt(); val b2 = (other.blue * 255f).toInt()
    val rMean = (r1 + r2) / 2
    val r = r1 - r2
    val g = g1 - g2
    val b = b1 - b2
    return (((512 + rMean) * r * r) shr 8) + 4 * g * g + (((767 - rMean) * b * b) shr 8)
}

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

/**
 * Inter at its text optical size (opsz 14) is ~15-20 % wider than SF Pro at the same point size while cap heights
 * match; the display cut (opsz 32) is ~10 % wider. Both families use opsz 32 and [iosTracking] closes the rest.
 */
val InterText = interFamily(32f)
val InterDisplay = interFamily(32f)

/**
 * Vazirmatn (Non-Latin build, variable, SIL OFL): Persian / Arabic script. Inter has no Arabic glyphs, so Android
 * would pick a system fallback (Noto Naskh / Roboto-era Arabic) with mismatched weights and metrics. Runs of Arabic
 * script are set in this family by [withPersianFont]; weights are pinned like Inter's so bold / semibold text matches.
 */
@OptIn(ExperimentalTextApi::class)
val PersianFamily = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.vazirmatn, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

private fun isArabicScript(ch: Char): Boolean {
    val c = ch.code
    return c in 0x0600..0x06FF || c in 0x0750..0x077F || c in 0x08A0..0x08FF || c in 0xFB50..0xFDFF || c in 0xFE70..0xFEFF
}

/** True if [s] contains Persian / Arabic script. A cheap scan; most text is Latin and returns at once. */
fun hasArabicScript(s: CharSequence): Boolean {
    for (i in 0 until s.length) if (isArabicScript(s[i])) return true
    return false
}

/**
 * Adds a [PersianFamily] span over every Arabic-script run of [text] (a ZWNJ / ZWJ inside a run stays in it), at
 * [offset] in this builder. Weight, size and color come from the surrounding style, so mixed Persian / English
 * lines keep one line height (all [TgTypography] styles use a fixed lineHeight).
 */
fun androidx.compose.ui.text.AnnotatedString.Builder.addPersianSpans(text: CharSequence, offset: Int = 0) {
    var i = 0
    val n = text.length
    while (i < n) {
        if (!isArabicScript(text[i])) { i++; continue }
        var j = i + 1
        while (j < n && (isArabicScript(text[j]) || ((text[j] == '\u200C' || text[j] == '\u200D') && j + 1 < n && isArabicScript(text[j + 1])))) j++
        addStyle(androidx.compose.ui.text.SpanStyle(fontFamily = PersianFamily), offset + i, offset + j)
        i = j
    }
}

/** [this] with Persian runs in [PersianFamily]; returns [this] unchanged when it has no Arabic script. */
fun androidx.compose.ui.text.AnnotatedString.withPersianFont(): androidx.compose.ui.text.AnnotatedString {
    if (!hasArabicScript(text)) return this
    val src = this
    return androidx.compose.ui.text.buildAnnotatedString {
        append(src)
        addPersianSpans(src.text)
    }
}

/** A plain string as an AnnotatedString with Persian runs in [PersianFamily]. */
fun String.withPersianFont(): androidx.compose.ui.text.AnnotatedString {
    val src = this
    return androidx.compose.ui.text.buildAnnotatedString {
        append(src)
        if (hasArabicScript(src)) addPersianSpans(src)
    }
}

/** Apple's SF Pro tracking per point size (HIG Dynamic Type tables), in points. */
private val SfTracking = listOf(
    11f to 0.06f, 12f to 0f, 13f to -0.08f, 14f to -0.15f, 15f to -0.23f, 16f to -0.31f, 17f to -0.43f,
    18f to -0.44f, 20f to -0.45f, 22f to -0.26f, 24f to 0.07f, 28f to 0.38f, 34f to 0.40f,
)

/**
 * Extra tracking that makes Inter (opsz 32) as wide as SF Pro: measured with the font's real advances against iOS
 * text widths ("Translate to English" 17 pt = 131 pt, "Message" 17 pt = 61 pt, "ARSLWN HS" 17 pt semibold = 87.5 pt).
 */
private const val InterWidthCorrectionEm = -0.02f

/** SF Pro tracking at [size] pt (interpolated) plus the Inter width correction, in em. */
internal fun iosTracking(size: Float): TextUnit {
    val t = SfTracking
    val pt = when {
        size <= t.first().first -> t.first().second
        size >= t.last().first -> t.last().second
        else -> {
            val i = t.indexOfFirst { it.first >= size }
            val (s0, v0) = t[i - 1]
            val (s1, v1) = t[i]
            v0 + (v1 - v0) * (size - s0) / (s1 - s0)
        }
    }
    return (pt / size + InterWidthCorrectionEm).em
}

/** An iOS text style at [size] pt; tracking follows the point size of the style before Text Size scaling. */
private fun ios(size: Float, weight: FontWeight = FontWeight.Normal, baseSize: Float = size, lineHeight: Float = size * 1.2f) =
    TextStyle(
        fontFamily = if (size >= 20f) InterDisplay else InterText,
        fontSize = size.sp,
        fontWeight = weight,
        letterSpacing = iosTracking(baseSize),
        lineHeight = lineHeight.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

fun typography(scale: Float) = TgTypography(
    largeTitle = ios(34f * scale, FontWeight.Bold, 34f),
    title1 = ios(28f * scale, FontWeight.Bold, 28f),
    title2 = ios(22f * scale, FontWeight.Bold, 22f),
    title3 = ios(20f * scale, FontWeight.SemiBold, 20f),
    headline = ios(17f * scale, FontWeight.SemiBold, 17f, 22f * scale),
    body = ios(17f * scale, FontWeight.Normal, 17f, 22f * scale),
    callout = ios(16f * scale, FontWeight.Normal, 16f, 21f * scale),
    subheadline = ios(15f * scale, FontWeight.Normal, 15f, 20f * scale),
    footnote = ios(13f * scale, FontWeight.Normal, 13f, 18f * scale),
    caption1 = ios(12f * scale, FontWeight.Normal, 12f, 16f * scale),
    caption2 = ios(11f * scale, FontWeight.Normal, 11f, 13f * scale),
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
    // Compact = Telegram iOS 26 measured on the owner's iPhone photo (ios-10, pixel-scanned): 69 pt pitch, 55 pt avatar
    // (avatar / pitch = 44 / 55 px), semibold 16 pt name (Telegram-iOS ChatListItem: 16/17 of the list size), 15 pt preview.
    Compact("Compact", 69, 55, 16f, 15f),
    Regular("Regular", 82, 60, 17f, 16f),
    Large("Large", 90, 66, 18f, 17f),
}

/**
 * The built-in themes of the Appearance carousel, in Telegram-iOS order (ThemeSettingsController: dayClassic,
 * nightAccent, day, night) and with their iOS names.
 */
enum class ColorTheme(val title: String, val dark: Boolean) {
    Classic("Classic", false),
    Day("Day", false),
    Night("Night", true),
    Tinted("Tinted Night", true),
}

/** Auto-Night Mode (ThemeAutoNightSettingsController): when the night theme replaces the day theme. */
enum class AutoNight(val title: String) {
    Disabled("Disabled"),
    System("System"),
    Scheduled("Scheduled"),
}

/**
 * A chat wallpaper: 1-4 gradient colors (all equal for a plain color) under Telegram's pattern at [intensity]
 * percent (Telegram-iOS WallpaperSettings.intensity: negative = the dark "inverted" pattern, 0 = no pattern).
 */
@Immutable
data class WallpaperOption(val colors: List<Color>, val solid: Boolean = false, val intensity: Int = if (solid) 0 else 50)

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

/** Everything that decides the palette; [buildColors] (TelegramThemes.kt) turns it into [TgColors]. */
@Immutable
data class ThemeSpec(
    val theme: ColorTheme,
    /** [AccentOption.key] in [accentOptions] of [theme]; unknown / empty = the theme's default. */
    val accent: String = "",
    /** null = the theme's own wallpaper (for the chosen accent). */
    val wallpaper: WallpaperOption? = null,
    val pattern: Boolean = true,
    /** 0..1, 0.5 = Telegram's default intensity. */
    val patternIntensity: Float = 0.5f,
    val bubbleGradient: Boolean = true,
    val glassTint: Boolean = false,
)

/** User-tunable appearance settings, persisted in SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("tglass", Context.MODE_PRIVATE)

    var glassLevel by mutableStateOf(GlassLevel.entries[prefs.getInt("glass", 0)])
        private set
    var animations by mutableStateOf(prefs.getBoolean("anim", true))
        private set
    /** Draws frame rate / slowest frame in a corner (Settings → Power Saving → Performance Overlay). */
    var perfOverlay by mutableStateOf(prefs.getBoolean("perfOverlay", false))
        private set
    var textScale by mutableFloatStateOf(prefs.getFloat("textScale", 1f))
        private set
    /** Interface Scale multiplier on top of the automatic fit to the iPhone's 393-point width (see [ProvideUiScale]). */
    var uiScale by mutableFloatStateOf(prefs.getFloat("uiScale", 1f))
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
    /** Accent of each [ColorTheme]: an [AccentOption.key] of [accentOptions]. */
    private val accents = mutableStateListOf(*Array(ColorTheme.entries.size) { i -> loadAccent(ColorTheme.entries[i]) })
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

    /** The accent key of [theme]; versions before 0.18 stored an index into one shared list of base colors. */
    private fun loadAccent(theme: ColorTheme): String {
        val options = accentOptions(theme)
        val stored = prefs.getString("accentKey_${theme.name}", null)
        if (stored != null && options.any { it.key == stored }) return stored
        val legacy = listOf("blue", "cyan", "green", "pink", "orange", "purple", "red", "yellow", "gray")
        val old = prefs.getInt("accent_${theme.name}", 0)
        val key = if (old > 0) legacy.getOrNull(old) else null
        return options.firstOrNull { it.key == key }?.key ?: options[0].key
    }

    fun accentKey(theme: ColorTheme): String = accents[theme.ordinal]

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
    fun updateAccent(theme: ColorTheme, key: String) { accents[theme.ordinal] = key; prefs.edit().putString("accentKey_${theme.name}", key).apply() }
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
        accent = accentKey(theme),
        wallpaper = if (theme.dark) NightWallpapers.getOrNull(wallpaperNightIndex) else DayWallpapers.getOrNull(wallpaperIndex),
        pattern = wallpaperPattern,
        patternIntensity = patternIntensity,
        bubbleGradient = bubbleGradient,
        glassTint = glassTint,
    )

    fun updateGlass(v: GlassLevel) { glassLevel = v; prefs.edit().putInt("glass", v.ordinal).apply() }
    fun updateAnimations(v: Boolean) { animations = v; prefs.edit().putBoolean("anim", v).apply() }
    fun updatePerfOverlay(v: Boolean) { perfOverlay = v; prefs.edit().putBoolean("perfOverlay", v).apply() }
    fun updateUiScale(v: Float) { uiScale = v; prefs.edit().putFloat("uiScale", v).apply() }
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

/** Width of the iPhone the design is taken from (Telegram iOS screenshots, 393 pt). */
private const val ReferenceWidthDp = 393f

/**
 * Every dp in the app was measured on the 393 pt wide iPhone. Android phones are 360–430 dp wide at whatever
 * "Display size" the owner picked, so the same dp looks too big on a narrow / zoomed phone. This scales the
 * density so the layout is always [ReferenceWidthDp] dp wide (times the user's Interface Scale), and keeps the
 * system font scale inside a sane range so a huge system font cannot blow the controls up.
 */
@Composable
fun ProvideUiScale(userScale: Float, content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val k = if (cfg.screenWidthDp >= 600) userScale else (cfg.screenWidthDp / ReferenceWidthDp * userScale).coerceIn(0.75f, 1.3f)
    val d = remember(density.density, density.fontScale, k) { Density(density.density * k, density.fontScale.coerceIn(0.85f, 1.1f)) }
    val c = remember(cfg, k) {
        android.content.res.Configuration(cfg).apply {
            screenWidthDp = (cfg.screenWidthDp / k).toInt()
            screenHeightDp = (cfg.screenHeightDp / k).toInt()
            smallestScreenWidthDp = (cfg.smallestScreenWidthDp / k).toInt()
            densityDpi = (cfg.densityDpi * k).toInt()
        }
    }
    CompositionLocalProvider(LocalDensity provides d, LocalConfiguration provides c, content = content)
}

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
    ) {
        ProvideUiScale(settings.uiScale, content)
    }
}

/** Shows [content] with the dark palette regardless of the app theme (full-screen media viewer and editor). */
@Composable
fun ProvideDarkColors(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTgColors provides DarkColors, content = content)
}
