package com.example.purrytify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.purrytify.MainActivity
import com.example.purrytify.R
import com.example.purrytify.models.Song
import com.example.purrytify.util.NotificationPermissionHandler

class PurrytifyNotificationManager(
    private val context: Context,
    private val mediaSession: MediaSessionCompat
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val channelId = "purrytify_music_channel"
    private val notificationId = 1

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.purrytify.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.purrytify.NEXT"
        const val ACTION_PREVIOUS = "com.example.purrytify.PREVIOUS"
        const val ACTION_STOP = "com.example.purrytify.STOP"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Purrytify Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showNotification(song: Song, isPlaying: Boolean, position: Long, duration: Long) {
        // Update media session metadata
        updateMediaSession(song, isPlaying, position, duration)

        // Load artwork and show notification
        loadArtworkAndShowNotification(song, isPlaying, position, duration)
    }

    private fun updateMediaSession(song: Song, isPlaying: Boolean, position: Long, duration: Long) {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                position,
                1.0f
            )

        mediaSession.setPlaybackState(stateBuilder.build())

        // Set metadata
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun loadArtworkAndShowNotification(song: Song, isPlaying: Boolean, position: Long, duration: Long) {
        if (song.coverUrl.isNotBlank() && !song.coverUrl.startsWith("android.resource://")) {
            // Load artwork from URL using Glide
            try {
                Glide.with(context)
                    .asBitmap()
                    .load(song.coverUrl)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            showNotificationWithArtwork(song, isPlaying, position, duration, resource)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            showNotificationWithArtwork(song, isPlaying, position, duration, createDefaultArtwork())
                        }

                        override fun onLoadFailed(errorDrawable: Drawable?) {
                            showNotificationWithArtwork(song, isPlaying, position, duration, createDefaultArtwork())
                        }
                    })
            } catch (e: Exception) {
                Log.e("NotificationManager", "Error loading artwork with Glide", e)
                showNotificationWithArtwork(song, isPlaying, position, duration, createDefaultArtwork())
            }
        } else {
            // Use default artwork
            showNotificationWithArtwork(song, isPlaying, position, duration, createDefaultArtwork())
        }
    }

    private fun createDefaultArtwork(): Bitmap {
        return try {
            // Create a simple colored bitmap as default
            val size = 200
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                color = ContextCompat.getColor(context, android.R.color.holo_purple)
                isAntiAlias = true
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

            // Draw a simple music note
            paint.color = ContextCompat.getColor(context, android.R.color.white)
            canvas.drawCircle(size * 0.6f, size * 0.7f, size * 0.15f, paint)
            canvas.drawRect(size * 0.73f, size * 0.3f, size * 0.78f, size * 0.7f, paint)

            bitmap
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error creating default artwork", e)
            // Return a minimal 1x1 bitmap if everything fails
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun showNotificationWithArtwork(
        song: Song,
        isPlaying: Boolean,
        position: Long,
        duration: Long,
        artwork: Bitmap?
    ) {
        // Check notification permission before showing
        if (!NotificationPermissionHandler.hasNotificationPermission(context)) {
            Log.w("NotificationManager", "Notification permission not granted, skipping notification")
            return
        }

        val notification = buildNotification(song, isPlaying, position, duration, artwork)

        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: SecurityException) {
            Log.e("NotificationManager", "Permission denied for notification", e)
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error showing notification", e)
        }
    }

    private fun buildNotification(
        song: Song,
        isPlaying: Boolean,
        position: Long,
        duration: Long,
        artwork: Bitmap?
    ): Notification {

        // Intent to open the app when notification is tapped
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player", true)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create action intents
        val playPauseIntent = createActionIntent(ACTION_PLAY_PAUSE)
        val nextIntent = createActionIntent(ACTION_NEXT)
        val previousIntent = createActionIntent(ACTION_PREVIOUS)
        val stopIntent = createActionIntent(ACTION_STOP)

        // Build the notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_audio_placeholder) // App logo as small icon
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText("Purrytify") // App name
            .setLargeIcon(artwork) // Cover art as large icon
            .setContentIntent(openAppPendingIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying) // Make it ongoing only when playing
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)

        // Add media style with transport controls
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2) // Show prev, play/pause, next in compact view
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopIntent)

        builder.setStyle(mediaStyle)

        // Add action buttons
        builder.addAction(
            R.drawable.ic_skip_previous,
            "Previous",
            previousIntent
        )

        // Add action buttons
        builder.addAction(
            R.drawable.ic_skip_previous,
            "Previous",
            previousIntent
        )

        builder.addAction(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
            if (isPlaying) "Pause" else "Play",
            playPauseIntent
        )

        builder.addAction(
            R.drawable.ic_skip_next,
            "Next",
            nextIntent
        )

        builder.addAction(
            R.drawable.ic_close,
            "Stop",
            stopIntent
        )

        // Add progress bar if duration is available
        if (duration > 0) {
            val progress = ((position.toFloat() / duration.toFloat()) * 100).toInt()
            builder.setProgress(100, progress, false)
        }

        return builder.build()
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun hideNotification() {
        try {
            notificationManager.cancel(notificationId)
        } catch (e: Exception) {
            Log.e("NotificationManager", "Error hiding notification", e)
        }
    }

    fun updateProgress(position: Long, duration: Long) {
        // This can be called to update just the progress without rebuilding the entire notification
        // For performance, we might want to limit how often this is called
    }
}