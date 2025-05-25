package com.example.purrytify.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.example.purrytify.data.entity.Song


@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY added_at DESC")
    fun getAllSongs(): LiveData<List<Song>>

    @Query("SELECT * FROM songs WHERE is_liked = 1 ORDER BY title ASC")
    fun getLikedSongs(): LiveData<List<Song>>

    @Query("SELECT * FROM songs WHERE last_played IS NOT NULL ORDER BY last_played DESC LIMIT 10")
    fun getRecentlyPlayed(): LiveData<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    fun getSongById(songId: Long): LiveData<Song>

    @Query("SELECT * FROM songs WHERE id = :songId")
    fun getSongByIdDirect(songId: Long): Song?

    @Query("SELECT * FROM songs ORDER BY added_at DESC LIMIT 5")
    fun getNewestSongs(): LiveData<List<Song>>

    // User-specific queries
    @Query("SELECT * FROM songs WHERE user_id = :userId ORDER BY added_at DESC")
    fun getSongsByUserId(userId: Int): LiveData<List<Song>>

    @Query("SELECT * FROM songs WHERE user_id = :userId AND is_liked = 1 ORDER BY title ASC")
    fun getLikedSongsByUserId(userId: Int): LiveData<List<Song>>

    @Query("SELECT * FROM songs WHERE user_id = :userId AND last_played IS NOT NULL ORDER BY last_played DESC LIMIT 10")
    fun getRecentlyPlayedByUserId(userId: Int): LiveData<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :songId AND user_id = :userId")
    fun getSongByIdForUser(songId: Long, userId: Int): LiveData<Song>

    @Query("SELECT * FROM songs WHERE id = :songId AND user_id = :userId")
    fun getSongByIdDirectForUser(songId: Long, userId: Int): Song?

    @Query("SELECT * FROM songs WHERE user_id = :userId ORDER BY added_at DESC LIMIT 5")
    fun getNewestSongsByUserId(userId: Int): LiveData<List<Song>>

    @Insert
    fun insertSong(song: Song): Long

    @Update
    fun updateSong(song: Song): Int

    @Delete
    fun deleteSong(song: Song): Int

    @Query("UPDATE songs SET is_liked = :isLiked WHERE id = :songId")
    fun updateLikeStatus(songId: Long, isLiked: Boolean): Int

    @Query("UPDATE songs SET last_played = :timestamp WHERE id = :songId")
    fun updateLastPlayed(songId: Long, timestamp: Long): Int

    // New queries for statistics
    @Query("SELECT COUNT(*) FROM songs")
    fun countAllSongs(): Int

    @Query("SELECT COUNT(*) FROM songs WHERE is_liked = 1")
    fun countLikedSongs(): Int

    @Query("SELECT COUNT(*) FROM songs WHERE last_played IS NOT NULL")
    fun countListenedSongs(): Int

    // Statistics for a specific user
    @Query("SELECT COUNT(*) FROM songs WHERE user_id = :userId")
    fun countAllSongsForUser(userId: Int): Int

    @Query("SELECT COUNT(*) FROM songs WHERE user_id = :userId AND is_liked = 1")
    fun countLikedSongsForUser(userId: Int): Int

    @Query("SELECT COUNT(*) FROM songs WHERE user_id = :userId AND last_played IS NOT NULL")
    fun countListenedSongsForUser(userId: Int): Int



    @Query("SELECT * FROM songs WHERE is_online = 1 AND user_id = :userId ORDER BY added_at DESC")
    fun getDownloadedOnlineSongs(userId: Int): List<Song>


    @Query("SELECT * FROM songs WHERE online_id = :onlineId LIMIT 1")
    fun getSongByOnlineId(onlineId: Int): Song?

    @Query("SELECT * FROM songs WHERE online_id = :onlineId AND user_id = :userId LIMIT 1")
    fun getDownloadedSongByOnlineId(onlineId: Int, userId: Int): Song?

    @Query("SELECT * FROM songs WHERE is_online = 1 AND user_id = :userId ORDER BY added_at DESC")
    fun getDownloadedOnlineSongsByUserId(userId: Int): LiveData<List<Song>>

    @Query("SELECT * FROM songs WHERE is_online = 1 ORDER BY added_at DESC")
    fun getDownloadedOnlineSongs(): LiveData<List<Song>>

    @Query("SELECT COUNT(*) FROM songs WHERE online_id = :onlineId")
    fun getCountByOnlineId(onlineId: Int): Int

    @Query("SELECT * FROM songs WHERE is_online = 1")
    fun getDownloadedOnlineSongsSync(): List<Song>

    @Query("SELECT * FROM songs ORDER BY added_at DESC")
    fun getAllSongsSync(): List<Song>

    @Query("SELECT * FROM songs WHERE user_id = :userId ORDER BY added_at DESC")
    fun getSongsByUserIdSync(userId: Int): List<Song>

    @Query("SELECT COUNT(*) FROM songs WHERE is_online = 1")
    fun countOnlineSongs(): Int

}