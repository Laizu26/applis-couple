package com.ensemble.app.ui.presence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.ensemble.app.data.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HEARTBEAT_INTERVAL_MS = 60_000L

/**
 * Marque l'utilisateur "en ligne" tant que l'app est au premier plan (avec un battement de
 * cœur périodique), et "hors ligne" dès qu'elle passe en arrière-plan.
 */
@Composable
fun PresenceEffect(container: AppContainer, myUid: String) {
    val scope = rememberCoroutineScope()

    DisposableEffect(myUid) {
        var heartbeatJob: Job? = null
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    heartbeatJob = scope.launch {
                        while (true) {
                            runCatching { container.coupleRepository.updatePresence(myUid, true) }
                            delay(HEARTBEAT_INTERVAL_MS)
                        }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    heartbeatJob?.cancel()
                    scope.launch { runCatching { container.coupleRepository.updatePresence(myUid, false) } }
                }
                else -> {}
            }
        }
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            heartbeatJob?.cancel()
        }
    }
}
