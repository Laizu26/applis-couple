package com.ensemble.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.crypto.PairingCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    val pairingCode: String? by lazy {
        runCatching {
            val keyBase64 = container.cryptoManager.exportCoupleKeyBase64()
            PairingCode.encode(coupleId, keyBase64)
        }.getOrNull()
    }

    val userEmail: String? get() = container.authRepository.currentUser?.email

    var appLockEnabled by mutableStateOf(container.cryptoManager.isAppLockEnabled)
        private set

    private val _ownDisplayName = MutableStateFlow("")
    val ownDisplayName: StateFlow<String> = _ownDisplayName

    private val _partnerDisplayName = MutableStateFlow<String?>(null)
    val partnerDisplayName: StateFlow<String?> = _partnerDisplayName

    private val _partnerNickname = MutableStateFlow("")
    val partnerNickname: StateFlow<String> = _partnerNickname

    private var isUser1 = true

    init {
        viewModelScope.launch {
            val profile = runCatching { container.coupleRepository.getUserProfile(myUid) }.getOrNull()
            _ownDisplayName.value = decrypt(profile?.displayNameIv, profile?.displayNameCipher).orEmpty()
        }
        viewModelScope.launch {
            container.coupleRepository.observeCouple(coupleId).collect { couple ->
                if (couple == null) return@collect
                isUser1 = couple.user1Id == myUid
                val nicknameIv = if (isUser1) couple.nicknameByUser1Iv else couple.nicknameByUser2Iv
                val nicknameCipher = if (isUser1) couple.nicknameByUser1Cipher else couple.nicknameByUser2Cipher
                _partnerNickname.value = decrypt(nicknameIv, nicknameCipher).orEmpty()

                val partnerUid = if (isUser1) couple.user2Id else couple.user1Id
                if (partnerUid != null) {
                    val partnerProfile = runCatching { container.coupleRepository.getUserProfile(partnerUid) }.getOrNull()
                    _partnerDisplayName.value = decrypt(partnerProfile?.displayNameIv, partnerProfile?.displayNameCipher)
                }
            }
        }
    }

    private fun decrypt(iv: String?, cipher: String?): String? {
        if (iv == null || cipher == null) return null
        return runCatching { container.cryptoManager.decryptText(EncryptedPayload(iv, cipher)) }.getOrNull()
    }

    fun setOwnDisplayName(name: String) {
        _ownDisplayName.value = name
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(name)
            runCatching { container.coupleRepository.updateDisplayName(myUid, payload.ivBase64, payload.cipherTextBase64) }
        }
    }

    fun setPartnerNickname(nickname: String) {
        _partnerNickname.value = nickname
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(nickname)
            runCatching { container.coupleRepository.updateNickname(coupleId, isUser1, payload.ivBase64, payload.cipherTextBase64) }
        }
    }

    fun toggleAppLock(enabled: Boolean) {
        container.cryptoManager.isAppLockEnabled = enabled
        appLockEnabled = enabled
    }

    fun logout() {
        container.authRepository.signOut()
    }
}
