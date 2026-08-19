package com.ensemble.app.util

import java.time.Instant
import java.time.ZoneId

/** Rejoue une date (jour+mois) dans une autre année. Null si la date n'existe pas cette année-là (29 février). */
fun occurrenceInYear(originalDateMillis: Long, targetYear: Int): Long? {
    val original = Instant.ofEpochMilli(originalDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    return try {
        original.withYear(targetYear)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

/**
 * Pour un événement récurrent chaque année : renvoie sa prochaine occurrence à partir de [now]
 * (cette année si pas encore passée, sinon l'année suivante). Pour un événement non récurrent,
 * renvoie simplement sa date d'origine.
 */
fun nextOccurrenceMillis(originalDateMillis: Long, recurringYearly: Boolean, now: Long): Long {
    if (!recurringYearly) return originalDateMillis
    val currentYear = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().year
    val thisYear = occurrenceInYear(originalDateMillis, currentYear) ?: originalDateMillis
    return if (thisYear >= now) thisYear else (occurrenceInYear(originalDateMillis, currentYear + 1) ?: thisYear)
}
