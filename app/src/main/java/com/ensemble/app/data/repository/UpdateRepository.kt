package com.ensemble.app.data.repository

import com.ensemble.app.data.model.AppUpdateInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val MAX_APK_BYTES = 100L * 1024 * 1024

class UpdateRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {
    fun observeLatestUpdate(): Flow<AppUpdateInfo?> = callbackFlow {
        val registration = db.collection("app_meta").document("update")
            .addSnapshotListener { snapshot, _ ->
                trySend(if (snapshot?.exists() == true) snapshot.toObject(AppUpdateInfo::class.java) else null)
            }
        awaitClose { registration.remove() }
    }

    suspend fun downloadApk(storagePath: String): ByteArray =
        storage.reference.child(storagePath).getBytes(MAX_APK_BYTES).await()
}
