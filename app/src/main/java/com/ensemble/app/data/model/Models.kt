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

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val ivBase64: String = "",
    val cipherTextBase64: String = "",
    val timestamp: Long = 0L
)

/** Message une fois déchiffré, prêt pour l'affichage. */
data class DecryptedMessage(
    val id: String,
    val senderId: String,
    val text: String,
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
