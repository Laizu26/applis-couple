package com.ensemble.app.data.model

data class Couple(
    val id: String = "",
    val user1Id: String = "",
    val user2Id: String? = null,
    val meetingLabelIv: String? = null,
    val meetingLabelCipher: String? = null,
    val meetingDateIv: String? = null,
    val meetingDateCipher: String? = null,
    val createdAt: Long = 0L
)

object MessageType {
    const val TEXT = "TEXT"
    const val PHOTO = "PHOTO"
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val type: String = MessageType.TEXT,
    val ivBase64: String = "",
    val cipherTextBase64: String = "",
    val photoStoragePath: String? = null,
    val photoIvBase64: String? = null,
    val reactions: Map<String, String> = emptyMap(),
    val editedAt: Long? = null,
    val timestamp: Long = 0L
)

/** Message une fois déchiffré, prêt pour l'affichage. */
data class DecryptedMessage(
    val id: String,
    val senderId: String,
    val type: String,
    val text: String,
    val photoStoragePath: String?,
    val photoIvBase64: String?,
    val reactions: Map<String, String>,
    val editedAt: Long?,
    val timestamp: Long
)

data class CouplePhoto(
    val id: String = "",
    val uploaderId: String = "",
    val storagePath: String = "",
    val ivBase64: String = "",
    val captionIvBase64: String? = null,
    val captionCipherBase64: String? = null,
    val timestamp: Long = 0L
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val coupleId: String? = null,
    val fcmToken: String? = null
)
