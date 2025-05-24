package com.example.purrytify.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.purrytify.models.EditProfileResponse
import com.example.purrytify.models.UserProfile
import com.example.purrytify.network.RetrofitClient
import com.example.purrytify.util.TokenManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import androidx.core.net.toUri

class UserRepository(private val tokenManager: TokenManager) {
    private val TAG = "UserRepository"

    suspend fun getUserProfile(): Result<UserProfile> {
        return try {
            val token = tokenManager.getToken()
            if (token.isNullOrEmpty()) {
                return Result.failure(Exception("Token tidak tersedia, silakan login terlebih dahulu"))
            }

            val response = RetrofitClient.apiService.getUserProfile("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Gagal mendapatkan profil: ${response.errorBody()?.string() ?: "Unknown error"}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Gagal mendapatkan profil: ${e.message}"))
        }
    }

    suspend fun editProfile(
        context: Context,
        location: String? = null,
        profilePhotoUri: String? = null
    ): Result<EditProfileResponse> {
        return try {
            val token = tokenManager.getToken()
            if (token.isNullOrEmpty()) {
                return Result.failure(Exception("Token tidak tersedia, silakan login terlebih dahulu"))
            }

            Log.d(TAG, "Editing profile with location: $location, photo: $profilePhotoUri")

            // Siapkan location part jika ada
            val locationPart = location?.let { loc ->
                Log.d(TAG, "Creating location part: $loc")
                loc.toRequestBody("text/plain".toMediaTypeOrNull())
            }

            // Siapkan profile photo part jika ada
            val profilePhotoPart = profilePhotoUri?.let { uriString ->
                Log.d(TAG, "Processing profile photo URI: $uriString")

                val uri = uriString.toUri()
                val file = uriToFile(context, uri, "profile_photo_${System.currentTimeMillis()}.jpg")

                if (file != null && file.exists()) {
                    val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData(
                        "profilePhoto",
                        file.name,
                        requestBody
                    )
                    Log.d(TAG, "Created profile photo part: ${file.name}, size: ${file.length()} bytes")
                    part
                } else {
                    Log.e(TAG, "Failed to create file from URI: $uriString")
                    throw IOException("Gagal memproses foto profil")
                }
            }

            // Validasi minimal ada satu perubahan
            if (locationPart == null && profilePhotoPart == null) {
                return Result.failure(Exception("Tidak ada perubahan untuk disimpan"))
            }

            // Kirim request ke server dengan parts terpisah
            Log.d(TAG, "Sending edit profile request to server")
            val response = RetrofitClient.apiService.editProfile(
                token = "Bearer $token",
                location = locationPart,
                profilePhoto = profilePhotoPart
            )

            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Profile updated successfully")
                val responseBody = response.body()!!
                Result.success(
                    EditProfileResponse(
                        success = true,
                        message = "Profile updated successfully",
                        updatedProfile = responseBody
                    )
                )
            } else {
                val errorMessage = response.errorBody()?.string() ?: "Unknown error"
                Log.e(TAG, "Edit profile failed: $errorMessage")
                Result.failure(Exception("Gagal mengubah profil: $errorMessage"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception in editProfile: ${e.message}")
            Result.failure(Exception("Gagal mengubah profil: ${e.message}"))
        }
    }

    // Fungsi helper untuk mengkonversi URI ke File
    private fun uriToFile(context: Context, uri: Uri, filename: String): File? {
        return try {
            Log.d(TAG, "Converting URI to file: $uri")

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open input stream for URI: $uri")

            val tempFile = File(context.cacheDir, filename)

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "File created successfully: ${tempFile.absolutePath}, size: ${tempFile.length()}")
            tempFile

        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to file: ${e.message}")
            null
        }
    }
}