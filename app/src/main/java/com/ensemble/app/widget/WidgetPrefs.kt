package com.ensemble.app.widget

import android.content.Context

/**
 * Petit cache local (déjà déchiffré, non-sensible car dérivé d'une simple date de retrouvailles)
 * permettant au widget d'écran d'accueil d'afficher le compte à rebours sans avoir besoin
 * d'accéder à Firestore ou à la clé de chiffrement depuis son propre processus/composant.
 */
object WidgetPrefs {
    private const val PREFS_NAME = "widget_prefs"
    private const val KEY_LABEL = "label"
    private const val KEY_DATE = "date"
    private const val KEY_IS_PAST = "is_past"

    fun save(context: Context, label: String?, dateMillis: Long?, isPast: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (label != null) putString(KEY_LABEL, label) else remove(KEY_LABEL)
            if (dateMillis != null) putLong(KEY_DATE, dateMillis) else remove(KEY_DATE)
            putBoolean(KEY_IS_PAST, isPast)
            apply()
        }
    }

    data class Snapshot(val label: String?, val dateMillis: Long?, val isPast: Boolean)

    fun read(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val label = prefs.getString(KEY_LABEL, null)
        val date = if (prefs.contains(KEY_DATE)) prefs.getLong(KEY_DATE, 0L) else null
        val isPast = prefs.getBoolean(KEY_IS_PAST, false)
        return Snapshot(label, date, isPast)
    }
}
