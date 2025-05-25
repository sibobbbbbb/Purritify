package com.example.purrytify.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.purrytify.models.OnlineSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class SongDownloadManager(private val context: Context) {

    private val TAG = "SongDownloadManager"

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    // Broadcast receiver for download completion
    private val downloadCompleteReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId != -1L) {
                    handleDownloadComplete(downloadId)
                }
            }
        }
    }

    // Download states
    sealed class DownloadState {
        data class Downloading(val songId: Int, val progress: Int) : DownloadState()
        data class Completed(val songId: Int, val localFilePath: String) : DownloadState()
        data class Failed(val songId: Int, val reason: String) : DownloadState()
    }

    data class DownloadResult(
        val songId: Int,
        val success: Boolean,
        val localFilePath: String? = null,
        val error: String? = null
    )

    // Track ongoing downloads
    private val _downloadStates = MutableStateFlow<Map<Int, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<Int, DownloadState>> = _downloadStates.asStateFlow()

    // Track download IDs
    private val downloadIds = mutableMapOf<Long, Int>() // downloadId -> songId
    private val songDownloadIds = mutableMapOf<Int, Long>() // songId -> downloadId

    init {
        // Register broadcast receiver for download completion
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

        // Using the appropriate flag based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API level 31) or newer requires a flag
            context.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // Older Android versions
            context.registerReceiver(downloadCompleteReceiver, filter)
        }
    }

    // Handle download completion
    private fun handleDownloadComplete(downloadId: Long) {
        val songId = downloadIds[downloadId] ?: return
        Log.d(TAG, "Handling completion for download ID: $downloadId, song ID: $songId")

        // Query download status
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusColumnIndex)

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    // Get local URI
                    val localUriColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUriString = cursor.getString(localUriColumnIndex)
                    val localUri = Uri.parse(localUriString)

                    // Get file path from URI
                    val filePath = getFilePathFromUri(localUri)

                    if (filePath != null) {
                        // Update state
                        updateDownloadState(songId, DownloadState.Completed(songId, filePath))
                        Log.d(TAG, "Download completed for song $songId: $filePath")
                    } else {
                        // Failed to get file path
                        handleDownloadFailure(songId, "Failed to get file path")
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    // Get reason
                    val reasonColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonColumnIndex)
                    val reasonText = getDownloadErrorReason(reason)

                    // Handle failure
                    handleDownloadFailure(songId, reasonText)
                }
            }
        }

        cursor.close()

        // Clean up
        downloadIds.remove(downloadId)
        songDownloadIds.remove(songId)
    }

    // Convert download error code to readable text
    private fun getDownloadErrorReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Device not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Error code: $reason"
        }
    }

    // Handle download failure
    private fun handleDownloadFailure(songId: Int, reason: String) {
        Log.e(TAG, "Download failed for song $songId: $reason")
        // Update state
        updateDownloadState(songId, DownloadState.Failed(songId, reason))
    }

    // Get file path from URI
    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            val path = uri.path
            if (path?.startsWith("file://") == true) {
                path.substring(7)  // Remove "file://" prefix
            } else {
                path
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file path from URI: ${e.message}")
            null
        }
    }

    // Update download state
    private fun updateDownloadState(songId: Int, state: DownloadState) {
        val currentStates = _downloadStates.value.toMutableMap()
        currentStates[songId] = state
        _downloadStates.value = currentStates
    }

    // Download a song
    fun downloadSong(song: OnlineSong): Long {
        // Check if song is already downloading
        if (songDownloadIds.containsKey(song.id)) {
            return songDownloadIds[song.id] ?: -1L
        }

        // Create download directory if it doesn't exist
        val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Downloads")
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }

        // Create file name from song details (sanitize the title to avoid invalid chars)
        val sanitizedTitle = song.title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val fileName = "${song.id}_${sanitizedTitle}.mp3"

        Log.d(TAG, "Starting download for song ${song.title} (ID: ${song.id}), URL: ${song.audioUrl}")

        // Create download request
        val request = DownloadManager.Request(Uri.parse(song.audioUrl))
            .setTitle(song.title)
            .setDescription("Downloading ${song.title} by ${song.artist}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, "Downloads/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        // Start download
        val downloadId = downloadManager.enqueue(request)

        // Store download ID
        downloadIds[downloadId] = song.id
        songDownloadIds[song.id] = downloadId

        // Update state
        updateDownloadState(song.id, DownloadState.Downloading(song.id, 0))

        Log.d(TAG, "Download started for song ${song.id} with download ID: $downloadId")
        return downloadId
    }

    // Check download progress
    fun checkDownloadProgress(songId: Int) {
        val downloadId = songDownloadIds[songId] ?: return

        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusColumnIndex)

            when (status) {
                DownloadManager.STATUS_RUNNING -> {
                    // Get progress
                    val bytesDownloadedColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    val bytesDownloaded = cursor.getLong(bytesDownloadedColumnIndex)
                    val bytesTotal = cursor.getLong(bytesTotalColumnIndex)

                    val progress = if (bytesTotal > 0) {
                        (bytesDownloaded * 100 / bytesTotal).toInt()
                    } else {
                        0
                    }

                    // Update state
                    updateDownloadState(songId, DownloadState.Downloading(songId, progress))
                }
                DownloadManager.STATUS_SUCCESSFUL -> {
                    // Call handleDownloadComplete to process the completed download
                    handleDownloadComplete(downloadId)
                }
                DownloadManager.STATUS_FAILED -> {
                    // Get reason
                    val reasonColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonColumnIndex)
                    val reasonText = getDownloadErrorReason(reason)

                    // Handle failure
                    handleDownloadFailure(songId, reasonText)
                }
            }
        }

        cursor.close()
    }

    // Cancel download
    fun cancelDownload(songId: Int): Boolean {
        val downloadId = songDownloadIds[songId] ?: return false

        // Remove from download manager
        val removed = downloadManager.remove(downloadId)

        // Clean up
        if (removed > 0) {
            downloadIds.remove(downloadId)
            songDownloadIds.remove(songId)

            // Update state
            val currentStates = _downloadStates.value.toMutableMap()
            currentStates.remove(songId)
            _downloadStates.value = currentStates

            Log.d(TAG, "Download canceled for song $songId")
            return true
        }

        return false
    }

    // Release resources
    fun release() {
        try {
            context.unregisterReceiver(downloadCompleteReceiver)
            Log.d(TAG, "Unregistered download complete receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    // Get download state for a song
    fun getDownloadState(songId: Int): DownloadState? {
        return _downloadStates.value[songId]
    }

    // Get all downloaded files
    fun getDownloadedFilePath(songId: Int): String? {
        val state = _downloadStates.value[songId]
        return if (state is DownloadState.Completed) {
            state.localFilePath
        } else {
            null
        }
    }
}

