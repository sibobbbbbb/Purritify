package com.example.purrytify

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log


class CoordinateShareActivity : Activity() {

    private val TAG = "CoordinateShareActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "CoordinateShareActivity started")

        // Handle intent data
        val intentData = intent.data
        val intentText = intent.getStringExtra(Intent.EXTRA_TEXT)

        Log.d(TAG, "Intent data: $intentData")
        Log.d(TAG, "Intent text: $intentText")

        var coordinateData: String? = null

        // Try to get coordinate from data URI
        if (intentData != null) {
            coordinateData = intentData.toString()
            Log.d(TAG, "Got coordinate from data: $coordinateData")
        }
        // Try to get coordinate from text
        else if (!intentText.isNullOrBlank()) {
            coordinateData = intentText
            Log.d(TAG, "Got coordinate from text: $coordinateData")
        }

        // Return result
        val resultIntent = Intent().apply {
            if (coordinateData != null) {
                data = android.net.Uri.parse(coordinateData)
                putExtra("coordinate_data", coordinateData)
            }
        }

        setResult(RESULT_OK, resultIntent)

        Log.d(TAG, "Finishing with result")
        finish()
    }
}