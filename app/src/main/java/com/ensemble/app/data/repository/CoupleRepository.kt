package com.ensemble.app.data.repository

import com.ensemble.app.data.model.Couple
import com.ensemble.app.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CoupleRepository(private val db: FirebaseFirestore) {

    private fun couples() = db.collection("couples")
    private fun users() = db.collection("users")

    suspend fun ensureUserDoc(uid: String, email: String) {
        users().document(uid).set(
            mapOf("uid" to uid, "email" to email),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    suspend fun getUserProfile(uid: String): UserProfile? =
        users().document(uid).get().await().toObject(UserProfile::class.java)

    suspend fun updateFcmToken(uid: String, token: String) {
        users().document(uid).set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    /** Crée un nouveau couple pour l'utilisateur courant et retourne son id. */
    suspend fun createCouple(uid: String): String {
        val doc = couples().document()
        val couple = Couple(id = doc.id, user1Id = uid, createdAt = System.currentTimeMillis())
        doc.set(couple).await()
        users().document(uid).set(mapOf("coupleId" to doc.id), com.google.firebase.firestore.SetOptions.merge()).await()
        return doc.id
    }

    /** Rejoint un couple existant via son id (extrait du code de pairing). */
    suspend fun joinCouple(coupleId: String, uid: String) {
        couples().document(coupleId).update("user2Id", uid).await()
        users().document(uid).set(mapOf("coupleId" to coupleId), com.google.firebase.firestore.SetOptions.merge()).await()
    }

    fun observeCouple(coupleId: String): Flow<Couple?> = callbackFlow {
        val registration = couples().document(coupleId).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toObject(Couple::class.java))
        }
        awaitClose { registration.remove() }
    }

    suspend fun updateTimeZone(uid: String, zoneId: String) {
        users().document(uid).set(mapOf("timeZoneId" to zoneId), com.google.firebase.firestore.SetOptions.merge()).await()
    }
}
