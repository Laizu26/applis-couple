package com.ensemble.app.data.repository

import com.ensemble.app.data.model.JournalDay
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class JournalRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    private fun days(coupleId: String) =
        db.collection("couples").document(coupleId).collection("journalDays")

    fun observeDays(coupleId: String): Flow<List<JournalDay>> = callbackFlow {
        val registration = days(coupleId)
            .orderBy("dateMillis", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(JournalDay::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /** [field] vaut "user1Text", "user2Text" ou "commonText" ; écrit {field}Iv et {field}Cipher. */
    suspend fun saveText(coupleId: String, dateKey: String, dateMillis: Long, field: String, ivBase64: String, cipherBase64: String) {
        days(coupleId).document(dateKey).set(
            mapOf(
                "dateMillis" to dateMillis,
                "${field}Iv" to ivBase64,
                "${field}Cipher" to cipherBase64,
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun uploadDayPhoto(coupleId: String, dateKey: String, dateMillis: Long, ivBase64: String, encryptedBytes: ByteArray) {
        val storagePath = "couples/$coupleId/journal/$dateKey.enc"
        storage.reference.child(storagePath).putBytes(encryptedBytes).await()
        days(coupleId).document(dateKey).set(
            mapOf(
                "dateMillis" to dateMillis,
                "photoStoragePath" to storagePath,
                "photoIvBase64" to ivBase64,
                "updatedAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun removeDayPhoto(coupleId: String, dateKey: String, storagePath: String?) {
        if (!storagePath.isNullOrBlank()) {
            runCatching { storage.reference.child(storagePath).delete().await() }
        }
        days(coupleId).document(dateKey).update(
            mapOf("photoStoragePath" to null, "photoIvBase64" to null)
        ).await()
    }
}
