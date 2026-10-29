package com.abtin.tglass.data.td

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The accounts signed in on this device (Telegram iOS allows several). Each account is a numbered slot with
 * its own TDLib database: slot 0 keeps the original `td` / `td_files` directories (so existing sign-ins
 * survive), slot N uses `td_N` / `td_files_N`. Only the active slot has a running TDLib client; the others
 * are shown from the name / phone / photo cached here while they were active.
 */
object TdAccounts {
    private var prefs: SharedPreferences? = null

    /** Slots in the order they were added. Always contains [active]. */
    var slots by mutableStateOf(listOf(0))
        private set
    var active by mutableIntStateOf(0)
        private set
    /**
     * Slot to go back to when the user cancels signing in a new account (-1: not adding). While it is set,
     * the active slot is a fresh, not yet signed-in database.
     */
    var returnTo by mutableIntStateOf(-1)
        private set
    /** Bumped whenever cached account info changes, so lists recompose. */
    var version by mutableIntStateOf(0)
        private set

    fun attach(context: Context) {
        if (prefs != null) return
        val p = runCatching { context.applicationContext.getSharedPreferences("tglass_accounts", Context.MODE_PRIVATE) }.getOrNull() ?: return
        prefs = p
        val list = p.getString("slots", null)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.distinct().orEmpty()
        val act = p.getInt("active", 0)
        slots = (list.ifEmpty { listOf(0) }).let { if (act in it) it else it + act }
        active = act
        returnTo = p.getInt("returnTo", -1).takeIf { it in slots && it != act } ?: -1
    }

    val adding: Boolean get() = returnTo >= 0

    fun suffix(slot: Int): String = if (slot == 0) "" else "_$slot"

    fun databaseDir(context: Context, slot: Int) = java.io.File(context.filesDir, "td" + suffix(slot))
    fun filesDir(context: Context, slot: Int) = java.io.File(context.filesDir, "td_files" + suffix(slot))

    /**
     * Adds a fresh slot for a new account and remembers the current one to return to; returns it. The caller
     * activates it once the current TDLib client has closed.
     */
    fun newSlot(): Int {
        val slot = (slots.maxOrNull() ?: 0) + 1
        forget(slot)
        slots = slots + slot
        returnTo = active
        save()
        return slot
    }

    /** Undoes [newSlot] when the switch to it never happened. */
    fun abortAdding(slot: Int) {
        if (slot == active) return
        slots = slots - slot
        returnTo = -1
        save()
    }

    fun activate(slot: Int) {
        if (slot !in slots) return
        active = slot
        if (slot == returnTo) returnTo = -1
        save()
    }

    /** The active slot finished signing in. */
    fun signedIn() {
        if (returnTo < 0) return
        returnTo = -1
        save()
    }

    fun remove(slot: Int) {
        if (slot !in slots || slots.size == 1) return
        slots = slots - slot
        if (returnTo == slot) returnTo = -1
        if (active == slot) active = slots.first()
        prefs?.edit()?.apply {
            remove("name_$slot"); remove("phone_$slot"); remove("user_$slot"); remove("photo_$slot")
        }?.apply()
        save()
        version++
    }

    /** Signed-in slots (those with a cached user id), plus the active one. */
    fun signedInSlots(): List<Int> = slots.filter { it == active || userId(it) != 0L }

    fun name(slot: Int): String = prefs?.getString("name_$slot", null).orEmpty()
    fun phone(slot: Int): String = prefs?.getString("phone_$slot", null).orEmpty()
    fun userId(slot: Int): Long = prefs?.getLong("user_$slot", 0L) ?: 0L
    fun photo(slot: Int): String? = prefs?.getString("photo_$slot", null)?.takeIf { java.io.File(it).exists() }

    fun cache(slot: Int, userId: Long, name: String, phone: String, photo: String?) {
        val p = prefs ?: return
        if (userId(slot) == userId && name(slot) == name && phone(slot) == phone && (photo == null || prefs?.getString("photo_$slot", null) == photo)) return
        p.edit().putLong("user_$slot", userId).putString("name_$slot", name).putString("phone_$slot", phone).apply {
            if (photo != null) putString("photo_$slot", photo)
        }.apply()
        version++
    }

    fun forget(slot: Int) {
        prefs?.edit()?.apply {
            remove("name_$slot"); remove("phone_$slot"); remove("user_$slot"); remove("photo_$slot")
        }?.apply()
        version++
    }

    private fun save() {
        prefs?.edit()?.putString("slots", slots.joinToString(","))?.putInt("active", active)?.putInt("returnTo", returnTo)?.apply()
    }
}
