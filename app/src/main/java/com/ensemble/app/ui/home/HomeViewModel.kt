package com.ensemble.app.ui.home

import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.CalendarEvent
import com.ensemble.app.data.model.DecryptedEvent
import com.ensemble.app.data.model.EventCategory
import com.ensemble.app.util.nextOccurrenceMillis
import com.ensemble.app.widget.CountdownWidget
import com.ensemble.app.widget.WidgetPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val TRACKED_CATEGORIES = setOf(EventCategory.ENSEMBLE, EventCategory.DEPART)

data class HomeUiState(
    val featuredEventId: String? = null,
    val category: String = EventCategory.ENSEMBLE,
    val label: String? = null,
    val meetingDateMillis: Long? = null,
    val recurringYearly: Boolean = false
)

/** Un événement à venir avec sa date effective déjà résolue (occurrence future si récurrent). */
data class UpcomingItem(val event: DecryptedEvent, val effectiveDateMillis: Long)

class HomeViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _partnerTimeZoneId = MutableStateFlow<String?>(null)
    val partnerTimeZoneId: StateFlow<String?> = _partnerTimeZoneId

    private val _upcomingEvents = MutableStateFlow<List<UpcomingItem>>(emptyList())
    val upcomingEvents: StateFlow<List<UpcomingItem>> = _upcomingEvents

    private var partnerUid: String? = null

    init {
        viewModelScope.launch {
            runCatching { container.coupleRepository.updateTimeZone(myUid, java.util.TimeZone.getDefault().id) }
        }
        viewModelScope.launch {
            container.coupleRepository.observeCouple(coupleId).collect { couple ->
                val partner = couple?.let { if (it.user1Id == myUid) it.user2Id else it.user1Id }
                if (partner != null && partner != partnerUid) {
                    partnerUid = partner
                    val profile = runCatching { container.coupleRepository.getUserProfile(partner) }.getOrNull()
                    _partnerTimeZoneId.value = profile?.timeZoneId
                }
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
                    appLockEnabled = container.cryptoManager.isAppLockEnabled
                )
                runCatching { CountdownWidget().updateAll(container.appContext) }
            }
        }
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
