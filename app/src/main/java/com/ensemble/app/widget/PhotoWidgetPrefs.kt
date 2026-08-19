package com.ensemble.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Cache local (déjà déchiffré) de la dernière photo ajoutée, pour que le widget puisse l'afficher
 * sans accéder à Firestore/Storage ni à la clé de chiffrement depuis son propre composant. Comme
 * pour WidgetPrefs, si le verrouillage de l'app est activé on ne garde aucune image lisible sur
 * le disque : un widget est visible sans authentification.
 */
object PhotoWidgetPrefs {
    private const val PREFS_NAME = "photo_widget_prefs"
    private const val KEY_LOCKED = "locked"
    private const val KEY_HAS_PHOTO = "has_photo"
    private const val FILE_NAME = "widget_last_photo.jpg"

    private fun photoFile(context: Context) = File(context.filesDir, FILE_NAME)

    fun save(context: Context, bitmap: Bitmap?, appLockEnabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val file = photoFile(context)
        prefs.edit().apply {
            putBoolean(KEY_LOCKED, appLockEnabled)
            if (appLockEnabled || bitmap == null) {
                file.delete()
                putBoolean(KEY_HAS_PHOTO, false)
            } else {
                runCatching {
                    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                }
                putBoolean(KEY_HAS_PHOTO, file.exists())
            }
            apply()
        }
    }

    data class Snapshot(val bitmap: Bitmap?, val locked: Boolean)

    fun read(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val locked = prefs.getBoolean(KEY_LOCKED, false)
        val hasPhoto = prefs.getBoolean(KEY_HAS_PHOTO, false)
        val bitmap = if (!locked && hasPhoto) {
            runCatching { BitmapFactory.decodeFile(photoFile(context).absolutePath) }.getOrNull()
        } else null
        return Snapshot(bitmap, locked)
    }
}
