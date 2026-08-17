package com.ensemble.app.data.crypto

import android.util.Base64

/**
 * Encode/décode le "code de pairing" échangé une seule fois entre les deux partenaires.
 * Il contient l'identifiant Firestore du couple + la clé de chiffrement AES-256, encodés
 * ensemble en Base64 pour être facilement copié-collé (SMS, en personne, etc.).
 * Ce code ne transite jamais par Firebase.
 */
object PairingCode {

    private const val SEPARATOR = "|"

    fun encode(coupleId: String, coupleKeyBase64: String): String {
        val raw = "$coupleId$SEPARATOR$coupleKeyBase64"
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decode(code: String): Pair<String, String>? {
        return try {
            val raw = String(Base64.decode(code.trim(), Base64.NO_WRAP), Charsets.UTF_8)
            val parts = raw.split(SEPARATOR)
            if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return null
            parts[0] to parts[1]
        } catch (e: Exception) {
            null
        }
    }
}
