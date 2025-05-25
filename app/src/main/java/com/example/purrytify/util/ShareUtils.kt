package com.example.purrytify.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.purrytify.models.OnlineSong

/**
 * Utility class for handling song sharing functionality
 */
object ShareUtils {
    private const val TAG = "ShareUtils"
    private const val DEEP_LINK_BASE = "purrytify://song/"

    /**
     * Create deep link for a song
     */
    fun createSongDeepLink(songId: Int): String {
        val deepLink = "$DEEP_LINK_BASE$songId"
        Log.d(TAG, "Created deep link: $deepLink")
        return deepLink
    }

    /**
     * Share song via URL using Android ShareSheet
     */
    fun shareSongUrl(context: Context, onlineSong: OnlineSong) {
        try {
            val deepLink = createSongDeepLink(onlineSong.id)

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, deepLink)
                putExtra(Intent.EXTRA_SUBJECT, "Check out this song: ${onlineSong.title} by ${onlineSong.artist}")
            }

            val chooser = Intent.createChooser(shareIntent, "Share song")
            context.startActivity(chooser)

            Log.d(TAG, "Share intent launched for song: ${onlineSong.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing song URL", e)
        }
    }

    /**
     * Extract song ID from deep link
     */
    fun extractSongIdFromDeepLink(deepLink: String): Int? {
        return try {
            if (deepLink.startsWith(DEEP_LINK_BASE)) {
                val songIdString = deepLink.removePrefix(DEEP_LINK_BASE)
                songIdString.toIntOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting song ID from deep link: $deepLink", e)
            null
        }
    }

    /**
     * Validate if the URL is a valid Purrytify deep link
     */
    fun isValidPurrytifyDeepLink(url: String): Boolean {
        return url.startsWith(DEEP_LINK_BASE) && extractSongIdFromDeepLink(url) != null
    }
}