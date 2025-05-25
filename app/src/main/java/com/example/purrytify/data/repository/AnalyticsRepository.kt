package com.example.purrytify.data.repository

import android.util.Log
import com.example.purrytify.data.dao.AnalyticsDao
import com.example.purrytify.data.entity.MonthlyAnalytics
import com.example.purrytify.data.entity.SongStreak
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsRepository(private val analyticsDao: AnalyticsDao) {

    private val TAG = "AnalyticsRepository"
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    suspend fun getTotalListeningTimeForMonthRaw(userId: Long, month: String): Long {
        return withContext(Dispatchers.IO) {
            analyticsDao.getTotalListeningTimeForMonth(userId, month)
        }
    }

    suspend fun getUniqueSongsCountForMonthRaw(userId: Long, month: String): Int {
        return withContext(Dispatchers.IO) {
            analyticsDao.getUniqueSongsCountForMonth(userId, month)
        }
    }

    suspend fun getUniqueArtistsCountForMonthRaw(userId: Long, month: String): Int {
        return withContext(Dispatchers.IO) {
            analyticsDao.getUniqueArtistsCountForMonth(userId, month)
        }
    }

    suspend fun insertOrUpdateMonthlyAnalyticsRaw(monthlyAnalytics: MonthlyAnalytics): Long {
        return withContext(Dispatchers.IO) {
            analyticsDao.insertOrUpdateMonthlyAnalytics(monthlyAnalytics)
        }
    }

    suspend fun getListeningSessionsByMonthRaw(userId: Long, month: String): List<com.example.purrytify.data.entity.ListeningSession> {
        return withContext(Dispatchers.IO) {
            analyticsDao.getListeningSessionsByMonth(userId, month)
        }
    }

    suspend fun getMonthlyAnalytics(userId: Long, month: String): MonthlyAnalytics? {
        return try {
            withContext(Dispatchers.IO) {
                analyticsDao.getMonthlyAnalytics(userId, month)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly analytics", e)
            null
        }
    }

    suspend fun getCurrentMonthAnalytics(userId: Long): MonthlyAnalytics? {
        val currentMonth = monthFormat.format(Date())
        return getMonthlyAnalytics(userId, currentMonth)
    }

    suspend fun getAllMonthlyAnalytics(userId: Long): List<MonthlyAnalytics> {
        return try {
            withContext(Dispatchers.IO) {
                analyticsDao.getAllMonthlyAnalytics(userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all monthly analytics", e)
            emptyList()
        }
    }

    fun getCurrentMonthListeningTimeFlow(userId: Long): Flow<Long> {
        val currentMonth = monthFormat.format(Date())
        return analyticsDao.getCurrentMonthListeningTimeFlow(userId, currentMonth)
    }

    suspend fun getTopArtists(userId: Long, month: String, limit: Int = 5): List<AnalyticsDao.ArtistStats> {
        return try {
            withContext(Dispatchers.IO) {
                analyticsDao.getTopArtistsByDuration(userId, month, limit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top artists", e)
            emptyList()
        }
    }

    suspend fun getCurrentMonthTopArtists(userId: Long, limit: Int = 5): List<AnalyticsDao.ArtistStats> {
        val currentMonth = monthFormat.format(Date())
        return getTopArtists(userId, currentMonth, limit)
    }

    suspend fun getTopSongs(userId: Long, month: String, limit: Int = 5): List<AnalyticsDao.SongStats> {
        return try {
            withContext(Dispatchers.IO) {
                analyticsDao.getTopSongsByPlayCount(userId, month, limit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top songs", e)
            emptyList()
        }
    }

    suspend fun getCurrentMonthTopSongs(userId: Long, limit: Int = 5): List<AnalyticsDao.SongStats> {
        val currentMonth = monthFormat.format(Date())
        return getTopSongs(userId, currentMonth, limit)
    }


    suspend fun getActiveStreaks(userId: Long): List<SongStreak> {
        return try {
            withContext(Dispatchers.IO) {
                analyticsDao.getActiveStreaks(userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active streaks", e)
            emptyList()
        }
    }

    suspend fun getTopStreaks(userId: Long, limit: Int = 10): List<SongStreak> {
        return try {
            withContext(Dispatchers.IO) {
                analyticsDao.getTopStreaks(userId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top streaks", e)
            emptyList()
        }
    }

    suspend fun cleanupOldStreaks(userId: Long) {
        try {
            withContext(Dispatchers.IO) {
                val cutoffTime = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L) // 30 days ago
                analyticsDao.cleanupOldStreaks(userId, cutoffTime)
                Log.d(TAG, "Cleaned up old streaks for user $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old streaks", e)
        }
    }

    fun formatListeningTime(timeInMillis: Long): String {
        val totalMinutes = timeInMillis / (1000 * 60)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }
    }

    suspend fun getAvailableMonths(userId: Long): List<String> {
        return try {
            getAllMonthlyAnalytics(userId).map { it.month }.distinct().sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available months", e)
            emptyList()
        }
    }

    suspend fun hasAnalyticsData(userId: Long): Boolean {
        return try {
            val currentMonth = monthFormat.format(Date())
            val analytics = getMonthlyAnalytics(userId, currentMonth)
            analytics?.totalListeningTime ?: 0 > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if user has analytics data", e)
            false
        }
    }

    data class MonthlyStatsSummary(
        val totalListeningTime: Long,
        val topArtist: String?,
        val topSong: String?,
        val activeStreaksCount: Int,
        val totalSongs: Int,
        val totalArtists: Int
    )

    suspend fun getCurrentMonthSummary(userId: Long): MonthlyStatsSummary {
        return try {
            val currentMonth = monthFormat.format(Date())
            Log.d(TAG, "Getting summary for user: $userId, month: $currentMonth")

            val analytics = getMonthlyAnalytics(userId, currentMonth)
            Log.d(TAG, "Monthly analytics: $analytics")

            val topArtists = getTopArtists(userId, currentMonth, 1)
            Log.d(TAG, "Top artists: $topArtists")

            val topSongs = getTopSongs(userId, currentMonth, 1)
            Log.d(TAG, "Top songs: $topSongs")

            val activeStreaks = getActiveStreaks(userId)
            Log.d(TAG, "Active streaks: $activeStreaks")

            val sessions = getListeningSessionsByMonthRaw(userId, currentMonth)
            Log.d(TAG, "Raw sessions: ${sessions.size} sessions")
            sessions.forEach { session ->
                Log.d(TAG, "  - ${session.songTitle}: ${session.durationListened}ms")
            }

            MonthlyStatsSummary(
                totalListeningTime = analytics?.totalListeningTime ?: 0L,
                topArtist = topArtists.firstOrNull()?.artistName,
                topSong = topSongs.firstOrNull()?.songTitle,
                activeStreaksCount = activeStreaks.size,
                totalSongs = analytics?.uniqueSongsCount ?: 0,
                totalArtists = analytics?.uniqueArtistsCount ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, " Error getting current month summary", e)
            MonthlyStatsSummary(0L, null, null, 0, 0, 0)
        }
    }
}