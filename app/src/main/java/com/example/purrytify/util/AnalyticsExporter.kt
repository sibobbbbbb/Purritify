package com.example.purrytify.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.purrytify.data.repository.AnalyticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsExporter(
    private val context: Context,
    private val analyticsRepository: AnalyticsRepository
) {
    private val TAG = "AnalyticsExporter"

    /**
     * Export analytics data to CSV format (save to Downloads)
     */
    suspend fun exportToCSV(userId: Long, month: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Exporting analytics to CSV for user $userId, month $month")

                // Get analytics data
                val monthlyAnalytics = analyticsRepository.getMonthlyAnalytics(userId, month)
                val topArtists = analyticsRepository.getTopArtists(userId, month, 10)
                val topSongs = analyticsRepository.getTopSongs(userId, month, 10)
                val activeStreaks = analyticsRepository.getActiveStreaks(userId)

                if (monthlyAnalytics == null) {
                    Log.w(TAG, "No analytics data found for month $month")
                    return@withContext null
                }

                // Create CSV content
                val csvContent = buildString {
                    // Header
                    append("Purrytify Sound Capsule - $month\n\n")

                    // Summary
                    append("SUMMARY\n")
                    append("Total Listening Time,${analyticsRepository.formatListeningTime(monthlyAnalytics.totalListeningTime)}\n")
                    append("Unique Songs,${monthlyAnalytics.uniqueSongsCount}\n")
                    append("Unique Artists,${monthlyAnalytics.uniqueArtistsCount}\n")
                    append("Active Streaks,${activeStreaks.size}\n\n")

                    // Top Artists
                    append("TOP ARTISTS\n")
                    append("Rank,Artist,Listening Time,Play Count\n")
                    topArtists.forEachIndexed { index, artist ->
                        append("${index + 1},\"${artist.artistName}\",${analyticsRepository.formatListeningTime(artist.totalDuration)},${artist.playCount}\n")
                    }
                    append("\n")

                    // Top Songs
                    append("TOP SONGS\n")
                    append("Rank,Song,Artist,Listening Time,Play Count\n")
                    topSongs.forEachIndexed { index, song ->
                        append("${index + 1},\"${song.songTitle}\",\"${song.artistName}\",${analyticsRepository.formatListeningTime(song.totalDuration)},${song.playCount}\n")
                    }
                    append("\n")

                    // Day Streaks
                    append("DAY STREAKS (2+ DAYS)\n")
                    append("Song,Artist,Streak Days,Last Played\n")
                    activeStreaks.forEach { streak ->
                        append("\"${streak.songTitle}\",\"${streak.artistName}\",${streak.currentStreak},${streak.lastPlayedDate}\n")
                    }
                }

                val fileName = "purrytify_analytics_${month.replace("-", "_")}.csv"

                // Save to Downloads folder (Android 10+)
                return@withContext if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToDownloadsModern(csvContent, fileName)
                } else {
                    saveToDownloadsLegacy(csvContent, fileName)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error exporting to CSV", e)
                return@withContext null
            }
        }
    }

    /**
     * Save to Downloads folder (Android 10+)
     */
    private fun saveToDownloadsModern(content: String, fileName: String): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )

            uri?.let { fileUri ->
                context.contentResolver.openOutputStream(fileUri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                Log.d(TAG, "✅ File saved to Downloads: $fileName")
                fileUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to Downloads (modern)", e)
            null
        }
    }

    /**
     * Save to Downloads folder (Android 9 and below)
     */
    private fun saveToDownloadsLegacy(content: String, fileName: String): Uri? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(content)
            }

            Log.d(TAG, "✅ File saved to Downloads: ${file.absolutePath}")

            // Return FileProvider URI for sharing
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to Downloads (legacy)", e)
            null
        }
    }

    /**
     * Share analytics via Android share sheet
     */
    suspend fun shareAnalytics(userId: Long, month: String) {
        try {
            val uri = exportToCSV(userId, month)
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Purrytify Sound Capsule - $month")
                    putExtra(Intent.EXTRA_TEXT, "Here's my music analytics for $month from Purrytify!")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooserIntent = Intent.createChooser(shareIntent, "Share Sound Capsule")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)

                Log.d(TAG, "Analytics share intent launched")
            } else {
                Log.e(TAG, "Failed to create shareable file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing analytics", e)
        }
    }

    /**
     * Export current month analytics
     */
    suspend fun exportCurrentMonth(userId: Long): Uri? {
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        return exportToCSV(userId, currentMonth)
    }

    /**
     * Share current month analytics
     */
    suspend fun shareCurrentMonth(userId: Long) {
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        shareAnalytics(userId, currentMonth)
    }
}