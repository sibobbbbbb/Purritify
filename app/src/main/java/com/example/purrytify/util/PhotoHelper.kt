package com.example.purrytify.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri


class PhotoHelper(private val context: Context) {
    private val TAG = "PhotoHelper"

    // SharedPreferences untuk persistent state
    private val prefs: SharedPreferences = context.getSharedPreferences("photo_helper_prefs", Context.MODE_PRIVATE)

    // Keys untuk SharedPreferences
    companion object {
        const val KEY_CAMERA_URI = "camera_uri"
        const val KEY_CAMERA_FILE_PATH = "camera_file_path"

        const val CAMERA_PERMISSION_REQUEST_CODE = 2001
        const val CAMERA_REQUEST_CODE = 2002
        const val GALLERY_REQUEST_CODE = 2003

        // Maximum file size untuk foto (5MB)
        const val MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024L
    }


    fun createCameraIntent(): Pair<Intent, Uri>? {
        return try {
            // Clear previous camera state
            clearCameraPhotoState()


            val photoFile = createImageFileInternal()

            Log.d(TAG, "Created camera photo file: ${photoFile.absolutePath}")


            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )

            // Simpan state ke SharedPreferences (persistent)
            prefs.edit().apply {
                putString(KEY_CAMERA_URI, photoUri.toString())
                putString(KEY_CAMERA_FILE_PATH, photoFile.absolutePath)
                apply()
            }

            // Buat intent camera
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }


            if (cameraIntent.resolveActivity(context.packageManager) == null) {
                Log.e(TAG, "No camera app available")
                clearCameraPhotoState()
                return null
            }

            Log.d(TAG, "Camera intent created with URI: $photoUri")
            Log.d(TAG, "Saved to persistent storage - URI: $photoUri, File: ${photoFile.absolutePath}")

            Pair(cameraIntent, photoUri)

        } catch (e: Exception) {
            Log.e(TAG, "Error creating camera intent: ${e.message}")
            clearCameraPhotoState()
            null
        }
    }

    fun createGalleryIntent(): Intent {
        return Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
    }


    private fun createImageFileInternal(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "PURRYTIFY_${timeStamp}_"


        val storageDir = File(context.filesDir, "pictures")


        if (!storageDir.exists()) {
            val created = storageDir.mkdirs()
            Log.d(TAG, "Pictures directory created: $created at ${storageDir.absolutePath}")
        }

        Log.d(TAG, "Creating image file in directory: ${storageDir.absolutePath}")

        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    }


    private fun createImageFileExternal(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "PURRYTIFY_${timeStamp}_"


        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "pictures") // Fallback ke internal


        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        Log.d(TAG, "Creating image file in external directory: ${storageDir.absolutePath}")

        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
    }


    fun getCameraPhotoUri(): Uri? {
        Log.d(TAG, "Getting camera photo URI from persistent storage")

        val uriString = prefs.getString(KEY_CAMERA_URI, null)
        val filePath = prefs.getString(KEY_CAMERA_FILE_PATH, null)

        Log.d(TAG, "Retrieved from storage - URI: $uriString, File: $filePath")

        if (uriString == null || filePath == null) {
            Log.w(TAG, "No camera photo data in persistent storage")
            return null
        }

        val uri = uriString.toUri()
        val file = File(filePath)

        if (!file.exists()) {
            Log.w(TAG, "Camera photo file does not exist: $filePath")
            clearCameraPhotoState()
            return null
        }

        val fileSize = file.length()
        if (fileSize <= 0) {
            Log.w(TAG, "Camera photo file is empty: $filePath")
            clearCameraPhotoState()
            return null
        }


        if (!file.canRead()) {
            Log.w(TAG, "Camera photo file cannot be read: $filePath")
            clearCameraPhotoState()
            return null
        }

        Log.d(TAG, "Camera photo file is valid - size: $fileSize bytes")
        return uri
    }


    private fun clearCameraPhotoState() {
        Log.d(TAG, "Clearing camera photo state from persistent storage")


        val filePath = prefs.getString(KEY_CAMERA_FILE_PATH, null)
        if (filePath != null) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d(TAG, "Deleted old camera photo file: $deleted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting old camera photo: ${e.message}")
            }
        }

        prefs.edit().apply {
            remove(KEY_CAMERA_URI)
            remove(KEY_CAMERA_FILE_PATH)
            apply()
        }
    }


    fun clearCameraPhotoUri() {
        Log.d(TAG, "Public clear camera photo URI called")
        clearCameraPhotoState()
    }


    fun isValidPhotoUri(uri: Uri?): Boolean {
        return try {
            if (uri == null) {
                Log.d(TAG, "URI is null")
                return false
            }

            Log.d(TAG, "Validating photo URI: $uri")

            // Untuk URI camera, cek file existence
            val filePath = prefs.getString(KEY_CAMERA_FILE_PATH, null)
            if (uri.toString() == prefs.getString(KEY_CAMERA_URI, null) && filePath != null) {
                val file = File(filePath)
                val isValid = file.exists() && file.length() > 0 && file.canRead()
                Log.d(TAG, "Camera URI validation - file exists: ${file.exists()}, size: ${file.length()}, readable: ${file.canRead()}")
                return isValid
            }

            // Untuk URI lainnya (gallery), cek dengan content resolver
            val inputStream = context.contentResolver.openInputStream(uri)
            val isValid = inputStream?.use {
                it.available() > 0
            } ?: false

            Log.d(TAG, "URI validation result: $isValid")
            isValid

        } catch (e: Exception) {
            Log.e(TAG, "Error validating photo URI: ${e.message}")
            false
        }
    }


    fun getFileSize(uri: Uri): Long {
        return try {
            Log.d(TAG, "Getting file size for URI: $uri")

            // Untuk URI camera, ambil size dari file langsung
            val filePath = prefs.getString(KEY_CAMERA_FILE_PATH, null)
            if (uri.toString() == prefs.getString(KEY_CAMERA_URI, null) && filePath != null) {
                val file = File(filePath)
                val size = file.length()
                Log.d(TAG, "Camera file size: $size bytes")
                return size
            }

            // Untuk URI lainnya, gunakan content resolver
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val size = inputStream.available().toLong()
                Log.d(TAG, "Content resolver file size: $size bytes")
                size
            } ?: -1L

        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size: ${e.message}")
            -1L
        }
    }


    fun debugPersistentState() {
        Log.d(TAG, "=== PERSISTENT STATE DEBUG ===")
        Log.d(TAG, "Stored URI: ${prefs.getString(KEY_CAMERA_URI, "null")}")
        Log.d(TAG, "Stored File Path: ${prefs.getString(KEY_CAMERA_FILE_PATH, "null")}")

        val filePath = prefs.getString(KEY_CAMERA_FILE_PATH, null)
        if (filePath != null) {
            val file = File(filePath)
            Log.d(TAG, "File exists: ${file.exists()}")
            Log.d(TAG, "File size: ${file.length()}")
            Log.d(TAG, "File readable: ${file.canRead()}")
            Log.d(TAG, "File path: ${file.absolutePath}")
        }
        Log.d(TAG, "==============================")
    }


    fun hasCameraPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }


    fun hasCamera(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }


    fun hasGallery(): Boolean {
        val galleryIntent = createGalleryIntent()
        return galleryIntent.resolveActivity(context.packageManager) != null
    }


    fun cleanupOldFiles() {
        try {
            val picturesDir = File(context.filesDir, "pictures")
            if (picturesDir.exists()) {
                val files = picturesDir.listFiles()
                files?.forEach { file ->
                    if (file.name.startsWith("PURRYTIFY_") && file.name.endsWith(".jpg")) {
                        // Hapus file yang lebih dari 1 hari
                        val fileAge = System.currentTimeMillis() - file.lastModified()
                        if (fileAge > 24 * 60 * 60 * 1000) { // 24 jam
                            val deleted = file.delete()
                            Log.d(TAG, "Cleaned up old file: ${file.name}, deleted: $deleted")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old files: ${e.message}")
        }
    }
}