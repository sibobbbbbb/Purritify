// Location: app/src/main/java/com/example/purrytify/repository/OnlineSongsRepository.kt

package com.example.purrytify.repository

import android.util.Log
import androidx.lifecycle.LiveData
import com.example.purrytify.data.dao.SongDao
import com.example.purrytify.data.entity.Song
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnlineSongsRepository(private val songDao: SongDao) {

    private val TAG = "OnlineSongsRepository"

    // Get global top songs
    suspend fun getGlobalTopSongs(): Result<List<OnlineSong>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getGlobalTopSongs()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to load global top songs: ${response.message()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching global top songs", e)
                Result.failure(Exception("Error fetching global top songs: ${e.message}"))
            }
        }
    }

    // Get country top songs
    suspend fun getCountryTopSongs(countryCode: String): Result<List<OnlineSong>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = RetrofitClient.apiService.getCountryTopSongs(countryCode)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Failed to load country top songs: ${response.message()}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching country top songs", e)
                Result.failure(Exception("Error fetching country top songs: ${e.message}"))
            }
        }
    }

    // Save downloaded song to database
    suspend fun saveDownloadedSong(onlineSong: OnlineSong, localFilePath: String, userId: Int): Long {
        return withContext(Dispatchers.IO) {
            try {
                // Check if the song is already downloaded
                val existingSong = songDao.getDownloadedSongByOnlineId(onlineId = onlineSong.id, userId = userId)
                if (existingSong != null) {
                    Log.d(TAG, "Song already exists in database, skipping insertion: ${onlineSong.title}")
                    return@withContext existingSong.id
                }

                val song = Song(
                    title = onlineSong.title,
                    artist = onlineSong.artist,
                    filePath = localFilePath, // Local path to the downloaded file
                    artworkPath = onlineSong.artworkUrl, // We'll use the online artwork URL
                    duration = onlineSong.getDurationInMillis(),
                    isLiked = false,
                    lastPlayed = null,
                    addedAt = System.currentTimeMillis(),
                    userId = userId,
                    isOnline = true, // Mark as online song
                    onlineId = onlineSong.id // Save reference to the online ID
                )

                val id = songDao.insertSong(song)
                Log.d(TAG, "Song saved to database: ${onlineSong.title}, ID: $id")
                id
            } catch (e: Exception) {
                Log.e(TAG, "Error saving downloaded song", e)
                -1L
            }
        }
    }

    suspend fun getDownloadedOnlineSongsSync(): List<Song> {
        return withContext(Dispatchers.IO) {
            try {
                songDao.getDownloadedOnlineSongsSync()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting downloaded songs synchronously", e)
                emptyList()
            }
        }
    }
    // Check if song is already downloaded
    suspend fun isSongDownloaded(onlineId: Int, userId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val song = songDao.getDownloadedSongByOnlineId(onlineId, userId)
                song != null
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if song is downloaded", e)
                false
            }
        }
    }

    // Get downloaded online songs
    fun getDownloadedOnlineSongs() = songDao.getDownloadedOnlineSongs()

    // Get downloaded song by online ID
    suspend fun getSongByOnlineId(onlineId: Int, userId: Int): Song? {
        return withContext(Dispatchers.IO) {
            try {
                songDao.getDownloadedSongByOnlineId(onlineId, userId)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting song by online ID", e)
                null
            }
        }
    }
}