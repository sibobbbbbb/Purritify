package com.example.purrytify.models

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val filePath: String,
    val duration: Long,
    val isPlaying: Boolean = false,
    val isLiked: Boolean = false,
    val isOnline: Boolean = false,
    val onlineId: Int? = null,
    val lastPlayed: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val userId: Int = -1
)