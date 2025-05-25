package com.example.purrytify.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.purrytify.ui.screens.HomeScreen
import com.example.purrytify.ui.screens.LibraryScreen
import com.example.purrytify.ui.screens.OnlineSongsScreen
import com.example.purrytify.ui.screens.ProfileScreen
import com.example.purrytify.ui.screens.QRScannerScreen
import com.example.purrytify.ui.screens.QueueScreen
import com.example.purrytify.ui.screens.EditProfileScreen
import com.example.purrytify.util.NetworkConnectionObserver
import com.example.purrytify.ui.components.NoInternetScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.purrytify.ui.screens.AnalyticsScreen
import com.example.purrytify.ui.screens.AudioDeviceScreen
import com.example.purrytify.viewmodels.MainViewModel
import com.example.purrytify.viewmodels.PlaylistContext
import com.example.purrytify.viewmodels.PlaylistType

object Destinations {
    const val HOME_ROUTE = "home"
    const val LIBRARY_ROUTE = "library"
    const val PROFILE_ROUTE = "profile"
    const val EDIT_PROFILE_ROUTE = "edit_profile"
    const val QUEUE_ROUTE = "queue"
    const val ONLINE_SONGS_ROUTE = "online_songs"
    const val QR_SCANNER_ROUTE = "qr_scanner"
    const val AUDIO_DEVICES_ROUTE = "audio_devices"
    const val ANALYTICS_ROUTE = "analytics"
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    mainViewModel: MainViewModel,
    networkConnectionObserver: NetworkConnectionObserver
) {
    val isConnected by networkConnectionObserver.isConnected.collectAsState()

    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = Destinations.HOME_ROUTE,
        modifier = modifier
    ) {
        composable(Destinations.HOME_ROUTE) {
            HomeScreen(navController = navController)
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
                onNavigateBack = { navController.popBackStack() },
                mainViewModel = mainViewModel
            )
        }
        composable(Destinations.ANALYTICS_ROUTE) {
            AnalyticsScreen(navController = navController)
        }
        composable(Destinations.ONLINE_SONGS_ROUTE) {
            OnlineSongsScreen(
                onSongSelected = { onlineSong ->
                    // UPDATED: Convert OnlineSong to Song and use playSong for backward compatibility
                    val song = onlineSong.toSong()
                    mainViewModel.playSong(song)
                },
                mainViewModel = mainViewModel // PASS MainViewModel
            )
        }
        composable(Destinations.QR_SCANNER_ROUTE) {
            QRScannerScreen(
                onQRCodeDetected = { qrCode ->
                    // Handle QR code detection
                    val songId = com.example.purrytify.util.ShareUtils.extractSongIdFromDeepLink(qrCode)
                    if (songId != null) {
                        // Navigate back and handle deep link
                        navController.popBackStack()

                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(qrCode))
                        context.startActivity(intent)
                    } else {
                        // Show error - invalid QR code
                        // Navigate back and show error message
                        navController.popBackStack()
                    }
                },
                onBackPressed = {
                    navController.popBackStack()
                }
            )
        }
        composable(Destinations.AUDIO_DEVICES_ROUTE) {
            AudioDeviceScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}