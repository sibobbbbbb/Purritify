package com.example.purrytify.util

import com.example.purrytify.models.OnlineSong
import com.example.purrytify.models.Song

fun OnlineSong.toSong(): Song {
    return Song(
        id = -this.id.toLong(), // Negative ID untuk online songs
        title = this.title,
        artist = this.artist,
        coverUrl = this.artworkUrl,
        filePath = this.audioUrl, // URL untuk streaming
        duration = this.getDurationInMillis(),
        isPlaying = false,
        isLiked = false,
        isOnline = true, // Mark sebagai online song
        onlineId = this.id,
        lastPlayed = null,
        addedAt = System.currentTimeMillis(),
        userId = -1
    )
}