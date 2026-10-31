package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/** A chat folder as listed in Settings → Chat Folders. */
@Immutable
data class FolderSummary(val id: Int, val title: String, val subtitle: String)

/** Everything the folder editor can change (mirrors TDLib's chatFolder without icon/color/pins). */
@Immutable
data class FolderDraft(
    val name: String = "",
    val includeContacts: Boolean = false,
    val includeNonContacts: Boolean = false,
    val includeGroups: Boolean = false,
    val includeChannels: Boolean = false,
    val includeBots: Boolean = false,
    val excludeMuted: Boolean = false,
    val excludeRead: Boolean = false,
    val excludeArchived: Boolean = false,
    /** Always included chats (TDLib's pinned + included chats). */
    val includedChatIds: List<Long> = emptyList(),
    /** Always excluded chats. */
    val excludedChatIds: List<Long> = emptyList(),
) {
    /** Telegram requires at least one included chat or chat type. */
    val hasIncluded: Boolean
        get() = includeContacts || includeNonContacts || includeGroups || includeChannels || includeBots || includedChatIds.isNotEmpty()
}
