package com.example.purrytify.models

/**
 * Model untuk request edit profile
 * Mendukung multipart/form-data untuk upload file
 */
data class EditProfileRequest(
    val location: String? = null,        // ISO 3166-1 alpha-2 country code (optional)
    val profilePhotoUri: String? = null  // URI local file untuk upload (optional)
)

/**
 * Response model untuk edit profile
 */
data class EditProfileResponse(
    val success: Boolean,
    val message: String,
    val updatedProfile: UserProfile? = null
)

/**
 * Model untuk hasil pemilihan lokasi
 */
data class LocationResult(
    val countryCode: String,
    val countryName: String,
    val address: String?,
    val latitude: Double? = null,
    val longitude: Double? = null
)

/**
 * Enum untuk sumber foto profil
 */
enum class PhotoSource {
    CAMERA,
    GALLERY
}