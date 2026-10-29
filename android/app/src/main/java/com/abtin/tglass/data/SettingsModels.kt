package com.abtin.tglass.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// Models behind the real Settings pages (blocked users, 2-step verification, storage, data usage,
// notification scopes). The demo defaults of [TelegramRepository] keep their state in [DemoSettings].

/** A user ([isUser]) or a chat the account blocked. [id] is the user id or the chat id. */
@Immutable
data class BlockedPeer(val id: Long, val isUser: Boolean, val name: String, val subtitle: String = "")

/** 2-step verification state. [pendingEmailPattern] is set while a new recovery email waits for its code. */
@Immutable
data class PasswordInfo(
    val hasPassword: Boolean,
    val hint: String = "",
    val hasRecoveryEmail: Boolean = false,
    val pendingEmailPattern: String? = null,
    val pendingEmailCodeLength: Int = 0,
)

/** Local storage used by the Telegram cache and database. */
@Immutable
data class StorageInfo(val filesSize: Long, val fileCount: Int, val databaseSize: Long, val otherSize: Long) {
    val total: Long get() = filesSize + databaseSize + otherSize
}

/** One row of Data Usage (photos, videos, calls…). */
@Immutable
data class DataUsageRow(val title: String, val sent: Long, val received: Long)

/** Network statistics since [sinceMs] (0 if unknown), split by network. */
@Immutable
data class DataUsage(val sinceMs: Long, val mobile: List<DataUsageRow>, val wifi: List<DataUsageRow>, val roaming: List<DataUsageRow>) {
    val all: List<List<DataUsageRow>> get() = listOf(mobile, wifi, roaming)
    val totalSent: Long get() = all.sumOf { l -> l.sumOf { it.sent } }
    val totalReceived: Long get() = all.sumOf { l -> l.sumOf { it.received } }
}

/** Chat types with their own default notification settings. */
enum class NotifyScope(val title: String) { Private("Private Chats"), Groups("Group Chats"), Channels("Channels") }

/** Default notification settings of a [NotifyScope]. */
@Immutable
data class ScopeNotifications(val enabled: Boolean, val preview: Boolean)

/** Networks with their own automatic media download settings. */
enum class DownloadNetwork(val title: String) { Cellular("Using Cellular"), WiFi("Using Wi-Fi"), Roaming("Roaming") }

/** Automatic media download for one network. */
@Immutable
data class AutoDownload(val enabled: Boolean, val photos: Boolean, val videos: Boolean, val files: Boolean)

/** "1.2 GB", "340 KB"… */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%d KB", bytes shr 10)
    else -> "$bytes B"
}

/** Account self-destruct periods offered by Telegram (in days). */
val AccountTtlOptions: List<Pair<Int, String>> = listOf(30 to "1 month", 90 to "3 months", 180 to "6 months", 365 to "12 months")

/** Label of the option closest to [days]. */
fun accountTtlLabel(days: Int): String = AccountTtlOptions.minByOrNull { kotlin.math.abs(it.first - days) }?.second ?: "$days days"

/** State of the demo account's settings (kept in memory, like the rest of the demo). */
object DemoSettings {
    val blocked = mutableStateListOf(
        BlockedPeer(9, true, "Behnam", "+98 912 999 0011"),
        BlockedPeer(11, true, "Hamid Soleimani", "@hamid"),
        BlockedPeer(12, true, "Elham Jafari", "+98 912 131 3131"),
    )
    var password by mutableStateOf(PasswordInfo(hasPassword = false))
    /** The demo's "password" so that changing / removing it can be tried out. */
    var passwordValue: String = ""
    var accountTtlDays by mutableStateOf(180)
    var storage by mutableStateOf(StorageInfo(filesSize = 1_180_000_000L, fileCount = 2_431, databaseSize = 48_000_000L, otherSize = 3_000_000L))
    var dataUsage by mutableStateOf(
        DataUsage(
            sinceMs = System.currentTimeMillis() - 30L * 24 * 3600 * 1000,
            mobile = listOf(DataUsageRow("Photos", 12_000_000, 210_000_000), DataUsageRow("Videos", 4_000_000, 640_000_000), DataUsageRow("Messages", 3_000_000, 18_000_000)),
            wifi = listOf(DataUsageRow("Photos", 40_000_000, 820_000_000), DataUsageRow("Videos", 90_000_000, 1_600_000_000), DataUsageRow("Messages", 6_000_000, 32_000_000)),
            roaming = emptyList(),
        )
    )
    val scopes = mutableStateMapOf(
        NotifyScope.Private to ScopeNotifications(enabled = true, preview = true),
        NotifyScope.Groups to ScopeNotifications(enabled = true, preview = true),
        NotifyScope.Channels to ScopeNotifications(enabled = false, preview = true),
    )
}
