package com.ensemble.app.data.repository

import com.ensemble.app.data.model.CalendarEvent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class EventRepository(private val db: FirebaseFirestore) {

    private fun events(coupleId: String) =
        db.collection("couples").document(coupleId).collection("events")

    fun observeEvents(coupleId: String): Flow<List<CalendarEvent>> = callbackFlow {
        val registration = events(coupleId)
            .orderBy("dateMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(CalendarEvent::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun addEvent(coupleId: String, event: CalendarEvent) {
        val id = event.id.ifBlank { events(coupleId).document().id }
        events(coupleId).document(id).set(event.copy(id = id)).await()
    }

    suspend fun deleteEvent(coupleId: String, eventId: String) {
        events(coupleId).document(eventId).delete().await()
    }
}
