package com.ensemble.app.ui.journal

import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.DecryptedMemory
import com.ensemble.app.data.model.Memory
import com.ensemble.app.util.compressImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JournalViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _memories = MutableStateFlow<List<DecryptedMemory>>(emptyList())
    val memories: StateFlow<List<DecryptedMemory>> = _memories

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    init {
        viewModelScope.launch {
            container.memoryRepository.observeMemories(coupleId).collect { list ->
                _memories.update { list.mapNotNull(::decrypt) }
            }
        }
    }

    private fun decrypt(memory: Memory): DecryptedMemory? = runCatching {
        val text = container.cryptoManager.decryptText(EncryptedPayload(memory.textIv, memory.textCipher))
        DecryptedMemory(memory.id, memory.authorId, text, memory.photoStoragePath, memory.photoIvBase64, memory.memoryDate, memory.createdAt)
    }.getOrNull()

    suspend fun loadBitmap(memory: DecryptedMemory): ImageBitmap? {
        val path = memory.photoStoragePath ?: return null
        val iv = memory.photoIvBase64 ?: return null
        return container.imageLoader.loadBitmap(path, iv)
    }

    fun addMemory(text: String, memoryDate: Long, photoBytes: ByteArray?) {
        if (text.isBlank()) return
        _isSaving.value = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val payload = container.cryptoManager.encryptText(text)
                    val encryptedPhoto = photoBytes?.let { container.cryptoManager.encryptBytes(compressImage(it)) }
                    container.memoryRepository.addMemory(
                        coupleId,
                        Memory(
                            authorId = myUid,
                            textIv = payload.ivBase64,
                            textCipher = payload.cipherTextBase64,
                            photoIvBase64 = encryptedPhoto?.first?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
                            memoryDate = memoryDate,
                            createdAt = System.currentTimeMillis()
                        ),
                        encryptedPhoto?.second
                    )
                }
            }
            _isSaving.value = false
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch { runCatching { container.memoryRepository.deleteMemory(coupleId, memoryId) } }
    }
}
