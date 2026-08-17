package com.ensemble.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.ensemble.app.data.lock.BiometricAuthenticator
import com.ensemble.app.navigation.EnsembleRoot
import com.ensemble.app.ui.theme.EnsembleTheme
import com.google.firebase.Firebase
import com.google.firebase.messaging.messaging
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        registerFcmTokenListener()

        val biometricAuthenticator = BiometricAuthenticator(this)

        setContent {
            val container = (application as EnsembleApplication).container
            EnsembleTheme {
                EnsembleRoot(container, biometricAuthenticator)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun registerFcmTokenListener() {
        val container = (application as EnsembleApplication).container
        Firebase.messaging.token.addOnSuccessListener { token ->
            val uid = container.authRepository.currentUser?.uid ?: return@addOnSuccessListener
            MainScope().launch {
                runCatching { container.coupleRepository.updateFcmToken(uid, token) }
            }
        }
    }
}
