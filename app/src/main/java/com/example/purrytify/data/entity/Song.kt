package com.example.purrytify.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "artwork_path")
    val artworkPath: String,

    @ColumnInfo(name = "duration")
    val duration: Long, // dalam milidetik

    @ColumnInfo(name = "is_liked")
    val isLiked: Boolean = false,

    @ColumnInfo(name = "last_played")
    val lastPlayed: Long? = null, // timestamp kapan terakhir diputar

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "user_id", defaultValue = "-1")
    val userId: Int = -1,

    @ColumnInfo(name = "is_online")
    val isOnline: Boolean = false,

    @ColumnInfo(name = "online_id")
    val onlineId: Int? = null // Reference to the online song ID
)
