package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/** Title and small photo of a chat a forwarded message came from (it may not be in the user's chat list). */
@Immutable
data class OriginChat(val title: String, val photo: ImageRef?)

/** Where a channel post's comments live: the discussion group and the post's copy there (0 = unknown). */
@Immutable
data class CommentsTarget(val chatId: Long, val messageId: Long = 0)
