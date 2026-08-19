package com.ensemble.app.ui.journal

import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.DecryptedJournalDay
import com.ensemble.app.data.model.JournalDay
import com.ensemble.app.data.model.JournalPhotoRef
import com.ensemble.app.util.compressImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Clé de document lisible et naturellement triable (ex: "2026-08-19"). */
fun LocalDate.toDateKey(): String = toString()

fun Long.toLocalDateKey(): String = java.time.Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate().toDateKey()

class JournalViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _days = MutableStateFlow<List<DecryptedJournalDay>>(emptyList())
    val days: StateFlow<List<DecryptedJournalDay>> = _days

    private val _isSavingPhoto = MutableStateFlow(false)
    val isSavingPhoto: StateFlow<Boolean> = _isSavingPhoto

    private val _partnerLabel = MutableStateFlow<String?>(null)
    val partnerLabel: StateFlow<String?> = _partnerLabel

    private var isUser1 = true
    private var rawDays: List<JournalDay> = emptyList()
    private var partnerUid: String? = null
    private var partnerDisplayNameCache: String? = null

    init {
        viewModelScope.launch {
            container.coupleRepository.observeCouple(coupleId).collect { couple ->
                if (couple == null) return@collect
                isUser1 = couple.user1Id == myUid
                _days.update { rawDays.mapNotNull(::decrypt) }

                val partner = if (isUser1) couple.user2Id else couple.user1Id
                val nicknameIv = if (isUser1) couple.nicknameByUser1Iv else couple.nicknameByUser2Iv
                val nicknameCipher = if (isUser1) couple.nicknameByUser1Cipher else couple.nicknameByUser2Cipher
                val nickname = decryptOrEmpty(nicknameIv, nicknameCipher).takeIf { it.isNotBlank() }

                if (partner != null && partner != partnerUid) {
                    partnerUid = partner
                    val profile = runCatching { container.coupleRepository.getUserProfile(partner) }.getOrNull()
                    partnerDisplayNameCache = decryptOrEmpty(profile?.displayNameIv, profile?.displayNameCipher).takeIf { it.isNotBlank() }
                }
                _partnerLabel.value = nickname ?: partnerDisplayNameCache
            }
        }
        viewModelScope.launch {
            container.journalRepository.observeDays(coupleId).collect { list ->
                rawDays = list
                _days.value = list.mapNotNull(::decrypt)
            }
        }
    }

    private fun decryptOrEmpty(iv: String?, cipher: String?): String {
        if (iv == null || cipher == null) return ""
        return runCatching { container.cryptoManager.decryptText(EncryptedPayload(iv, cipher)) }.getOrDefault("")
    }

    private fun decrypt(day: JournalDay): DecryptedJournalDay? = runCatching {
        val myIv = if (isUser1) day.user1TextIv else day.user2TextIv
        val myCipher = if (isUser1) day.user1TextCipher else day.user2TextCipher
        val partnerIv = if (isUser1) day.user2TextIv else day.user1TextIv
        val partnerCipher = if (isUser1) day.user2TextCipher else day.user1TextCipher
        DecryptedJournalDay(
            id = day.id,
            dateMillis = day.dateMillis,
            myText = decryptOrEmpty(myIv, myCipher),
            partnerText = decryptOrEmpty(partnerIv, partnerCipher),
            commonText = decryptOrEmpty(day.commonTextIv, day.commonTextCipher),
            myPhotos = if (isUser1) day.user1Photos else day.user2Photos,
            partnerPhotos = if (isUser1) day.user2Photos else day.user1Photos,
            commonPhotos = day.commonPhotos
        )
    }.getOrNull()

    fun todayDateKey(): String = LocalDate.now().toDateKey()
    fun todayDateMillis(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun saveMyText(dateKey: String, dateMillis: Long, text: String) {
        val field = if (isUser1) "user1Text" else "user2Text"
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(text)
            runCatching { container.journalRepository.saveText(coupleId, dateKey, dateMillis, field, payload.ivBase64, payload.cipherTextBase64) }
        }
    }

    fun saveCommonText(dateKey: String, dateMillis: Long, text: String) {
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(text)
            runCatching { container.journalRepository.saveText(coupleId, dateKey, dateMillis, "commonText", payload.ivBase64, payload.cipherTextBase64) }
        }
    }

    suspend fun loadPhotoBitmap(photo: JournalPhotoRef): ImageBitmap? =
        container.imageLoader.loadBitmap(photo.storagePath, photo.ivBase64)

    /** Ajoutée à mon passage du jour ET, en même temps, à la galerie partagée de l'onglet Photos. */
    fun addMyPhoto(dateKey: String, dateMillis: Long, rawBytes: ByteArray) {
        addPhoto(dateKey, dateMillis, if (isUser1) "user1Photos" else "user2Photos", rawBytes)
    }

    fun addCommonPhoto(dateKey: String, dateMillis: Long, rawBytes: ByteArray) {
        addPhoto(dateKey, dateMillis, "commonPhotos", rawBytes)
    }

    private fun addPhoto(dateKey: String, dateMillis: Long, field: String, rawBytes: ByteArray) {
        _isSavingPhoto.value = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val (iv, cipherBytes) = container.cryptoManager.encryptBytes(compressImage(rawBytes))
                    container.journalRepository.addPhoto(
                        coupleId, dateKey, dateMillis, field, myUid, Base64.encodeToString(iv, Base64.NO_WRAP), cipherBytes
                    )
                }
            }
            _isSavingPhoto.value = false
        }
    }

    fun removeMyPhoto(dateKey: String, photo: JournalPhotoRef) {
        removePhoto(dateKey, if (isUser1) "user1Photos" else "user2Photos", photo)
    }

    fun removeCommonPhoto(dateKey: String, photo: JournalPhotoRef) {
        removePhoto(dateKey, "commonPhotos", photo)
    }

    private fun removePhoto(dateKey: String, field: String, photo: JournalPhotoRef) {
        viewModelScope.launch { runCatching { container.journalRepository.removePhotoRef(coupleId, dateKey, field, photo) } }
    }
}
