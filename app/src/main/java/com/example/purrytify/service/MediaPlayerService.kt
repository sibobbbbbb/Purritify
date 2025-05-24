package com.example.purrytify.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.purrytify.models.Song
import com.example.purrytify.receivers.AudioNoisyReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

class MediaPlayerService : Service() {
    private val TAG = "MediaPlayerService"
    private val mediaPlayer = MediaPlayer()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration

    // Flags for the end of playback
    private val _reachedEndOfPlayback = MutableStateFlow(false)
    val reachedEndOfPlayback: StateFlow<Boolean> = _reachedEndOfPlayback

    // Track which song is being played to prevent duplicate song infinite loop
    private val _currentPlayingId = MutableStateFlow<Long?>(null)

    // Bonus features state
    private var shuffleEnabled = false
    private var repeatMode = 0 // 0: off, 1: repeat all, 2: repeat one

    private val binder = MediaPlayerBinder()
    private lateinit var localBroadcastManager: LocalBroadcastManager


    // Audio focus management
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest

    // Receiver for audio noisy events (e.g., headphone unplugged)
    private lateinit var audioNoisyReceiver: AudioNoisyReceiver
    private var audioNoisyReceiverRegistered = false


    inner class MediaPlayerBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Setup audio focus request
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()

        // Initialize audio noisy receiver
        audioNoisyReceiver = AudioNoisyReceiver {
            // Pause playback when audio becomes noisy
            if (mediaPlayer.isPlaying) {
                togglePlayPause()

                // Send broadcast to update UI
                val intent = Intent("com.example.purrytify.AUDIO_BECOMING_NOISY")
                localBroadcastManager.sendBroadcast(intent)
            }
        }

        // Set up completion listener
        mediaPlayer.setOnCompletionListener {
            Log.d(TAG, "Song completed playback")
            _isPlaying.value = false

            // Handle repeat one mode
            if (repeatMode == 2) {
                Log.d(TAG, "Repeat One mode active, replaying current song")
                _currentSong.value?.let { _ ->
                    // Replay the same song
                    playAgain()
                }
            } else {
                // For both Repeat All and No Repeat modes, let the ViewModel handle the next song logic
                // Send broadcast to notify of song completion
                val intent = Intent("com.example.purrytify.SONG_COMPLETED")

                // In normal mode (not repeat all), we need to check if we're at the end
                if (repeatMode == 0) {
                    // Check if we're at the end of all songs or queue
                    _reachedEndOfPlayback.value = true
                    intent.putExtra("END_OF_PLAYBACK", true)
                }

                // Add the current song ID to avoid infinite loops
                intent.putExtra("COMPLETED_SONG_ID", _currentPlayingId.value)

                // Send the broadcast to the ViewModel
                localBroadcastManager.sendBroadcast(intent)
            }
        }

        // Set up error listener
        mediaPlayer.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
            true // Return true to indicate we handled the error
        }
    }

    fun playSong(song: Song) {
        try {
            Log.d(TAG, "Playing song: ${song.title}, path: ${song.filePath}")

            // Request audio focus
            if (!requestAudioFocus()) {
                Log.e(TAG, "Failed to get audio focus")
                return
            }

            // Register audio noisy receiver
            if (!audioNoisyReceiverRegistered) {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                registerReceiver(audioNoisyReceiver, filter)
                audioNoisyReceiverRegistered = true
            }

            // Reset end of playback flag when starting a new song
            _reachedEndOfPlayback.value = false

            // Reset media player if currently playing another song
            mediaPlayer.reset()

            // Save the current song ID to track playback and prevent infinite loops
            _currentPlayingId.value = song.id

            // Check if path is content URI or local file path
            if (song.filePath.startsWith("content://")) {
                // Use ContentResolver for content URI
                val uri = song.filePath.toUri()
                val contentResolver = applicationContext.contentResolver
                val afd = contentResolver.openFileDescriptor(uri, "r")

                if (afd != null) {
                    mediaPlayer.setDataSource(afd.fileDescriptor)
                    afd.close()
                } else {
                    throw IOException("Cannot open file descriptor for URI: ${song.filePath}")
                }
            } else {
                // Regular local file
                mediaPlayer.setDataSource(song.filePath)
            }

            // Prepare and play
            mediaPlayer.prepare()
            mediaPlayer.start()

            // Update state
            _currentSong.value = song
            _isPlaying.value = true
            _duration.value = mediaPlayer.duration
            Log.d(TAG, "Song duration: ${mediaPlayer.duration}ms")

            // Start position tracking
            startPositionTracking()

        } catch (e: IOException) {
            Log.e(TAG, "Error playing song: ${e.message}")
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error: ${e.message}")
            abandonAudioFocus()
            e.printStackTrace()
        }
    }

    // Play the current song again (for repeat one)
    private fun playAgain() {
        try {
            mediaPlayer.seekTo(0)
            mediaPlayer.start()
            _isPlaying.value = true
            startPositionTracking()
        } catch (e: Exception) {
            Log.e(TAG, "Error replaying song: ${e.message}")
        }
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            Log.d(TAG, "Pausing playback")
            mediaPlayer.pause()
            _isPlaying.value = false
        } else {
            Log.d(TAG, "Resuming playback")
            mediaPlayer.start()
            _isPlaying.value = true
            startPositionTracking()
        }
    }

    fun seekTo(position: Int) {
        Log.d(TAG, "Seeking to position: ${position}ms")
        mediaPlayer.seekTo(position)
        _currentPosition.value = position
    }

    // Set shuffle mode
    fun setShuffleEnabled(enabled: Boolean) {
        Log.d(TAG, "Shuffle mode set to: $enabled")
        shuffleEnabled = enabled
    }

    // Set repeat mode
    fun setRepeatMode(mode: Int) {
        Log.d(TAG, "Repeat mode set to: $mode")
        repeatMode = mode
    }

    // Reset the end of playback flag
    fun resetEndOfPlaybackFlag() {
        _reachedEndOfPlayback.value = false
    }

    // Method to stop playback and move to end of track
    fun stopPlayback() {
        try {
            if (mediaPlayer.isPlaying) {
                // Get the total duration of the current song
                val totalDuration = mediaPlayer.duration

                // Seek to the end of the track
                mediaPlayer.seekTo(totalDuration)

                // Pause playback
                mediaPlayer.pause()

                // Update states
                _isPlaying.value = false
                _currentPosition.value = totalDuration
                _reachedEndOfPlayback.value = true

                Log.d(TAG, "Playback stopped and moved to end of track")
            } else {
                Log.d(TAG, "No need to stop playback, already paused")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback: ${e.message}")
        }
    }

    private fun startPositionTracking() {
        Thread {
            while (mediaPlayer.isPlaying) {
                try {
                    _currentPosition.value = mediaPlayer.currentPosition
                    Thread.sleep(500) // Update more frequently for smoother UI
                } catch (e: Exception) {
                    Log.e(TAG, "Error in position tracking: ${e.message}")
                    break
                }
            }
        }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")

        // Unregister audio noisy receiver
        if (audioNoisyReceiverRegistered) {
            unregisterReceiver(audioNoisyReceiver)
            audioNoisyReceiverRegistered = false
        }

        abandonAudioFocus()
        mediaPlayer.release()
        super.onDestroy()
    }

    // Audio focus listener
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume playback atau increase volume
                Log.d(TAG, "Audio focus gained")
                if (!mediaPlayer.isPlaying && _isPlaying.value) {
                    mediaPlayer.start()
                }
                mediaPlayer.setVolume(1.0f, 1.0f)
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Lost focus for an unbounded amount of time
                Log.d(TAG, "Audio focus lost")
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                    _isPlaying.value = false
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Lost focus for a short time
                Log.d(TAG, "Audio focus lost transient")
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.pause()
                }
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Lost focus but can duck (lower volume)
                Log.d(TAG, "Audio focus lost - can duck")
                mediaPlayer.setVolume(0.3f, 0.3f)
            }
        }
    }

    // Add method untuk request audio focus
    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(audioFocusRequest)

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    // Add method untuk abandon audio focus
    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    // Add method untuk handle audio routing changes
    fun handleAudioRouteChange() {
        Log.d(TAG, "Audio route changed")
        try {
            if (mediaPlayer.isPlaying) {
                val currentPosition = mediaPlayer.currentPosition
                mediaPlayer.pause()

                Thread.sleep(100)

                mediaPlayer.seekTo(currentPosition)
                mediaPlayer.start()

                Log.d(TAG, "Audio route transition completed, resumed at position: $currentPosition")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling audio route change: ${e.message}")
        }
    }

    // Set audio attributes based on the device type
    fun setAudioAttributesForDevice(deviceType: String) {
        try {
            val audioAttributes = when (deviceType) {
                "BLUETOOTH" -> {
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                }
                "WIRED_HEADSET" -> {
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                }
                else -> {
                    // Default untuk speaker internal
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                }
            }

            mediaPlayer.setAudioAttributes(audioAttributes)
            Log.d(TAG, "Audio attributes set for device type: $deviceType")

        } catch (e: Exception) {
            Log.e(TAG, "Error setting audio attributes: ${e.message}")
        }
    }
}