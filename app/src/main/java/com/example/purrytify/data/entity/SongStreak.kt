package com.example.purrytify.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_streaks")
data class SongStreak(
    @PrimaryKey
    val id: String, // combination of songId + userId + onlineId

    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "song_title")
    val songTitle: String,

    @ColumnInfo(name = "artist_name")
    val artistName: String,

    @ColumnInfo(name = "current_streak")
    val currentStreak: Int,

    @ColumnInfo(name = "last_played_date")
    val lastPlayedDate: String, // format: "yyyy-MM-dd"

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "is_online")
    val isOnline: Boolean = false,

    @ColumnInfo(name = "online_id")
    val onlineId: Int? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)