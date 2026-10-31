package com.abtin.tglass.data.td

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Text formatting for data that TDLib gives as numbers (last seen, file sizes, …). */
internal object Formats {
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val date = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.US)

    fun lastSeen(ms: Long): String {
        val now = System.currentTimeMillis()
        val minutes = (now - ms) / 60_000
        val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        val days = ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now())
        return when {
            minutes < 1 -> "last seen just now"
            minutes < 60 -> "last seen $minutes minute${if (minutes == 1L) "" else "s"} ago"
            days == 0L -> "last seen at ${time.format(dt)}"
            days == 1L -> "last seen yesterday at ${time.format(dt)}"
            else -> "last seen ${date.format(dt)}"
        }
    }

    fun shortDate(ms: Long): String {
        val dt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return if (dt.toLocalDate() == LocalDate.now()) time.format(dt) else date.format(dt)
    }

    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(Locale.US, bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> "%.1f MB".format(Locale.US, bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> "%d KB".format(Locale.US, bytes shr 10)
        else -> "$bytes B"
    }

    fun duration(sec: Int): String = "%d:%02d".format(Locale.US, sec / 60, sec % 60)
}
