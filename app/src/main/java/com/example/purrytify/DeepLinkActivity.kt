package com.example.purrytify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.purrytify.network.RetrofitClient
import com.example.purrytify.util.ShareUtils
import com.example.purrytify.util.TokenManager
import kotlinx.coroutines.launch

/**
 * Activity to handle deep link intents for song sharing
 */
class DeepLinkActivity : ComponentActivity() {
    private val TAG = "DeepLinkActivity"
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenManager = TokenManager(this)

        // Handle the deep link
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(intent: Intent) {
        val data: Uri? = intent.data

        if (data != null) {
            val deepLink = data.toString()
            Log.d(TAG, "Received deep link: $deepLink")

            if (ShareUtils.isValidPurrytifyDeepLink(deepLink)) {
                val songId = ShareUtils.extractSongIdFromDeepLink(deepLink)

                if (songId != null) {
                    if (tokenManager.isLoggedIn()) {
                        // User is logged in, fetch and play the song
                        fetchAndPlaySong(songId)
                    } else {
                        // User is not logged in, redirect to login
                        Toast.makeText(this, "Please log in to play this song", Toast.LENGTH_LONG).show()
                        redirectToLogin(songId)
                    }
                } else {
                    Log.e(TAG, "Invalid song ID in deep link")
                    showErrorAndClose("Invalid song link")
                }
            } else {
                Log.e(TAG, "Invalid Purrytify deep link: $deepLink")
                showErrorAndClose("Invalid Purrytify link")
            }
        } else {
            Log.e(TAG, "No data in deep link intent")
            showErrorAndClose("Invalid link")
        }
    }

    private fun fetchAndPlaySong(songId: Int) {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Fetching song with ID: $songId")

                val response = RetrofitClient.apiService.getSongById(songId)

                if (response.isSuccessful) {
                    val onlineSong = response.body()

                    if (onlineSong != null) {
                        Log.d(TAG, "Successfully fetched song: ${onlineSong.title}")

                        // Navigate to MainActivity and show player
                        val mainIntent = Intent(this@DeepLinkActivity, MainActivity::class.java).apply {
                            putExtra("PLAY_ONLINE_SONG_ID", songId)
                            putExtra("ONLINE_SONG_TITLE", onlineSong.title)
                            putExtra("ONLINE_SONG_ARTIST", onlineSong.artist)
                            putExtra("ONLINE_SONG_URL", onlineSong.audioUrl)
                            putExtra("ONLINE_SONG_ARTWORK", onlineSong.artworkUrl)
                            putExtra("SHOW_PLAYER", true)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }

                        startActivity(mainIntent)
                        finish()
                    } else {
                        Log.e(TAG, "Song data is null")
                        showErrorAndClose("Song not found")
                    }
                } else {
                    Log.e(TAG, "Failed to fetch song: ${response.code()}")
                    when (response.code()) {
                        404 -> showErrorAndClose("Song not found")
                        403 -> showErrorAndClose("Access denied. Please log in again.")
                        else -> showErrorAndClose("Failed to load song")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching song", e)
                showErrorAndClose("Network error. Please check your connection.")
            }
        }
    }

    private fun redirectToLogin(songId: Int) {
        val loginIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("PENDING_SONG_ID", songId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(loginIntent)
        finish()
    }

    private fun showErrorAndClose(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // Navigate to main activity
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        startActivity(mainIntent)
        finish()
    }
}