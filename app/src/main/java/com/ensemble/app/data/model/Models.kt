package com.ensemble.app.data.model

data class Couple(
    val id: String = "",
    val user1Id: String = "",
    val user2Id: String? = null,
    // Surnom que user1 a donné à user2, et vice versa (chiffrés avec la clé du couple).
    val nicknameByUser1Iv: String? = null,
    val nicknameByUser1Cipher: String? = null,
    val nicknameByUser2Iv: String? = null,
    val nicknameByUser2Cipher: String? = null,
    val createdAt: Long = 0L
)

object EventCategory {
    const val ENSEMBLE = "ENSEMBLE"
    const val DEPART = "DEPART"
    const val ANNIVERSAIRE = "ANNIVERSAIRE"
    const val VOYAGE = "VOYAGE"
    const val RENDEZVOUS = "RENDEZVOUS"
    const val AUTRE = "AUTRE"

    val all = listOf(ENSEMBLE, DEPART, ANNIVERSAIRE, VOYAGE, RENDEZVOUS, AUTRE)

    fun emoji(category: String): String = when (category) {
        ENSEMBLE -> "💕"
        DEPART -> "✈️"
        ANNIVERSAIRE -> "🎂"
        VOYAGE -> "🧳"
        RENDEZVOUS -> "📅"
        else -> "⭐"
    }

    fun label(category: String): String = when (category) {
        ENSEMBLE -> "Ensemble"
        DEPART -> "Départ"
        ANNIVERSAIRE -> "Anniversaire"
        VOYAGE -> "Voyage"
        RENDEZVOUS -> "Rendez-vous"
        else -> "Autre"
    }

    /** Couleur associée, utilisée pour les puces du calendrier. */
    fun colorHex(category: String): Long = when (category) {
        ENSEMBLE -> 0xFFE05271
        DEPART -> 0xFF5B8DEF
        ANNIVERSAIRE -> 0xFFE0A458
        VOYAGE -> 0xFF4CAF88
        RENDEZVOUS -> 0xFF9C6ADE
        else -> 0xFF9E9E9E
    }
}

data class CalendarEvent(
    val id: String = "",
    val authorId: String = "",
    val category: String = EventCategory.AUTRE,
    val titleIv: String = "",
    val titleCipher: String = "",
    val dateMillis: Long = 0L,
    val recurringYearly: Boolean = false,
    val createdAt: Long = 0L
)

data class DecryptedEvent(
    val id: String,
    val authorId: String,
    val category: String,
    val title: String,
    val dateMillis: Long,
    val recurringYearly: Boolean,
    val createdAt: Long
)

object MessageType {
    const val TEXT = "TEXT"
    const val PHOTO = "PHOTO"
    const val AUDIO = "AUDIO"
}

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val type: String = MessageType.TEXT,
    val ivBase64: String = "",
    val cipherTextBase64: String = "",
    val photoStoragePath: String? = null,
    val photoIvBase64: String? = null,
    val audioStoragePath: String? = null,
    val audioIvBase64: String? = null,
    val audioDurationMs: Long? = null,
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
    val audioStoragePath: String?,
    val audioIvBase64: String?,
    val audioDurationMs: Long?,
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
    val albumId: String? = null,
    val timestamp: Long = 0L
)

data class PhotoAlbum(
    val id: String = "",
    val authorId: String = "",
    val nameIv: String = "",
    val nameCipher: String = "",
    val createdAt: Long = 0L
)

data class DecryptedAlbum(
    val id: String,
    val name: String,
    val createdAt: Long
)

/**
 * Une entrée de journal par jour, partagée par le couple : chaque partenaire a son propre
 * passage (texte chiffré séparément), plus un passage "commun" que les deux peuvent modifier.
 * L'id du document est la date au format yyyy-MM-dd.
 */
data class JournalDay(
    val id: String = "",
    val dateMillis: Long = 0L,
    val user1TextIv: String? = null,
    val user1TextCipher: String? = null,
    val user2TextIv: String? = null,
    val user2TextCipher: String? = null,
    val commonTextIv: String? = null,
    val commonTextCipher: String? = null,
    val photoStoragePath: String? = null,
    val photoIvBase64: String? = null,
    val updatedAt: Long = 0L
)

data class DecryptedJournalDay(
    val id: String,
    val dateMillis: Long,
    val myText: String,
    val partnerText: String,
    val commonText: String,
    val photoStoragePath: String?,
    val photoIvBase64: String?
)

/** Référence la dernière version publiée de l'app (doc public app_meta/update, lecture seule côté client). */
data class AppUpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkStoragePath: String = "",
    val notes: String = ""
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val coupleId: String? = null,
    val fcmToken: String? = null,
    val timeZoneId: String? = null,
    val online: Boolean = false,
    val lastActiveAt: Long = 0L,
    val displayNameIv: String? = null,
    val displayNameCipher: String? = null
)
