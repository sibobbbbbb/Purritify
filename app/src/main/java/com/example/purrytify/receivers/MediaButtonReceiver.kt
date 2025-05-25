package com.example.purrytify.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.purrytify.service.PurrytifyNotificationManager

class MediaButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            PurrytifyNotificationManager.ACTION_PLAY_PAUSE -> {
                sendActionToService(context, "TOGGLE_PLAY_PAUSE")
            }
            PurrytifyNotificationManager.ACTION_NEXT -> {
                sendActionToService(context, "NEXT")
            }
            PurrytifyNotificationManager.ACTION_PREVIOUS -> {
                sendActionToService(context, "PREVIOUS")
            }
            PurrytifyNotificationManager.ACTION_STOP -> {
                sendActionToService(context, "STOP")
            }
        }
    }

    private fun sendActionToService(context: Context, action: String) {
        val serviceIntent = Intent(context, com.example.purrytify.service.MediaPlayerService::class.java)
        serviceIntent.putExtra("action", action)
        context.startService(serviceIntent)
    }
}