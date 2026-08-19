package com.ensemble.app.ui.home

import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.CalendarEvent
import com.ensemble.app.data.model.CouplePhoto
import com.ensemble.app.data.model.DecryptedEvent
import com.ensemble.app.data.model.EventCategory
import com.ensemble.app.util.compressImage
import com.ensemble.app.util.nextOccurrenceMillis
import com.ensemble.app.widget.CountdownWidget
import com.ensemble.app.widget.PhotoWidget
import com.ensemble.app.widget.PhotoWidgetPrefs
import com.ensemble.app.widget.WidgetPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private val TRACKED_CATEGORIES = setOf(EventCategory.ENSEMBLE, EventCategory.DEPART)
private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

data class HomeUiState(
    val featuredEventId: String? = null,
    val category: String = EventCategory.ENSEMBLE,
    val label: String? = null,
    val meetingDateMillis: Long? = null,
    val recurringYearly: Boolean = false
)

/** Un événement à venir avec sa date effective déjà résolue (occurrence future si récurrent). */
data class UpcomingItem(val event: DecryptedEvent, val effectiveDateMillis: Long)

data class WeeklyStats(val messagesThisWeek: Int = 0, val photosThisWeek: Int = 0)

class HomeViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _partnerTimeZoneId = MutableStateFlow<String?>(null)
    val partnerTimeZoneId: StateFlow<String?> = _partnerTimeZoneId

    private val _partnerLabel = MutableStateFlow<String?>(null)
    val partnerLabel: StateFlow<String?> = _partnerLabel

    private val _upcomingEvents = MutableStateFlow<List<UpcomingItem>>(emptyList())
    val upcomingEvents: StateFlow<List<UpcomingItem>> = _upcomingEvents

    private val _ownDisplayName = MutableStateFlow<String?>(null)
    val ownDisplayName: StateFlow<String?> = _ownDisplayName

    private val _weeklyStats = MutableStateFlow(WeeklyStats())
    val weeklyStats: StateFlow<WeeklyStats> = _weeklyStats

    private val _memoryOfTheDay = MutableStateFlow<CouplePhoto?>(null)
    val memoryOfTheDay: StateFlow<CouplePhoto?> = _memoryOfTheDay

    private val _isUploadingQuickPhoto = MutableStateFlow(false)
    val isUploadingQuickPhoto: StateFlow<Boolean> = _isUploadingQuickPhoto

    private var partnerUid: String? = null
    private var partnerDisplayNameCache: String? = null
    private var lastWidgetPhotoId: String? = null

    init {
        viewModelScope.launch {
            runCatching { container.coupleRepository.updateTimeZone(myUid, java.util.TimeZone.getDefault().id) }
        }
        viewModelScope.launch {
            val profile = runCatching { container.coupleRepository.getUserProfile(myUid) }.getOrNull()
            _ownDisplayName.value = decryptOrNull(profile?.displayNameIv, profile?.displayNameCipher)
        }
        viewModelScope.launch {
            container.messageRepository.observeMessages(coupleId).collect { messages ->
                val since = System.currentTimeMillis() - WEEK_MS
                _weeklyStats.update { it.copy(messagesThisWeek = messages.count { m -> m.timestamp >= since }) }
            }
        }
        viewModelScope.launch {
            container.photoRepository.observePhotos(coupleId).collect { photos ->
                val since = System.currentTimeMillis() - WEEK_MS
                _weeklyStats.update { it.copy(photosThisWeek = photos.count { p -> p.timestamp >= since }) }
                if (photos.isNotEmpty()) {
                    val dayOfYear = java.time.LocalDate.now().dayOfYear
                    _memoryOfTheDay.value = photos[dayOfYear % photos.size]
                }

                val latestPhoto = photos.firstOrNull()
                if (latestPhoto?.id != lastWidgetPhotoId) {
                    lastWidgetPhotoId = latestPhoto?.id
                    val bitmap = latestPhoto?.let {
                        container.imageLoader.loadBitmap(it.storagePath, it.ivBase64)?.asAndroidBitmap()
                    }
                    PhotoWidgetPrefs.save(container.appContext, bitmap, container.cryptoManager.isAppLockEnabled)
                    runCatching { PhotoWidget().updateAll(container.appContext) }
                }
            }
        }
        viewModelScope.launch {
            container.coupleRepository.observeCouple(coupleId).collect { couple ->
                if (couple == null) return@collect
                val isUser1 = couple.user1Id == myUid
                val partner = if (isUser1) couple.user2Id else couple.user1Id
                val nicknameIv = if (isUser1) couple.nicknameByUser1Iv else couple.nicknameByUser2Iv
                val nicknameCipher = if (isUser1) couple.nicknameByUser1Cipher else couple.nicknameByUser2Cipher
                val nickname = decryptOrNull(nicknameIv, nicknameCipher)

                if (partner != null && partner != partnerUid) {
                    partnerUid = partner
                    val profile = runCatching { container.coupleRepository.getUserProfile(partner) }.getOrNull()
                    _partnerTimeZoneId.value = profile?.timeZoneId
                    partnerDisplayNameCache = decryptOrNull(profile?.displayNameIv, profile?.displayNameCipher)
                }
                _partnerLabel.value = nickname?.takeIf { it.isNotBlank() } ?: partnerDisplayNameCache?.takeIf { it.isNotBlank() }
            }
        }
        viewModelScope.launch {
            container.eventRepository.observeEvents(coupleId).collect { events ->
                val now = System.currentTimeMillis()
                val decrypted = events.mapNotNull(::decrypt)
                fun effectiveDate(e: DecryptedEvent) = nextOccurrenceMillis(e.dateMillis, e.recurringYearly, now)

                val tracked = decrypted.filter { it.category in TRACKED_CATEGORIES }

                // Priorité au plus proche événement à venir (Ensemble OU Départ) ; sinon,
                // dernière fois qu'on a été "Ensemble" dans le passé.
                val featured = tracked.filter { effectiveDate(it) >= now }.minByOrNull { effectiveDate(it) }
                    ?: tracked.filter { it.category == EventCategory.ENSEMBLE && !it.recurringYearly && it.dateMillis < now }
                        .maxByOrNull { it.dateMillis }
                val featuredEffectiveDate = featured?.let { effectiveDate(it) }

                _uiState.update { current ->
                    current.copy(
                        featuredEventId = featured?.id,
                        category = featured?.category ?: EventCategory.ENSEMBLE,
                        label = featured?.title,
                        meetingDateMillis = featuredEffectiveDate,
                        recurringYearly = featured?.recurringYearly ?: false
                    )
                }

                _upcomingEvents.value = decrypted
                    .map { UpcomingItem(it, effectiveDate(it)) }
                    .filter { it.effectiveDateMillis >= now && it.event.id != featured?.id }
                    .sortedBy { it.effectiveDateMillis }
                    .take(3)

                WidgetPrefs.save(
                    container.appContext,
                    featured?.title,
                    featuredEffectiveDate,
                    isPast = featuredEffectiveDate != null && featuredEffectiveDate < now,
                    appLockEnabled = container.cryptoManager.isAppLockEnabled,
                    category = featured?.category ?: EventCategory.ENSEMBLE
                )
                runCatching { CountdownWidget().updateAll(container.appContext) }
            }
        }
    }

    suspend fun loadMemoryBitmap(photo: CouplePhoto): ImageBitmap? =
        container.imageLoader.loadBitmap(photo.storagePath, photo.ivBase64)

    /** Ajoute une photo à la galerie directement depuis l'accueil, sans passer par l'onglet Photos. */
    fun quickAddPhoto(rawBytes: ByteArray) {
        _isUploadingQuickPhoto.value = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val (iv, cipherBytes) = container.cryptoManager.encryptBytes(compressImage(rawBytes))
                    val photo = CouplePhoto(
                        id = UUID.randomUUID().toString(),
                        uploaderId = myUid,
                        ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
                        timestamp = System.currentTimeMillis()
                    )
                    container.photoRepository.uploadPhoto(coupleId, photo, cipherBytes)
                }
            }
            _isUploadingQuickPhoto.value = false
        }
    }

    private fun decryptOrNull(iv: String?, cipher: String?): String? {
        if (iv == null || cipher == null) return null
        return runCatching { container.cryptoManager.decryptText(EncryptedPayload(iv, cipher)) }.getOrNull()
    }

    private fun decrypt(event: CalendarEvent): DecryptedEvent? = runCatching {
        val title = container.cryptoManager.decryptText(EncryptedPayload(event.titleIv, event.titleCipher))
        DecryptedEvent(event.id, event.authorId, event.category, title, event.dateMillis, event.recurringYearly, event.createdAt)
    }.getOrNull()

    /** Crée ou met à jour (si édition) l'événement mis en avant sur l'accueil (Ensemble ou Départ). */
    fun setMeeting(category: String, label: String, dateMillis: Long, recurringYearly: Boolean) {
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(label)
            // Si la catégorie change, on ne réutilise pas l'id : ça créerait un nouvel événement
            // au lieu d'écraser un événement passé (ex. transformer un "Ensemble depuis" en "Départ").
            val reuseId = if (_uiState.value.category == category) _uiState.value.featuredEventId.orEmpty() else ""
            runCatching {
                container.eventRepository.addEvent(
                    coupleId,
                    CalendarEvent(
                        id = reuseId,
                        authorId = myUid,
                        category = category,
                        titleIv = payload.ivBase64,
                        titleCipher = payload.cipherTextBase64,
                        dateMillis = dateMillis,
                        recurringYearly = recurringYearly,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
