package com.abtin.tglass.ui.components

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val zone: ZoneId get() = ZoneId.systemDefault()
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val weekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)
private val dateFmt = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.US)
private val dayFmt = DateTimeFormatter.ofPattern("MMMM d", Locale.US)

fun formatTime(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(zone))

/** Chat list style: time today, weekday this week, date otherwise. */
fun formatListDate(ms: Long): String {
    val dt = Instant.ofEpochMilli(ms).atZone(zone)
    val days = ChronoUnit.DAYS.between(dt.toLocalDate(), LocalDate.now(zone))
    return when {
        days <= 0 -> timeFmt.format(dt)
        days < 7 -> weekdayFmt.format(dt)
        else -> dateFmt.format(dt)
    }
}

fun formatDay(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(d, LocalDate.now(zone))
    return when (days) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> dayFmt.format(d)
    }
}

fun formatDuration(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)
