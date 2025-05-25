package com.example.purrytify

import android.app.Application
import android.os.StrictMode
import android.util.Log
import com.android.volley.BuildConfig
import com.example.purrytify.data.database.AppDatabase
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.data.repository.AnalyticsRepository
import com.example.purrytify.service.AnalyticsService
import com.example.purrytify.network.RetrofitClient
import com.example.purrytify.util.NetworkConnectionObserver
import com.example.purrytify.util.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PurrytifyApp : Application() {
    // Create app-level coroutine scope
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lightweight components, initialize immediately
    lateinit var tokenManager: TokenManager
    lateinit var networkConnectionObserver: NetworkConnectionObserver

    // Heavy components, initialize lazily
    val database by lazy {
        Log.d("PurrytifyApp", "Initializing Room Database")
        AppDatabase.getDatabase(this)
    }

    val songRepository by lazy {
        SongRepository(database.songDao())
    }

    // ADD: Analytics components
    val analyticsRepository by lazy {
        Log.d("PurrytifyApp", "Creating AnalyticsRepository")
        AnalyticsRepository(database.analyticsDao())
    }

    val analyticsService by lazy {
        Log.d("PurrytifyApp", "Creating AnalyticsService")
        AnalyticsService(analyticsRepository, this)
    }

    override fun onCreate() {
        super.onCreate()

        // Enable strict mode in debug builds
        if (BuildConfig.DEBUG) {
            enableStrictMode()
        }

        // Initialize lightweight components immediately
        tokenManager = TokenManager(this)
        networkConnectionObserver = NetworkConnectionObserver(this)

        // Initialize RetrofitClient (lightweight)
        RetrofitClient.initialize(this)

        // Start network observer on a background thread
        applicationScope.launch(Dispatchers.IO) {
            networkConnectionObserver.start()
        }

        // Preload database in background for faster access later
        applicationScope.launch(Dispatchers.IO) {
            try {
                // Just access the database to trigger initialization
                val db = database
                Log.d("PurrytifyApp", "Database pre-warmed")
            } catch (e: Exception) {
                Log.e("PurrytifyApp", "Error pre-warming database: ${e.message}")
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        networkConnectionObserver.stop()
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }
}