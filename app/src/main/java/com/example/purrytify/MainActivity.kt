package com.example.purrytify

import android.content.BroadcastReceiver
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.compose.rememberNavController
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.receivers.SongCompletionReceiver
import com.example.purrytify.service.TokenRefreshService
import com.example.purrytify.ui.components.BottomNavbar
import com.example.purrytify.ui.components.LoadingScreen
import com.example.purrytify.ui.components.MiniPlayer
import com.example.purrytify.ui.components.NoInternetConnection
import com.example.purrytify.ui.navigation.AppNavigation
import com.example.purrytify.ui.screens.LoginScreen
import com.example.purrytify.ui.screens.PlayerScreen
import com.example.purrytify.ui.theme.PurrytifyTheme
import com.example.purrytify.util.EventBus
import com.example.purrytify.util.NetworkConnectionObserver
import com.example.purrytify.util.TokenManager
import com.example.purrytify.viewmodels.MainViewModel
import com.example.purrytify.viewmodels.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.purrytify.util.SongDownloadManager
import com.example.purrytify.util.NotificationPermissionHandler

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var tokenManager: TokenManager
    private lateinit var networkConnectionObserver: NetworkConnectionObserver
    private lateinit var mainViewModel: MainViewModel
    private lateinit var songRepository: SongRepository
    private val isLoggedIn = mutableStateOf(false)
    private val isNetworkAvailable = mutableStateOf(true)
    private val isInitialized = mutableStateOf(false)
    private lateinit var songCompletionReceiver: SongCompletionReceiver
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private lateinit var downloadManager: SongDownloadManager

    // TAMBAHAN: Media Button Action Receiver
    private lateinit var mediaButtonActionReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIncomingIntent(intent)
        // Inisialisasi NetworkConnectionObserver
        networkConnectionObserver = NetworkConnectionObserver(applicationContext)
        networkConnectionObserver.start()

        tokenManager = (application as PurrytifyApp).tokenManager

        // Initialize SongDownloadManager
        downloadManager = SongDownloadManager(this)

        // Request notification permission for Android 13+
        NotificationPermissionHandler.requestNotificationPermission(this)

        // Lightweight initialization first
        lifecycleScope.launch(Dispatchers.Default) {
            initializeApp()
        }

        setContent {
            PurrytifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Show loading screen while initializing
                    if (!isInitialized.value) {
                        LoadingScreen()
                        return@Surface
                    }

                    val networkStatus by networkConnectionObserver.isConnected.collectAsState()

                    if (isLoggedIn.value) {
                        initializeUserData()

                        val navController = rememberNavController()

                        val currentSong by mainViewModel.currentSong.collectAsState()
                        val isPlaying by mainViewModel.isPlaying.collectAsState()

                        // State to track if the full player is showing
                        var showPlayerScreen by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Main scaffold with navigation bar and mini player
                            Scaffold(
                                bottomBar = {
                                    AnimatedVisibility(
                                        visible = !showPlayerScreen,
                                        enter = fadeIn() + slideInVertically { it },
                                        exit = fadeOut() + slideOutVertically { it }
                                    ) {
                                        Column {
                                            if (currentSong != null) {
                                                MiniPlayer(
                                                    currentSong = currentSong,
                                                    isPlaying = isPlaying,
                                                    onPlayPauseClick = { mainViewModel.togglePlayPause() },
                                                    onPlayerClick = { showPlayerScreen = true },
                                                    onAudioDeviceClick = {
                                                        navController.navigate("audio_devices")
                                                    }
                                                )
                                            }

                                            BottomNavbar(navController = navController)
                                        }
                                    }
                                },
                                topBar = {
                                    NoInternetConnection(isVisible = !networkStatus)
                                }
                            ) { paddingValues ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(if (!showPlayerScreen) paddingValues else PaddingValues(0.dp))
                                ) {
                                    AppNavigation(
                                        navController = navController,
                                        networkConnectionObserver = networkConnectionObserver,
                                        mainViewModel = mainViewModel // Tambahkan parameter ini
                                    )
                                }
                            }

                            // Show the full player screen if needed
                            AnimatedVisibility(
                                visible = showPlayerScreen,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                PlayerScreen(
                                    onDismiss = { showPlayerScreen = false },
                                    navController = navController,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        LoginScreen(
                            onLoginSuccess = {
                                isLoggedIn.value = true
                                // Only start token service after successful login
                                lifecycleScope.launch(Dispatchers.IO) {
                                    startTokenRefreshService()
                                }

                                // Check if the current song belongs to the new user
                                checkCurrentSongAfterLogin()

                                // Check network status after login
                                networkConnectionObserver.checkAndUpdateConnectionStatus()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        // Handle deep link for online song
        val playOnlineSongId = intent.getIntExtra("PLAY_ONLINE_SONG_ID", -1)
        val showPlayer = intent.getBooleanExtra("SHOW_PLAYER", false)

        if (playOnlineSongId != -1) {
            val title = intent.getStringExtra("ONLINE_SONG_TITLE") ?: ""
            val artist = intent.getStringExtra("ONLINE_SONG_ARTIST") ?: ""
            val audioUrl = intent.getStringExtra("ONLINE_SONG_URL") ?: ""
            val artworkUrl = intent.getStringExtra("ONLINE_SONG_ARTWORK") ?: ""

            // Play the online song after initialization
            lifecycleScope.launch {
                // Wait for initialization to complete
                while (!isInitialized.value) {
                    kotlinx.coroutines.delay(100)
                }

                if (tokenManager.isLoggedIn()) {
                    val onlineSong = com.example.purrytify.models.OnlineSong(
                        id = playOnlineSongId,
                        title = title,
                        artist = artist,
                        artworkUrl = artworkUrl,
                        audioUrl = audioUrl,
                        durationString = "0:00", // Default duration, will be updated when playing
                        country = "",
                        rank = 0,
                        createdAt = "",
                        updatedAt = ""
                    )

                    mainViewModel.playOnlineSong(onlineSong)

                    if (showPlayer) {
                        // Set a flag to show player screen
                        // This would need to be implemented in the UI layer
                    }
                }
            }
        }

        // Handle pending song ID after login
        val pendingSongId = intent.getIntExtra("PENDING_SONG_ID", -1)
        if (pendingSongId != -1 && tokenManager.isLoggedIn()) {
            // Fetch and play the pending song
            lifecycleScope.launch {
                try {
                    val response = com.example.purrytify.network.RetrofitClient.apiService.getSongById(pendingSongId)
                    if (response.isSuccessful) {
                        response.body()?.let { onlineSong ->
                            mainViewModel.playOnlineSong(onlineSong)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching pending song", e)
                }
            }
        }
    }

    private suspend fun initializeApp() {
        try {
            Log.d(TAG, "Starting app initialization")

            // Initialize lightweight components first
            withContext(Dispatchers.Main) {
                try {
                    tokenManager = (application as PurrytifyApp).tokenManager
                    songRepository = (application as PurrytifyApp).songRepository
                    networkConnectionObserver = (application as PurrytifyApp).networkConnectionObserver
                    localBroadcastManager = LocalBroadcastManager.getInstance(this@MainActivity)

                    // Check login status (simple operation)
                    isLoggedIn.value = tokenManager.isLoggedIn()
                    Log.d(TAG, "Basic initialization completed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error in basic initialization", e)
                    throw e
                }
            }

            // Initialize network observer (non-blocking)
            try {
                networkConnectionObserver.start()
                Log.d(TAG, "Network observer started")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting network observer", e)
            }

            // Initialize MainViewModel (potentially heavy)
            withContext(Dispatchers.Main) {
                try {
                    mainViewModel = ViewModelProvider(
                        this@MainActivity,
                        ViewModelFactory.getInstance(applicationContext)
                    )[MainViewModel::class.java]
                    Log.d(TAG, "MainViewModel initialized")

                    // Bind media player service
                    mainViewModel.bindService(this@MainActivity)
                    Log.d(TAG, "Media player service bound")
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing MainViewModel", e)
                    throw e
                }
            }

            // Initialize broadcast receivers - FIXED FOR ANDROID 14+
            withContext(Dispatchers.Main) {
                try {
                    songCompletionReceiver = SongCompletionReceiver(mainViewModel)
                    localBroadcastManager.registerReceiver(
                        songCompletionReceiver,
                        IntentFilter("com.example.purrytify.SONG_COMPLETED")
                    )
                    Log.d(TAG, "Song completion receiver registered")

                    // Register media button action receiver - LOCAL BROADCAST ONLY
                    mediaButtonActionReceiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            try {
                                if (intent?.action == "com.example.purrytify.MEDIA_BUTTON_ACTION") {
                                    val action = intent.getStringExtra("action")
                                    Log.d(TAG, "Received media button action: $action")

                                    when (action) {
                                        "NEXT" -> mainViewModel.playNext()
                                        "PREVIOUS" -> mainViewModel.playPrevious()
                                        "STOP" -> mainViewModel.stopPlayback()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error handling media button action", e)
                            }
                        }
                    }

                    // Use LocalBroadcastManager only (no exported/not exported needed)
                    localBroadcastManager.registerReceiver(
                        mediaButtonActionReceiver,
                        IntentFilter("com.example.purrytify.MEDIA_BUTTON_ACTION")
                    )
                    Log.d(TAG, "Media button receiver registered")
                } catch (e: Exception) {
                    Log.e(TAG, "Error registering receivers", e)
                    throw e
                }
            }

            // Setup EventBus listeners
            try {
                setupEventBusListeners()
                Log.d(TAG, "EventBus listeners setup")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up EventBus listeners", e)
            }

            // Only start token service if user is logged in
            if (isLoggedIn.value) {
                try {
                    startTokenRefreshService()
                    Log.d(TAG, "Token refresh service started")
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting token service", e)
                }
            }

            // Mark initialization as complete
            isInitialized.value = true
            Log.d(TAG, "App initialization completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Critical error during initialization: ${e.message}", e)
            // Even on error, we need to mark as initialized to show login screen
            isInitialized.value = true
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupEventBusListeners() {
        // Event Bus for token events
        lifecycleScope.launch {
            EventBus.tokenEvents.collectLatest { event ->
                when (event) {
                    is EventBus.TokenEvent.TokenRefreshFailed -> {
                        // Show toast message and log out
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Session expired. Please log in again.",
                                Toast.LENGTH_LONG
                            ).show()
                            logout()
                        }
                    }
                }
            }
        }

        // Event Bus for network events
        lifecycleScope.launch {
            EventBus.networkEvents.collectLatest { event ->
                when (event) {
                    is EventBus.NetworkEvent.Connected -> {
                        Log.d(TAG, "Network connected")
                        isNetworkAvailable.value = true
                        networkConnectionObserver.checkAndUpdateConnectionStatus()
                    }
                    is EventBus.NetworkEvent.Disconnected -> {
                        Log.d(TAG, "Network disconnected")
                        isNetworkAvailable.value = false
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check is service running and start if not, but only if initialized and logged in
        if (isInitialized.value && tokenManager.isLoggedIn()) {
            lifecycleScope.launch(Dispatchers.IO) {
                startTokenRefreshService()
            }
        }
    }

    // TAMBAHAN: Handle permission result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        NotificationPermissionHandler.handlePermissionResult(
            requestCode, permissions, grantResults,
            onGranted = {
                Log.d(TAG, "Notification permission granted")
            },
            onDenied = {
                Toast.makeText(this, "Notification permission required for media controls", Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the broadcast receiver using LocalBroadcastManager
        try {
            localBroadcastManager.unregisterReceiver(songCompletionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering song completion receiver: ${e.message}")
        }

        // TAMBAHAN: Unregister media button action receiver
        try {
            localBroadcastManager.unregisterReceiver(mediaButtonActionReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering media button receiver: ${e.message}")
        }

        networkConnectionObserver.stop()
        stopTokenRefreshService()
        mainViewModel.unbindService(this)

        // Release download manager resources
        if (::downloadManager.isInitialized) {
            downloadManager.release()
        }
    }

    private fun startTokenRefreshService() {
        val serviceIntent = Intent(this, TokenRefreshService::class.java)
        startService(serviceIntent)
    }

    private fun stopTokenRefreshService() {
        val serviceIntent = Intent(this, TokenRefreshService::class.java)
        stopService(serviceIntent)
    }

    fun logout() {
        stopTokenRefreshService()
        tokenManager.deleteTokens()
        songRepository.setCurrentUserId(-1)
        mainViewModel.handleLogout()

        isLoggedIn.value = false

        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }

    private fun initializeUserData() {
        val userId = tokenManager.getUserId()
        if (userId > 0) {
            Log.d(TAG, "Initializing with user ID: $userId")
            songRepository.setCurrentUserId(userId)
        }
    }

    private fun checkCurrentSongAfterLogin() {
        val currentSong = mainViewModel.currentSong.value

        if (currentSong != null) {
            lifecycleScope.launch {
                try {
                    val songExists = songRepository.getSongByIdDirect(currentSong.id) != null

                    if (!songExists) {
                        Log.d(TAG, "Current song does not belong to the new user, stopping playback")
                        mainViewModel.handleLogout()
                    } else {
                        Log.d(TAG, "Current song belongs to the new user, continuing playback")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking current song after login: ${e.message}")
                    mainViewModel.handleLogout()
                }
            }
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )

            if (permissions.any {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }) {
                ActivityCompat.requestPermissions(this, permissions, BLUETOOTH_PERMISSION_REQUEST_CODE)
            }
        }
    }

    companion object {
        private const val BLUETOOTH_PERMISSION_REQUEST_CODE = 1001
    }

    // Handle permission result:
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Permissions granted, refresh devices
                    Log.d(TAG, "Bluetooth permissions granted")
                } else {
                    // Show message that Bluetooth devices won't be available
                    Toast.makeText(
                        this,
                        "Bluetooth permission required to see wireless audio devices",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}