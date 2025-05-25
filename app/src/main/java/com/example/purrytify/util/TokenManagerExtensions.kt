package com.example.purrytify.util

import android.content.Context
import com.example.purrytify.PurrytifyApp

/**
 * Helper function to get current user ID from token manager
 */
fun getCurrentUserId(context: Context): Long {
    return try {
        val app = context.applicationContext as PurrytifyApp
        app.tokenManager.getUserId().toLong()
    } catch (e: Exception) {
        -1L
    }
}