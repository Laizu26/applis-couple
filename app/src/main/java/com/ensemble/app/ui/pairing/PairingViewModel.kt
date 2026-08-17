package com.ensemble.app.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.PairingCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PairingUiState(
    val existingCoupleId: String? = null,
    val generatedCode: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class PairingViewModel(
    private val container: AppContainer,
    private val uid: String,
    existingCoupleId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState(existingCoupleId = existingCoupleId))
    val uiState: StateFlow<PairingUiState> = _uiState

    fun createCouple() {
        if (_uiState.value.existingCoupleId != null) return
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                container.cryptoManager.generateAndStoreCoupleKey()
                val keyBase64 = container.cryptoManager.exportCoupleKeyBase64()
                val coupleId = container.coupleRepository.createCouple(uid)
                val code = PairingCode.encode(coupleId, keyBase64)
                _uiState.value = _uiState.value.copy(isLoading = false, generatedCode = code)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Erreur lors de la création du couple")
            }
        }
    }

    fun joinWithCode(rawCode: String, onDone: () -> Unit) {
        val decoded = PairingCode.decode(rawCode)
        if (decoded == null) {
            _uiState.value = _uiState.value.copy(errorMessage = "Ce code n'est pas valide")
            return
        }
        val (coupleId, keyBase64) = decoded
        val existing = _uiState.value.existingCoupleId
        if (existing != null && existing != coupleId) {
            _uiState.value = _uiState.value.copy(errorMessage = "Ce code ne correspond pas à votre couple")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                container.cryptoManager.storeCoupleKey(keyBase64)
                if (existing == null) {
                    container.coupleRepository.joinCouple(coupleId, uid)
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Erreur lors de la connexion")
            }
        }
    }
}
