package com.ensemble.app.data.repository

import com.ensemble.app.data.model.CouplePhoto
import com.ensemble.app.data.model.JournalDay
import com.ensemble.app.data.model.JournalPhotoRef
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class JournalRepository(
    private val db: FirebaseFirestore,
    private val photoRepository: PhotoRepository
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

    /**
     * Ajoute une photo à un passage du jour. La photo est aussi créée comme une vraie photo de
     * la galerie partagée (même storagePath/id) : elle apparaît donc automatiquement dans
     * l'onglet Photos en plus du journal, sans double upload ni double chiffrement.
     * [field] vaut "user1Photos", "user2Photos" ou "commonPhotos".
     */
    suspend fun addPhoto(
        coupleId: String,
        dateKey: String,
        dateMillis: Long,
        field: String,
        uploaderId: String,
        ivBase64: String,
        encryptedBytes: ByteArray
    ): JournalPhotoRef {
        val photoId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        photoRepository.uploadPhoto(
            coupleId,
            CouplePhoto(id = photoId, uploaderId = uploaderId, ivBase64 = ivBase64, timestamp = timestamp),
            encryptedBytes
        )
        val ref = JournalPhotoRef(
            id = photoId,
            uploaderId = uploaderId,
            storagePath = "couples/$coupleId/photos/$photoId.enc",
            ivBase64 = ivBase64,
            timestamp = timestamp
        )
        days(coupleId).document(dateKey).set(
            mapOf(
                "dateMillis" to dateMillis,
                field to FieldValue.arrayUnion(ref),
                "updatedAt" to timestamp
            ),
            SetOptions.merge()
        ).await()
        return ref
    }

    /** Détache uniquement la photo de ce passage du journal ; elle reste dans la galerie partagée. */
    suspend fun removePhotoRef(coupleId: String, dateKey: String, field: String, ref: JournalPhotoRef) {
        days(coupleId).document(dateKey).update(field, FieldValue.arrayRemove(ref)).await()
    }
}
