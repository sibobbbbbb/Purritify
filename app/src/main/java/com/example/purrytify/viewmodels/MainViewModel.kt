package com.example.purrytify.viewmodels

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.entity.Song as EntitySong
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.models.Song
import com.example.purrytify.service.MediaPlayerService
import com.example.purrytify.util.SongMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.example.purrytify.data.repository.AnalyticsRepository
import com.example.purrytify.service.AnalyticsService
import com.example.purrytify.util.TokenManager


class MainViewModel(private val songRepository: SongRepository, private val analyticsRepository: AnalyticsRepository, private val tokenManager: TokenManager) : ViewModel() {
    private val TAG = "MainViewModel"
    private var mediaPlayerService: MediaPlayerService? = null
    private var bound = false

    // Currently playing song
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration

    // Queue for songs (bonus feature)
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue

    // All songs from repository (for navigation when no queue is set)
    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())

    // Current index in the queue (for next/previous navigation)
    private val _currentQueueIndex = MutableStateFlow(-1)

    private val playedSongIds = mutableSetOf<String>()

    // Keep track of previously played songs (for previous function)
    private val _playHistory = MutableStateFlow<List<Song>>(emptyList())

    // Repeat mode (bonus feature) - 0: Off, 1: Repeat All, 2: Repeat One
    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode

    // Shuffle mode
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled

    // Analytics-related state flows
    private val _currentMonthListeningTime = MutableStateFlow(0L)
    val currentMonthListeningTime: StateFlow<Long> = _currentMonthListeningTime

    private val _monthlyAnalytics = MutableStateFlow<AnalyticsRepository.MonthlyStatsSummary?>(null)
    val monthlyAnalytics: StateFlow<AnalyticsRepository.MonthlyStatsSummary?> = _monthlyAnalytics

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as MediaPlayerService.MediaPlayerBinder
            mediaPlayerService = binder.getService()
            bound = true

            initializeAnalyticsTracking()

            // Collect data from service
            viewModelScope.launch {
                mediaPlayerService?.currentSong?.collect { song ->
                    Log.d(TAG, "Current song updated: ${song?.title}")
                    _currentSong.value = song
                    song?.let { ensureCurrentSongInQueue(it) }
                }
            }

            viewModelScope.launch {
                mediaPlayerService?.isPlaying?.collect { playing ->
                    Log.d(TAG, "Playing state updated: $playing")
                    _isPlaying.value = playing
                }
            }

            viewModelScope.launch {
                mediaPlayerService?.currentPosition?.collect { position ->
                    _currentPosition.value = position
                }
            }

            viewModelScope.launch {
                mediaPlayerService?.duration?.collect { duration ->
                    Log.d(TAG, "Duration updated: $duration ms")
                    _duration.value = duration
                }
            }

            viewModelScope.launch {
                mediaPlayerService?.reachedEndOfPlayback?.collect { reachedEnd ->
                    if (reachedEnd) {
                        Log.d(TAG, "End of playback reached")
                        onSongCompleted(true)
                    }
                }
            }

            viewModelScope.launch {
                mediaPlayerService?.currentSong?.collect { serviceSong ->
                    Log.d(TAG, "Received song from service: ${serviceSong?.title}")

                    if (serviceSong != null) {
                        val currentViewModelSong = _currentSong.value

                        val finalSong = if (currentViewModelSong?.isOnline == true) {
                            serviceSong.copy(
                                isOnline = true,
                                onlineId = currentViewModelSong.onlineId
                            )
                        } else {
                            serviceSong
                        }

                        Log.d(TAG, "Final song - isOnline: ${finalSong.isOnline}, onlineId: ${finalSong.onlineId}")
                        _currentSong.value = finalSong
                    }
                }
            }

            viewModelScope.launch {
                mediaPlayerService?.getAnalyticsService()?.getCurrentMonthListeningTime()?.collect { time ->
                    _currentMonthListeningTime.value = time
                }
            }

            // If we already have a song to play when service connects, play it
            _currentSong.value?.let { song ->
                if (_isPlaying.value) {
                    mediaPlayerService?.playSong(song)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            mediaPlayerService = null
            bound = false
        }
    }

    private fun initializeAnalyticsTracking() {
        viewModelScope.launch {
            try {
                // Get current user ID from token manager
                val userId = getCurrentUserId()
                if (userId > 0) {
                    mediaPlayerService?.initializeAnalyticsForUser(userId)

                    // Load initial analytics data
                    loadCurrentMonthAnalytics(userId)

                    Log.d(TAG, "Analytics tracking initialized for user: $userId")
                } else {
                    Log.w(TAG, "No valid user ID for analytics initialization")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing analytics tracking", e)
            }
        }
    }

    private fun getCurrentUserId(): Long {
        return try {
            // Get user ID from TokenManager
            tokenManager.getUserId().toLong()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user ID", e)
            -1L
        }
    }

    // Load current month analytics
    private fun loadCurrentMonthAnalytics(userId: Long) {
        viewModelScope.launch {
            try {
                val summary = analyticsRepository.getCurrentMonthSummary(userId)
                _monthlyAnalytics.value = summary
                Log.d(TAG, "Loaded monthly analytics summary")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading monthly analytics", e)
            }
        }
    }

    // Analytics-related methods for UI

    /**
     * Get top artists for current month
     */
    suspend fun getCurrentMonthTopArtists(limit: Int = 5): List<com.example.purrytify.data.dao.AnalyticsDao.ArtistStats> {
        return try {
            val userId = getCurrentUserId()
            if (userId > 0) {
                analyticsRepository.getCurrentMonthTopArtists(userId, limit)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top artists", e)
            emptyList()
        }
    }

    /**
     * Get top songs for current month
     */
    suspend fun getCurrentMonthTopSongs(limit: Int = 5): List<com.example.purrytify.data.dao.AnalyticsDao.SongStats> {
        return try {
            val userId = getCurrentUserId()
            if (userId > 0) {
                analyticsRepository.getCurrentMonthTopSongs(userId, limit)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top songs", e)
            emptyList()
        }
    }

    /**
     * Get active song streaks
     */
    suspend fun getActiveStreaks(): List<com.example.purrytify.data.entity.SongStreak> {
        return try {
            val userId = getCurrentUserId()
            if (userId > 0) {
                analyticsRepository.getActiveStreaks(userId)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active streaks", e)
            emptyList()
        }
    }

    /**
     * Get available months with analytics data
     */
    suspend fun getAvailableAnalyticsMonths(): List<String> {
        return try {
            val userId = getCurrentUserId()
            if (userId > 0) {
                analyticsRepository.getAvailableMonths(userId)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available months", e)
            emptyList()
        }
    }

    /**
     * Get analytics for a specific month
     */
    suspend fun getMonthlyAnalytics(month: String): AnalyticsRepository.MonthlyStatsSummary? {
        return try {
            val userId = getCurrentUserId()
            if (userId > 0) {
                val analytics = analyticsRepository.getMonthlyAnalytics(userId, month)
                val topArtists = analyticsRepository.getTopArtists(userId, month, 1)
                val topSongs = analyticsRepository.getTopSongs(userId, month, 1)
                val activeStreaks = analyticsRepository.getActiveStreaks(userId)

                AnalyticsRepository.MonthlyStatsSummary(
                    totalListeningTime = analytics?.totalListeningTime ?: 0L,
                    topArtist = topArtists.firstOrNull()?.artistName,
                    topSong = topSongs.firstOrNull()?.songTitle,
                    activeStreaksCount = activeStreaks.size,
                    totalSongs = analytics?.uniqueSongsCount ?: 0,
                    totalArtists = analytics?.uniqueArtistsCount ?: 0
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting monthly analytics", e)
            null
        }
    }

    /**
     * Export analytics to CSV
     */
    fun exportAnalyticsCSV(context: Context, month: String) {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserId()
                if (userId > 0) {
                    val exporter = com.example.purrytify.util.AnalyticsExporter(context, analyticsRepository)
                    exporter.exportToCSV(userId, month)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error exporting analytics", e)
            }
        }
    }

    /**
     * Share analytics via share sheet
     */
    fun shareAnalytics(context: Context, month: String) {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserId()
                if (userId > 0) {
                    val exporter = com.example.purrytify.util.AnalyticsExporter(context, analyticsRepository)
                    exporter.shareAnalytics(userId, month)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing analytics", e)
            }
        }
    }

    /**
     * Refresh analytics data
     */
    fun refreshAnalytics() {
        viewModelScope.launch {
            try {
                val userId = getCurrentUserId()
                if (userId > 0) {
                    loadCurrentMonthAnalytics(userId)
                    Log.d(TAG, "Analytics data refreshed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing analytics", e)
            }
        }
    }

    /**
     * Check if user has analytics data
     */
    suspend fun hasAnalyticsData(): Boolean {
        return try {
            val userId = getCurrentUserId()
            if (userId > 0) {
                analyticsRepository.hasAnalyticsData(userId)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking analytics data", e)
            false
        }
    }

    /**
     * Format listening time for display
     */
    fun formatListeningTime(timeInMillis: Long): String {
        return analyticsRepository.formatListeningTime(timeInMillis)
    }

    init {
        // Instead of loading all songs immediately, we'll do it lazily when needed
        // viewModelScope.launch {
        //    loadAllSongs()
        // }
    }

    // Helper function to ensure the current song is in the queue at position 0
    // without affecting the rest of the queue
    private fun ensureCurrentSongInQueue(song: Song) {
        val currentQueue = _queue.value.toMutableList()

        // Check if the song is already in the queue
        val songIndex = currentQueue.indexOfFirst { it.id == song.id }

        // If song is already at position 0, no need to change anything
        if (songIndex == 0) {
            return
        }

        // Remove the song from its current position if it exists elsewhere in the queue
        if (songIndex > 0) {
            currentQueue.removeAt(songIndex)
        }

        // If the queue is empty or the current song is not at position 0, insert it there
        if (currentQueue.isEmpty() || currentQueue[0].id != song.id) {
            // Insert at the beginning without removing other songs
            currentQueue.add(0, song)
        }

        // Update the queue
        _queue.value = currentQueue

        // Set current queue index to 0 since we're playing the first song
        _currentQueueIndex.value = 0

        Log.d(TAG, "Current song (${song.title}) ensured at front of queue. Queue size: ${currentQueue.size}")
    }

    fun bindService(context: Context) {
        Log.d(TAG, "Binding to service")
        Intent(context, MediaPlayerService::class.java).also { intent ->
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            context.startService(intent)
        }
    }

    fun unbindService(context: Context) {
        if (bound) {
            Log.d(TAG, "Unbinding from service")
            context.unbindService(serviceConnection)
            bound = false
        }
    }


    fun playSong(song: Song) {
        Log.d(TAG, "Playing song: ${song.title}, isOnline: ${song.isOnline}")

        // Reset history when manually selecting a song
        _playHistory.value = emptyList()


        mediaPlayerService?.playSong(song)

        // Update current song state
        _currentSong.value = song
        _isPlaying.value = true

        viewModelScope.launch {
            // Update queue
            _queue.value = listOf(song)
            _currentQueueIndex.value = 0


            // Online songs don't need to update database timestamp
            if (!song.isOnline && song.id > 0) {
                Log.d(TAG, "Updating last played timestamp for offline song ${song.id}")
                songRepository.updateLastPlayed(song.id)
            } else {
                Log.d(TAG, "Skipping last played update for online song: ${song.title}")
            }
        }
    }

    // Load all songs from repository
    private suspend fun loadAllSongs() {
        Log.d(TAG, "Loading all songs")

        try {
            // Switch to IO dispatcher for database operations
            withContext(Dispatchers.IO) {
                val observer = Observer<List<EntitySong>> { songList ->
                    Log.d(TAG, "Received ${songList.size} songs from repository")
                    // Convert to UI model and update state
                    val uiSongs = SongMapper.fromEntityList(songList)
                    _allSongs.value = uiSongs
                }

                // Observe on the Main thread to get LiveData updates
                withContext(Dispatchers.Main) {
                    // Store the LiveData temporarily to avoid memory leaks
                    val songsLiveData = songRepository.allSongs
                    songsLiveData.observeForever(observer)

                    try {
                        // Wait for the first emission with a timeout
                        withTimeoutOrNull(5000L) {
                            // Use a channel to get a notification when data arrives
                            val channel = Channel<Unit>(Channel.RENDEZVOUS)
                            val tempObserver = Observer<List<EntitySong>> {
                                channel.trySend(Unit)
                            }
                            songsLiveData.observeForever(tempObserver)
                            try {
                                channel.receive() // Wait for data
                            } finally {
                                songsLiveData.removeObserver(tempObserver)
                            }
                        }
                    } finally {
                        // Make sure to clean up the observer
                        songsLiveData.removeObserver(observer)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading songs: ${e.message}")
            // Set an empty list in case of error
            _allSongs.value = emptyList()
        }
    }

    // Toggle play/pause
    fun togglePlayPause() {
        Log.d(TAG, "Toggle play/pause")
        mediaPlayerService?.togglePlayPause()
    }

    fun seekTo(position: Int) {
        Log.d(TAG, "Seek to position: $position")
        mediaPlayerService?.seekTo(position)
    }

    // Set current song
    private fun setCurrentSong(song: Song) {
        Log.d(TAG, "Setting current song: ${song.title}")
        _currentSong.value = song
        _isPlaying.value = true

        // Play the song using the media player service
        mediaPlayerService?.playSong(song)

        // Jika queue kosong, buat queue baru dengan lagu ini
        if (_queue.value.isEmpty()) {
            Log.d(TAG, "Queue is empty, creating new queue with current song")
            _queue.value = listOf(song)
            _currentQueueIndex.value = 0
        }
        // Jika lagu yang sedang diputar sudah berada di posisi 0 dalam queue, tidak perlu melakukan apa-apa
        else if (_queue.value[0].id == song.id) {
            Log.d(TAG, "Current song is already at position 0 in queue")
            _currentQueueIndex.value = 0
        }
        // Jika lagu yg akan diputar tidak sama dengan lagu di posisi 0,
        // tapi tidak ada di posisi lain dalam queue, ganti lagu di posisi 0
        // (kasus ketika kita menavigasi dengan playNextFromAllSongs/playPreviousFromAllSongs)
        else if (_queue.value.indexOfFirst { it.id == song.id } == -1) {
            Log.d(TAG, "Replacing song at position 0 with current song")
            val updatedQueue = _queue.value.toMutableList()
            updatedQueue[0] = song
            _queue.value = updatedQueue
            _currentQueueIndex.value = 0
        }
        // Jika lagu ada di posisi lain dalam queue (bukan 0),
        // pindahkan ke posisi 0 (kasus ketika kita memilih lagu dari queue)
        else {
            Log.d(TAG, "Moving song from elsewhere in queue to position 0")
            val songIndex = _queue.value.indexOfFirst { it.id == song.id }
            val updatedQueue = _queue.value.toMutableList()

            // Hapus lagu dari posisi saat ini
            updatedQueue.removeAt(songIndex)

            // Tambahkan ke posisi 0
            updatedQueue.add(0, song)

            _queue.value = updatedQueue
            _currentQueueIndex.value = 0
        }
    }

    // Play next song
    fun playNext() {
        Log.d(TAG, "Play next requested")

        // Check if we have an active queue
        if (_queue.value.isNotEmpty()) {
            Log.d(TAG, "Using queue for next song (queue size: ${_queue.value.size})")
            playNextFromQueue()
        } else {
            Log.d(TAG, "Queue is empty, using all songs for next song")
            playNextFromAllSongs()
        }
    }

    // Play next song from the active queue
    private fun playNextFromQueue() {
        val queue = _queue.value
        val currentSong = _currentSong.value

        if (queue.isEmpty()) {
            Log.d(TAG, "Queue is empty, cannot play next")
            return
        }

        // Jika queue memiliki lebih dari 1 lagu
        if (queue.size > 1) {
            // Ada lagu berikutnya, jadi lanjutkan normal
            val nextSong = queue[1]

            // Tambahkan lagu saat ini ke history jika ada
            currentSong?.let { song ->
                val updatedHistory = _playHistory.value.toMutableList()
                updatedHistory.add(song)
                _playHistory.value = updatedHistory
                Log.d(TAG, "Added current song to history, history size: ${updatedHistory.size}")
            }

            // Buat queue baru tanpa lagu saat ini
            val newQueue = queue.toMutableList()
            newQueue.removeAt(0) // Hapus lagu saat ini

            // Set queue baru
            _queue.value = newQueue

            // Log operasi
            Log.d(TAG, "Playing next song from queue: ${nextSong.title}, new queue size: ${newQueue.size}")

            // Set current song without automatically updating queue again
            _currentSong.value = nextSong
            _isPlaying.value = true

            // Play using the media player service
            mediaPlayerService?.playSong(nextSong)
        } else {
            // Queue hanya berisi 1 lagu (lagu terakhir), dan tombol next ditekan
            Log.d(TAG, "Last song in queue and next pressed, switching to all songs navigation")

            // Tambahkan lagu saat ini ke history
            currentSong?.let { song ->
                val updatedHistory = _playHistory.value.toMutableList()
                updatedHistory.add(song)
                _playHistory.value = updatedHistory
                Log.d(TAG, "Added current song to history, history size: ${updatedHistory.size}")
            }

            // Clear queue dan beralih ke navigasi semua lagu
            _queue.value = emptyList()
            playNextFromAllSongs()
        }
    }
    // Play next song from all songs if no queue is active
    private fun playNextFromAllSongs() {
        // Lazily load all songs if they haven't been loaded yet
        viewModelScope.launch {
            if (_allSongs.value.isEmpty()) {
                loadAllSongs()
            }

            val songs = _allSongs.value

            if (songs.isEmpty()) {
                Log.d(TAG, "No songs available, can't play next")
                // Stop playback and reset queue to normal state
                stopCurrentPlayback()
                return@launch
            }

            // Find current song index in all songs
            val currentSong = _currentSong.value
            val currentIndex = if (currentSong != null) {
                songs.indexOfFirst { it.id == currentSong.id }
            } else {
                -1
            }

            if (currentIndex == -1) {
                // If we can't find the current song, start from the beginning
                val firstSong = songs.firstOrNull()
                if (firstSong != null) {
                    Log.d(TAG, "Current song not found in all songs, playing first song")
                    // Create a new queue with just this song
                    _queue.value = listOf(firstSong)
                    _currentQueueIndex.value = 0
                    setCurrentSong(firstSong)
                } else {
                    // No songs available
                    stopCurrentPlayback()
                }
                return@launch
            }

            // Determine next index based on repeat mode and shuffle status
            val nextIndex = when (_repeatMode.value) {
                1 -> {
                    // Repeat All - use shuffle logic or wrap around
                    if (_shuffleEnabled.value) {
                        getNextShuffledSongIndex(currentIndex, songs.size)
                    } else {
                        if (currentIndex >= songs.size - 1) 0 else currentIndex + 1
                    }
                }
                2 -> {
                    // Repeat One - stay on current song
                    currentIndex
                }
                else -> {
                    // No repeat - use shuffle logic if enabled or stop at end
                    if (_shuffleEnabled.value) {
                        // For shuffle without repeat, we don't want to revisit already played songs
                        // This requires tracking played songs, but for simplicity we'll just get a random next song
                        getNextShuffledSongIndex(currentIndex, songs.size)
                    } else {
                        if (currentIndex >= songs.size - 1) {
                            // End of list - stop playback
                            Log.d(TAG, "End of all songs reached without repeat mode, stopping playback")
                            stopCurrentPlayback()
                            return@launch
                        } else {
                            currentIndex + 1
                        }
                    }
                }
            }

            // Play the next song
            songs.getOrNull(nextIndex)?.let { nextSong ->
                Log.d(TAG, "Playing next song from all songs: ${nextSong.title}")

                // Create a new queue with just this song when navigating through all songs
                // This prevents any looping issues
                _queue.value = listOf(nextSong)
                _currentQueueIndex.value = 0

                setCurrentSong(nextSong)
            } ?: run {
                Log.d(TAG, "No song found in all songs at index $nextIndex")
                stopCurrentPlayback()
            }
        }
    }

    fun playOnlineSong(onlineSong: OnlineSong) {
        viewModelScope.launch {
            Log.d(TAG, "=== MAIN VIEW MODEL PLAYING ONLINE SONG ===")
            Log.d(TAG, "OnlineSong ID: ${onlineSong.id}")
            Log.d(TAG, "OnlineSong Title: ${onlineSong.title}")
            Log.d(TAG, "OnlineSong Artist: ${onlineSong.artist}")

            try {
                // Convert OnlineSong to Song model for internal use
                val song = Song(
                    id = System.currentTimeMillis(),
                    title = onlineSong.title,
                    artist = onlineSong.artist,
                    coverUrl = onlineSong.artworkUrl,
                    filePath = onlineSong.audioUrl,
                    duration = onlineSong.getDurationInMillis(),
                    isPlaying = false,
                    isLiked = false,
                    isOnline = true,
                    onlineId = onlineSong.id,
                    lastPlayed = System.currentTimeMillis(),
                    addedAt = System.currentTimeMillis(),
                    userId = -1
                )

                Log.d(TAG, "Created Song object:")
                Log.d(TAG, "  - isOnline: ${song.isOnline}")
                Log.d(TAG, "  - onlineId: ${song.onlineId}")
                Log.d(TAG, "  - title: ${song.title}")

                _currentSong.value = song
                _isPlaying.value = false // Will be set to true by service

                // Create a new queue with just this song
                _queue.value = listOf(song)
                _currentQueueIndex.value = 0

                Log.d(TAG, "State set in ViewModel - currentSong isOnline: ${_currentSong.value?.isOnline}")

                if (mediaPlayerService != null) {
                    mediaPlayerService?.playOnlineSongWithId(
                        audioUrl = onlineSong.audioUrl,
                        title = onlineSong.title,
                        artist = onlineSong.artist,
                        coverUrl = onlineSong.artworkUrl,
                        onlineId = onlineSong.id
                    )

                    Log.d(TAG, "Called MediaPlayerService.playOnlineSongWithId")
                } else {
                    Log.e(TAG, "MediaPlayerService is not available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing online song", e)
            }
        }
    }

    private fun preserveOnlineProperties(updatedSong: Song, originalSong: Song): Song {
        return updatedSong.copy(
            isOnline = originalSong.isOnline,
            onlineId = originalSong.onlineId
        )
    }

    // Helper method to stop current playback
    private fun stopCurrentPlayback() {
        // Stop the MediaPlayer using the service's dedicated method
        mediaPlayerService?.stopPlayback()

        // Update UI state to reflect stopped playback
        _isPlaying.value = false

        // Keep the current song in the queue but make sure there are no other songs
        // This ensures we return to a "normal" state where navigation will use all songs
        _currentSong.value?.let { currentSong ->
            if (_queue.value.size > 1) {
                // If we have more than just the current song, trim to just keep the current one
                _queue.value = listOf(currentSong)
            }
        } ?: run {
            // If there's no current song, clear the queue completely
            _queue.value = emptyList()
        }

        // Reset the queue index
        _currentQueueIndex.value = 0

        // Reset end of playback flag in service
        mediaPlayerService?.resetEndOfPlaybackFlag()

        Log.d(TAG, "Playback stopped and state reset to normal")
    }

    // Play previous song
    fun playPrevious() {
        Log.d(TAG, "Play previous requested")
        Log.d(TAG, "DEBUG_QUEUE: playPrevious called, queue size: ${_queue.value.size}, history size: ${_playHistory.value.size}")

        // If we're more than 3 seconds into the song, restart it instead of going to previous
        if (_currentPosition.value > 3000) {
            Log.d(TAG, "More than 3 seconds into song, restarting current song")
            mediaPlayerService?.seekTo(0)
            return
        }

        // Jika history memiliki lagu (artinya kita pernah memutar lagu sebelumnya)
        if (_playHistory.value.isNotEmpty()) {
            Log.d(TAG, "DEBUG_QUEUE: Using history for previous song, history size: ${_playHistory.value.size}")
            playPreviousFromHistory()
            return
        }

        // Jika queue memiliki lebih dari 1 lagu (kasus normal)
        if (_queue.value.size > 1) {
            Log.d(TAG, "DEBUG_QUEUE: Using queue for previous song, queue size: ${_queue.value.size}")
            playPreviousFromQueue()
            return
        }

        // Jika tidak ada history dan queue hanya berisi 1 lagu, gunakan navigasi semua lagu
        Log.d(TAG, "DEBUG_QUEUE: No history and queue has only current song, using all songs navigation")
        playPreviousFromAllSongs()
    }

    // Play previous song from the active queue
    private fun playPreviousFromQueue() {
        val queue = _queue.value
        val history = _playHistory.value

        if (queue.size <= 1 && history.isEmpty()) {
            // If queue only has the current song and no history, use all songs navigation
            playPreviousFromAllSongs()
            return
        }

        if (history.isEmpty()) {
            // Jika tidak ada history tetapi queue > 1, berarti kita berada di lagu pertama queue
            // Gunakan navigasi normal
            Log.d(TAG, "No history available for previous, using all songs navigation")
            playPreviousFromAllSongs()
            return
        }

        // Ambil lagu terakhir dari history
        val previousSong = history.last()

        // Buat history baru tanpa lagu terakhir
        val newHistory = history.dropLast(1)
        _playHistory.value = newHistory

        // Buat queue baru dengan previous song di depan
        val newQueue = mutableListOf(previousSong)
        newQueue.addAll(queue) // Tambahkan semua lagu dari queue saat ini

        // Set queue baru
        _queue.value = newQueue

        // Log operasi
        Log.d(TAG, "Playing previous song from history: ${previousSong.title}, remaining history: ${newHistory.size}")

        // Set current song without automatically updating queue again
        _currentSong.value = previousSong
        _isPlaying.value = true

        // Play using the media player service
        mediaPlayerService?.playSong(previousSong)
    }

    private fun playPreviousFromHistory() {
        val history = _playHistory.value
        val currentQueue = _queue.value

        if (history.isEmpty()) {
            Log.d(TAG, "DEBUG_QUEUE: History is empty, cannot play previous from history")
            playPreviousFromAllSongs()
            return
        }

        // Ambil lagu terakhir dari history
        val previousSong = history.last()
        Log.d(TAG, "DEBUG_QUEUE: Playing previous song from history: ${previousSong.title}")

        // Buat history baru tanpa lagu terakhir
        val newHistory = history.dropLast(1)
        _playHistory.value = newHistory

        // Jika queue tetap ada, buat queue baru dengan lagu sebelumnya di depan
        // PENTING: Kita tetap mempertahankan queue, tetapi menambahkan lagu sebelumnya ke posisi 0
        val newQueue = mutableListOf(previousSong)
        newQueue.addAll(currentQueue)

        // Set queue baru
        _queue.value = newQueue
        Log.d(TAG, "DEBUG_QUEUE: New queue size after adding previous song: ${newQueue.size}")

        // Play the previous song
        _currentSong.value = previousSong
        _isPlaying.value = true
        mediaPlayerService?.playSong(previousSong)
    }


    // Play previous song from all songs if no queue is active
    private fun playPreviousFromAllSongs() {
        // Lazily load all songs if they haven't been loaded yet
        viewModelScope.launch {
            if (_allSongs.value.isEmpty()) {
                loadAllSongs()
            }

            val songs = _allSongs.value

            if (songs.isEmpty()) {
                Log.d(TAG, "No songs available, can't play previous")
                return@launch
            }

            // Find current song index in all songs
            val currentSong = _currentSong.value
            val currentIndex = if (currentSong != null) {
                songs.indexOfFirst { it.id == currentSong.id }
            } else {
                -1
            }

            if (currentIndex == -1) {
                // If we can't find the current song, start from the beginning
                val firstSong = songs.firstOrNull()
                if (firstSong != null) {
                    Log.d(TAG, "Current song not found in all songs, playing first song")
                    // Create a fresh queue with just the first song
                    _queue.value = listOf(firstSong)
                    _currentQueueIndex.value = 0
                    setCurrentSong(firstSong)
                }
                return@launch
            }

            // Determine previous index based on repeat mode
            val prevIndex = when (_repeatMode.value) {
                1 -> {
                    // Repeat All - wrap around from first to last
                    if (currentIndex <= 0) songs.size - 1 else currentIndex - 1
                }
                2 -> {
                    // Repeat One - stay on current song
                    currentIndex
                }
                else -> {
                    // No repeat - stop at beginning
                    if (currentIndex <= 0) {
                        // Already at first song, just stay there
                        currentIndex
                    } else {
                        // Go to previous song
                        currentIndex - 1
                    }
                }
            }

            // Play the previous song
            songs.getOrNull(prevIndex)?.let { prevSong ->
                Log.d(TAG, "Playing previous song from all songs: ${prevSong.title}")

                // Update queue with just this song to avoid problems with looping
                _queue.value = listOf(prevSong)
                _currentQueueIndex.value = 0

                setCurrentSong(prevSong)
            } ?: Log.d(TAG, "No song found in all songs at index $prevIndex")
        }
    }

    // Get next index considering repeat modes
    private fun getNextIndex(listSize: Int): Int {
        val currentIndex = _currentQueueIndex.value

        // Check for invalid scenario
        if (listSize == 0) return -1

        // Return current index for repeat one
        if (_repeatMode.value == 2) {
            Log.d(TAG, "Repeat One mode, returning current index: $currentIndex")
            return currentIndex
        }

        // Standard next index
        val nextIndex = currentIndex + 1

        // Check if we're at the end of the list
        if (nextIndex >= listSize) {
            if (_repeatMode.value == 1) {
                // Repeat All mode - wrap around to the beginning
                Log.d(TAG, "Repeat All mode, wrapping to beginning")
                return 0
            } else {
                // No repeat mode - stay at end and don't play next
                Log.d(TAG, "No repeat mode and reached end, keeping current index")
                // Send a signal that we've reached the end
                mediaPlayerService?.let {
                    if (!it.reachedEndOfPlayback.value) {
                        // The MediaPlayerService handles playback end, but we set the flag here too
                        // in case this method is called outside of song completion event
                    }
                }
                return currentIndex
            }
        } else {
            // Not at end of list, return next index
            return nextIndex
        }
    }

    // Get previous index considering repeat modes
    private fun getPreviousIndex(listSize: Int): Int {
        val currentIndex = _currentQueueIndex.value

        // Check for invalid scenario
        if (listSize == 0) return -1

        // Return current index for repeat one
        if (_repeatMode.value == 2) {
            Log.d(TAG, "Repeat One mode, returning current index: $currentIndex")
            return currentIndex
        }

        // Standard previous index
        val prevIndex = currentIndex - 1
        // Consider repeat all
        return if (prevIndex < 0) {
            if (_repeatMode.value == 1) {
                Log.d(TAG, "Repeat All mode, wrapping to end")
                listSize - 1
            } else {
                Log.d(TAG, "No repeat, staying at current index")
                currentIndex
            }
        } else {
            prevIndex
        }
    }

    // Add a song to the queue
    fun addToQueue(song: Song) {
        val currentQueue = _queue.value.toMutableList()

        // If the song is already the current song (at position 0), don't add it again to the queue
        if (currentQueue.isNotEmpty() && currentQueue[0].id == song.id) {
            Log.d(TAG, "Song ${song.title} is already the current song, not adding to queue")
            return
        }

        // Check if song already exists in queue (other than position 0)
        val existingIndex = currentQueue.indexOfFirst { it.id == song.id }
        if (existingIndex > 0) {
            // Remove the duplicate before adding to the end
            currentQueue.removeAt(existingIndex)
            Log.d(TAG, "Removed existing instance of ${song.title} from queue at position $existingIndex")
        }

        // Add the song to the end of the queue
        currentQueue.add(song)
        _queue.value = currentQueue

        Log.d(TAG, "Added ${song.title} to queue. Queue size: ${currentQueue.size}")

        // If no song is currently playing, start playing the first song in queue
        if (_currentSong.value == null) {
            Log.d(TAG, "No song playing, starting playback with first song in queue")
            // Play the first song in the queue
            playSong(currentQueue[0])
        }
    }

    // Remove song from queue by index
    fun removeFromQueue(index: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (index >= 0 && index < currentQueue.size) {
            // Don't allow removing the current song (index 0)
            if (index == 0) {
                Log.d(TAG, "Cannot remove currently playing song from queue")
                return
            }

            Log.d(TAG, "Removing song at index $index from queue")
            currentQueue.removeAt(index)
            _queue.value = currentQueue
        }
    }

    // Clear the entire queue except for the current song
    fun clearQueue() {
        Log.d(TAG, "Clearing queue and history")

        // Clear play history
        _playHistory.value = emptyList()

        // Keep only the current song in the queue if it exists
        if (_queue.value.isNotEmpty()) {
            // Preserve the first song (currently playing) and remove the rest
            _queue.value = _queue.value.take(1)
            Log.d(TAG, "Queue cleared, preserving only the currently playing song")
        } else {
            // If there's no song in the queue, keep it empty
            _queue.value = emptyList()
            Log.d(TAG, "Queue was already empty, nothing to clear")
        }
    }

    // Set repeat mode
    fun setRepeatMode(mode: Int) {
        Log.d(TAG, "Setting repeat mode to $mode")
        _repeatMode.value = mode
        mediaPlayerService?.setRepeatMode(mode)
    }

    // Set shuffle mode
    fun setShuffleEnabled(enabled: Boolean) {
        Log.d(TAG, "Setting shuffle to $enabled")
        _shuffleEnabled.value = enabled
        mediaPlayerService?.setShuffleEnabled(enabled)

        // Jika shuffle diaktifkan, acak queue yang ada
        if (enabled && _queue.value.isNotEmpty()) {
            shuffleCurrentQueue()
        }
    }

    private fun shuffleCurrentQueue() {
        val currentQueue = _queue.value.toMutableList()

        // Jika queue kosong atau hanya berisi 1 lagu, tidak perlu melakukan apa-apa
        if (currentQueue.size <= 1) {
            Log.d(TAG, "Queue too small to shuffle")
            return
        }

        // Ambil lagu yang sedang diputar
        val currentSong = currentQueue.removeAt(0)

        // Acak lagu yang tersisa
        val shuffledSongs = currentQueue.shuffled()

        // Kembalikan lagu saat ini ke posisi awal
        val newQueue = mutableListOf<Song>()
        newQueue.add(currentSong)
        newQueue.addAll(shuffledSongs)

        // Perbarui queue
        _queue.value = newQueue

        Log.d(TAG, "Queue shuffled, current song remains at position 0")
    }

    private fun getNextShuffledSongIndex(currentIndex: Int, totalSongs: Int): Int {
        // Jika shuffle tidak aktif, gunakan logika normal
        if (!_shuffleEnabled.value) {
            return if (currentIndex >= totalSongs - 1) 0 else currentIndex + 1
        }

        // Jika shuffle aktif, pilih indeks acak yang bukan indeks saat ini
        var nextIndex: Int
        do {
            nextIndex = (0 until totalSongs).random()
        } while (nextIndex == currentIndex && totalSongs > 1)

        Log.d(TAG, "Shuffled next: $nextIndex (from currentIndex: $currentIndex)")
        return nextIndex
    }

    // Toggle like status for a song
    fun toggleLike(songId: Long, isLiked: Boolean) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Toggling like for song $songId to $isLiked")
                // Update in repository
                songRepository.toggleLike(songId, isLiked)

                // Update current song if it's the same one
                _currentSong.value?.let { song ->
                    if (song.id == songId) {
                        _currentSong.value = song.copy(isLiked = isLiked)
                    }
                }

                // Update in queue if present
                val updatedQueue = _queue.value.map { song ->
                    if (song.id == songId) song.copy(isLiked = isLiked) else song
                }
                _queue.value = updatedQueue

                // Update in all songs
                val updatedAllSongs = _allSongs.value.map { song ->
                    if (song.id == songId) song.copy(isLiked = isLiked) else song
                }
                _allSongs.value = updatedAllSongs

            } catch (e: Exception) {
                Log.e(TAG, "Error toggling like: ${e.message}")
            }
        }
    }

    // Handle song completion (called from song completion receiver)
    fun onSongCompleted(isEndOfPlayback: Boolean = false, completedSongId: String? = null) {
        Log.d(TAG, "Song completed, isEndOfPlayback: $isEndOfPlayback")

        val currentSong = _currentSong.value
        val queue = _queue.value

        // First check if there are songs in the queue
        if (queue.size > 1) {
            // Queue memiliki lebih dari 1 lagu, putar lagu berikutnya

            // Before moving to next song, add current song to history
            currentSong?.let { song ->
                val updatedHistory = _playHistory.value.toMutableList()
                updatedHistory.add(song)
                _playHistory.value = updatedHistory
                Log.d(TAG, "Added completed song to history, history size: ${updatedHistory.size}")
            }

            // More songs in queue, play the next song
            Log.d(TAG, "More songs in queue, playing next song")
            playNextFromQueue()
            return
        } else if (queue.size == 1 && isEndOfPlayback) {
            // Ini adalah lagu terakhir dalam queue dan baru saja selesai diputar

            // Add it to history
            currentSong?.let { song ->
                val updatedHistory = _playHistory.value.toMutableList()
                updatedHistory.add(song)
                _playHistory.value = updatedHistory
                Log.d(TAG, "Added last queue song to history, history size: ${updatedHistory.size}")
            }

            // Clear the queue since the last song has completed
            _queue.value = emptyList()
            Log.d(TAG, "Last song in queue completed, queue cleared")

            // Now decide what to do based on repeat mode
            if (_repeatMode.value == 1) {
                // Repeat All mode - use standard navigation
                Log.d(TAG, "Repeat All mode, playing next song from all songs")
                playNextFromAllSongs()
                return
            } else if (_repeatMode.value == 2) {
                // Repeat One mode - should be handled by MediaPlayerService already
                Log.d(TAG, "Repeat One mode, handled by MediaPlayerService")
                return
            } else {
                // No repeat mode (0) - we're at the end of queue
                // Just stop playback
                Log.d(TAG, "Last song in queue completed, no repeat mode, stopping playback")
                _isPlaying.value = false
                mediaPlayerService?.resetEndOfPlaybackFlag()
                return
            }
        }

        // Fallback - no songs in queue
        Log.d(TAG, "No songs in queue, using all songs navigation")
        // Check repeat modes
        when (_repeatMode.value) {
            1 -> {
                // Repeat All mode - use standard navigation
                Log.d(TAG, "Repeat All mode, playing next song")
                playNextFromAllSongs()
            }
            2 -> {
                // Repeat One mode - should be handled by MediaPlayerService already
                Log.d(TAG, "Repeat One mode, should be handled by MediaPlayerService")
            }
            else -> {
                // No repeat (0)
                if (isEndOfPlayback) {
                    // We're at the end of the queue/playlist and not in repeat mode
                    // Just stop - don't proceed to the next song
                    Log.d(TAG, "End of playlist reached without repeat mode, stopping playback")

                    // Update UI state to indicate playback has ended
                    _isPlaying.value = false

                    // Reset the service's end of playback flag if needed
                    mediaPlayerService?.resetEndOfPlaybackFlag()
                } else {
                    // We're not at the end yet, try to play the next song from all songs
                    Log.d(TAG, "Playing next song (not at end of playlist)")
                    playNextFromAllSongs()
                }
            }
        }
    }


    // Reorder song in queue
    fun moveSongInQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return

        // Don't allow moving the currently playing song (index 0)
        if (fromIndex == 0 || toIndex == 0) {
            Log.d(TAG, "Cannot move the currently playing song")
            return
        }

        val currentQueue = _queue.value.toMutableList()
        if (fromIndex < 0 || fromIndex >= currentQueue.size ||
            toIndex < 0 || toIndex >= currentQueue.size) {
            Log.e(TAG, "Invalid indices for queue reordering: from=$fromIndex, to=$toIndex")
            return
        }

        // Move the item in the queue
        val movedItem = currentQueue.removeAt(fromIndex)
        currentQueue.add(toIndex, movedItem)
        _queue.value = currentQueue

        Log.d(TAG, "Moved song in queue from $fromIndex to $toIndex, new queue size: ${currentQueue.size}")
    }

    // Handle song deletion
    fun handleSongDeleted(songId: Long) {
        Log.d(TAG, "Handling deleted song with ID: $songId")

        // If the deleted song is currently playing, stop playback and clear current song
        if (_currentSong.value?.id == songId) {
            Log.d(TAG, "Currently playing song was deleted, stopping playback")
            mediaPlayerService?.stopPlayback()

            // Set current song to null
            _currentSong.value = null
            _isPlaying.value = false

            // Clear the queue
            _queue.value = emptyList()
        } else {
            // Remove the song from the queue if present
            val currentQueue = _queue.value.toMutableList()
            val wasInQueue = currentQueue.removeIf { it.id == songId }

            if (wasInQueue) {
                Log.d(TAG, "Removed deleted song from queue")
                _queue.value = currentQueue
            }
        }
    }

    // Handle song update
    fun handleSongUpdated(updatedSong: Song) {
        Log.d(TAG, "Handling updated song with ID: ${updatedSong.id}")

        // If the updated song is currently playing, update the current song
        if (_currentSong.value?.id == updatedSong.id) {
            Log.d(TAG, "Updating currently playing song")
            _currentSong.value = updatedSong.copy(isPlaying = _isPlaying.value)
        }

        // Update the song in the queue if present
        val currentQueue = _queue.value.toMutableList()
        val queueIndices = currentQueue.mapIndexedNotNull { index, song ->
            if (song.id == updatedSong.id) index else null
        }

        queueIndices.forEach { index ->
            Log.d(TAG, "Updating song in queue at position $index")
            currentQueue[index] = updatedSong.copy(isPlaying = index == 0 && _isPlaying.value)
        }

        if (queueIndices.isNotEmpty()) {
            _queue.value = currentQueue
        }

        // Update in all songs list if present
        val allSongsUpdated = _allSongs.value.map { song ->
            if (song.id == updatedSong.id) updatedSong else song
        }
        _allSongs.value = allSongsUpdated
    }

    // Handle logout
    fun handleLogout() {
        Log.d(TAG, "Handling logout")

        // ADD: Cleanup analytics
        mediaPlayerService?.handleUserLogout()
        _currentMonthListeningTime.value = 0L
        _monthlyAnalytics.value = null

        // Clear all songs and queue
        _allSongs.value = emptyList()
        _queue.value = emptyList()
        _currentQueueIndex.value = -1
        _currentSong.value = null
        _isPlaying.value = false


        // Stop playback
        mediaPlayerService?.stopPlayback()
    }

    fun stopPlayback() {
        Log.d(TAG, "Stop playback requested from notification")

        // Stop the media player service
        mediaPlayerService?.stopPlayback()

        // Update UI state
        _isPlaying.value = false
        _currentSong.value = null


        // Clear queue and reset state
        _queue.value = emptyList()
        _playHistory.value = emptyList()
        _currentQueueIndex.value = -1

        // Reset position and duration
        _currentPosition.value = 0
        _duration.value = 0

        Log.d(TAG, "Playback stopped and state cleared")
    }

    fun handleUserLogin(userId: Long) {
        viewModelScope.launch {
            try {
                mediaPlayerService?.initializeAnalyticsForUser(userId)
                loadCurrentMonthAnalytics(userId)
                Log.d(TAG, "Analytics initialized for user login: $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling user login for analytics", e)
            }
        }
    }
}