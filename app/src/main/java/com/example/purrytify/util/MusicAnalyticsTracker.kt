package com.example.purrytify.util

import android.util.Log
import com.example.purrytify.data.dao.AnalyticsDao
import com.example.purrytify.data.entity.ListeningSession
import com.example.purrytify.data.entity.MonthlyAnalytics
import com.example.purrytify.data.entity.SongStreak
import com.example.purrytify.models.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

class MusicAnalyticsTracker(
    private val analyticsDao: AnalyticsDao,
    private val userId: Long
) {
    private val TAG = "MusicAnalyticsTracker"
    private val trackerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Current listening session
    private var currentSessionId: Long? = null
    private var sessionStartTime: Long = 0
    private var lastUpdateTime: Long = 0
    private var accumulatedListeningTime: Long = 0

    // Real-time stats
    private val _currentMonthListeningTime = MutableStateFlow(0L)
    val currentMonthListeningTime: StateFlow<Long> = _currentMonthListeningTime

    // Date formatters
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    init {
        // Load current month listening time
        trackerScope.launch {
            updateCurrentMonthListeningTime()
        }
    }

    /**
     * Start tracking a new listening session
     */
    fun startListeningSession(song: Song) {
        trackerScope.launch {
            try {
                Log.d(TAG, "Starting listening session for: ${song.title}")

                val currentTime = System.currentTimeMillis()
                val date = dateFormat.format(Date(currentTime))
                val month = monthFormat.format(Date(currentTime))

                val session = ListeningSession(
                    songId = song.id,
                    songTitle = song.title,
                    artistName = song.artist,
                    startTime = currentTime,
                    endTime = null,
                    durationListened = 0,
                    totalDuration = song.duration,
                    date = date,
                    month = month,
                    userId = userId,
                    isOnline = song.isOnline,
                    onlineId = song.onlineId
                )

                currentSessionId = analyticsDao.insertListeningSession(session)
                sessionStartTime = currentTime
                lastUpdateTime = currentTime
                accumulatedListeningTime = 0

                Log.d(TAG, "Created listening session with ID: $currentSessionId")

                // Update streak for this song
                updateSongStreak(song, date)

            } catch (e: Exception) {
                Log.e(TAG, "Error starting listening session", e)
            }
        }
    }

    /**
     * Update current listening session with playback progress
     */
    fun updateListeningProgress(currentPosition: Long, isPlaying: Boolean) {
        if (currentSessionId == null || !isPlaying) return

        trackerScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastUpdateTime

                // Only count time if less than 5 seconds have passed (prevents big jumps from seek)
                if (timeSinceLastUpdate < 5000 && timeSinceLastUpdate > 0) {
                    accumulatedListeningTime += timeSinceLastUpdate

                    // Update session every 10 seconds or when significant progress is made
                    if (timeSinceLastUpdate >= 10000 || currentPosition % 30000 < 1000) {
                        currentSessionId?.let { sessionId ->
                            analyticsDao.updateSessionEndTime(
                                sessionId = sessionId,
                                endTime = currentTime,
                                durationListened = accumulatedListeningTime
                            )

                            // Update real-time stats
                            updateCurrentMonthListeningTime()
                        }
                    }
                }

                lastUpdateTime = currentTime

            } catch (e: Exception) {
                Log.e(TAG, "Error updating listening progress", e)
            }
        }
    }

    /**
     * End current listening session
     */
    fun endListeningSession() {
        trackerScope.launch {
            try {
                currentSessionId?.let { sessionId ->
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastUpdate = currentTime - lastUpdateTime

                    // Add any remaining time
                    if (timeSinceLastUpdate < 5000 && timeSinceLastUpdate > 0) {
                        accumulatedListeningTime += timeSinceLastUpdate
                    }

                    analyticsDao.updateSessionEndTime(
                        sessionId = sessionId,
                        endTime = currentTime,
                        durationListened = accumulatedListeningTime
                    )

                    Log.d(TAG, "Ended listening session $sessionId with ${accumulatedListeningTime}ms listened")

                    // Update monthly analytics
                    updateMonthlyAnalytics()

                    // Reset session data
                    currentSessionId = null
                    sessionStartTime = 0
                    lastUpdateTime = 0
                    accumulatedListeningTime = 0

                    // Update real-time stats
                    updateCurrentMonthListeningTime()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error ending listening session", e)
            }
        }
    }

    /**
     * Pause listening session tracking
     */
    fun pauseListeningSession() {
        if (currentSessionId == null) return

        trackerScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastUpdate = currentTime - lastUpdateTime

                if (timeSinceLastUpdate < 5000 && timeSinceLastUpdate > 0) {
                    accumulatedListeningTime += timeSinceLastUpdate

                    currentSessionId?.let { sessionId ->
                        analyticsDao.updateSessionEndTime(
                            sessionId = sessionId,
                            endTime = currentTime,
                            durationListened = accumulatedListeningTime
                        )
                    }
                }

                lastUpdateTime = currentTime
                Log.d(TAG, "Paused listening session tracking")

            } catch (e: Exception) {
                Log.e(TAG, "Error pausing listening session", e)
            }
        }
    }

    /**
     * Resume listening session tracking
     */
    fun resumeListeningSession() {
        if (currentSessionId == null) return

        lastUpdateTime = System.currentTimeMillis()
        Log.d(TAG, "Resumed listening session tracking")
    }

    /**
     * Update song streak tracking
     */
    private suspend fun updateSongStreak(song: Song, currentDate: String) {
        try {
            // Create unique streak ID
            val streakId = if (song.isOnline && song.onlineId != null) {
                "streak_${userId}_online_${song.onlineId}"
            } else {
                "streak_${userId}_local_${song.id}"
            }

            val existingStreak = analyticsDao.getSongStreakById(streakId)

            if (existingStreak == null) {
                // Create new streak
                val newStreak = SongStreak(
                    id = streakId,
                    songId = song.id,
                    songTitle = song.title,
                    artistName = song.artist,
                    currentStreak = 1,
                    lastPlayedDate = currentDate,
                    userId = userId,
                    isOnline = song.isOnline,
                    onlineId = song.onlineId
                )
                analyticsDao.insertOrUpdateSongStreak(newStreak)
                Log.d(TAG, "Created new streak for: ${song.title}")

            } else {
                // Update existing streak
                val lastDate = existingStreak.lastPlayedDate
                val daysDifference = calculateDaysDifference(lastDate, currentDate)

                val updatedStreak = when {
                    daysDifference == 1 -> {
                        // Consecutive day - increase streak
                        existingStreak.copy(
                            currentStreak = existingStreak.currentStreak + 1,
                            lastPlayedDate = currentDate,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    daysDifference == 0 -> {
                        // Same day - just update timestamp
                        existingStreak.copy(
                            lastPlayedDate = currentDate,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    else -> {
                        // Gap in days - reset streak
                        existingStreak.copy(
                            currentStreak = 1,
                            lastPlayedDate = currentDate,
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                }

                analyticsDao.insertOrUpdateSongStreak(updatedStreak)
                Log.d(TAG, "Updated streak for ${song.title}: ${updatedStreak.currentStreak} days")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating song streak", e)
        }
    }

    /**
     * Update monthly analytics aggregation
     */
    private suspend fun updateMonthlyAnalytics() {
        try {
            val currentTime = System.currentTimeMillis()
            val month = monthFormat.format(Date(currentTime))
            val analyticsId = "analytics_${userId}_$month"

            // Calculate monthly stats
            val totalListeningTime = analyticsDao.getTotalListeningTimeForMonth(userId, month)
            val uniqueSongsCount = analyticsDao.getUniqueSongsCountForMonth(userId, month)
            val uniqueArtistsCount = analyticsDao.getUniqueArtistsCountForMonth(userId, month)

            val monthlyAnalytics = MonthlyAnalytics(
                id = analyticsId,
                month = month,
                userId = userId,
                totalListeningTime = totalListeningTime,
                totalSongsPlayed = 0, // Will be calculated separately if needed
                uniqueSongsCount = uniqueSongsCount,
                uniqueArtistsCount = uniqueArtistsCount,
                lastUpdated = currentTime
            )

            analyticsDao.insertOrUpdateMonthlyAnalytics(monthlyAnalytics)
            Log.d(TAG, "Updated monthly analytics for $month")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating monthly analytics", e)
        }
    }

    /**
     * Update real-time current month listening time
     */
    private suspend fun updateCurrentMonthListeningTime() {
        try {
            val month = monthFormat.format(Date(System.currentTimeMillis()))
            val totalTime = analyticsDao.getTotalListeningTimeForMonth(userId, month)
            _currentMonthListeningTime.value = totalTime
        } catch (e: Exception) {
            Log.e(TAG, "Error updating current month listening time", e)
        }
    }

    /**
     * Calculate difference in days between two date strings
     */
    private fun calculateDaysDifference(date1: String, date2: String): Int {
        return try {
            val d1 = dateFormat.parse(date1)
            val d2 = dateFormat.parse(date2)
            if (d1 != null && d2 != null) {
                val diffInMillis = d2.time - d1.time
                (diffInMillis / (24 * 60 * 60 * 1000)).toInt()
            } else {
                Int.MAX_VALUE // Force reset if dates are invalid
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating date difference", e)
            Int.MAX_VALUE
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        trackerScope.cancel()
        runBlocking {
            try {
                // Force end current session before cleanup
                currentSessionId?.let { sessionId ->
                    val currentTime = System.currentTimeMillis()
                    val timeSinceLastUpdate = currentTime - lastUpdateTime

                    if (timeSinceLastUpdate < 5000 && timeSinceLastUpdate > 0) {
                        accumulatedListeningTime += timeSinceLastUpdate
                    }

                    analyticsDao.updateSessionEndTime(
                        sessionId = sessionId,
                        endTime = currentTime,
                        durationListened = accumulatedListeningTime
                    )

                    Log.d(TAG, "Force ended session during cleanup")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
    }

    /**
     * Set user ID for tracking (useful when user logs in/out)
     */
    fun updateUserId(newUserId: Long) {
        // End current session if any
        runBlocking {
            try {
                endListeningSession()
            } catch (e: Exception) {
                Log.e(TAG, "Error ending session during user ID update", e)
            }
        }

        // Update user ID would require creating new tracker instance

        Log.d(TAG, "User ID updated from $userId to $newUserId")
    }
}