package com.ensemble.app.data.repository

import com.ensemble.app.data.model.ChatMessage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val TYPING_TIMEOUT_MS = 6000L

class MessageRepository(private val db: FirebaseFirestore) {

    private fun couple(coupleId: String) = db.collection("couples").document(coupleId)
    private fun messages(coupleId: String) = couple(coupleId).collection("messages")
    private fun typing(coupleId: String) = couple(coupleId).collection("typing")
    private fun reads(coupleId: String) = couple(coupleId).collection("reads")

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
        val id = message.id.ifBlank { messages(coupleId).document().id }
        messages(coupleId).document(id).set(message.copy(id = id)).await()
    }

    suspend fun editMessage(coupleId: String, messageId: String, ivBase64: String, cipherTextBase64: String) {
        messages(coupleId).document(messageId).update(
            mapOf(
                "ivBase64" to ivBase64,
                "cipherTextBase64" to cipherTextBase64,
                "editedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun deleteMessage(coupleId: String, messageId: String) {
        messages(coupleId).document(messageId).delete().await()
    }

    suspend fun toggleReaction(coupleId: String, messageId: String, uid: String, emoji: String, currentlySet: Boolean) {
        val field = "reactions.$uid"
        messages(coupleId).document(messageId).update(
            field,
            if (currentlySet) FieldValue.delete() else emoji
        ).await()
    }

    /** Signale que l'utilisateur est en train d'écrire (ou plus). Utilise le timestamp pour expirer automatiquement côté lecteur. */
    suspend fun setTyping(coupleId: String, uid: String, isTyping: Boolean) {
        typing(coupleId).document(uid).set(
            mapOf("isTyping" to isTyping, "updatedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        ).await()
    }

    fun observeTyping(coupleId: String, partnerUid: String): Flow<Boolean> = callbackFlow {
        val registration = typing(coupleId).document(partnerUid).addSnapshotListener { snapshot, _ ->
            val isTyping = snapshot?.getBoolean("isTyping") == true
            val updatedAt = snapshot?.getLong("updatedAt") ?: 0L
            val fresh = System.currentTimeMillis() - updatedAt < TYPING_TIMEOUT_MS
            trySend(isTyping && fresh)
        }
        awaitClose { registration.remove() }
    }

    suspend fun markRead(coupleId: String, uid: String, timestamp: Long) {
        reads(coupleId).document(uid).set(mapOf("timestamp" to timestamp), SetOptions.merge()).await()
    }

    fun observePartnerRead(coupleId: String, partnerUid: String): Flow<Long> = callbackFlow {
        val registration = reads(coupleId).document(partnerUid).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.getLong("timestamp") ?: 0L)
        }
        awaitClose { registration.remove() }
    }
}
