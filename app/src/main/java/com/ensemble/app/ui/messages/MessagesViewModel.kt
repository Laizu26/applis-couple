package com.ensemble.app.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.ChatMessage
import com.ensemble.app.data.model.DecryptedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MessagesUiState(
    val messages: List<DecryptedMessage> = emptyList(),
    val draft: String = ""
)

class MessagesViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessagesUiState())
    val uiState: StateFlow<MessagesUiState> = _uiState

    init {
        viewModelScope.launch {
            container.messageRepository.observeMessages(coupleId).collect { messages ->
                val decrypted = messages.mapNotNull { msg ->
                    runCatching {
                        val text = container.cryptoManager.decryptText(
                            EncryptedPayload(msg.ivBase64, msg.cipherTextBase64)
                        )
                        DecryptedMessage(msg.id, msg.senderId, text, msg.timestamp)
                    }.getOrNull()
                }
                _uiState.value = _uiState.value.copy(messages = decrypted)
            }
        }
    }

    fun onDraftChange(value: String) {
        _uiState.value = _uiState.value.copy(draft = value)
    }

    fun sendMessage() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty()) return
        _uiState.value = _uiState.value.copy(draft = "")
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(text)
            container.messageRepository.sendMessage(
                coupleId,
                ChatMessage(
                    senderId = myUid,
                    ivBase64 = payload.ivBase64,
                    cipherTextBase64 = payload.cipherTextBase64,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    val currentUserId: String get() = myUid
}
