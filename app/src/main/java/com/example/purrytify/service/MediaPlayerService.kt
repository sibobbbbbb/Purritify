package com.example.purrytify.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.purrytify.models.Song
import com.example.purrytify.receivers.MediaButtonReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.example.purrytify.receivers.AudioNoisyReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import com.example.purrytify.service.AnalyticsService

class MediaPlayerService : Service() {
    private val TAG = "MediaPlayerService"
    private val mediaPlayer = MediaPlayer()
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: PurrytifyNotificationManager
    private lateinit var mediaButtonReceiver: MediaButtonReceiver
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressUpdateJob: Job? = null
    private lateinit var analyticsService: AnalyticsService
    private var isAnalyticsInitialized = false

    // State flows
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration

    private val _reachedEndOfPlayback = MutableStateFlow(false)
    val reachedEndOfPlayback: StateFlow<Boolean> = _reachedEndOfPlayback

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

    private lateinit var audioDeviceSwitchReceiver: BroadcastReceiver

    inner class MediaPlayerBinder : Binder() {
        fun getService(): MediaPlayerService = this@MediaPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, "PurrytifyMediaSession")
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )

        // Set up MediaSession callback
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                togglePlayPause()
            }

            override fun onPause() {
                togglePlayPause()
            }

            override fun onSkipToNext() {
                handleNextAction()
            }

            override fun onSkipToPrevious() {
                handlePreviousAction()
            }

            override fun onStop() {
                handleStopAction()
            }

            override fun onSeekTo(pos: Long) {
                seekTo(pos.toInt())
            }
        })

        mediaSession.isActive = true

        // Initialize notification manager
        notificationManager = PurrytifyNotificationManager(this, mediaSession)

        // Initialize media button receiver
        mediaButtonReceiver = MediaButtonReceiver()
        registerMediaButtonReceiver()

        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        try {
            val app = applicationContext as com.example.purrytify.PurrytifyApp
            analyticsService = app.analyticsService
            Log.d(TAG, "✅ Analytics service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing analytics service", e)
        }

        setupMediaPlayerListeners()
    }

    fun initializeAnalyticsForUser(userId: Long) {
        try {
            if (::analyticsService.isInitialized) {
                analyticsService.initializeForUser(userId)
                isAnalyticsInitialized = true
                Log.d(TAG, "✅ Analytics initialized for user: $userId")
            } else {
                Log.e(TAG, "❌ Analytics service not initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing analytics for user", e)
            isAnalyticsInitialized = false
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerMediaButtonReceiver() {
        val filter = IntentFilter().apply {
            addAction(PurrytifyNotificationManager.ACTION_PLAY_PAUSE)
            addAction(PurrytifyNotificationManager.ACTION_NEXT)
            addAction(PurrytifyNotificationManager.ACTION_PREVIOUS)
            addAction(PurrytifyNotificationManager.ACTION_STOP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                mediaButtonReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            // Older Android versions
            registerReceiver(mediaButtonReceiver, filter)
        }
    }

    private fun setupMediaPlayerListeners() {
        // Set up completion listener
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

            if (isAnalyticsInitialized) {
                analyticsService.endTracking()
                Log.d(TAG, "Ended analytics tracking for completed song")
            }

            hideNotification()

            // Handle repeat one mode
            if (repeatMode == 2) {
                Log.d(TAG, "Repeat One mode active, replaying current song")
                _currentSong.value?.let {
                    playAgain()
                }
            } else {
                // Send broadcast to notify of song completion
                val intent = Intent("com.example.purrytify.SONG_COMPLETED")

                if (repeatMode == 0) {
                    _reachedEndOfPlayback.value = true
                    intent.putExtra("END_OF_PLAYBACK", true)
                }

                intent.putExtra("COMPLETED_SONG_ID", _currentPlayingId.value)
                localBroadcastManager.sendBroadcast(intent)
            }
        }

        // Set up error listener
        mediaPlayer.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
            hideNotification()
            true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.getStringExtra("action")}")

        intent?.getStringExtra("action")?.let { action ->
            when (action) {
                "TOGGLE_PLAY_PAUSE" -> togglePlayPause()
                "NEXT" -> handleNextAction()
                "PREVIOUS" -> handlePreviousAction()
                "STOP" -> handleStopAction()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    fun playOnlineSongWithId(
        audioUrl: String,
        title: String,
        artist: String,
        coverUrl: String,
        onlineId: Int
    ) {
        Log.d(TAG, "=== PLAYING ONLINE SONG WITH ID ===")
        Log.d(TAG, "Online ID: $onlineId")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "Artist: $artist")

        try {
            // Reset and prepare media player
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(audioUrl)
            mediaPlayer?.prepareAsync()

            val onlineSong = Song(
                id = System.currentTimeMillis(),
                title = title,
                artist = artist,
                coverUrl = coverUrl,
                filePath = audioUrl,
                duration = 0L, // Will be updated when prepared
                isPlaying = false,
                isLiked = false,
                isOnline = true,
                onlineId = onlineId,
                lastPlayed = System.currentTimeMillis(),
                addedAt = System.currentTimeMillis(),
                userId = -1
            )

            Log.d(TAG, "Created online song in service:")
            Log.d(TAG, "  - isOnline: ${onlineSong.isOnline}")
            Log.d(TAG, "  - onlineId: ${onlineSong.onlineId}")

            // Set current song immediately
            _currentSong.value = onlineSong

            // Setup prepared listener
            mediaPlayer?.setOnPreparedListener { mp ->
                Log.d(TAG, "=== ONLINE SONG PREPARED ===")

                val duration = mp.duration

                val updatedSong = onlineSong.copy(
                    duration = duration.toLong(),
                    isPlaying = true
                )

                Log.d(TAG, "Updated online song:")
                Log.d(TAG, "  - isOnline: ${updatedSong.isOnline}")
                Log.d(TAG, "  - onlineId: ${updatedSong.onlineId}")
                Log.d(TAG, "  - duration: ${updatedSong.duration}")

                _currentSong.value = updatedSong

                mp.start()
                _isPlaying.value = true
                _duration.value = duration

                if (isAnalyticsInitialized) {
                    analyticsService.startTrackingSong(updatedSong)
                    Log.d(TAG, "Started analytics tracking for online song: $title")
                }

                Log.d(TAG, "=== ONLINE PLAYBACK STARTED ===")
                Log.d(TAG, "Final state - isOnline: ${_currentSong.value?.isOnline}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error playing online song with ID", e)
        }
    }

    fun playOnlineSong(audioUrl: String, title: String, artist: String, coverUrl: String) {
        // Call new method with default ID
        playOnlineSongWithId(audioUrl, title, artist, coverUrl, -1)
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

            mediaPlayer.reset()
            _currentPlayingId.value = song.id

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
                mediaPlayer.setDataSource(song.filePath)
            }

            mediaPlayer.prepare()
            mediaPlayer.start()

            _currentSong.value = song
            _isPlaying.value = true
            _duration.value = mediaPlayer.duration

            if (isAnalyticsInitialized) {
                analyticsService.startTrackingSong(song)
                Log.d(TAG, "Started analytics tracking for: ${song.title}")
            }

            Log.d(TAG, "Offline song prepared and started: ${song.title}, duration: ${mediaPlayer.duration}ms")

            startPositionTracking()
            showNotification()

        } catch (e: IOException) {
            Log.e(TAG, "Error playing offline song: ${e.message}")
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error playing offline song: ${e.message}")
            abandonAudioFocus()
            e.printStackTrace()
        }
    }


    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            Log.d(TAG, "Pausing playback")
            mediaPlayer.pause()
            _isPlaying.value = false

            if (isAnalyticsInitialized) {
                analyticsService.pauseTracking()
            }
        } else {
            Log.d(TAG, "Resuming playback")
            mediaPlayer.start()
            _isPlaying.value = true
            startPositionTracking()
        }
        showNotification()
    }

    fun seekTo(position: Int) {
        Log.d(TAG, "Seeking to position: ${position}ms")
        mediaPlayer.seekTo(position)
        _currentPosition.value = position
        showNotification()
    }

    private fun handleNextAction() {
        // Send broadcast to MainViewModel to handle next song logic
        val intent = Intent("com.example.purrytify.MEDIA_BUTTON_ACTION")
        intent.putExtra("action", "NEXT")
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun handlePreviousAction() {
        // Send broadcast to MainViewModel to handle previous song logic
        val intent = Intent("com.example.purrytify.MEDIA_BUTTON_ACTION")
        intent.putExtra("action", "PREVIOUS")
        localBroadcastManager.sendBroadcast(intent)
    }

    private fun handleStopAction() {
        Log.d(TAG, "Stop action received")
        stopPlayback()
        hideNotification()

        // Send broadcast to update UI
        val intent = Intent("com.example.purrytify.MEDIA_BUTTON_ACTION")
        intent.putExtra("action", "STOP")
        localBroadcastManager.sendBroadcast(intent)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        Log.d(TAG, "Shuffle mode set to: $enabled")
        shuffleEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        Log.d(TAG, "Repeat mode set to: $mode")
        repeatMode = mode
    }

    fun resetEndOfPlaybackFlag() {
        _reachedEndOfPlayback.value = false
    }

    fun stopPlayback() {
        try {
            if (mediaPlayer.isPlaying) {
                val totalDuration = mediaPlayer.duration
                mediaPlayer.seekTo(totalDuration)
                mediaPlayer.pause()
                _isPlaying.value = false
                _currentPosition.value = totalDuration
                _reachedEndOfPlayback.value = true

                if (isAnalyticsInitialized) {
                    analyticsService.endTracking()
                    Log.d(TAG, "Ended analytics tracking for stopped song")
                }
                Log.d(TAG, "Playback stopped and moved to end of track")
            } else {
                Log.d(TAG, "No need to stop playback, already paused")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping playback: ${e.message}")
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

    private fun startPositionTracking() {
        // Cancel existing job first
        progressUpdateJob?.cancel()

        progressUpdateJob = serviceScope.launch {
            try {
                while (isActive && mediaPlayer?.isPlaying == true) {
                    try {
                        val position = mediaPlayer?.currentPosition ?: 0
                        _currentPosition.value = position

                        if (isAnalyticsInitialized) {
                            analyticsService.updateTrackingProgress(position.toLong(), true)
                        }
                        delay(1000) // Update every second
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting current position", e)
                        break
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "Error in position tracking", e)
                }
            }
        }
    }

    private fun stopPositionTracking() {
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    private fun showNotification() {
        _currentSong.value?.let { song ->
            notificationManager.showNotification(
                song,
                _isPlaying.value,
                _currentPosition.value.toLong(),
                _duration.value.toLong()
            )
        }
    }

    private fun hideNotification() {
        notificationManager.hideNotification()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")

        // Clean up
        progressUpdateJob?.cancel()
        stopPositionTracking()

        // Unregister audio noisy receiver
        if (audioNoisyReceiverRegistered) {
            unregisterReceiver(audioNoisyReceiver)
            audioNoisyReceiverRegistered = false
        }

        abandonAudioFocus()
        mediaPlayer.release()
        mediaSession.release()
        hideNotification()

        try {
            unregisterReceiver(mediaButtonReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering media button receiver: ${e.message}")
        }

        if (isAnalyticsInitialized) {
            analyticsService.cleanup()
            Log.d(TAG, "Analytics service cleaned up")
        }

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


    fun handleAudioDeviceSwitch(deviceType: String) {
        Log.d(TAG, "Handling audio device switch to: $deviceType")

        try {
            val wasPlaying = mediaPlayer.isPlaying
            val currentPosition = if (wasPlaying) mediaPlayer.currentPosition else 0

            // Pause playback temporarily
            if (wasPlaying) {
                mediaPlayer.pause()
            }

            // Set audio attributes based on device type
            setAudioAttributesForDevice(deviceType)

            // Wait for audio routing to settle
            Thread.sleep(150)

            // Resume playback if it was playing
            if (wasPlaying) {
                mediaPlayer.seekTo(currentPosition)
                mediaPlayer.start()

                // Update UI state
                _isPlaying.value = true
                startPositionTracking()
                showNotification()
            }

            Log.d(TAG, "Audio device switch completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling audio device switch: ${e.message}")

            // Try to recover playback
            try {
                if (mediaPlayer.isPlaying) {
                    _isPlaying.value = true
                    startPositionTracking()
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to recover playback: ${ex.message}")
            }
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

    fun handleUserLogout() {
        if (isAnalyticsInitialized) {
            analyticsService.handleUserLogout()
            isAnalyticsInitialized = false
            Log.d(TAG, "Analytics tracking stopped due to user logout")
        }
    }

    fun getAnalyticsService(): AnalyticsService? {
        return if (isAnalyticsInitialized) analyticsService else null
    }
}