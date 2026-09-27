package com.abtin.tglass.features.profile

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import com.abtin.tglass.core.design.GlassLevel
import com.abtin.tglass.core.design.LocalAppSettings
import com.abtin.tglass.core.design.TgColors
import com.abtin.tglass.core.glass.glass
import com.kyant.shapes.RoundedRectangularShape

/**
 * How the top of a profile looks (owner's iOS 26 reference):
 * [Photo] — the user's photo fills the top, white text and dark glass buttons over it;
 * [Colored] — the peer's profile color fills the page, round avatar with floating gifts;
 * [Plain] — grouped background, round avatar, white glass circles (channels, people without a photo).
 */
internal enum class HeaderStyle { Photo, Colored, Plain }

/** Colors of everything drawn on a profile page for one [HeaderStyle]. */
@Immutable
internal data class ProfilePalette(
    val page: Color,
    val title: Color,
    val subtitle: Color,
    val online: Color,
    val button: Color,
    val buttonSolid: Color,
    val buttonIcon: Color,
    val card: Color,
    val cardText: Color,
    val cardLabel: Color,
    val cardAccent: Color,
    val divider: Color,
    val tab: Color,
    val tabSelected: Color,
    val pill: Color,
    val pillSolid: Color,
) {
    /** Blends every color toward [other] (0 = this, 1 = other). */
    fun lerpTo(other: ProfilePalette, t: Float): ProfilePalette {
        if (t <= 0f) return this
        if (t >= 1f) return other
        return ProfilePalette(
            lerp(page, other.page, t), lerp(title, other.title, t), lerp(subtitle, other.subtitle, t), lerp(online, other.online, t),
            lerp(button, other.button, t), lerp(buttonSolid, other.buttonSolid, t), lerp(buttonIcon, other.buttonIcon, t),
            lerp(card, other.card, t), lerp(cardText, other.cardText, t), lerp(cardLabel, other.cardLabel, t), lerp(cardAccent, other.cardAccent, t),
            lerp(divider, other.divider, t), lerp(tab, other.tab, t), lerp(tabSelected, other.tabSelected, t), lerp(pill, other.pill, t),
            lerp(pillSolid, other.pillSolid, t),
        )
    }
}

internal fun plainPalette(c: TgColors) = ProfilePalette(
    page = c.groupedBackground,
    title = c.text,
    subtitle = c.secondaryText,
    online = c.accent,
    button = if (c.isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.75f),
    buttonSolid = c.cell,
    buttonIcon = c.text,
    card = c.cell,
    cardText = c.text,
    cardLabel = c.secondaryText,
    cardAccent = c.accent,
    divider = c.separator,
    tab = c.secondaryText,
    tabSelected = c.text,
    pill = if (c.isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.85f),
    pillSolid = c.cell,
)

/** White-on-color palette for a colored page ([base] = page color). */
internal fun tintedPalette(base: Color) = ProfilePalette(
    page = base,
    title = Color.White,
    subtitle = Color.White.copy(alpha = 0.72f),
    online = Color.White.copy(alpha = 0.8f),
    button = Color.Black.copy(alpha = 0.16f),
    buttonSolid = lerp(base, Color.Black, 0.18f),
    buttonIcon = Color.White,
    card = Color.Black.copy(alpha = 0.14f),
    cardText = Color.White,
    cardLabel = Color.White.copy(alpha = 0.62f),
    cardAccent = Color.White,
    divider = Color.White.copy(alpha = 0.16f),
    tab = Color.White.copy(alpha = 0.72f),
    tabSelected = Color.White,
    pill = Color.Black.copy(alpha = 0.2f),
    pillSolid = lerp(base, Color.Black, 0.22f),
)

/** Telegram's profile colors (approximations of the built-in palette), keyed by profile accent color id. */
private val ProfileColors = listOf(
    Color(0xFFC8645C), Color(0xFFC98A42), Color(0xFF8E6CCB), Color(0xFF5AA35E),
    Color(0xFF3F9AAE), Color(0xFF5585C4), Color(0xFFC0607F), Color(0xFF7C8793),
    Color(0xFFBD9A36), Color(0xFF3D8C7F), Color(0xFF9E5FA8), Color(0xFF4C7FAE),
    Color(0xFFB0694A), Color(0xFF5E9B8B), Color(0xFF8C7A55), Color(0xFF6A72B8),
)

internal fun profileColor(id: Int, dark: Boolean): Color? {
    if (id < 0) return null
    val base = ProfileColors[id % ProfileColors.size]
    return if (dark) lerp(base, Color.Black, 0.35f) else base
}

/** Average color of a picture (for the page under a photo header), darkened so white text reads on it. */
internal fun photoPageColor(image: ImageBitmap?, fallback: Color): Color {
    val avg = image?.let {
        runCatching {
            val small = Bitmap.createScaledBitmap(it.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false), 1, 1, true)
            Color(small.getPixel(0, 0))
        }.getOrNull()
    } ?: fallback
    return lerp(avg, Color(0xFF10141A), 0.55f)
}

/** Remembers [photoPageColor] per bitmap. */
@Composable
internal fun rememberPhotoPageColor(image: ImageBitmap?, fallback: Color): Color =
    remember(image, fallback) { photoPageColor(image, fallback) }

/**
 * Liquid-glass surface for the profile's own controls (only ever used outside the list's backdrop layer).
 * With reduced effects it draws [solid] instead of the theme's opaque glass so white icons stay readable.
 */
@Composable
internal fun Modifier.profileGlass(shape: RoundedRectangularShape, surface: Color, solid: Color): Modifier {
    val level = LocalAppSettings.current.glassLevel
    return if (level == GlassLevel.Low || level == GlassLevel.Off) this.clip(shape).background(solid)
    else this.glass(shape = shape, surface = surface)
}
