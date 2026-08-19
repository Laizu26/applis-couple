package com.ensemble.app.widget

import android.content.Context
import com.ensemble.app.data.model.EventCategory

/**
 * Petit cache local (déjà déchiffré) permettant au widget d'écran d'accueil d'afficher le
 * compte à rebours sans avoir besoin d'accéder à Firestore ou à la clé de chiffrement depuis
 * son propre processus/composant. Si le verrouillage de l'app est activé, on n'y stocke rien
 * de lisible : un widget est visible sans authentification, il ne doit donc jamais contourner
 * le verrouillage.
 */
object WidgetPrefs {
    private const val PREFS_NAME = "widget_prefs"
    private const val KEY_LABEL = "label"
    private const val KEY_DATE = "date"
    private const val KEY_IS_PAST = "is_past"
    private const val KEY_LOCKED = "locked"
    private const val KEY_CATEGORY = "category"

    fun save(
        context: Context,
        label: String?,
        dateMillis: Long?,
        isPast: Boolean,
        appLockEnabled: Boolean,
        category: String = EventCategory.ENSEMBLE
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_LOCKED, appLockEnabled)
            if (appLockEnabled) {
                remove(KEY_LABEL)
                remove(KEY_DATE)
            } else {
                if (label != null) putString(KEY_LABEL, label) else remove(KEY_LABEL)
                if (dateMillis != null) putLong(KEY_DATE, dateMillis) else remove(KEY_DATE)
            }
            putBoolean(KEY_IS_PAST, isPast)
            putString(KEY_CATEGORY, category)
            apply()
        }
    }

    data class Snapshot(
        val label: String?,
        val dateMillis: Long?,
        val isPast: Boolean,
        val locked: Boolean,
        val category: String
    )

    fun read(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val locked = prefs.getBoolean(KEY_LOCKED, false)
        val label = prefs.getString(KEY_LABEL, null)
        val date = if (prefs.contains(KEY_DATE)) prefs.getLong(KEY_DATE, 0L) else null
        val isPast = prefs.getBoolean(KEY_IS_PAST, false)
        val category = prefs.getString(KEY_CATEGORY, EventCategory.ENSEMBLE) ?: EventCategory.ENSEMBLE
        return Snapshot(label, date, isPast, locked, category)
    }
}
