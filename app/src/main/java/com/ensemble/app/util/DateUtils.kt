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

/** Heure actuelle dans un fuseau donné (ex: "Europe/Paris"), au format HH:mm. Null si le fuseau est invalide. */
fun currentTimeInZone(zoneId: String): String? = runCatching {
    val zdt = java.time.ZonedDateTime.now(java.time.ZoneId.of(zoneId))
    zdt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrNull()

/** Différence d'heures entre le fuseau donné et l'heure locale, arrondie (ex: +2, -5). */
fun hourOffsetFromLocal(zoneId: String): Int? = runCatching {
    val now = java.time.Instant.now()
    val target = java.time.ZoneId.of(zoneId).rules.getOffset(now)
    val local = java.time.ZoneId.systemDefault().rules.getOffset(now)
    (target.totalSeconds - local.totalSeconds) / 3600
}.getOrNull()

/** Formate une durée en mm:ss (ex: 65000 -> "1:05"). */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Le DatePicker Material3 raisonne en "millis UTC du début du jour" (voir la doc de
 * DatePickerState.selectedDateMillis), alors que le reste de l'app stocke/décode les dates via
 * le fuseau local de l'appareil (formatDate, la grille du calendrier...). Sans ce pont, un jour
 * choisi dans le picker peut être enregistré un jour avant/après selon le fuseau de
 * l'utilisateur — ces deux fonctions évitent le décalage quel que soit son fuseau.
 */
fun localDateToPickerMillis(date: java.time.LocalDate): Long =
    date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToLocalDayMillis(pickerMillis: Long): Long {
    val date = java.time.Instant.ofEpochMilli(pickerMillis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
    return date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
