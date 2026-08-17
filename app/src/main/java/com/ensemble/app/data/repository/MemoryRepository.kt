package com.ensemble.app.data.repository

import com.ensemble.app.data.model.Memory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MemoryRepository(
    private val db: FirebaseFirestore,
    private val photoRepository: PhotoRepository
) {
    private fun memories(coupleId: String) =
        db.collection("couples").document(coupleId).collection("memories")

    fun observeMemories(coupleId: String): Flow<List<Memory>> = callbackFlow {
        val registration = memories(coupleId)
            .orderBy("memoryDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(Memory::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun addMemory(coupleId: String, memory: Memory, encryptedPhotoBytes: ByteArray?) {
        var toSave = memory
        val id = memory.id.ifBlank { memories(coupleId).document().id }
        if (encryptedPhotoBytes != null) {
            val storagePath = "couples/$coupleId/memories/$id.enc"
            photoRepository.uploadEncryptedBytes(storagePath, encryptedPhotoBytes)
            toSave = toSave.copy(photoStoragePath = storagePath)
        }
        memories(coupleId).document(id).set(toSave.copy(id = id)).await()
    }

    suspend fun deleteMemory(coupleId: String, memoryId: String) {
        memories(coupleId).document(memoryId).delete().await()
    }
}
