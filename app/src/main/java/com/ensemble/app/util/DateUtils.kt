package com.ensemble.app.util

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Countdown(val days: Long, val hours: Long, val minutes: Long, val isPast: Boolean)

fun countdownTo(epochMillis: Long): Countdown {
    val diff = epochMillis - System.currentTimeMillis()
    val isPast = diff < 0
    val abs = kotlin.math.abs(diff)
    val days = TimeUnit.MILLISECONDS.toDays(abs)
    val hours = TimeUnit.MILLISECONDS.toHours(abs) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(abs) % 60
    return Countdown(days, hours, minutes, isPast)
}

fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH).format(java.util.Date(epochMillis))

fun formatMessageTime(epochMillis: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.FRENCH).format(java.util.Date(epochMillis))
