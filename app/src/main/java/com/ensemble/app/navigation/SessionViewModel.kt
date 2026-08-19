package com.ensemble.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class SessionState {
    data object Loading : SessionState()
    data object LoggedOut : SessionState()
    data class NeedsPairing(val uid: String, val existingCoupleId: String?) : SessionState()
    data class Ready(val coupleId: String) : SessionState()
}

class SessionViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state

    init {
        viewModelScope.launch {
            container.authRepository.authStateFlow().collect { user ->
                if (user == null) {
                    _state.value = SessionState.LoggedOut
                } else {
                    container.coupleRepository.ensureUserDoc(user.uid, user.email.orEmpty())
                    syncFcmToken(user.uid)
                    refresh(user.uid)
                }
            }
        }
    }

    /**
     * Sauvegarde le token FCM courant à chaque fois qu'un utilisateur devient authentifié
     * (démarrage à froid déjà connecté, OU connexion pendant la session en cours). Ne pas se
     * fier uniquement à onNewToken() : ce callback ne se redéclenche pas à chaque lancement, donc
     * si le token avait été généré avant que l'utilisateur soit connecté, il ne serait jamais
     * sauvegardé et ce partenaire ne recevrait plus jamais de notifications depuis cet appareil.
     */
    private fun syncFcmToken(uid: String) {
        viewModelScope.launch {
            runCatching {
                val token = FirebaseMessaging.getInstance().token.await()
                container.coupleRepository.updateFcmToken(uid, token)
            }
        }
    }

    suspend fun refresh(uid: String) {
        val profile = container.coupleRepository.getUserProfile(uid)
        val coupleId = profile?.coupleId
        _state.value = when {
            coupleId.isNullOrBlank() -> SessionState.NeedsPairing(uid, null)
            !container.cryptoManager.hasCoupleKey -> SessionState.NeedsPairing(uid, coupleId)
            else -> SessionState.Ready(coupleId)
        }
    }

    fun refreshNow() {
        val uid = container.authRepository.currentUser?.uid ?: return
        viewModelScope.launch { refresh(uid) }
    }
}
