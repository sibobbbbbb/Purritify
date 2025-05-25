package com.example.purrytify.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_analytics")
data class MonthlyAnalytics(
    @PrimaryKey
    val id: String, // combination of month + userId

    @ColumnInfo(name = "month")
    val month: String, // format: "yyyy-MM"

    @ColumnInfo(name = "user_id")
    val userId: Long,

    @ColumnInfo(name = "total_listening_time")
    val totalListeningTime: Long, // in milliseconds

    @ColumnInfo(name = "total_songs_played")
    val totalSongsPlayed: Int,

    @ColumnInfo(name = "unique_songs_count")
    val uniqueSongsCount: Int,

    @ColumnInfo(name = "unique_artists_count")
    val uniqueArtistsCount: Int,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)