package com.example.purrytify.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.dao.AnalyticsDao
import com.example.purrytify.data.entity.SongStreak
import com.example.purrytify.data.entity.MonthlyAnalytics
import com.example.purrytify.data.repository.AnalyticsRepository
import com.example.purrytify.util.AnalyticsExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsViewModel(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val TAG = "AnalyticsViewModel"
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

    // Current selected month
    private val _selectedMonth = MutableStateFlow(monthFormat.format(Date()))
    val selectedMonth: StateFlow<String> = _selectedMonth

    // Analytics data
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _totalListeningTime = MutableStateFlow(0L)
    val totalListeningTime: StateFlow<Long> = _totalListeningTime

    private val _topArtist = MutableStateFlow<String?>(null)
    val topArtist: StateFlow<String?> = _topArtist

    private val _topSong = MutableStateFlow<String?>(null)
    val topSong: StateFlow<String?> = _topSong

    private val _topArtists = MutableStateFlow<List<AnalyticsDao.ArtistStats>>(emptyList())
    val topArtists: StateFlow<List<AnalyticsDao.ArtistStats>> = _topArtists

    private val _topSongs = MutableStateFlow<List<AnalyticsDao.SongStats>>(emptyList())
    val topSongs: StateFlow<List<AnalyticsDao.SongStats>> = _topSongs

    private val _dayStreaks = MutableStateFlow<List<SongStreak>>(emptyList())
    val dayStreaks: StateFlow<List<SongStreak>> = _dayStreaks

    private val _availableMonths = MutableStateFlow<List<String>>(emptyList())
    val availableMonths: StateFlow<List<String>> = _availableMonths

    private val _hasData = MutableStateFlow(false)
    val hasData: StateFlow<Boolean> = _hasData

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage

    private var currentUserId: Long = -1

    /**
     * Initialize with user ID
     */
    fun initializeForUser(userId: Long) {
        currentUserId = userId
        Log.d(TAG, "Analytics ViewModel initialized for user: $userId")
        loadAvailableMonths()
        loadAnalyticsForCurrentMonth()
    }

    /**
     * Load analytics for current month
     */
    fun loadAnalyticsForCurrentMonth() {
        loadAnalyticsForMonth(_selectedMonth.value)
    }

    /**
     * Load analytics for specific month
     */
    fun loadAnalyticsForMonth(month: String) {
        if (currentUserId <= 0) {
            Log.w(TAG, "No valid user ID for loading analytics")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null
                _selectedMonth.value = month

                Log.d(TAG, "Loading analytics for month: $month, user: $currentUserId")

                // Get summary from repository
                val summary = analyticsRepository.getCurrentMonthSummary(currentUserId)
                Log.d(TAG, "Summary: $summary")

                // Get detailed data
                val topArtists = analyticsRepository.getCurrentMonthTopArtists(currentUserId, 5)
                val topSongs = analyticsRepository.getCurrentMonthTopSongs(currentUserId, 5)
                val streaks = analyticsRepository.getActiveStreaks(currentUserId)

                Log.d(TAG, "Top artists: ${topArtists.size}")
                Log.d(TAG, "Top songs: ${topSongs.size}")
                Log.d(TAG, "Streaks: ${streaks.size}")

                // Update state
                _totalListeningTime.value = summary.totalListeningTime
                _topArtist.value = summary.topArtist
                _topSong.value = summary.topSong
                _topArtists.value = topArtists
                _topSongs.value = topSongs
                _dayStreaks.value = streaks.filter { it.currentStreak >= 2 }

                _hasData.value = summary.totalListeningTime > 0 || topArtists.isNotEmpty() || topSongs.isNotEmpty()

                Log.d(TAG, "Analytics loaded successfully for $month - hasData: ${_hasData.value}")

            } catch (e: Exception) {
                Log.e(TAG, "Error loading analytics for month $month", e)
                _errorMessage.value = "Failed to load analytics: ${e.message}"
                _hasData.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load available months
     */
    private fun loadAvailableMonths() {
        if (currentUserId <= 0) return

        viewModelScope.launch {
            try {
                val months = analyticsRepository.getAvailableMonths(currentUserId)
                _availableMonths.value = months
                Log.d(TAG, "Available months loaded: ${months.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading available months", e)
                _availableMonths.value = listOf(_selectedMonth.value) // At least show current month
            }
        }
    }

    /**
     * Force update analytics - for debugging
     */
    fun forceUpdateAnalytics() {
        if (currentUserId <= 0) return

        viewModelScope.launch {
            try {
                Log.d(TAG, "Force updating analytics...")

                val month = monthFormat.format(Date())
                val analyticsId = "analytics_${currentUserId}_$month"

                val totalTime = analyticsRepository.getTotalListeningTimeForMonthRaw(currentUserId, month)
                val uniqueSongs = analyticsRepository.getUniqueSongsCountForMonthRaw(currentUserId, month)
                val uniqueArtists = analyticsRepository.getUniqueArtistsCountForMonthRaw(currentUserId, month)

                Log.d(TAG, "Raw stats - Time: ${totalTime}ms, Songs: $uniqueSongs, Artists: $uniqueArtists")

                if (totalTime > 0) {
                    val monthlyAnalytics = MonthlyAnalytics(
                        id = analyticsId,
                        month = month,
                        userId = currentUserId,
                        totalListeningTime = totalTime,
                        totalSongsPlayed = 0,
                        uniqueSongsCount = uniqueSongs,
                        uniqueArtistsCount = uniqueArtists,
                        lastUpdated = System.currentTimeMillis()
                    )


                    analyticsRepository.insertOrUpdateMonthlyAnalyticsRaw(monthlyAnalytics)
                    Log.d(TAG, "Force updated monthly analytics")

                    // Reload analytics
                    loadAnalyticsForCurrentMonth()
                } else {
                    Log.w(TAG, "No listening time found, cannot update analytics")

                    // Check raw sessions for debugging
                    val sessions = analyticsRepository.getListeningSessionsByMonthRaw(currentUserId, month)
                    Log.d(TAG, "Found ${sessions.size} raw sessions")
                    sessions.forEach { session ->
                        Log.d(TAG, "  - Session: ${session.songTitle}, Duration: ${session.durationListened}ms")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error force updating analytics", e)
            }
        }
    }

    /**
     * Refresh current analytics data
     */
    fun refreshAnalytics() {
        loadAnalyticsForMonth(_selectedMonth.value)
    }

    /**
     * Select different month
     */
    fun selectMonth(month: String) {
        if (month != _selectedMonth.value) {
            loadAnalyticsForMonth(month)
        }
    }

    /**
     * Format listening time for display
     */
    fun formatListeningTime(timeInMillis: Long): String {
        return analyticsRepository.formatListeningTime(timeInMillis)
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Get month name for display
     */
    fun getMonthDisplayName(month: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(month)
            val displayFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            date?.let { displayFormat.format(it) } ?: month
        } catch (e: Exception) {
            month
        }
    }

    fun exportAnalyticsCSV(context: Context, month: String = _selectedMonth.value) {
        if (currentUserId <= 0) {
            Log.w(TAG, "No valid user ID for exporting analytics")
            return
        }

        viewModelScope.launch {
            try {
                _isExporting.value = true
                _exportMessage.value = null

                Log.d(TAG, "Exporting analytics for user: $currentUserId, month: $month")

                val exporter = AnalyticsExporter(context, analyticsRepository)
                val uri = exporter.exportToCSV(currentUserId, month)

                if (uri != null) {
                    _exportMessage.value = "Analytics saved to Downloads folder!"
                    Log.d(TAG, "Analytics exported to Downloads")
                } else {
                    _exportMessage.value = "Failed to export analytics"
                    Log.e(TAG, "Export failed - no URI returned")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error exporting analytics", e)
                _exportMessage.value = "Error exporting analytics: ${e.message}"
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun shareAnalytics(context: Context, month: String = _selectedMonth.value) {
        if (currentUserId <= 0) {
            Log.w(TAG, "No valid user ID for sharing analytics")
            return
        }

        viewModelScope.launch {
            try {
                _isExporting.value = true


                Log.d(TAG, "Sharing analytics for user: $currentUserId, month: $month")

                val exporter = AnalyticsExporter(context, analyticsRepository)
                exporter.shareAnalytics(currentUserId, month)

                Log.d(TAG, "Share dialog launched")

            } catch (e: Exception) {
                Log.e(TAG, "Error sharing analytics", e)
                _exportMessage.value = "Error sharing analytics: ${e.message}"
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * Export current month analytics
     */
    fun exportCurrentMonth(context: Context) {
        exportAnalyticsCSV(context, monthFormat.format(Date()))
    }

    /**
     * Share current month analytics
     */
    fun shareCurrentMonth(context: Context) {
        shareAnalytics(context, monthFormat.format(Date()))
    }

    /**
     * Clear export message
     */
    fun clearExportMessage() {
        _exportMessage.value = null
    }

}