package com.example.purrytify.data.repository

import androidx.lifecycle.LiveData
import com.example.purrytify.data.dao.SongDao
import com.example.purrytify.data.entity.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class SongRepository(private val songDao: SongDao) {
    // Store the current user ID for use in queries
    private var currentUserId: Int = -1

    // Set the current user ID when user logs in
    fun setCurrentUserId(userId: Int) {
        currentUserId = userId
    }

    // Properties for accessing songs
    val allSongs: LiveData<List<Song>>
        get() = if (currentUserId > 0) {
            songDao.getSongsByUserId(currentUserId)
        } else {
            songDao.getAllSongs()
        }

    val likedSongs: LiveData<List<Song>>
        get() = if (currentUserId > 0) {
            songDao.getLikedSongsByUserId(currentUserId)
        } else {
            songDao.getLikedSongs()
        }

    val recentlyPlayed: LiveData<List<Song>>
        get() = if (currentUserId > 0) {
            songDao.getRecentlyPlayedByUserId(currentUserId)
        } else {
            songDao.getRecentlyPlayed()
        }

    // Insert a song
    suspend fun insert(song: Song): Long {
        // If user ID is set and the song doesn't have a user ID, add it
        val songToInsert = if (currentUserId > 0 && song.userId <= 0) {
            song.copy(userId = currentUserId)
        } else {
            song
        }

        return withContext(Dispatchers.IO) {
            songDao.insertSong(songToInsert)
        }
    }

    suspend fun getTotalSongCount(): Int {
        return withContext(Dispatchers.IO) {
            songDao.countAllSongs()
        }
    }

    suspend fun getOnlineSongCount(): Int {
        return withContext(Dispatchers.IO) {
            songDao.countOnlineSongs()
        }
    }

    suspend fun getAllSongsSync(): List<Song> {
        return withContext(Dispatchers.IO) {
            if (currentUserId > 0) {
                songDao.getSongsByUserIdSync(currentUserId)
            } else {
                songDao.getAllSongsSync()
            }
        }
    }

    // Update a song
    suspend fun update(song: Song): Int {
        return withContext(Dispatchers.IO) {
            songDao.updateSong(song)
        }
    }

    // Delete a song
    suspend fun delete(song: Song): Int {
        return withContext(Dispatchers.IO) {
            songDao.deleteSong(song)
        }
    }

    // Toggle like status for a song
    suspend fun toggleLike(songId: Long, isLiked: Boolean): Int {
        return withContext(Dispatchers.IO) {
            songDao.updateLikeStatus(songId, isLiked)
        }
    }

    // Update last played timestamp for a song
    suspend fun updateLastPlayed(songId: Long, timestamp: Long = System.currentTimeMillis()): Int {
        return withContext(Dispatchers.IO) {
            songDao.updateLastPlayed(songId, timestamp)
        }
    }

    // Get a song by ID
    fun getSongById(songId: Long): LiveData<Song> {
        return if (currentUserId > 0) {
            songDao.getSongByIdForUser(songId, currentUserId)
        } else {
            songDao.getSongById(songId)
        }
    }

    fun getNewestSongs(): LiveData<List<Song>> {
        return if (currentUserId > 0) {
            songDao.getNewestSongsByUserId(currentUserId)
        } else {
            songDao.getNewestSongs()
        }
    }

    // Get a song by ID directly (synchronously)
    suspend fun getSongByIdDirect(songId: Long): Song? {
        return withContext(Dispatchers.IO) {
            if (currentUserId > 0) {
                songDao.getSongByIdDirectForUser(songId, currentUserId)
            } else {
                songDao.getSongByIdDirect(songId)
            }
        }
    }

    // Count all songs
    fun getAllSongsCount(): Flow<Int> = flow {
        val count = withContext(Dispatchers.IO) {
            if (currentUserId > 0) {
                songDao.countAllSongsForUser(currentUserId)
            } else {
                songDao.countAllSongs()
            }
        }
        emit(count)
    }.flowOn(Dispatchers.IO)

    // Count liked songs
    fun getLikedSongsCount(): Flow<Int> = flow {
        val count = withContext(Dispatchers.IO) {
            if (currentUserId > 0) {
                songDao.countLikedSongsForUser(currentUserId)
            } else {
                songDao.countLikedSongs()
            }
        }
        emit(count)
    }.flowOn(Dispatchers.IO)

    // Count songs that have been played
    fun getListenedSongsCount(): Flow<Int> = flow {
        val count = withContext(Dispatchers.IO) {
            if (currentUserId > 0) {
                songDao.countListenedSongsForUser(currentUserId)
            } else {
                songDao.countListenedSongs()
            }
        }
        emit(count)
    }.flowOn(Dispatchers.IO)
}