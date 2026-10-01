package com.abtin.tglass.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.Calendar

// Models behind Edit Profile (birthday, personal channel, name color, username check).
// The demo defaults of [TelegramRepository] keep their state in [DemoProfile].

/** A birthday; [year] 0 = without the year. */
@Immutable
data class ProfileBirthdate(val day: Int, val month: Int, val year: Int = 0) {
    /** "22 Feb 1982 (43 years old)" / "22 Feb" like Telegram-iOS. */
    fun label(withAge: Boolean = false): String {
        val base = "$day ${MonthsShort[(month - 1).coerceIn(0, 11)]}" + if (year > 0) " $year" else ""
        if (!withAge || year <= 0) return base
        val now = Calendar.getInstance()
        var age = now.get(Calendar.YEAR) - year
        val m = now.get(Calendar.MONTH) + 1
        if (m < month || (m == month && now.get(Calendar.DAY_OF_MONTH) < day)) age--
        return if (age >= 0) "$base ($age years old)" else base
    }

    companion object {
        val MonthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val Months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

        fun daysIn(month: Int, year: Int): Int = when (month) {
            2 -> if (year == 0 || (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0))) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}

/** A channel that can be shown on the profile (Settings → Edit Profile → Personal Channel). */
@Immutable
data class PersonalChannel(val chatId: Long, val title: String, val subtitle: String = "")

/** Result of checking a username while it is typed. */
enum class UsernameCheck(val message: String, val ok: Boolean) {
    Checking("Checking username…", false),
    Available("is available.", true),
    Taken("This username is already taken.", false),
    Invalid("This username is invalid.", false),
    TooShort("A username must have at least 5 characters.", false),
    Purchasable("This username is for sale on Fragment.", false),
    TooMany("Sorry, you have reserved too many public usernames.", false),
    Error("Couldn't check this username.", false),
}

/** Local rules for usernames (a–z, 0–9, underscores; 5–32 characters; starts with a letter). */
fun localUsernameCheck(name: String): UsernameCheck? = when {
    name.isEmpty() -> null
    name.first().isDigit() || name.first() == '_' -> UsernameCheck.Invalid
    name.endsWith("_") || "__" in name -> UsernameCheck.Invalid
    name.any { !(it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_') } -> UsernameCheck.Invalid
    name.length < 5 -> UsernameCheck.TooShort
    else -> null
}

/**
 * Telegram's 7 built-in name colors (accent_color_id 0–6), light and dark variants
 * (Telegram-iOS PeerNameColors defaults).
 */
object NameColors {
    val names = listOf("Red", "Orange", "Violet", "Green", "Cyan", "Blue", "Pink")
    private val light = listOf(0xFFCC5049, 0xFFD67722, 0xFF955CDB, 0xFF40A920, 0xFF309EBA, 0xFF368AD1, 0xFFC7508B)
    private val dark = listOf(0xFFFF8E86, 0xFFFFA357, 0xFFB18FFF, 0xFF4DD6BF, 0xFF45E8D1, 0xFF52BFFF, 0xFFFF7FD5)

    val count get() = light.size
    fun color(id: Int, dark: Boolean): Color = Color((if (dark) this.dark else light)[id.mod(light.size)])
}

/** Demo account's profile extras (kept in memory, like the rest of the demo). */
object DemoProfile {
    var birthdate by mutableStateOf<ProfileBirthdate?>(ProfileBirthdate(14, 3, 1998))
    var personalChannel by mutableStateOf<PersonalChannel?>(null)
    var nameColor by mutableIntStateOf(5)
}

/** Shared-media tabs of the profile page whose sizes are counted up front, so empty tabs can be hidden. */
enum class SharedKind { Media, Files, Links, Music, Voice, Gifs }

/**
 * Extra profile details beyond [ChatInfo]: what Telegram shows under a user's name on iOS.
 * Every field is optional; the live repository fills what TDLib reports (userFullInfo) and leaves the rest empty.
 */
@Immutable
data class ProfileDetails(
    /** "March 14, 1998", or "March 14" when the year is hidden. */
    val birthday: String? = null,
    /** Age in years when the birth year is known. */
    val age: Int? = null,
    /** The first song of the user's profile music ("saved music"). */
    val music: ProfileMusic? = null,
    /** Personal channel shown on the profile (0 = none). */
    val personalChatId: Long = 0,
    /** Telegram Business: address of the business, if set. */
    val businessAddress: String? = null,
    /** Telegram Business: "Open" / "Closed" / "Opens in 2 h" etc., if opening hours are set. */
    val businessHours: String? = null,
    val giftCount: Int = 0,
    val commonGroupCount: Int = 0,
    /** False when the user can't be called (privacy / bots). */
    val canCall: Boolean = true,
    val videoCalls: Boolean = true,
    /** Custom emoji the user set as emoji status (shown after the name), if any. */
    val emojiStatus: StickerItem? = null,
    /** Fallback glyph for [emojiStatus] (demo / while the sticker loads). */
    val emojiStatusEmoji: String? = null,
    /** Telegram profile accent color id (users with a profile color, boosted channels); -1 = none. */
    val profileColorId: Int = -1,
    /** Bots: the short description shown on their profile. */
    val botDescription: String? = null,
)

/** An audio file from a user's profile music. [file] is null in the demo (nothing to play). */
@Immutable
data class ProfileMusic(
    val title: String,
    val performer: String,
    val duration: Int,
    val file: ImageRef? = null,
    val cover: ImageRef? = null,
)

/** One profile photo: [image] is the big version, [thumb] the small one (already cached for the avatar). */
@Immutable
data class ProfilePhotoItem(
    val id: Long,
    val image: ImageRef?,
    val thumb: ImageRef? = null,
    /** Unix seconds when the photo was set (0 = unknown). */
    val date: Long = 0,
)

/**
 * A gift shown on a profile's Gifts tab. [sticker] is the gift's sticker (animated .tgs or static preview);
 * the demo leaves it null and draws [emoji] instead. Colors are ARGB ints of the gift card's radial background.
 */
@Immutable
data class ProfileGift(
    val id: String,
    val sticker: StickerItem?,
    val emoji: String = "🎁",
    val stars: Long = 0,
    /** Upgraded (collectible) gifts: "Plush Pepe #1234". */
    val title: String? = null,
    val senderName: String? = null,
    val message: String? = null,
    /** Unix seconds. */
    val date: Long = 0,
    val centerColor: Int? = null,
    val edgeColor: Int? = null,
    val pinned: Boolean = false,
    /** Corner ribbon text ("1 of 5.6K", "#123"); null = none. */
    val ribbon: String? = null,
)

/** Sample gifts for the demo profile pages. */
object ProfileDemo {
    val gifts: List<ProfileGift> = listOf(
        ProfileGift("d1", null, "🧸", 50, centerColor = 0x9C7BE8, edgeColor = 0x6A4FC4, ribbon = "1 of 5.6K", pinned = true, senderName = "Emma"),
        ProfileGift("d2", null, "💎", 100, centerColor = 0x6BD6C9, edgeColor = 0x2E9C95, ribbon = "1 of 5.6K", pinned = true),
        ProfileGift("d3", null, "👜", 250, centerColor = 0xF2C14E, edgeColor = 0xD08C2A, ribbon = "1 of 5.6K", pinned = true),
        ProfileGift("d4", null, "💝", 15, centerColor = 0x7FB4F0, edgeColor = 0x4A7FD0),
        ProfileGift("d5", null, "🎀", 25, centerColor = 0xE9A0C0, edgeColor = 0xC0628F, ribbon = "1 of 12K"),
        ProfileGift("d6", null, "🌹", 25, centerColor = 0x8DD08A, edgeColor = 0x4E9C57),
        ProfileGift("d7", null, "🚀", 50, centerColor = 0xF0A070, edgeColor = 0xC8643E),
        ProfileGift("d8", null, "🏆", 100, centerColor = 0xE8D67A, edgeColor = 0xB89A2E),
        ProfileGift("d9", null, "💐", 50, centerColor = 0xB9A3F0, edgeColor = 0x8468D0),
    )
}
