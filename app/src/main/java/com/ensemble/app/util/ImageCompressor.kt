package com.ensemble.app.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

private const val MAX_DIMENSION = 1920
private const val JPEG_QUALITY = 85

/**
 * Redimensionne et recompresse une image avant chiffrement/envoi : accélère les transferts et
 * réduit la consommation de stockage Firebase, sans perte visible sur un écran de téléphone.
 * Retourne les octets d'origine si le décodage échoue (fichier déjà petit, format inattendu…).
 */
fun compressImage(rawBytes: ByteArray): ByteArray {
    val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return rawBytes
    val largestSide = maxOf(bitmap.width, bitmap.height)
    val scale = MAX_DIMENSION.toFloat() / largestSide
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    } else {
        bitmap
    }
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    if (scaled !== bitmap) bitmap.recycle()
    scaled.recycle()
    return output.toByteArray()
}
