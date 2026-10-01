package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/**
 * One page of in-chat search results (newest first). [total] is the server's count of all matches (for "3 of 12"),
 * [nextFrom] the message id to continue from (0 = no more pages).
 */
@Immutable
data class ChatSearchPage(val messages: List<Message>, val total: Int, val nextFrom: Long)

/** Voters of one poll option: sender ids (users, or chats for anonymous admins / channels) and the full count. */
@Immutable
data class PollVotersPage(val voterIds: List<Long>, val total: Int)
