package com.ensemble.app.data.repository

import com.ensemble.app.data.model.CouplePhoto
import com.ensemble.app.data.model.PhotoAlbum
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val MAX_PHOTO_BYTES = 15L * 1024 * 1024

class PhotoRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage
) {

    private fun photos(coupleId: String) =
        db.collection("couples").document(coupleId).collection("photos")

    fun observePhotos(coupleId: String): Flow<List<CouplePhoto>> = callbackFlow {
        val registration = photos(coupleId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(CouplePhoto::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun uploadPhoto(coupleId: String, photo: CouplePhoto, encryptedBytes: ByteArray) {
        val storagePath = "couples/$coupleId/photos/${photo.id}.enc"
        storage.reference.child(storagePath).putBytes(encryptedBytes).await()
        photos(coupleId).document(photo.id).set(photo.copy(storagePath = storagePath)).await()
    }

    suspend fun downloadEncryptedBytes(storagePath: String): ByteArray =
        storage.reference.child(storagePath).getBytes(MAX_PHOTO_BYTES).await()

    /** Upload générique d'octets chiffrés vers un chemin Storage donné (photos et pièces jointes du chat). */
    suspend fun uploadEncryptedBytes(storagePath: String, encryptedBytes: ByteArray) {
        storage.reference.child(storagePath).putBytes(encryptedBytes).await()
    }

    suspend fun updateCaption(coupleId: String, photoId: String, ivBase64: String, cipherTextBase64: String) {
        photos(coupleId).document(photoId).update(
            mapOf(
                "captionIvBase64" to ivBase64,
                "captionCipherBase64" to cipherTextBase64
            )
        ).await()
    }

    suspend fun deletePhoto(coupleId: String, photo: CouplePhoto) {
        if (photo.storagePath.isNotBlank()) {
            runCatching { storage.reference.child(photo.storagePath).delete().await() }
        }
        photos(coupleId).document(photo.id).delete().await()
    }

    suspend fun setPhotoAlbum(coupleId: String, photoId: String, albumId: String?) {
        photos(coupleId).document(photoId).update("albumId", albumId).await()
    }

    private fun albums(coupleId: String) =
        db.collection("couples").document(coupleId).collection("albums")

    fun observeAlbums(coupleId: String): Flow<List<PhotoAlbum>> = callbackFlow {
        val registration = albums(coupleId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { it.toObject(PhotoAlbum::class.java) }.orEmpty()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun createAlbum(coupleId: String, album: PhotoAlbum) {
        albums(coupleId).document(album.id).set(album).await()
    }

    suspend fun deleteAlbum(coupleId: String, albumId: String) {
        albums(coupleId).document(albumId).delete().await()
        val photosInAlbum = photos(coupleId).whereEqualTo("albumId", albumId).get().await()
        for (doc in photosInAlbum.documents) {
            doc.reference.update("albumId", null).await()
        }
    }
}
