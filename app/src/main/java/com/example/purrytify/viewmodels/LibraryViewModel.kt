package com.example.purrytify.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.entity.Song as EntitySong
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class LibraryViewModel(private val repository: SongRepository) : ViewModel() {
    private val TAG = "LibraryViewModel"

    // StateFlow untuk daftar lagu
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    // PERBAIKAN: Hapus state management untuk current song dan playing status
    // Biarkan MainViewModel yang mengelola state ini secara terpusat

    // Status for operations
    private val _operationStatus = MutableStateFlow<OperationStatus?>(null)
    val operationStatus: StateFlow<OperationStatus?> = _operationStatus

    init {
        viewModelScope.launch {
            repository.allSongs.asFlow().collect { dbSongs ->
                Log.d(TAG, "Collected ${dbSongs.size} songs from repository")
                _songs.value = dbSongs.map { dbSong ->
                    Song(
                        id = dbSong.id,
                        title = dbSong.title,
                        artist = dbSong.artist,
                        coverUrl = dbSong.artworkPath,
                        filePath = dbSong.filePath,
                        duration = dbSong.duration,
                        isLiked = dbSong.isLiked,
                        isPlaying = false, // PERBAIKAN: Always false, let MainViewModel handle this
                        isOnline = dbSong.isOnline,
                        onlineId = dbSong.onlineId
                    )
                }
            }
        }
    }

    // PERBAIKAN: Hapus fungsi playSong dari LibraryViewModel
    // MainViewModel akan mengelola semua playback state

    fun toggleLike(song: Song) {
        viewModelScope.launch {
            repository.toggleLike(song.id, !song.isLiked)
        }
    }

    fun debugDatabaseContent() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val totalCount = repository.getTotalSongCount()
                val onlineCount = repository.getOnlineSongCount()
                Log.d(TAG, "DEBUG: Database contains $totalCount total songs, $onlineCount online songs")

                // Get all songs and log their details
                val allSongs = repository.getAllSongsSync()
                Log.d(TAG, "DEBUG: All songs in database (${allSongs.size}):")
                allSongs.forEach { song ->
                    Log.d(TAG, "DEBUG: Song[id=${song.id}, title=${song.title}, isOnline=${song.isOnline}, onlineId=${song.onlineId}]")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error debugging database content", e)
            }
        }
    }

    fun addSong(title: String, artist: String, filePath: String, artworkPath: String, duration: Long) {
        viewModelScope.launch {
            val newSong = EntitySong(
                title = title,
                artist = artist,
                filePath = filePath,
                artworkPath = artworkPath,
                duration = duration,
                isLiked = false,
                lastPlayed = null,
                addedAt = System.currentTimeMillis()
            )
            repository.insert(newSong)
            _operationStatus.value = OperationStatus.Success("Song added successfully")
        }
    }

    fun editSong(songId: Long, title: String, artist: String, filePath: String, artworkPath: String, duration: Long, mainViewModel: MainViewModel? = null) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting edit for song ID: $songId")

                // Use the direct method that returns the song synchronously
                val existingSong = repository.getSongByIdDirect(songId)

                Log.d(TAG, "Retrieved song: $existingSong")

                if (existingSong != null) {
                    // Create updated song entity
                    val updatedSong = EntitySong(
                        id = existingSong.id,
                        title = title,
                        artist = artist,
                        filePath = filePath,
                        artworkPath = artworkPath,
                        duration = duration,
                        isLiked = existingSong.isLiked,
                        lastPlayed = existingSong.lastPlayed,
                        addedAt = existingSong.addedAt,
                        userId = existingSong.userId
                    )

                    // Update song in repository
                    val result = repository.update(updatedSong)
                    if (result > 0) {
                        _operationStatus.value = OperationStatus.Success("Song updated successfully")

                        // Convert to UI model for MainViewModel
                        val updatedUiSong = Song(
                            id = updatedSong.id,
                            title = updatedSong.title,
                            artist = updatedSong.artist,
                            coverUrl = updatedSong.artworkPath,
                            filePath = updatedSong.filePath,
                            duration = updatedSong.duration,
                            isLiked = updatedSong.isLiked
                        )

                        // Notify main view model about the update if provided
                        mainViewModel?.handleSongUpdated(updatedUiSong)
                    } else {
                        _operationStatus.value = OperationStatus.Error("Failed to update song")
                    }
                } else {
                    Log.e(TAG, "Song not found for ID: $songId")
                    _operationStatus.value = OperationStatus.Error("Song not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating song: ${e.message}")
                _operationStatus.value = OperationStatus.Error("Error updating song: ${e.message}")
            }
        }
    }

    fun deleteSong(songId: Long, mainViewModel: MainViewModel? = null) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting delete for song ID: $songId")

                // Use the direct method that returns the song synchronously
                val existingSong = repository.getSongByIdDirect(songId)

                Log.d(TAG, "Retrieved song for deletion: $existingSong")

                if (existingSong != null) {
                    // Notify main view model before deletion if provided
                    // This ensures the song is removed from queue and playback if needed
                    mainViewModel?.handleSongDeleted(songId)

                    // Delete song from repository
                    val result = repository.delete(existingSong)

                    if (result > 0) {
                        // Attempt to delete associated files
                        deleteAssociatedFiles(existingSong.filePath, existingSong.artworkPath)
                        _operationStatus.value = OperationStatus.Success("Song deleted successfully")
                    } else {
                        _operationStatus.value = OperationStatus.Error("Failed to delete song")
                    }
                } else {
                    Log.e(TAG, "Song not found for deletion, ID: $songId")
                    _operationStatus.value = OperationStatus.Error("Song not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting song: ${e.message}")
                _operationStatus.value = OperationStatus.Error("Error deleting song: ${e.message}")
            }
        }
    }

    // Helper function to delete the song file and artwork file
    private fun deleteAssociatedFiles(filePath: String, artworkPath: String) {
        try {
            // Delete song file
            val songFile = File(filePath)
            if (songFile.exists()) {
                songFile.delete()
            }

            // Delete artwork file if it exists and is a local file
            // (not an http URL or other non-file path)
            if (!artworkPath.startsWith("http") && artworkPath.isNotEmpty()) {
                val artworkFile = File(artworkPath)
                if (artworkFile.exists()) {
                    artworkFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting associated files: ${e.message}")
        }
    }

    fun clearOperationStatus() {
        _operationStatus.value = null
    }

    // PERBAIKAN: Hapus fungsi setIsPlaying karena tidak diperlukan lagi
    // MainViewModel mengelola semua state playing
}

// Data class for operation status
sealed class OperationStatus {
    data class Success(val message: String) : OperationStatus()
    data class Error(val message: String) : OperationStatus()
}