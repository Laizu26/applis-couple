package com.ensemble.app.data.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Déverrouille l'app via l'empreinte/visage/code déjà configurés sur l'appareil (pas de PIN
 * séparé à gérer côté app).
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    fun isAvailable(): Boolean {
        val manager = BiometricManager.from(activity)
        return manager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticate(): Boolean = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(false)
                }

                override fun onAuthenticationFailed() {
                    // Tentative échouée mais l'utilisateur peut réessayer ; on ne résout pas la coroutine ici.
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(com.ensemble.app.R.string.lock_title))
            .setSubtitle(activity.getString(com.ensemble.app.R.string.lock_subtitle))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(info)

        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
}
