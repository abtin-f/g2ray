package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/** A sticker or custom-emoji set, for the "Add Stickers" / "Add Emoji" sheet. */
@Immutable
data class StickerSetInfo(
    val id: Long,
    val title: String,
    /** Short name (t.me/addstickers/<name>, t.me/addemoji/<name>). */
    val name: String,
    /** Installed (and not archived) for this account. */
    val installed: Boolean,
    /** A custom (premium) emoji pack rather than a sticker pack. */
    val emoji: Boolean,
    val stickers: List<StickerItem>,
)

/**
 * Custom-emoji reactions travel through the app as [Reaction.emoji] strings with this prefix
 * ("custom-emoji:<id>"), so the rest of the reaction code keeps working with plain strings.
 */
object CustomReactions {
    const val Prefix = "custom-emoji:"

    fun key(customEmojiId: Long): String = Prefix + customEmojiId

    /** The custom emoji id behind a reaction key, or null for a regular emoji reaction. */
    fun idOf(reaction: String): Long? =
        if (reaction.startsWith(Prefix)) reaction.substring(Prefix.length).toLongOrNull() else null
}
