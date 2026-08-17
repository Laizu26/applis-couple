package com.ensemble.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val label: String? = null,
    val meetingDateMillis: Long? = null,
    val isSaving: Boolean = false
)

class HomeViewModel(
    private val container: AppContainer,
    private val coupleId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        viewModelScope.launch {
            container.coupleRepository.observeCouple(coupleId).collect { couple ->
                if (couple == null) return@collect
                val label = if (couple.meetingLabelCipher != null && couple.meetingLabelIv != null) {
                    runCatching {
                        container.cryptoManager.decryptText(EncryptedPayload(couple.meetingLabelIv, couple.meetingLabelCipher))
                    }.getOrNull()
                } else null
                val date = if (couple.meetingDateCipher != null && couple.meetingDateIv != null) {
                    runCatching {
                        container.cryptoManager.decryptText(EncryptedPayload(couple.meetingDateIv, couple.meetingDateCipher)).toLongOrNull()
                    }.getOrNull()
                } else null
                _uiState.value = _uiState.value.copy(label = label, meetingDateMillis = date)
            }
        }
    }

    fun setMeeting(label: String, dateMillis: Long) {
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch {
            val labelPayload = container.cryptoManager.encryptText(label)
            val datePayload = container.cryptoManager.encryptText(dateMillis.toString())
            container.coupleRepository.updateMeeting(
                coupleId = coupleId,
                labelIv = labelPayload.ivBase64,
                labelCipher = labelPayload.cipherTextBase64,
                dateIv = datePayload.ivBase64,
                dateCipher = datePayload.cipherTextBase64
            )
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }
}
