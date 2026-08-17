package com.ensemble.app.data.repository

import com.ensemble.app.data.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MessageRepository(private val db: FirebaseFirestore) {

    private fun messages(coupleId: String) =
        db.collection("couples").document(coupleId).collection("messages")

    fun observeMessages(coupleId: String): Flow<List<ChatMessage>> = callbackFlow {
        val registration = messages(coupleId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun sendMessage(coupleId: String, message: ChatMessage) {
        val doc = messages(coupleId).document()
        messages(coupleId).document(doc.id).set(message.copy(id = doc.id)).await()
    }
}
