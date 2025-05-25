// Location: app/src/main/java/com/example/purrytify/models/OnlineSong.kt

package com.example.purrytify.models

import com.google.gson.annotations.SerializedName

data class OnlineSong(
    @SerializedName("id") val id: Int,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("artwork") val artworkUrl: String,
    @SerializedName("url") val audioUrl: String,
    @SerializedName("duration") val durationString: String,
    @SerializedName("country") val country: String,
    @SerializedName("rank") val rank: Int,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("updatedAt") val updatedAt: String
) {
    // Convert duration string (mm:ss) to milliseconds for compatibility with local songs
    fun getDurationInMillis(): Long {
        val parts = durationString.split(":")
        if (parts.size == 2) {
            val minutes = parts[0].toLongOrNull() ?: 0
            val seconds = parts[1].toLongOrNull() ?: 0
            return (minutes * 60 + seconds) * 1000
        }
        return 0
    }

    // Convert to Song model for UI
    fun toSong(localFilePath: String? = null, isDownloaded: Boolean = false): Song {
        return Song(
            id = -id.toLong(), // Use negative ID to avoid conflicts with local songs
            title = title,
            artist = artist,
            coverUrl = artworkUrl,
            filePath = localFilePath ?: audioUrl,
            duration = getDurationInMillis(),
            isPlaying = false,
            isLiked = false,
            isOnline = true,
            onlineId = id
        )
    }
}