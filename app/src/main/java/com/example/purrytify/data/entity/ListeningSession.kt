package com.example.purrytify.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "listening_sessions")
data class ListeningSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "song_title")
    val songTitle: String,

    @ColumnInfo(name = "artist_name")
    val artistName: String,

    @ColumnInfo(name = "start_time")
    val startTime: Long, // timestamp in milliseconds

    @ColumnInfo(name = "end_time")
    val endTime: Long?, // timestamp in milliseconds, null if still playing

    @ColumnInfo(name = "duration_listened")
    val durationListened: Long, // actual time listened in milliseconds

    @ColumnInfo(name = "total_duration")
    val totalDuration: Long, // total song duration in milliseconds

    @ColumnInfo(name = "date")
    val date: String, // format: "yyyy-MM-dd"

    @ColumnInfo(name = "month")
    val month: String, // format: "yyyy-MM"

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "is_online")
    val isOnline: Boolean = false,

    @ColumnInfo(name = "online_id")
    val onlineId: Int? = null
)