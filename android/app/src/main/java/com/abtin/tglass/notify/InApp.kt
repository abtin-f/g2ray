package com.abtin.tglass.notify

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Whether the app UI is visible (set by MainActivity) — decides banner vs. system notification. */
object AppVisibility {
    @Volatile var foreground = false
}

/** A message in another chat while the app is open (iOS in-app banner). */
data class Banner(val chatId: Long, val peerId: Long, val title: String, val text: String)

object InAppBanners {
    private val _flow = MutableSharedFlow<Banner>(extraBufferCapacity = 4)
    val flow: SharedFlow<Banner> = _flow
    fun post(b: Banner) { _flow.tryEmit(b) }
}
