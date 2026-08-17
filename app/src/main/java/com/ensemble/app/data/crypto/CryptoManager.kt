package com.ensemble.app.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chiffrement bout-en-bout côté client.
 *
 * La clé du couple (AES-256) n'est JAMAIS envoyée à Firebase : elle est générée sur l'appareil
 * du premier partenaire, partagée hors-bande via le "code de pairing" (l'autre l'entre à la
 * main dans l'app), puis stockée localement dans un EncryptedSharedPreferences protégé par
 * Android Keystore. Firestore/Storage ne voient jamais que du texte chiffré.
 */
class CryptoManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ensemble_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val hasCoupleKey: Boolean
        get() = prefs.contains(KEY_COUPLE_SECRET)

    /** Génère une nouvelle clé de couple AES-256 aléatoire et la stocke localement. */
    fun generateAndStoreCoupleKey(): ByteArray {
        val key = ByteArray(32)
        SecureRandom().nextBytes(key)
        prefs.edit().putString(KEY_COUPLE_SECRET, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    /** Importe une clé de couple reçue via un code de pairing et la stocke localement. */
    fun storeCoupleKey(keyBase64: String) {
        prefs.edit().putString(KEY_COUPLE_SECRET, keyBase64).apply()
    }

    fun exportCoupleKeyBase64(): String =
        prefs.getString(KEY_COUPLE_SECRET, null)
            ?: error("Aucune clé de couple stockée sur cet appareil")

    fun clearCoupleKey() {
        prefs.edit().remove(KEY_COUPLE_SECRET).apply()
    }

    private fun secretKey(): SecretKeySpec {
        val raw = Base64.decode(exportCoupleKeyBase64(), Base64.NO_WRAP)
        return SecretKeySpec(raw, "AES")
    }

    /** Chiffre une chaîne de caractères (message, légende de photo, libellé…). */
    fun encryptText(plainText: String): EncryptedPayload {
        val (iv, cipherBytes) = encryptBytes(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            cipherTextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        )
    }

    fun decryptText(payload: EncryptedPayload): String {
        val iv = Base64.decode(payload.ivBase64, Base64.NO_WRAP)
        val cipherBytes = Base64.decode(payload.cipherTextBase64, Base64.NO_WRAP)
        return String(decryptBytes(iv, cipherBytes), Charsets.UTF_8)
    }

    /** Chiffre des octets bruts (ex : contenu JPEG d'une photo). Retourne (iv, cipherText). */
    fun encryptBytes(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain)
        return iv to cipherText
    }

    fun decryptBytes(iv: ByteArray, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    companion object {
        private const val KEY_COUPLE_SECRET = "couple_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}

data class EncryptedPayload(
    val ivBase64: String,
    val cipherTextBase64: String
)
