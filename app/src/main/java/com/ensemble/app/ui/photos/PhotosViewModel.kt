package com.ensemble.app.ui.photos

import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.CouplePhoto
import com.ensemble.app.data.model.DecryptedAlbum
import com.ensemble.app.data.model.PhotoAlbum
import com.ensemble.app.util.compressImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val _albums = MutableStateFlow<List<DecryptedAlbum>>(emptyList())
    val albums: StateFlow<List<DecryptedAlbum>> = _albums

    /** null = "Toutes les photos" (pas de filtre par album). */
    private val _selectedAlbumId = MutableStateFlow<String?>(null)
    val selectedAlbumId: StateFlow<String?> = _selectedAlbumId

    val visiblePhotos: StateFlow<List<CouplePhoto>> = combine(_photos, _selectedAlbumId) { photos, albumId ->
        if (albumId == null) photos else photos.filter { it.albumId == albumId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uploadCount = MutableStateFlow(0)
    val uploadCount: StateFlow<Int> = _uploadCount

    init {
        viewModelScope.launch {
            container.photoRepository.observePhotos(coupleId).collect { _photos.value = it }
        }
        viewModelScope.launch {
            container.photoRepository.observeAlbums(coupleId).collect { list ->
                _albums.value = list.mapNotNull(::decryptAlbum)
            }
        }
    }

    private fun decryptAlbum(album: PhotoAlbum): DecryptedAlbum? = runCatching {
        val name = container.cryptoManager.decryptText(EncryptedPayload(album.nameIv, album.nameCipher))
        DecryptedAlbum(album.id, name, album.createdAt)
    }.getOrNull()

    fun selectAlbum(albumId: String?) {
        _selectedAlbumId.value = albumId
    }

    fun createAlbum(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(name)
            runCatching {
                container.photoRepository.createAlbum(
                    coupleId,
                    PhotoAlbum(
                        id = UUID.randomUUID().toString(),
                        authorId = myUid,
                        nameIv = payload.ivBase64,
                        nameCipher = payload.cipherTextBase64,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch {
            runCatching { container.photoRepository.deleteAlbum(coupleId, albumId) }
            if (_selectedAlbumId.value == albumId) _selectedAlbumId.value = null
        }
    }

    fun setPhotoAlbum(photo: CouplePhoto, albumId: String?) {
        viewModelScope.launch { runCatching { container.photoRepository.setPhotoAlbum(coupleId, photo.id, albumId) } }
    }

    /** Vignette de couverture d'un album : la photo la plus récente qu'il contient. */
    fun coverPhoto(albumId: String): CouplePhoto? =
        _photos.value.filter { it.albumId == albumId }.maxByOrNull { it.timestamp }

    suspend fun loadBitmap(photo: CouplePhoto): ImageBitmap? =
        container.imageLoader.loadBitmap(photo.storagePath, photo.ivBase64)

    fun decryptCaption(photo: CouplePhoto): String? {
        val iv = photo.captionIvBase64 ?: return null
        val cipher = photo.captionCipherBase64 ?: return null
        return runCatching { container.cryptoManager.decryptText(EncryptedPayload(iv, cipher)) }.getOrNull()
    }

    /** Les photos ajoutées pendant qu'un album est ouvert y sont directement rattachées. */
    fun addPhotos(rawBytesList: List<ByteArray>) {
        val albumId = _selectedAlbumId.value
        _uploadCount.value += rawBytesList.size
        viewModelScope.launch {
            for (rawBytes in rawBytesList) {
                withContext(Dispatchers.IO) {
                    val (iv, cipherBytes) = container.cryptoManager.encryptBytes(compressImage(rawBytes))
                    val photo = CouplePhoto(
                        id = UUID.randomUUID().toString(),
                        uploaderId = myUid,
                        ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                        albumId = albumId,
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

    fun deletePhoto(photo: CouplePhoto) {
        viewModelScope.launch {
            runCatching { container.photoRepository.deletePhoto(coupleId, photo) }
            container.imageLoader.evict(photo.storagePath)
        }
    }
}
