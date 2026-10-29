package com.abtin.tglass.data

import androidx.compose.runtime.Immutable

/**
 * One account signed in on this device (Settings → accounts list, long-press on the Settings tab).
 * [slot] identifies its local database; [photoPath] is a cached local copy of its profile photo for accounts
 * that are not active (the active one uses the live avatar of [userId]).
 */
@Immutable
data class AccountInfo(
    val slot: Int,
    val userId: Long,
    val name: String,
    val phone: String,
    val photoPath: String?,
    val active: Boolean,
)
