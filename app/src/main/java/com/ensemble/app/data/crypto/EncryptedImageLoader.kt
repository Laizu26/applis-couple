package com.ensemble.app.data.crypto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ensemble.app.data.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_CACHE_SIZE = 60

/** Télécharge et déchiffre les images (photos et pièces jointes du chat), avec un cache mémoire partagé. */
class EncryptedImageLoader(
    private val photoRepository: PhotoRepository,
    private val cryptoManager: CryptoManager
) {
    private val cache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
                size > MAX_CACHE_SIZE
        }
    )

    suspend fun loadBitmap(storagePath: String, ivBase64: String): ImageBitmap? {
        cache[storagePath]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val encrypted = photoRepository.downloadEncryptedBytes(storagePath)
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val plain = cryptoManager.decryptBytes(iv, encrypted)
                val bitmap: Bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                bitmap.asImageBitmap()
            }.getOrNull()?.also { cache[storagePath] = it }
        }
    }
}
