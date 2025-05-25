package com.example.purrytify.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.entity.Song as EntitySong
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.models.Song
import com.example.purrytify.util.SongMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val songRepository: SongRepository) : ViewModel() {
    private val TAG = "HomeViewModel"

    // PERBAIKAN: Hapus state management untuk current song dan playing status
    // Biarkan MainViewModel yang mengelola state ini

    // LiveData untuk model UI
    private val _allSongs = MutableLiveData<List<Song>>(emptyList())
    val allSongs: LiveData<List<Song>> = _allSongs

    private val _recentlyPlayed = MutableLiveData<List<Song>>(emptyList())
    val recentlyPlayed: LiveData<List<Song>> = _recentlyPlayed

    // New songs (5 terbaru)
    private val _newSongs = MutableLiveData<List<Song>>(emptyList())
    val newSongs: LiveData<List<Song>> = _newSongs

    // Store observers so we can clean them up
    private val allSongsObserver = Observer<List<EntitySong>> { entityList ->
        Log.d(TAG, "All songs updated, count: ${entityList.size}")
        _allSongs.value = convertToModelSongs(entityList)
    }

    private val recentlyPlayedObserver = Observer<List<EntitySong>> { entityList ->
        Log.d(TAG, "Recently played updated, count: ${entityList.size}")
        _recentlyPlayed.value = convertToModelSongs(entityList)
    }

    private val newSongsObserver = Observer<List<EntitySong>> { entityList ->
        Log.d(TAG, "New songs updated, count: ${entityList.size}")
        _newSongs.value = convertToModelSongs(entityList)
    }

    init {
        // Set up observers for repository data
        setupObservers()
    }

    private fun setupObservers() {
        // Observe allSongs from repository
        songRepository.allSongs.observeForever(allSongsObserver)

        // Observe recentlyPlayed from repository
        songRepository.recentlyPlayed.observeForever(recentlyPlayedObserver)

        // Observe new songs
        songRepository.getNewestSongs().observeForever(newSongsObserver)
    }

    private fun convertToModelSongs(entityList: List<EntitySong>): List<Song> {
        // PERBAIKAN: Tidak lagi mencoba mengelola playing status di sini
        // Biarkan MainViewModel yang menangani status playing
        return entityList.map { entity ->
            SongMapper.fromEntity(entity, false) // Always set playing to false
        }
    }

    // PERBAIKAN: Hapus fungsi playSong dari HomeViewModel
    // Biarkan MainViewModel yang mengelola pemutaran lagu

    /**
     * Toggle status like lagu
     * @param songId ID lagu
     * @param isLiked status like baru
     */
    fun toggleLike(songId: Long, isLiked: Boolean) {
        viewModelScope.launch {
            try {
                songRepository.toggleLike(songId, isLiked)
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling like status", e)
            }
        }
    }

    /**
     * Update last played timestamp untuk lagu tertentu
     * Fungsi ini bisa dipanggil dari luar untuk update database
     */
    fun updateLastPlayed(songId: Long) {
        viewModelScope.launch {
            try {
                songRepository.updateLastPlayed(songId)
                Log.d(TAG, "Updated last played for song $songId")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating last played timestamp", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Properly remove observers to prevent memory leaks
        songRepository.allSongs.removeObserver(allSongsObserver)
        songRepository.recentlyPlayed.removeObserver(recentlyPlayedObserver)
        try {
            songRepository.getNewestSongs().removeObserver(newSongsObserver)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing new songs observer", e)
        }
    }
}