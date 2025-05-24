package com.example.purrytify.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.purrytify.ui.components.NoInternetScreen
import com.example.purrytify.ui.screens.HomeScreen
import com.example.purrytify.ui.screens.LibraryScreen
import com.example.purrytify.ui.screens.ProfileScreen
import com.example.purrytify.ui.screens.EditProfileScreen
import com.example.purrytify.util.NetworkConnectionObserver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.purrytify.ui.screens.AudioDeviceScreen
import com.example.purrytify.ui.screens.QueueScreen

object Destinations {
    const val HOME_ROUTE = "home"
    const val LIBRARY_ROUTE = "library"
    const val PROFILE_ROUTE = "profile"
    const val EDIT_PROFILE_ROUTE = "edit_profile"
    const val QUEUE_ROUTE = "queue"
    const val AUDIO_DEVICES_ROUTE = "audio_devices"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    networkConnectionObserver: NetworkConnectionObserver,
) {

    val isConnected by networkConnectionObserver.isConnected.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Destinations.HOME_ROUTE,
        modifier = modifier
    ) {
        composable(Destinations.HOME_ROUTE) {
            HomeScreen()
        }
        composable(Destinations.LIBRARY_ROUTE) {
            LibraryScreen()
        }
        composable(Destinations.PROFILE_ROUTE) {
            networkConnectionObserver.checkAndUpdateConnectionStatus()
            if (isConnected) {
                ProfileScreen(navController = navController)
            } else {
                NoInternetScreen()
            }
        }
        composable(Destinations.EDIT_PROFILE_ROUTE) {
            networkConnectionObserver.checkAndUpdateConnectionStatus()
            if (isConnected) {
                EditProfileScreen(navController = navController)
            } else {
                NoInternetScreen()
            }
        }
        composable(Destinations.QUEUE_ROUTE) {
            QueueScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        composable(Destinations.AUDIO_DEVICES_ROUTE) {
            AudioDeviceScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}