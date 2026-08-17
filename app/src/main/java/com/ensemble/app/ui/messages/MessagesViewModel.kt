package com.ensemble.app.ui.messages

import android.media.MediaPlayer
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.audio.ByteArrayMediaDataSource
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.ChatMessage
import com.ensemble.app.data.model.DecryptedMessage
import com.ensemble.app.data.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TYPING_IDLE_DELAY_MS = 3000L

data class MessagesUiState(
    val messages: List<DecryptedMessage> = emptyList(),
    val draft: String = "",
    val errorMessage: String? = null
)

class MessagesViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState

    private val _partnerTyping = MutableStateFlow(false)
    val partnerTyping: StateFlow<Boolean> = _partnerTyping

    private val _partnerReadTimestamp = MutableStateFlow(0L)
    val partnerReadTimestamp: StateFlow<Long> = _partnerReadTimestamp

    val currentUserId: String get() = myUid

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId

    private var partnerUid: String? = null
    private var typingResetJob: Job? = null
    private var isCurrentlyTyping = false
    private var mediaPlayer: MediaPlayer? = null

    init {
        viewModelScope.launch {
            container.coupleRepository.observeCouple(coupleId).collect { couple ->
                val partner = couple?.let { if (it.user1Id == myUid) it.user2Id else it.user1Id }
                if (partner != null && partner != partnerUid) {
                    partnerUid = partner
                    observePartnerSignals(partner)
                }
            }
        }
        viewModelScope.launch {
            container.messageRepository.observeMessages(coupleId).collect { messages ->
                val decrypted = messages.mapNotNull(::decrypt)
                _uiState.update { it.copy(messages = decrypted) }
                decrypted.maxOfOrNull { it.timestamp }?.let { latest ->
                    if (latest > 0) runCatching { container.messageRepository.markRead(coupleId, myUid, latest) }
                }
            }
        }
    }

    private fun observePartnerSignals(partner: String) {
        viewModelScope.launch {
            runCatching {
                container.messageRepository.observeTyping(coupleId, partner).collect { _partnerTyping.value = it }
            }
        }
        viewModelScope.launch {
            runCatching {
                container.messageRepository.observePartnerRead(coupleId, partner).collect { _partnerReadTimestamp.value = it }
            }
        }
    }

    private fun decrypt(msg: ChatMessage): DecryptedMessage? = runCatching {
        val text = if (msg.ivBase64.isNotEmpty() && msg.cipherTextBase64.isNotEmpty()) {
            container.cryptoManager.decryptText(EncryptedPayload(msg.ivBase64, msg.cipherTextBase64))
        } else ""
        if (msg.type == MessageType.TEXT && text.isEmpty()) return null
        DecryptedMessage(
            id = msg.id,
            senderId = msg.senderId,
            type = msg.type,
            text = text,
            photoStoragePath = msg.photoStoragePath,
            photoIvBase64 = msg.photoIvBase64,
            audioStoragePath = msg.audioStoragePath,
            audioIvBase64 = msg.audioIvBase64,
            audioDurationMs = msg.audioDurationMs,
            reactions = msg.reactions,
            editedAt = msg.editedAt,
            timestamp = msg.timestamp
        )
    }.getOrNull()

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value) }
        handleTyping(value.isNotEmpty())
    }

    private fun handleTyping(isTyping: Boolean) {
        typingResetJob?.cancel()
        if (isTyping) {
            if (!isCurrentlyTyping) {
                isCurrentlyTyping = true
                viewModelScope.launch { runCatching { container.messageRepository.setTyping(coupleId, myUid, true) } }
            }
            typingResetJob = viewModelScope.launch {
                delay(TYPING_IDLE_DELAY_MS)
                isCurrentlyTyping = false
                runCatching { container.messageRepository.setTyping(coupleId, myUid, false) }
            }
        } else if (isCurrentlyTyping) {
            isCurrentlyTyping = false
            viewModelScope.launch { runCatching { container.messageRepository.setTyping(coupleId, myUid, false) } }
        }
    }

    fun sendMessage() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(draft = "") }
        handleTyping(false)
        viewModelScope.launch {
            runCatching {
                val payload = container.cryptoManager.encryptText(text)
                container.messageRepository.sendMessage(
                    coupleId,
                    ChatMessage(
                        senderId = myUid,
                        type = MessageType.TEXT,
                        ivBase64 = payload.ivBase64,
                        cipherTextBase64 = payload.cipherTextBase64,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }.onFailure { restoreDraftAfterFailure(text) }
        }
    }

    private fun restoreDraftAfterFailure(text: String) {
        _uiState.update { it.copy(draft = text, errorMessage = "Message non envoyé, réessaie") }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun sendPhotos(rawBytesList: List<ByteArray>) {
        viewModelScope.launch {
            for (bytes in rawBytesList) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val (iv, cipherBytes) = container.cryptoManager.encryptBytes(bytes)
                        val messageId = UUID.randomUUID().toString()
                        val storagePath = "couples/$coupleId/chat/$messageId.enc"
                        container.photoRepository.uploadEncryptedBytes(storagePath, cipherBytes)
                        container.messageRepository.sendMessage(
                            coupleId,
                            ChatMessage(
                                id = messageId,
                                senderId = myUid,
                                type = MessageType.PHOTO,
                                photoStoragePath = storagePath,
                                photoIvBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }.onFailure { _uiState.update { it.copy(errorMessage = "Envoi de la photo échoué") } }
            }
        }
    }

    suspend fun loadPhotoBitmap(message: DecryptedMessage): ImageBitmap? {
        val path = message.photoStoragePath ?: return null
        val iv = message.photoIvBase64 ?: return null
        return container.imageLoader.loadBitmap(path, iv)
    }

    fun sendVoiceNote(bytes: ByteArray, durationMs: Long) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val (iv, cipherBytes) = container.cryptoManager.encryptBytes(bytes)
                    val messageId = UUID.randomUUID().toString()
                    val storagePath = "couples/$coupleId/chat/$messageId.audio.enc"
                    container.photoRepository.uploadEncryptedBytes(storagePath, cipherBytes)
                    container.messageRepository.sendMessage(
                        coupleId,
                        ChatMessage(
                            id = messageId,
                            senderId = myUid,
                            type = MessageType.AUDIO,
                            audioStoragePath = storagePath,
                            audioIvBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                            audioDurationMs = durationMs,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }.onFailure { _uiState.update { it.copy(errorMessage = "Envoi de la note vocale échoué") } }
        }
    }

    fun togglePlayback(message: DecryptedMessage) {
        if (_playingMessageId.value == message.id) {
            stopPlayback()
            return
        }
        stopPlayback()
        val path = message.audioStoragePath ?: return
        val ivBase64 = message.audioIvBase64 ?: return
        _playingMessageId.value = message.id
        viewModelScope.launch {
            val result = runCatching {
                val encrypted = withContext(Dispatchers.IO) { container.photoRepository.downloadEncryptedBytes(path) }
                val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                val plain = container.cryptoManager.decryptBytes(iv, encrypted)
                val player = MediaPlayer()
                player.setDataSource(ByteArrayMediaDataSource(plain))
                player.setOnCompletionListener { stopPlayback() }
                player.prepare()
                player.start()
                player
            }
            result.onSuccess { player ->
                if (_playingMessageId.value == message.id) mediaPlayer = player else player.release()
            }.onFailure { stopPlayback() }
        }
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        _playingMessageId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPlayback()
    }

    fun editMessage(messageId: String, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val payload = container.cryptoManager.encryptText(newText)
                container.messageRepository.editMessage(coupleId, messageId, payload.ivBase64, payload.cipherTextBase64)
            }.onFailure { _uiState.update { it.copy(errorMessage = "Modification échouée") } }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            runCatching { container.messageRepository.deleteMessage(coupleId, messageId) }
                .onFailure { _uiState.update { it.copy(errorMessage = "Suppression échouée") } }
        }
    }

    fun toggleReaction(message: DecryptedMessage, emoji: String) {
        val currentlySet = message.reactions[myUid] == emoji
        viewModelScope.launch {
            runCatching {
                container.messageRepository.toggleReaction(coupleId, message.id, myUid, emoji, currentlySet)
            }
        }
    }
}
