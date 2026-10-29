package com.abtin.tglass.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// The four built-in Telegram-iOS themes, ported from TelegramMessenger/Telegram-iOS
// submodules/TelegramPresentationData/Sources:
//   Classic       = makeDefaultDayPresentationTheme(day: false)   + customizeDefaultDayTheme
//   Day           = makeDefaultDayPresentationTheme(day: true)    + customizeDefaultDayTheme
//   Night         = makeDefaultDarkPresentationTheme              + customizeDefaultDarkPresentationTheme
//   Tinted Night  = makeDefaultDarkTintedPresentationTheme        + customizeDefaultDarkTintedPresentationTheme
// Accent rows: SettingsUI ThemePickerController (accentColor entries) + ThemeColorPresets.swift.
// Default wallpapers: defaultBuiltinWallpaperGradientColors / defaultDarkWallpaperGradientColors /
// PresentationThemeBaseColor.colorWallpaper (pattern `fqv01SQemVIBAAAApND8LDRUhRU`).

private fun hex(rgb: Long, alpha: Float = 1f): Color {
    val c = Color(0xFF000000 or rgb)
    return if (alpha < 1f) c.copy(alpha = alpha) else c
}

private val White = Color.White
private val Black = Color.Black

/** PresentationThemeBaseColor. */
enum class BaseAccent(val rgb: Long) {
    Blue(0x0088FF), Cyan(0x00C2ED), Green(0x29B327), Pink(0xEB6CA4), Orange(0xF08200), Purple(0x9472EE),
    Red(0xD33213), Yellow(0xEDB400), Gray(0x6D839E), Black(0x000000), White(0xFFFFFF);

    val color: Color get() = Color(0xFF000000 or rgb)
}

/**
 * One circle of the accent row (PresentationThemeAccentColor): a base color, or a preset with its own accent,
 * bubble colors and wallpaper. [isDefault] = the theme exactly as Telegram ships it (no accent customization).
 */
@Immutable
data class AccentOption(
    val key: String,
    val base: BaseAccent?,
    val accent: Color? = null,
    val bubbles: List<Color> = emptyList(),
    val wallpaper: WallpaperOption? = null,
    val isDefault: Boolean = false,
)

private fun pw(vararg c: Long, intensity: Int = 50) = WallpaperOption(c.map { hex(it) }, intensity = intensity)

private fun preset(key: String, accent: Long, bubbles: List<Long>, wallpaper: WallpaperOption? = null) =
    AccentOption(key, null, hex(accent), bubbles.map { hex(it) }, wallpaper)

private fun bases(vararg b: BaseAccent) = b.map { AccentOption(it.name.lowercase(), it) }

/** ThemeColorPresets.swift dayClassicColorPresets (row order). */
private val ClassicPresets = listOf(
    preset("p106", 0xF55783, listOf(0xD6F5FF, 0xC9FDFE), pw(0x8DC0EB, 0xB9D1EA, 0xC6B1EF, 0xEBD7EF)),
    preset("p102", 0xFF5FA9, listOf(0xFFF4D7), pw(0xEAA36E, 0xF0E486, 0xF29EBF, 0xE8C06E)),
    preset("p104", 0x5A9E29, listOf(0xFFF8DF), pw(0x7FC289, 0xE4D573, 0xAFD677, 0xF0C07A)),
    preset("p101", 0x7E5FE5, listOf(0xF5E2FF), pw(0xE4B2EA, 0x8376C2, 0xEAB9D9, 0xB493E6)),
    preset("p107", 0x2CB9ED, listOf(0xADF7B5, 0xFCFF8B), pw(0x1A2E1A, 0x47623C, 0x222E24, 0x314429)),
    preset("p103", 0x199972, listOf(0xFFFEC7), pw(0xDCEB92, 0x8FE1D6, 0x67A3F2, 0x85D685)),
    preset("p105", 0xDA90D9, listOf(0x94FFF9, 0xCCFFC7), pw(0xFFC3B2, 0xE2C0FF, 0xFFE7B2)),
)

/** dayColorPresets. */
private val DayPresets = listOf(
    preset("p101", 0x0088FF, listOf(0x0088FF, 0xFF53F4)),
    preset("p102", 0x00B09B, listOf(0xAEE946, 0x00B09B)),
    preset("p103", 0xD33213, listOf(0xF9DB00, 0xD33213)),
    preset("p104", 0xEA8CED, listOf(0xEA8CED, 0x00C2ED)),
)

/** nightColorPresets. */
private val NightPresets = listOf(
    preset("p102", 0x00B09B, listOf(0xAEE946, 0x00B09B), pw(0xE4B2EA, 0x8376C2, 0xEAB9D9, 0xB493E6, intensity = -35)),
    preset("p103", 0xD33213, listOf(0xF9DB00, 0xD33213), pw(0xFEC496, 0xDD6CB9, 0x962FBF, 0x4F5BD5, intensity = -40)),
    preset("p104", 0xEA8CED, listOf(0xEA8CED, 0x00C2ED), pw(0x8ADBF2, 0x888DEC, 0xE39FEA, 0x679CED, intensity = -30)),
)

private val ClassicAccents: List<AccentOption> = listOf(AccentOption("default", null, isDefault = true)) + ClassicPresets +
    bases(BaseAccent.Blue, BaseAccent.Cyan, BaseAccent.Green, BaseAccent.Pink, BaseAccent.Orange, BaseAccent.Purple, BaseAccent.Red, BaseAccent.Yellow, BaseAccent.Gray)

private val DayAccents: List<AccentOption> = listOf(AccentOption("blue", BaseAccent.Blue, isDefault = true)) + DayPresets +
    bases(BaseAccent.Cyan, BaseAccent.Green, BaseAccent.Pink, BaseAccent.Orange, BaseAccent.Purple, BaseAccent.Red, BaseAccent.Yellow, BaseAccent.Gray, BaseAccent.Black)

/** Night's blue is Telegram's 0x3E88F7 customization (what iOS applies for Auto-Night and shows in the carousel). */
private val NightAccents: List<AccentOption> = listOf(AccentOption("blue", BaseAccent.Blue)) + NightPresets +
    bases(BaseAccent.Cyan, BaseAccent.Green, BaseAccent.Pink, BaseAccent.Orange, BaseAccent.Purple, BaseAccent.Red, BaseAccent.Yellow, BaseAccent.White)

private val TintedAccents: List<AccentOption> = listOf(AccentOption("blue", BaseAccent.Blue, isDefault = true)) +
    bases(BaseAccent.Cyan, BaseAccent.Green, BaseAccent.Pink, BaseAccent.Orange, BaseAccent.Purple, BaseAccent.Red, BaseAccent.Yellow, BaseAccent.Gray)

/** The accent row of [theme], in Telegram-iOS order. */
fun accentOptions(theme: ColorTheme): List<AccentOption> = when (theme) {
    ColorTheme.Classic -> ClassicAccents
    ColorTheme.Day -> DayAccents
    ColorTheme.Night -> NightAccents
    ColorTheme.Tinted -> TintedAccents
}

fun accentOption(theme: ColorTheme, key: String): AccentOption =
    accentOptions(theme).let { list -> list.firstOrNull { it.key == key } ?: list[0] }

// ---- Wallpapers ----

/** defaultBuiltinWallpaperGradientColors (Classic). */
private val ClassicGradient = listOf(hex(0xDBDDBB), hex(0x6BA587), hex(0xD5D88D), hex(0x88B884))
/** defaultDarkWallpaperGradientColors (Night, shown through the pattern at intensity -34). */
private val NightGradient = listOf(hex(0x598BF6), hex(0x7A5EEF), hex(0xD67CFF), hex(0xF38B58))

private val ClassicWallpaper = WallpaperOption(ClassicGradient, intensity = 50)
private val DayWallpaper = WallpaperOption(List(4) { White }, solid = true)
private val NightWallpaper = WallpaperOption(NightGradient, intensity = -34)
private val TintedWallpaper = pw(0x1B2836, 0x121A22, 0x1B2836, 0x121A22)

/** DefaultDarkPresentationTheme colorWallpaper. */
private fun nightBaseWallpaper(b: BaseAccent?): WallpaperOption? = when (b) {
    BaseAccent.Cyan -> pw(0xA4DBFF, 0x009FDD, 0x527BDD, intensity = -30)
    BaseAccent.Green -> pw(0x7FA381, 0xFFF5C5, 0x336F55, 0xFBE37D, intensity = -20)
    BaseAccent.Pink -> pw(0xE4B2EA, 0x8376C2, 0xEAB9D9, 0xB493E6, intensity = -35)
    BaseAccent.Orange -> pw(0xFEC496, 0xDD6CB9, 0x962FBF, 0x4F5BD5, intensity = -40)
    BaseAccent.Purple -> pw(0x8ADBF2, 0x888DEC, 0xE39FEA, 0x679CED, intensity = -30)
    BaseAccent.Red -> pw(0xE4B2EA, 0x8376C2, 0xEAB9D9, 0xB493E6, intensity = -35)
    BaseAccent.Yellow -> pw(0xEAA36E, 0xF0E486, 0xF29EBF, 0xE8C06E, intensity = -30)
    else -> null
}

/** DefaultDarkTintedPresentationTheme colorWallpaper. */
private fun tintedBaseWallpaper(b: BaseAccent?): WallpaperOption? = when (b) {
    BaseAccent.Blue -> pw(0x1E3557, 0x182036, 0x1C4352, 0x16263A, intensity = 40)
    BaseAccent.Cyan -> pw(0x1E3557, 0x151A36, 0x1C4352, 0x2A4541, intensity = 40)
    BaseAccent.Green -> pw(0x2D4836, 0x172B19, 0x364331, 0x103231, intensity = 40)
    BaseAccent.Pink -> pw(0x2C0B22, 0x290020, 0x160A22, 0x3B1834, intensity = 40)
    BaseAccent.Orange -> pw(0x2C211B, 0x442917, 0x22191F, 0x3B2714, intensity = 40)
    BaseAccent.Purple -> pw(0x3A1C3A, 0x24193C, 0x392E3E, 0x1A1632, intensity = 40)
    BaseAccent.Red -> pw(0x2C211B, 0x44332A, 0x22191F, 0x3B2D36, intensity = 40)
    BaseAccent.Yellow -> pw(0x2C2512, 0x45360B, 0x221D08, 0x3B2F13, intensity = 40)
    BaseAccent.Gray -> pw(0x1C2731, 0x1A1C25, 0x27303B, 0x1B1B21, intensity = 40)
    else -> null
}

/** The wallpaper a theme uses with [accentKey] when the user did not pick one (theme.chat.defaultWallpaper). */
fun themeWallpaper(theme: ColorTheme, accentKey: String): WallpaperOption {
    val o = accentOption(theme, accentKey)
    return when (theme) {
        // Classic + a base color: suggestedWallpaper = the same gradient, without the pattern.
        ColorTheme.Classic -> o.wallpaper ?: if (o.isDefault) ClassicWallpaper else WallpaperOption(ClassicGradient, intensity = 0)
        ColorTheme.Day -> DayWallpaper
        ColorTheme.Night -> o.wallpaper ?: nightBaseWallpaper(o.base) ?: NightWallpaper
        ColorTheme.Tinted -> o.wallpaper ?: (if (o.isDefault) TintedWallpaper else tintedBaseWallpaper(o.base))
            ?: WallpaperOption(List(4) { tintedAccent(o).multiplied(1.024f, 0.573f, 0.18f) }, solid = true)
    }
}

private fun gradient(vararg c: Long, intensity: Int = 50) = WallpaperOption(c.map { hex(it) }, intensity = intensity)
private fun solid(c: Long) = WallpaperOption(List(4) { hex(c) }, solid = true)

/**
 * Day wallpapers. Index 0 = the theme's own wallpaper; 1-4 keep the order of earlier versions so a saved
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
    gradient(0x8DC0EB, 0xB9D1EA, 0xC6B1EF, 0xEBD7EF),
    gradient(0x7FC289, 0xE4D573, 0xAFD677, 0xF0C07A),
    gradient(0xDCEB92, 0x8FE1D6, 0x67A3F2, 0x85D685),
    gradient(0xFFC3B2, 0xE2C0FF, 0xFFE7B2),
)

/** Night wallpapers (index 0 = the theme's own). New entries are appended so saved indexes keep their meaning. */
val NightWallpapers: List<WallpaperOption?> = listOf(
    null,
    gradient(0x1E3557, 0x182036, 0x1C4352, 0x16263A, intensity = 40),
    gradient(0x0B1A2B, 0x1C2B3E, 0x0F2031, 0x223246, intensity = 40),
    gradient(0x2D4836, 0x172B19, 0x364331, 0x103231, intensity = 40),
    gradient(0x2C0B22, 0x290020, 0x160A22, 0x3B1834, intensity = 40),
    gradient(0x2C211B, 0x442917, 0x22191F, 0x3B2714, intensity = 40),
    gradient(0x3A1C3A, 0x24193C, 0x392E3E, 0x1A1632, intensity = 40),
    gradient(0x2C2512, 0x45360B, 0x221D08, 0x3B2F13, intensity = 40),
    gradient(0x1C2731, 0x1A1C25, 0x27303B, 0x1B1B21, intensity = 40),
    solid(0x000000), solid(0x1C1C1E), solid(0x121B26), solid(0x17212B), solid(0x1E2A22), solid(0x2A1E26),
    solid(0x26221A), solid(0x23212E),
    gradient(0x598BF6, 0x7A5EEF, 0xD67CFF, 0xF38B58, intensity = -34),
    gradient(0xA4DBFF, 0x009FDD, 0x527BDD, intensity = -30),
    gradient(0x7FA381, 0xFFF5C5, 0x336F55, 0xFBE37D, intensity = -20),
    gradient(0xE4B2EA, 0x8376C2, 0xEAB9D9, 0xB493E6, intensity = -35),
    gradient(0xFEC496, 0xDD6CB9, 0x962FBF, 0x4F5BD5, intensity = -40),
    gradient(0x8ADBF2, 0x888DEC, 0xE39FEA, 0x679CED, intensity = -30),
    gradient(0xEAA36E, 0xF0E486, 0xF29EBF, 0xE8C06E, intensity = -30),
    gradient(0x1B2836, 0x121A22, 0x1B2836, 0x121A22),
)

// ---- Classic / Day (DefaultDayPresentationTheme.swift) ----

private val DefaultDayAccent = hex(0x0088FF)

/** customizeDefaultDayTheme: a too-light accent is darkened (lightness > 0.705). */
private fun dayAdjustedAccent(x: Color): Color = if (x.lightness > 0.705f) {
    val (h, s, b) = x.hsb()
    hsbColor(h, min(1f, s * 1.1f), min(b, 0.6f))
} else x

/** message.incoming (Classic). */
private val ClassicIncoming = BubblePalette(
    text = Black, meta = hex(0x77828C), link = hex(0x004BAD), accent = DefaultDayAccent, control = DefaultDayAccent,
    controlGlyph = White, inactive = hex(0xCACACA), fileTitle = hex(0x0B8BED), fileDescription = hex(0x999999),
    check = hex(0x77828C), reactionBg = DefaultDayAccent.copy(alpha = 0.1f), reactionFg = DefaultDayAccent,
    reactionActiveBg = DefaultDayAccent, reactionActiveFg = White, pollBar = DefaultDayAccent, radio = hex(0xC8C7CC),
    separator = hex(0xC8C7CC), mediaPlaceholder = hex(0xE8ECF0), selection = DefaultDayAccent.copy(alpha = 0.2f),
)

/** message.outgoing (Classic): the green bubbles. */
private val ClassicOutgoing = BubblePalette(
    text = Black, meta = hex(0x008C09, 0.8f), link = hex(0x004BAD), accent = hex(0x00A700), control = hex(0x3FC33B),
    controlGlyph = hex(0xE1FFC7), inactive = hex(0x93D987), fileTitle = hex(0x3FAA3C), fileDescription = hex(0x6FB26A),
    check = hex(0x19C700), reactionBg = hex(0x3FC33B, 0.12f), reactionFg = hex(0x3FC33B), reactionActiveBg = hex(0x3FC33B),
    reactionActiveFg = hex(0xE1FFC7), pollBar = hex(0x00A700), radio = hex(0x93D987), separator = hex(0x93D987),
    mediaPlaceholder = hex(0xD2F2B6), selection = hex(0xBBDE9F),
)

/** messageDay.incoming. */
private val DayIncoming = BubblePalette(
    text = Black, meta = hex(0x525252, 0.6f), link = hex(0x004BAD), accent = DefaultDayAccent, control = DefaultDayAccent,
    controlGlyph = White, inactive = hex(0xCACACA), fileTitle = DefaultDayAccent, fileDescription = hex(0x999999),
    check = hex(0x525252, 0.6f), reactionBg = DefaultDayAccent.copy(alpha = 0.1f), reactionFg = DefaultDayAccent,
    reactionActiveBg = DefaultDayAccent, reactionActiveFg = White, pollBar = DefaultDayAccent, radio = hex(0xC8C7CC),
    separator = hex(0xC8C7CC), mediaPlaceholder = White.brightnessBy(0.95f), selection = DefaultDayAccent.copy(alpha = 0.3f),
)

/** messageDay.outgoing: white content on the blue gradient. */
private val DayOutgoing = BubblePalette(
    text = White, meta = White.copy(alpha = 0.65f), link = White, accent = White, control = White,
    controlGlyph = DefaultDayAccent, inactive = White.copy(alpha = 0.65f), fileTitle = White,
    fileDescription = White.copy(alpha = 0.65f), check = White, reactionBg = White.copy(alpha = 0.12f), reactionFg = White,
    reactionActiveBg = White, reactionActiveFg = DefaultDayAccent, pollBar = White, radio = White.copy(alpha = 0.65f),
    separator = White.copy(alpha = 0.65f), mediaPlaceholder = hex(0x0077D9), selection = White.copy(alpha = 0.2f),
)

/** customizeDefaultDayTheme: the incoming side follows the accent. */
private fun BubblePalette.withDayAccent(a: Color) = copy(
    accent = a, control = a, fileTitle = a, pollBar = a, reactionBg = a.multipliedAlpha(0.1f), reactionFg = a,
    reactionActiveBg = a, selection = a.copy(alpha = 0.2f),
)

/** customizeDefaultDayTheme: outgoing colors derived from the bubble colors (and the outgoing accent, Classic). */
private fun dayOutgoingFrom(bubbles: List<Color>, outAccent: Color?, day: Boolean, accent: Color): BubblePalette {
    val first = bubbles.first()
    val mixed = averageColor(bubbles)
    val hueF = 0.75f
    val satF = 1.1f
    val text: Color; val meta: Color; val accentText: Color; val link: Color; val control: Color; val inactive: Color
    val fileTitle: Color; val fileDescription: Color; var placeholder: Color; val radio: Color; val pollBar: Color
    val selection: Color; val check: Color
    if (mixed.lightness > 0.705f) {
        text = Black
        if (outAccent != null) {
            meta = outAccent; accentText = outAccent; link = outAccent; control = outAccent
            inactive = outAccent.multipliedAlpha(0.5f); fileTitle = outAccent; pollBar = outAccent
            selection = outAccent.copy(alpha = 0.2f); check = outAccent
        } else {
            meta = first.multiplied(1.344f * hueF, 4.554f * satF, 0.549f).copy(alpha = 0.8f)
            accentText = mixed.multiplied(1.302f * hueF, 4.554f * satF, 0.655f)
            link = hex(0x004BAD)
            control = mixed.multiplied(1.283f * hueF, 3.176f, 0.765f)
            inactive = mixed.multiplied(1.207f * hueF, 1.721f, 0.851f)
            fileTitle = mixed.multiplied(1.285f * hueF, 2.946f, 0.667f)
            pollBar = mixed.multiplied(1.283f * hueF, 3.176f, 0.765f)
            selection = mixed.multiplied(1.013f * hueF, 1.292f, 0.871f)
            check = mixed.multiplied(1.344f * hueF, 4.554f * satF, 0.549f).copy(alpha = 0.8f)
        }
        fileDescription = first.multiplied(1.257f * hueF, 1.842f, 0.698f)
        placeholder = first.multiplied(0.998f, 1.129f, 0.949f)
        radio = first.multiplied(1.207f * hueF, 1.721f, 0.851f)
    } else {
        text = White; meta = White.copy(alpha = 0.65f); accentText = White; link = White; control = White
        inactive = White.copy(alpha = 0.65f); fileTitle = White; fileDescription = White.copy(alpha = 0.65f)
        placeholder = first.brightnessBy(0.95f); radio = White.copy(alpha = 0.65f); pollBar = White
        selection = White.copy(alpha = 0.2f); check = White
    }
    if (day) placeholder = accent.brightnessBy(0.95f)
    // mediaControlInnerBackgroundColor / reactionActiveForeground are `.clear`: the bubble shows through.
    val knockout = bubbles.last()
    return BubblePalette(
        text = text, meta = meta, link = link, accent = accentText, control = control, controlGlyph = knockout,
        inactive = inactive, fileTitle = fileTitle, fileDescription = fileDescription, check = check,
        reactionBg = control.multipliedAlpha(0.1f), reactionFg = control, reactionActiveBg = control, reactionActiveFg = knockout,
        pollBar = pollBar, radio = radio, separator = radio, mediaPlaceholder = placeholder, selection = selection,
    )
}

private fun lightColors(
    accent: Color, classic: Boolean, bubbleIn: Color, bubbles: List<Color>,
    incoming: BubblePalette, outgoing: BubblePalette,
): TgColors = TgColors(
    isDark = false,
    groupedBackground = hex(0xEFEFF4),
    background = White,
    cell = White,
    cellPressed = hex(0xE5E5EA),
    text = Black,
    secondaryText = hex(0x8E8E93),
    tertiaryText = hex(0xBAB9BE),
    separator = hex(0xC8C7CC),
    accent = accent,
    destructive = hex(0xFF3B30),
    green = hex(0x34C759),
    orange = hex(0xFF9500),
    mutedBadge = hex(0xB6B6BB),
    searchField = Black.copy(alpha = 0.06f),
    bubbleIn = bubbleIn,
    bubbleOut = bubbles.last(),
    bubbleOutGradient = if (bubbles.size > 1 && bubbles.distinct().size > 1) bubbles else null,
    bubbleInText = incoming.text,
    bubbleOutText = outgoing.text,
    bubbleInMeta = incoming.meta,
    bubbleOutMeta = outgoing.meta,
    bubbleOutAccent = outgoing.accent,
    // GlassBackgroundComponent .panel (light): white veil (legacy fill white @ 70 %, native tint white @ 10 %).
    glassSurface = White.copy(alpha = 0.6f),
    // rootNavigationBar.opaqueBackgroundColor: #F7F7F7 mixed with 14 % white.
    glassOpaque = hex(0xF8F8F8),
    menuSurface = hex(0xF9F9F9, 0.78f),
    scrim = hex(0x000A26, 0.2f),
    wallpaper = ClassicGradient,
    wallpaperPattern = Color.Transparent,
    serviceBubble = Black.copy(alpha = 0.2f),
    onlineDot = hex(0x4CC91F),
    // chatList.checkmarkColor: Classic keeps its green checks whatever the accent; Day uses the accent.
    listCheckmark = if (classic) hex(0x21C004) else accent,
    pinnedRow = hex(0xF7F7F7),
    onAccent = White,
    badgeText = White,
    mutedBadgeText = White,
    chatListSecondary = hex(0x8E8E93),
    muteIcon = hex(0xA7A7AD),
    pinnedIcon = hex(0xB6B6BB),
    draftText = hex(0xDD4B39),
    verified = if (classic) DefaultDayAccent else accent,
    verifiedFg = White,
    sectionHeader = hex(0x6D6D72),
    switchOn = hex(0x35C759),
    serviceText = White,
    unreadBar = if (classic) White.copy(alpha = 0.9f) else White,
    unreadBarText = if (classic) hex(0x86868D) else hex(0x8D8E93),
    inputPlaceholder = Black.copy(alpha = 0.4f),
    inputIcon = Black.copy(alpha = 0.5f),
    bubbleInPalette = incoming,
    bubbleOutPalette = outgoing,
)

private fun classicColors(o: AccentOption): TgColors {
    if (o.isDefault) return lightColors(DefaultDayAccent, true, White, listOf(hex(0xE1FFC7)), ClassicIncoming, ClassicOutgoing)
    val x = o.accent ?: o.base?.color ?: DefaultDayAccent
    var outAccent: Color? = null
    val bubbles = if (o.bubbles.isNotEmpty()) o.bubbles else {
        val (h, s, b) = x.hsb()
        outAccent = dayAdjustedAccent(x)
        listOf(hsbColor(h, if (s > 0f && b > 0f) 0.14f else 0f, 0.79f + b * 0.21f))
    }
    val accent = dayAdjustedAccent(x)
    return lightColors(accent, true, White, bubbles, ClassicIncoming.withDayAccent(accent), dayOutgoingFrom(bubbles, outAccent, false, accent))
}

private fun dayColors(o: AccentOption): TgColors {
    if (o.isDefault) return lightColors(DefaultDayAccent, false, White, listOf(hex(0x57B2E0), DefaultDayAccent), DayIncoming, DayOutgoing)
    val x = o.accent ?: o.base?.color ?: DefaultDayAccent
    val bubbles = o.bubbles.ifEmpty { listOf(x.multiplied(0.966f, 0.61f, 0.98f), x) }
    val accent = dayAdjustedAccent(x)
    return lightColors(accent, false, White, bubbles, DayIncoming.withDayAccent(accent), dayOutgoingFrom(bubbles, null, true, accent))
}

// ---- Night (DefaultDarkPresentationTheme.swift) ----

private val NightBlue = hex(0x3E88F7)

/** customizeDefaultDarkPresentationTheme for 0x3E88F7: `[0x0771ff, 0x9047ff, 0xa256bf].reversed()` (top to bottom). */
private val NightBubbleGradient = listOf(hex(0xA256BF), hex(0x9047FF), hex(0x0771FF))

private fun nightColors(o: AccentOption): TgColors {
    val x = o.accent ?: when (o.base) {
        null, BaseAccent.Blue -> NightBlue
        else -> o.base.color
    }
    val monochrome = o.accent == null && o.base == BaseAccent.White
    val bubbles = o.bubbles.ifEmpty {
        when {
            monochrome -> listOf(hex(0x313131), hex(0x313131))
            o.accent == null && (o.base == null || o.base == BaseAccent.Blue) -> NightBubbleGradient
            else -> listOf(x.multiplied(0.966f, 0.61f, 0.98f), x)
        }
    }
    var accent = x
    val badgeText: Color
    val onAccent: Color
    if (monochrome) {
        badgeText = Black; onAccent = Black
    } else {
        badgeText = White
        if (x.lightness > 0.735f) onAccent = Black else {
            onAccent = White
            val (h, s, b) = x.hsb()
            accent = hsbColor(h, s, max(b, 0.55f))
        }
    }
    // Outgoing text: black on light bubbles (lightness of the top / bottom mix > 0.735).
    var top = bubbles.first()
    var bottom = bubbles.last()
    if (top != bottom && abs(top.lightness - bottom.lightness) > 0.7f) {
        if (top.lightness > bottom.lightness) top = top.multiplied(1f, 1f, 0.85f) else bottom = bottom.multiplied(1f, 1f, 0.85f)
    }
    val darkText = top.mixedWith(bottom, 0.5f).lightness > 0.735f
    val primary = if (darkText) Black else White
    val secondary = if (darkText) Black.copy(alpha = 0.5f) else White.copy(alpha = 0.5f)
    val outgoing = BubblePalette(
        text = primary, meta = secondary, link = primary, accent = primary, control = primary,
        controlGlyph = bubbles.first(), inactive = secondary, fileTitle = primary, fileDescription = secondary,
        check = if (darkText) Black.copy(alpha = 0.5f) else White, reactionBg = White.copy(alpha = 0.12f), reactionFg = White,
        reactionActiveBg = White, reactionActiveFg = bubbles.first(), pollBar = primary, radio = secondary, separator = secondary,
        mediaPlaceholder = White.copy(alpha = 0.2f), selection = White.copy(alpha = 0.2f),
    )
    val incoming = BubblePalette(
        text = White, meta = White.copy(alpha = 0.5f), link = accent, accent = accent, control = accent,
        controlGlyph = hex(0x262628), inactive = accent.copy(alpha = 0.4f), fileTitle = accent,
        fileDescription = White.copy(alpha = 0.5f), check = White.copy(alpha = 0.5f), reactionBg = White.copy(alpha = 0.07f),
        reactionFg = White, reactionActiveBg = accent, reactionActiveFg = if (monochrome) Black else White, pollBar = accent,
        radio = hex(0x737373), separator = Black, mediaPlaceholder = hex(0x1F1F1F).mixedWith(White, 0.05f),
        selection = accent.copy(alpha = 0.2f),
    )
    return TgColors(
        isDark = true,
        groupedBackground = Black,
        background = Black,
        cell = hex(0x1C1C1D),
        cellPressed = hex(0x313135),
        text = White,
        secondaryText = hex(0x98989E),
        tertiaryText = White.copy(alpha = 0.28f),
        separator = hex(0x545458, 0.55f),
        accent = accent,
        destructive = hex(0xEB5545),
        green = hex(0x30D158),
        orange = hex(0xFF9F0A),
        mutedBadge = hex(0x666666),
        searchField = White.copy(alpha = 0.1f),
        // incoming fill #1D1D1D @ 90 % (mixed with the wallpaper in buildColors).
        bubbleIn = hex(0x1D1D1D),
        bubbleOut = bubbles.last(),
        bubbleOutGradient = if (bubbles.distinct().size > 1) bubbles else null,
        bubbleInText = incoming.text,
        bubbleOutText = outgoing.text,
        bubbleInMeta = incoming.meta,
        bubbleOutMeta = outgoing.meta,
        bubbleOutAccent = outgoing.accent,
        // iOS 26 dark Liquid Glass (.panel; legacy fill #1C1C1C @ 85 %): reads #1C1C1C over black on the iPhone.
        glassSurface = hex(0x3A3A3C, 0.5f),
        // rootNavigationBar.opaqueBackgroundColor: #1D1D1D mixed with 10 % black.
        glassOpaque = hex(0x1A1A1A),
        menuSurface = hex(0x252525, 0.78f),
        scrim = Black.copy(alpha = 0.6f),
        wallpaper = NightGradient,
        wallpaperPattern = Color.Transparent,
        // serviceMessage fill #1F1F1F (iOS 26 shows it as glass: measured ~#1A1A1A over black).
        serviceBubble = hex(0x1F1F1F, 0.85f),
        onlineDot = hex(0x4CC91F),
        listCheckmark = accent,
        pinnedRow = hex(0x1C1C1D),
        onAccent = onAccent,
        badgeText = onAccent,
        mutedBadgeText = Black,
        chatListSecondary = hex(0x8D8E93),
        muteIcon = hex(0x8D8E93),
        pinnedIcon = hex(0x767677),
        draftText = hex(0xDD4B39),
        verified = accent,
        verifiedFg = badgeText,
        sectionHeader = hex(0x8D8E93),
        switchOn = hex(0x67CE67),
        serviceText = White,
        unreadBar = hex(0x1B1B1B),
        unreadBarText = White,
        inputPlaceholder = White.copy(alpha = 0.48f),
        inputIcon = White.copy(alpha = 0.5f),
        bubbleInPalette = incoming,
        bubbleOutPalette = outgoing,
    )
}

// ---- Tinted Night (DefaultDarkTintedPresentationTheme.swift) ----

/** The accent before the brightness floor (blue maps to defaultDarkTintedAccentColor 0x2EA6FF). */
private fun tintedAccent(o: AccentOption): Color = o.accent ?: when (o.base) {
    null, BaseAccent.Blue -> hex(0x2EA6FF)
    else -> o.base.color
}

private fun tintedColors(o: AccentOption): TgColors {
    val x = tintedAccent(o)
    val outBottom = x.multiplied(1.019f, 0.731f, 0.59f)
    val bubbles = o.bubbles.ifEmpty { listOf(outBottom.multiplied(0.966f, 0.61f, 0.98f), outBottom) }
    val (ah, asat, ab) = x.hsb()
    val accent = hsbColor(ah, asat, max(ab, 0.18f))
    val onAccent = if (accent.lightness > 0.7f) Black else White
    val mainBackground = accent.multiplied(1.024f, 0.585f, 0.25f)
    val mainSelection = accent.multiplied(1.03f, 0.585f, 0.12f)
    val additionalBackground = accent.multiplied(1.024f, 0.573f, 0.18f)
    val mainSeparator = accent.multiplied(1.033f, 0.426f, 0.34f)
    val mainSecondaryText = accent.multiplied(0.956f, 0.17f, 1.0f)
    val mainFreeText = accent.multiplied(1.019f, 0.097f, 0.56f)
    val darkText = bubbles.first().mixedWith(bubbles.last(), 0.5f).lightness > 0.7f
    val primary = if (darkText) Black else White
    val secondary = if (darkText) Black.copy(alpha = 0.5f) else White.copy(alpha = 0.5f)
    val outgoing = BubblePalette(
        text = primary, meta = secondary, link = primary, accent = primary, control = primary,
        controlGlyph = bubbles.first(), inactive = secondary, fileTitle = primary, fileDescription = secondary,
        check = secondary, reactionBg = White.copy(alpha = 0.1f), reactionFg = White, reactionActiveBg = White,
        reactionActiveFg = bubbles.first(), pollBar = primary, radio = primary, separator = mainSeparator,
        mediaPlaceholder = accent.multiplied(1.019f, 0.804f, 0.51f), selection = White.copy(alpha = 0.2f),
    )
    val incoming = BubblePalette(
        text = White, meta = mainSecondaryText.copy(alpha = 0.5f), link = accent, accent = accent, control = accent,
        controlGlyph = mainBackground, inactive = accent.copy(alpha = 0.5f), fileTitle = accent,
        fileDescription = mainSecondaryText.copy(alpha = 0.5f), check = mainSecondaryText.copy(alpha = 0.5f),
        reactionBg = White.copy(alpha = 0.07f), reactionFg = White, reactionActiveBg = accent, reactionActiveFg = White,
        pollBar = accent, radio = accent.multiplied(0.995f, 0.317f, 0.51f), separator = mainSeparator,
        mediaPlaceholder = accent.multiplied(1.019f, 0.585f, 0.23f), selection = accent.copy(alpha = 0.2f),
    )
    val (mh, ms, _) = mainBackground.hsb()
    return TgColors(
        isDark = true,
        groupedBackground = additionalBackground,
        background = additionalBackground,
        cell = mainBackground,
        cellPressed = mainSelection,
        text = White,
        secondaryText = mainSecondaryText.copy(alpha = 0.5f),
        tertiaryText = mainSecondaryText.copy(alpha = 0.5f),
        separator = mainSeparator,
        accent = accent,
        destructive = hex(0xFF6767),
        green = hex(0x30D158),
        orange = hex(0xFF9F0A),
        mutedBadge = mainSecondaryText.copy(alpha = 0.4f),
        searchField = White.copy(alpha = 0.1f),
        // incoming fill mainBackground @ 90 % (mixed with the wallpaper in buildColors).
        bubbleIn = mainBackground,
        bubbleOut = bubbles.last(),
        bubbleOutGradient = if (bubbles.distinct().size > 1) bubbles else null,
        bubbleInText = incoming.text,
        bubbleOutText = outgoing.text,
        bubbleInMeta = incoming.meta,
        bubbleOutMeta = outgoing.meta,
        bubbleOutAccent = outgoing.accent,
        // The Night glass veil (#3A3A3C @ 50 %) in the theme's hue: iOS glass keeps the hue of what is behind it.
        glassSurface = hsbColor(mh, ms, 0.235f).copy(alpha = 0.5f),
        glassOpaque = mainBackground,
        menuSurface = mainBackground.copy(alpha = 0.78f),
        scrim = Black.copy(alpha = 0.6f),
        wallpaper = TintedWallpaper.colors,
        wallpaperPattern = Color.Transparent,
        serviceBubble = additionalBackground.copy(alpha = 0.85f),
        onlineDot = hex(0x4CC91F),
        listCheckmark = accent,
        pinnedRow = mainBackground,
        onAccent = onAccent,
        badgeText = onAccent,
        mutedBadgeText = additionalBackground,
        chatListSecondary = mainSecondaryText.copy(alpha = 0.5f),
        muteIcon = mainSecondaryText.copy(alpha = 0.4f),
        pinnedIcon = mainSecondaryText.copy(alpha = 0.5f),
        draftText = hex(0xDD4B39),
        verified = accent,
        verifiedFg = White,
        sectionHeader = mainFreeText,
        switchOn = accent,
        serviceText = White,
        unreadBar = mainBackground,
        unreadBarText = White,
        inputPlaceholder = mainSecondaryText.copy(alpha = 0.4f),
        inputIcon = mainSecondaryText.copy(alpha = 0.4f),
        bubbleInPalette = incoming,
        bubbleOutPalette = outgoing,
    )
}

// ---- Assembly ----

/** serviceColor(with:) in PresentationData.swift. */
private fun serviceColorWith(c: Color): Color {
    var (h, s, b) = c.hsb()
    if (s > 0f) s = min(1f, s + 0.05f + 0.1f * (1f - s))
    b = max(0f, b * 0.65f)
    return hsbColor(h, s, b).copy(alpha = 0.4f)
}

/** chatServiceBackgroundColor(wallpaper:) for color / gradient / pattern wallpapers. */
private fun serviceColorFor(wall: WallpaperOption): Color {
    val n = if (wall.solid) 1 else wall.colors.size
    return when {
        n >= 3 -> Black.copy(alpha = 0.3f)
        n == 2 -> serviceColorWith(wall.colors[0].mixedWith(wall.colors[1], 0.5f))
        else -> serviceColorWith(wall.colors.firstOrNull() ?: White)
    }
}

/** Builds the palette of a theme like Telegram-iOS makeDefault…Theme + customizeDefault…Theme(accentColor:) do. */
fun buildColors(spec: ThemeSpec): TgColors {
    val theme = spec.theme
    val option = accentOption(theme, spec.accent)
    var c = when (theme) {
        ColorTheme.Classic -> classicColors(option)
        ColorTheme.Day -> dayColors(option)
        ColorTheme.Night -> nightColors(option)
        ColorTheme.Tinted -> tintedColors(option)
    }
    val wall = spec.wallpaper ?: themeWallpaper(theme, option.key)
    val wallIsWhite = wall.solid && wall.colors.all { it.lightness > 0.995f }

    // Pattern: WallpaperSettings.intensity, scaled by the Intensity slider (0.5 = as shipped).
    val intensity = if (!spec.pattern || wall.solid) 0f else (wall.intensity / 100f * spec.patternIntensity * 2f).coerceIn(-1f, 1f)
    val inverted = intensity < 0f
    val lightWall = averageColor(wall.colors).hsb()[2] > 0.3f
    val pattern = when {
        intensity == 0f -> Color.Transparent
        inverted -> White.copy(alpha = -intensity)
        lightWall -> Black.copy(alpha = intensity)
        else -> White.copy(alpha = intensity)
    }
    c = c.copy(wallpaper = wall.colors, wallpaperPattern = pattern, wallpaperInverted = inverted)

    val avg = averageColor(wall.colors)
    when (theme) {
        // Incoming #1D1D1D @ 90 % / mainBackground @ 90 % over (a clean copy of) the wallpaper gradient.
        ColorTheme.Night, ColorTheme.Tinted -> c = c.copy(bubbleIn = c.bubbleIn.mixedWith(avg, 0.1f))
        ColorTheme.Day -> c = if (wallIsWhite) {
            // messageDay withoutWallpaper + serviceMessageDay withDefaultWallpaper.
            c.copy(bubbleIn = hex(0xF1F1F4), serviceBubble = White.copy(alpha = 0.8f), serviceText = hex(0x8D8E93))
        } else c.copy(serviceBubble = serviceColorFor(wall))
        ColorTheme.Classic -> if (wallIsWhite) c = c.copy(serviceBubble = hex(0x939FAB, 0.5f))
    }
    // Knockout glyphs on the incoming side show the bubble itself.
    if (c.bubbleInPalette.reactionActiveFg == White && !c.isDark) c = c.copy(bubbleInPalette = c.bubbleInPalette.copy(reactionActiveFg = c.bubbleIn))

    if (!spec.bubbleGradient && c.bubbleOutGradient != null) c = c.copy(bubbleOutGradient = null)
    if (spec.glassTint) c = c.copy(
        glassSurface = c.glassSurface.mixedWith(c.accent.copy(alpha = c.glassSurface.alpha), 0.16f),
        glassOpaque = c.glassOpaque.mixedWith(c.accent.copy(alpha = c.glassOpaque.alpha), 0.08f),
        menuSurface = c.menuSurface.mixedWith(c.accent.copy(alpha = c.menuSurface.alpha), 0.08f),
    )
    return c
}

/** Telegram's default "Classic" theme. */
val LightColors: TgColors = buildColors(ThemeSpec(ColorTheme.Classic))

/** Telegram-iOS "Night" with its blue accent (0x3E88F7) and the black pattern wallpaper. */
val DarkColors: TgColors = buildColors(ThemeSpec(ColorTheme.Night))

/** What an accent circle shows (ThemeSettingsAccentColorItem): fill, ring and the bubble colors in its middle. */
@Immutable
data class AccentSwatch(val fill: Color, val ring: Color, val center: List<Color>?)

fun accentSwatch(theme: ColorTheme, o: AccentOption, cell: Color, controlSecondary: Color): AccentSwatch {
    var fill = o.accent ?: o.base?.color ?: DefaultDayAccent
    var center: List<Color>? = null
    when {
        o.bubbles.isNotEmpty() -> center = listOf(o.bubbles.first(), o.bubbles.last())
        theme == ColorTheme.Classic -> if (o.base == null && o.accent == null) {
            fill = DefaultDayAccent; center = listOf(hex(0xE1FFC7))
        } else {
            val (h, s, b) = fill.hsb()
            center = listOf(hsbColor(h, if (s > 0f && b > 0f) 0.14f else 0f, 0.79f + b * 0.21f))
        }
        theme == ColorTheme.Tinted -> {
            val bottom = fill.multiplied(1.019f, 0.731f, 0.59f)
            center = listOf(bottom.multiplied(0.966f, 0.61f, 0.98f), bottom)
        }
    }
    var ring = o.base?.color ?: fill
    if (ring.distanceTo(cell) < 200) ring = if (ring.distanceTo(White) < 200) hex(0x999999) else controlSecondary
    return AccentSwatch(fill, ring, center)
}
