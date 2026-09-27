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
