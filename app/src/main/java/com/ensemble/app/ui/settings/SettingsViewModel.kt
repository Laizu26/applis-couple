package com.ensemble.app.ui.settings

import androidx.lifecycle.ViewModel
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.PairingCode

class SettingsViewModel(
    private val container: AppContainer,
    private val coupleId: String
) : ViewModel() {

    val pairingCode: String? by lazy {
        runCatching {
            val keyBase64 = container.cryptoManager.exportCoupleKeyBase64()
            PairingCode.encode(coupleId, keyBase64)
        }.getOrNull()
    }

    val userEmail: String? get() = container.authRepository.currentUser?.email

    fun logout() {
        container.authRepository.signOut()
    }
}
