package com.ensemble.app.ui.photos

import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.CouplePhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class PhotosViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _photos = MutableStateFlow<List<CouplePhoto>>(emptyList())
    val photos: StateFlow<List<CouplePhoto>> = _photos

    private val _uploadCount = MutableStateFlow(0)
    val uploadCount: StateFlow<Int> = _uploadCount

    init {
        viewModelScope.launch {
            container.photoRepository.observePhotos(coupleId).collect { _photos.value = it }
        }
    }

    suspend fun loadBitmap(photo: CouplePhoto): ImageBitmap? =
        container.imageLoader.loadBitmap(photo.storagePath, photo.ivBase64)

    fun decryptCaption(photo: CouplePhoto): String? {
        val iv = photo.captionIvBase64 ?: return null
        val cipher = photo.captionCipherBase64 ?: return null
        return runCatching { container.cryptoManager.decryptText(EncryptedPayload(iv, cipher)) }.getOrNull()
    }

    fun addPhotos(rawBytesList: List<ByteArray>) {
        _uploadCount.value += rawBytesList.size
        viewModelScope.launch {
            for (rawBytes in rawBytesList) {
                withContext(Dispatchers.IO) {
                    val (iv, cipherBytes) = container.cryptoManager.encryptBytes(rawBytes)
                    val photo = CouplePhoto(
                        id = UUID.randomUUID().toString(),
                        uploaderId = myUid,
                        ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                        timestamp = System.currentTimeMillis()
                    )
                    container.photoRepository.uploadPhoto(coupleId, photo, cipherBytes)
                }
                _uploadCount.value -= 1
            }
        }
    }

    fun setCaption(photo: CouplePhoto, caption: String) {
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(caption)
            container.photoRepository.updateCaption(coupleId, photo.id, payload.ivBase64, payload.cipherTextBase64)
        }
    }
}
