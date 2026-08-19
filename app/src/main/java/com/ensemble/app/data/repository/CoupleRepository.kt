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

    suspend fun updatePresence(uid: String, online: Boolean) {
        users().document(uid).set(
            mapOf("online" to online, "lastActiveAt" to System.currentTimeMillis()),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = users().document(uid).addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.toObject(UserProfile::class.java))
        }
        awaitClose { registration.remove() }
    }

    suspend fun updateDisplayName(uid: String, ivBase64: String, cipherTextBase64: String) {
        users().document(uid).set(
            mapOf("displayNameIv" to ivBase64, "displayNameCipher" to cipherTextBase64),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    /** Enregistre le surnom que l'utilisateur courant (user1 ou user2 du couple) donne à son/sa partenaire. */
    suspend fun updateNickname(coupleId: String, isUser1: Boolean, ivBase64: String, cipherTextBase64: String) {
        val ivField = if (isUser1) "nicknameByUser1Iv" else "nicknameByUser2Iv"
        val cipherField = if (isUser1) "nicknameByUser1Cipher" else "nicknameByUser2Cipher"
        couples().document(coupleId).set(
            mapOf(ivField to ivBase64, cipherField to cipherTextBase64),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }
}
