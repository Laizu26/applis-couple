package com.ensemble.app.data.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.ensemble.app.data.repository.PhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val MAX_MEMORY_CACHE_SIZE = 60
private const val MAX_DISK_CACHE_BYTES = 300L * 1024 * 1024

/**
 * Télécharge et déchiffre les images (photos et pièces jointes du chat), avec un cache mémoire
 * partagé ET un cache disque local pour la consultation hors-ligne. Le cache disque ne contient
 * QUE les octets chiffrés tels qu'ils sont sur Firebase Storage (même AES-256-GCM, même IV) :
 * sans la clé du couple, jamais écrite sur le disque et dérivée en mémoire à chaque session,
 * ces fichiers restent illisibles. Rien n'est donc jamais stocké en clair sur l'appareil.
 */
class EncryptedImageLoader(
    private val photoRepository: PhotoRepository,
    private val cryptoManager: CryptoManager,
    context: Context
) {
    private val diskCacheDir = File(context.filesDir, "encrypted_photo_cache").apply { mkdirs() }

    private val memoryCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
                size > MAX_MEMORY_CACHE_SIZE
        }
    )

    suspend fun loadBitmap(storagePath: String, ivBase64: String): ImageBitmap? {
        memoryCache[storagePath]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val cacheFile = diskCacheFile(storagePath)
                val encrypted = if (cacheFile.exists()) {
                    cacheFile.readBytes()
                } else {
                    photoRepository.downloadEncryptedBytes(storagePath).also { writeToDiskCache(cacheFile, it) }
                }
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val plain = cryptoManager.decryptBytes(iv, encrypted)
                val bitmap: Bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                bitmap.asImageBitmap()
            }.getOrNull()?.also { memoryCache[storagePath] = it }
        }
    }

    /** À appeler quand une photo est vraiment supprimée (pas juste détachée d'un passage du journal). */
    fun evict(storagePath: String) {
        memoryCache.remove(storagePath)
        runCatching { diskCacheFile(storagePath).delete() }
    }

    private fun diskCacheFile(storagePath: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(storagePath.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(diskCacheDir, name)
    }

    private fun writeToDiskCache(file: File, bytes: ByteArray) {
        runCatching {
            file.writeBytes(bytes)
            evictDiskCacheIfNeeded()
        }
    }

    private fun evictDiskCacheIfNeeded() {
        val files = diskCacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_DISK_CACHE_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (totalSize <= MAX_DISK_CACHE_BYTES) break
            totalSize -= file.length()
            file.delete()
        }
    }
}
