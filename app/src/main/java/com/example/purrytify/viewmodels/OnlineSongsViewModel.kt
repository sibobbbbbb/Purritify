// Location: app/src/main/java/com/example/purrytify/viewmodels/OnlineSongsViewModel.kt

package com.example.purrytify.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.entity.Song
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.repository.OnlineSongsRepository
import com.example.purrytify.repository.UserRepository
import kotlinx.coroutines.launch

class OnlineSongsViewModel(
    private val onlineSongsRepository: OnlineSongsRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val TAG = "OnlineSongsViewModel"

    // Global top songs
    private val _globalTopSongs = MutableLiveData<List<OnlineSong>>()
    val globalTopSongs: LiveData<List<OnlineSong>> = _globalTopSongs

    // Country top songs
    private val _countryTopSongs = MutableLiveData<List<OnlineSong>>()
    val countryTopSongs: LiveData<List<OnlineSong>> = _countryTopSongs

    // Downloaded songs
    val downloadedOnlineSongs = onlineSongsRepository.getDownloadedOnlineSongs()

    // Loading state
    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Error message
    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    // Current user ID
    private var currentUserId: Int = -1

    // Downloaded song IDs
    private val _downloadedSongIds = MutableLiveData<Set<Int>>(emptySet())
    val downloadedSongIds: LiveData<Set<Int>> = _downloadedSongIds

    // Currently downloading songs
    private val _downloadingSongs = MutableLiveData<Set<Int>>(emptySet())
    val downloadingSongs: LiveData<Set<Int>> = _downloadingSongs

    // Load data
    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Get user profile
                val userProfileResult = userRepository.getUserProfile()
                userProfileResult.onSuccess { profile ->
                    currentUserId = profile.id

                    // Load global top songs
                    loadGlobalTopSongs()

                    // Load country top songs based on user's country
                    loadCountryTopSongs(profile.location)

                    // Load downloaded song IDs
                    loadDownloadedSongIds()
                }.onFailure { error ->
                    Log.e(TAG, "Error getting user profile", error)
                    _error.value = "Failed to get user profile: ${error.message}"

                    // Load with default country code
                    loadGlobalTopSongs()
                    loadCountryTopSongs("ID") // Default to Indonesia
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading data", e)
                _error.value = "Error loading data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadGlobalTopSongs() {
        viewModelScope.launch {
            try {
                val result = onlineSongsRepository.getGlobalTopSongs()
                result.onSuccess { songs ->
                    _globalTopSongs.value = songs
                }.onFailure { error ->
                    Log.e(TAG, "Error loading global top songs", error)
                    _error.value = "Failed to load global top songs: ${error.message}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading global top songs", e)
                _error.value = "Error loading global top songs: ${e.message}"
            }
        }
    }

    private fun loadCountryTopSongs(countryCode: String) {
        viewModelScope.launch {
            try {
                val result = onlineSongsRepository.getCountryTopSongs(countryCode)
                result.onSuccess { songs ->
                    _countryTopSongs.value = songs
                }.onFailure { error ->
                    Log.e(TAG, "Error loading country top songs", error)
                    _error.value = "Failed to load country top songs: ${error.message}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading country top songs", e)
                _error.value = "Error loading country top songs: ${e.message}"
            }
        }
    }


    // Mark song as downloading
    fun markSongAsDownloading(songId: Int) {
        val currentDownloading = _downloadingSongs.value?.toMutableSet() ?: mutableSetOf()
        currentDownloading.add(songId)
        _downloadingSongs.value = currentDownloading
        Log.d(TAG, "Marked song $songId as downloading")
    }

    // Remove a song from downloading state
    fun removeFromDownloading(songId: Int) {
        val currentDownloading = _downloadingSongs.value?.toMutableSet() ?: mutableSetOf()
        currentDownloading.remove(songId)
        _downloadingSongs.value = currentDownloading
        Log.d(TAG, "Removed song $songId from downloading state")
    }

    // Make this public so it can be called from the UI
    fun loadDownloadedSongIds() {
        viewModelScope.launch {
            try {
                // Use direct query instead of LiveData for immediate result
                val downloadedSongs = onlineSongsRepository.getDownloadedOnlineSongsSync()
                val ids = downloadedSongs.mapNotNull { it.onlineId }.toSet()
                _downloadedSongIds.value = ids
                Log.d(TAG, "Loaded ${ids.size} downloaded song IDs: $ids")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading downloaded song IDs", e)
            }
        }
    }

    // Mark song as downloaded
    fun markSongAsDownloaded(songId: Int) {
        // Update downloading state
        val currentDownloading = _downloadingSongs.value?.toMutableSet() ?: mutableSetOf()
        currentDownloading.remove(songId)
        _downloadingSongs.value = currentDownloading

        // Update downloaded state
        val currentDownloaded = _downloadedSongIds.value?.toMutableSet() ?: mutableSetOf()
        currentDownloaded.add(songId)
        _downloadedSongIds.value = currentDownloaded

        Log.d(TAG, "Marked song $songId as downloaded")
    }

    // Save downloaded song
    suspend fun saveDownloadedSong(onlineSong: OnlineSong, localFilePath: String): Long {
        return if (currentUserId > 0) {
            val result = onlineSongsRepository.saveDownloadedSong(onlineSong, localFilePath, currentUserId)
            Log.d(TAG, "Saved downloaded song ${onlineSong.title} with ID: $result")
            result
        } else {
            Log.e(TAG, "Cannot save downloaded song: User ID is not set")
            -1L
        }
    }

    // Check if song is downloaded
    fun isSongDownloaded(songId: Int): Boolean {
        return _downloadedSongIds.value?.contains(songId) == true
    }

    // Check if song is downloading
    fun isSongDownloading(songId: Int): Boolean {
        return _downloadingSongs.value?.contains(songId) == true
    }
}