package com.ensemble.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun onEmailChange(value: String) { _uiState.value = _uiState.value.copy(email = value, errorMessage = null) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value, errorMessage = null) }
    fun toggleMode() { _uiState.value = _uiState.value.copy(isSignUp = !_uiState.value.isSignUp, errorMessage = null) }

    fun submit(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.length < 6) {
            _uiState.value = state.copy(errorMessage = "Adresse e-mail valide et mot de passe de 6 caractères minimum")
            return
        }
        _uiState.value = state.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                if (state.isSignUp) {
                    container.authRepository.signUp(state.email.trim(), state.password)
                } else {
                    container.authRepository.login(state.email.trim(), state.password)
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Une erreur est survenue")
            }
        }
    }
}
