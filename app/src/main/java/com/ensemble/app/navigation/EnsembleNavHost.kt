package com.ensemble.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ensemble.app.R
import com.ensemble.app.data.AppContainer
import com.ensemble.app.data.lock.BiometricAuthenticator
import com.ensemble.app.ui.auth.AuthScreen
import com.ensemble.app.ui.auth.AuthViewModel
import com.ensemble.app.ui.calendar.CalendarScreen
import com.ensemble.app.ui.calendar.CalendarViewModel
import com.ensemble.app.ui.home.HomeScreen
import com.ensemble.app.ui.home.HomeViewModel
import com.ensemble.app.ui.journal.JournalScreen
import com.ensemble.app.ui.journal.JournalViewModel
import com.ensemble.app.ui.lock.AppLockGate
import com.ensemble.app.ui.messages.MessagesScreen
import com.ensemble.app.ui.messages.MessagesViewModel
import com.ensemble.app.ui.more.MoreScreen
import com.ensemble.app.ui.pairing.PairingScreen
import com.ensemble.app.ui.pairing.PairingViewModel
import com.ensemble.app.ui.photos.PhotosScreen
import com.ensemble.app.ui.photos.PhotosViewModel
import com.ensemble.app.ui.presence.PresenceEffect
import com.ensemble.app.ui.settings.SettingsScreen
import com.ensemble.app.ui.settings.SettingsViewModel

private sealed class Tab(val route: String, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Home : Tab("home", R.string.nav_home, Icons.Default.Home)
    data object Messages : Tab("messages", R.string.nav_messages, Icons.Default.ChatBubble)
    data object Photos : Tab("photos", R.string.nav_photos, Icons.Default.Photo)
    data object More : Tab("more", R.string.nav_more, Icons.Default.MoreHoriz)
}

private val tabs = listOf(Tab.Home, Tab.Messages, Tab.Photos, Tab.More)
private val moreSubRoutes = setOf("calendar", "journal", "settings")

@Composable
fun EnsembleRoot(container: AppContainer, biometricAuthenticator: BiometricAuthenticator) {
    AppLockGate(container = container, biometricAuthenticator = biometricAuthenticator) {
        EnsembleContent(container)
    }
}

@Composable
private fun EnsembleContent(container: AppContainer) {
    val sessionViewModel: SessionViewModel = viewModel(
        factory = viewModelFactory { initializer { SessionViewModel(container) } }
    )
    val state by sessionViewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is SessionState.Loading -> FullScreenLoading()
        is SessionState.LoggedOut -> {
            val vm: AuthViewModel = viewModel(factory = viewModelFactory { initializer { AuthViewModel(container) } })
            AuthScreen(vm) { /* la session se met à jour automatiquement via authStateFlow */ }
        }
        is SessionState.NeedsPairing -> {
            val vm: PairingViewModel = viewModel(
                factory = viewModelFactory { initializer { PairingViewModel(container, s.uid, s.existingCoupleId) } }
            )
            PairingScreen(vm) { sessionViewModel.refreshNow() }
        }
        is SessionState.Ready -> {
            val myUid = container.authRepository.currentUser?.uid.orEmpty()
            MainScaffold(container, s.coupleId, myUid)
        }
    }
}

@Composable
private fun FullScreenLoading() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) { CircularProgressIndicator() }
}

@Composable
private fun MainScaffold(container: AppContainer, coupleId: String, myUid: String) {
    val navController = rememberNavController()
    PresenceEffect(container, myUid)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                val currentRoute = currentDestination?.route
                tabs.forEach { tab ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == tab.route } == true ||
                        (tab == Tab.More && currentRoute in moreSubRoutes)
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Tab.Home.route) {
                val vm: HomeViewModel = viewModel(
                    factory = viewModelFactory { initializer { HomeViewModel(container, coupleId, myUid) } }
                )
                HomeScreen(
                    vm,
                    onOpenCalendar = { navController.navigate("calendar") },
                    onOpenMessages = {
                        navController.navigate(Tab.Messages.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Tab.Messages.route) {
                val vm: MessagesViewModel = viewModel(
                    factory = viewModelFactory { initializer { MessagesViewModel(container, coupleId, myUid) } }
                )
                MessagesScreen(vm)
            }
            composable(Tab.Photos.route) {
                val vm: PhotosViewModel = viewModel(
                    factory = viewModelFactory { initializer { PhotosViewModel(container, coupleId, myUid) } }
                )
                PhotosScreen(vm)
            }
            composable(Tab.More.route) {
                MoreScreen(
                    onCalendarClick = { navController.navigate("calendar") },
                    onJournalClick = { navController.navigate("journal") },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
            composable("calendar") {
                val vm: CalendarViewModel = viewModel(
                    factory = viewModelFactory { initializer { CalendarViewModel(container, coupleId, myUid) } }
                )
                CalendarScreen(vm, onBack = { navController.popBackStack() })
            }
            composable("journal") {
                val vm: JournalViewModel = viewModel(
                    factory = viewModelFactory { initializer { JournalViewModel(container, coupleId, myUid) } }
                )
                JournalScreen(vm, onBack = { navController.popBackStack() })
            }
            composable("settings") {
                val vm: SettingsViewModel = viewModel(
                    factory = viewModelFactory { initializer { SettingsViewModel(container, coupleId, myUid) } }
                )
                SettingsScreen(vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
