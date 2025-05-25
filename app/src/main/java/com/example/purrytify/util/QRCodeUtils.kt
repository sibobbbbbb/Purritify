package com.example.purrytify.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.example.purrytify.models.OnlineSong
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility class for QR Code generation and sharing
 */
object QRCodeUtils {
    private const val TAG = "QRCodeUtils"
    private const val QR_CODE_SIZE = 512 // 512x512 pixels

    /**
     * Generate QR Code bitmap from deep link
     */
    fun generateQRCode(deepLink: String, size: Int = QR_CODE_SIZE): Bitmap? {
        return try {
            Log.d(TAG, "Generating QR code for: $deepLink")

            val writer = MultiFormatWriter()
            val bitMatrix: BitMatrix = writer.encode(deepLink, BarcodeFormat.QR_CODE, size, size)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }

            Log.d(TAG, "QR code generated successfully")
            bitmap
        } catch (e: WriterException) {
            Log.e(TAG, "Error generating QR code", e)
            null
        }
    }

    /**
     * Generate QR Code for online song
     */
    fun generateSongQRCode(onlineSong: OnlineSong): Bitmap? {
        val deepLink = ShareUtils.createSongDeepLink(onlineSong.id)
        return generateQRCode(deepLink)
    }

    /**
     * Save QR Code bitmap to cache directory and return URI
     */
    fun saveQRCodeToCache(context: Context, bitmap: Bitmap, songTitle: String): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "qr_codes")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            // Clean filename
            val cleanTitle = songTitle.replace(Regex("[^a-zA-Z0-9\\s]"), "").take(20)
            val fileName = "qr_${cleanTitle}_${System.currentTimeMillis()}.png"
            val file = File(cacheDir, fileName)

            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            // Get URI using FileProvider
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            Log.d(TAG, "QR code saved to: ${file.absolutePath}")
            uri
        } catch (e: IOException) {
            Log.e(TAG, "Error saving QR code", e)
            null
        }
    }

    /**
     * Share QR Code image via Android ShareSheet
     */
    fun shareQRCode(context: Context, onlineSong: OnlineSong) {
        try {
            Log.d(TAG, "Sharing QR code for song: ${onlineSong.title}")

            val bitmap = generateSongQRCode(onlineSong)
            if (bitmap == null) {
                Log.e(TAG, "Failed to generate QR code")
                return
            }

            val uri = saveQRCodeToCache(context, bitmap, onlineSong.title)
            if (uri == null) {
                Log.e(TAG, "Failed to save QR code")
                return
            }

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Scan this QR code to listen to \"${onlineSong.title}\" by ${onlineSong.artist} on Purrytify!")
                putExtra(Intent.EXTRA_SUBJECT, "Share Song: ${onlineSong.title}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share QR Code")
            context.startActivity(chooser)

            Log.d(TAG, "QR code share intent launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing QR code", e)
        }
    }

    /**
     * Clean up old QR code files from cache
     */
    fun cleanupOldQRCodes(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "qr_codes")
            if (cacheDir.exists()) {
                val files = cacheDir.listFiles()
                val now = System.currentTimeMillis()
                val oneHour = 60 * 60 * 1000L // 1 hour in milliseconds

                files?.forEach { file ->
                    if (now - file.lastModified() > oneHour) {
                        file.delete()
                        Log.d(TAG, "Deleted old QR code: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up QR codes", e)
        }
    }
}