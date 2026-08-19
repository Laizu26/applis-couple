package com.ensemble.app.ui.lock

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.ensemble.app.R
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.lock.BiometricAuthenticator
import com.ensemble.app.ui.theme.RosePrimary
import kotlinx.coroutines.launch

@Composable
fun AppLockGate(
    container: AppContainer,
    biometricAuthenticator: BiometricAuthenticator,
    content: @Composable () -> Unit
) {
    var lockEnabled by remember { mutableStateOf(container.cryptoManager.isAppLockEnabled) }
    var unlocked by remember { mutableStateOf(!lockEnabled) }

    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Relu à chaque mise en arrière-plan : si l'utilisateur vient d'activer le
                // verrouillage dans Réglages sans avoir redémarré l'app, il prend effet ici.
                lockEnabled = container.cryptoManager.isAppLockEnabled
                if (lockEnabled) unlocked = false
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (lockEnabled && !unlocked) {
        LockScreen(
            onUnlock = { if (it) unlocked = true },
            authenticator = biometricAuthenticator
        )
    } else {
        content()
    }
}

@Composable
private fun LockScreen(onUnlock: (Boolean) -> Unit, authenticator: BiometricAuthenticator) {
    val scope = rememberCoroutineScope()

    fun tryUnlock() {
        scope.launch { onUnlock(authenticator.authenticate()) }
    }

    LaunchedEffect(Unit) { tryUnlock() }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = RosePrimary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { tryUnlock() }) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}
