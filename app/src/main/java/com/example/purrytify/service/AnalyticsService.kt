package com.example.purrytify.service

import android.content.Context
import android.util.Log
import com.example.purrytify.data.repository.AnalyticsRepository
import com.example.purrytify.models.Song
import com.example.purrytify.util.MusicAnalyticsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AnalyticsService(
    private val analyticsRepository: AnalyticsRepository,
    private val context: Context
) {
    private val TAG = "AnalyticsService"
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var analyticsTracker: MusicAnalyticsTracker? = null
    private var currentUserId: Long = -1

    /**
     * Initialize analytics tracking for a user
     */
    fun initializeForUser(userId: Long) {
        if (currentUserId == userId && analyticsTracker != null) {
            Log.d(TAG, "Analytics already initialized for user $userId")
            return
        }

        // Clean up existing tracker
        analyticsTracker?.cleanup()

        // Create new tracker for this user
        analyticsTracker = MusicAnalyticsTracker(
            analyticsDao = (context.applicationContext as com.example.purrytify.PurrytifyApp).database.analyticsDao(),
            userId = userId
        )

        currentUserId = userId
        Log.d(TAG, "Analytics tracker initialized for user $userId")

        // Schedule periodic cleanup
        schedulePeriodicCleanup()
    }

    /**
     * Start tracking a song
     */
    fun startTrackingSong(song: Song) {
        analyticsTracker?.startListeningSession(song)
        Log.d(TAG, "Started tracking song: ${song.title}")
    }

    /**
     * Update tracking progress
     */
    fun updateTrackingProgress(currentPosition: Long, isPlaying: Boolean) {
        analyticsTracker?.updateListeningProgress(currentPosition, isPlaying)
    }

    /**
     * Pause tracking
     */
    fun pauseTracking() {
        analyticsTracker?.pauseListeningSession()
        Log.d(TAG, "Paused analytics tracking")
    }

    /**
     * Resume tracking
     */
    fun resumeTracking() {
        analyticsTracker?.resumeListeningSession()
        Log.d(TAG, "Resumed analytics tracking")
    }

    /**
     * End current tracking session
     */
    fun endTracking() {
        analyticsTracker?.endListeningSession()
        Log.d(TAG, "Ended analytics tracking")
    }

    /**
     * Get real-time current month listening time
     */
    fun getCurrentMonthListeningTime(): StateFlow<Long>? {
        return analyticsTracker?.currentMonthListeningTime
    }

    /**
     * Get analytics repository for UI access
     */
    fun getAnalyticsRepository(): AnalyticsRepository {
        return analyticsRepository
    }

    /**
     * Clean up analytics service
     */
    fun cleanup() {
        analyticsTracker?.cleanup()
        analyticsTracker = null
        currentUserId = -1
        Log.d(TAG, "Analytics service cleaned up")
    }

    /**
     * Handle user logout
     */
    fun handleUserLogout() {
        endTracking()
        cleanup()
        Log.d(TAG, "Analytics tracking stopped due to user logout")
    }

    /**
     * Schedule periodic cleanup of old data
     */
    private fun schedulePeriodicCleanup() {
        serviceScope.launch {
            try {
                if (currentUserId > 0) {
                    analyticsRepository.cleanupOldStreaks(currentUserId)
                    Log.d(TAG, "Performed periodic cleanup")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during periodic cleanup", e)
            }
        }
    }

    /**
     * Check if analytics is properly initialized
     */
    fun isInitialized(): Boolean {
        return analyticsTracker != null && currentUserId > 0
    }

    /**
     * Get current user ID
     */
    fun getCurrentUserId(): Long {
        return currentUserId
    }
}