package com.example.purrytify.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log

/**
 * BroadcastReceiver untuk mendeteksi saat audio menjadi "noisy"
 * (headphone dicabut, bluetooth disconnect)
 */
class AudioNoisyReceiver(
    private val onAudioBecomingNoisy: () -> Unit
) : BroadcastReceiver() {

    private val TAG = "AudioNoisyReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            Log.d(TAG, "Audio becoming noisy - pausing playback")

            // Call the callback to pause playback
            onAudioBecomingNoisy()
        }
    }
}