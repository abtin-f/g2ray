package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

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
