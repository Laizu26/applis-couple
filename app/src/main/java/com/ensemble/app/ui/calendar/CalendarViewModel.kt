package com.ensemble.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.crypto.EncryptedPayload
import com.ensemble.app.data.model.CalendarEvent
import com.ensemble.app.data.model.DecryptedEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CalendarViewModel(
    private val container: AppContainer,
    private val coupleId: String,
    private val myUid: String
) : ViewModel() {

    private val _events = MutableStateFlow<List<DecryptedEvent>>(emptyList())
    val events: StateFlow<List<DecryptedEvent>> = _events

    init {
        viewModelScope.launch {
            container.eventRepository.observeEvents(coupleId).collect { list ->
                _events.update { list.mapNotNull(::decrypt) }
            }
        }
    }

    private fun decrypt(event: CalendarEvent): DecryptedEvent? = runCatching {
        val title = container.cryptoManager.decryptText(EncryptedPayload(event.titleIv, event.titleCipher))
        DecryptedEvent(event.id, event.authorId, event.category, title, event.dateMillis, event.recurringYearly, event.createdAt)
    }.getOrNull()

    fun addEvent(category: String, title: String, dateMillis: Long, recurringYearly: Boolean) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val payload = container.cryptoManager.encryptText(title)
            runCatching {
                container.eventRepository.addEvent(
                    coupleId,
                    CalendarEvent(
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

    fun deleteEvent(eventId: String) {
        viewModelScope.launch { runCatching { container.eventRepository.deleteEvent(coupleId, eventId) } }
    }
}
