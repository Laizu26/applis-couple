package com.ensemble.app.ui.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.model.CouplePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val MAX_CACHE_SIZE = 60

class PhotosViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _photos = MutableStateFlow<List<CouplePhoto>>(emptyList())
    val photos: StateFlow<List<CouplePhoto>> = _photos

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    private val bitmapCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean =
                size > MAX_CACHE_SIZE
        }
    )

    init {
        viewModelScope.launch {
            container.photoRepository.observePhotos(coupleId).collect { _photos.value = it }
        }
    }

    suspend fun loadBitmap(photo: CouplePhoto): ImageBitmap? {
        bitmapCache[photo.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val encrypted = container.photoRepository.downloadEncryptedBytes(photo.storagePath)
                val iv = android.util.Base64.decode(photo.ivBase64, android.util.Base64.NO_WRAP)
                val plain = container.cryptoManager.decryptBytes(iv, encrypted)
                val bitmap: Bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size)
                bitmap.asImageBitmap()
            }.getOrNull()?.also { bitmapCache[photo.id] = it }
        }
    }

    fun addPhoto(rawBytes: ByteArray) {
        _isUploading.value = true
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val (iv, cipherBytes) = container.cryptoManager.encryptBytes(rawBytes)
                val photo = CouplePhoto(
                    id = UUID.randomUUID().toString(),
                    uploaderId = myUid,
                    ivBase64 = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
                    timestamp = System.currentTimeMillis()
                )
                container.photoRepository.uploadPhoto(coupleId, photo, cipherBytes)
            }
            _isUploading.value = false
        }
    }
}
