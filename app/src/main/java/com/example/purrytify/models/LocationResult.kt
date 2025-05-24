package com.example.purrytify.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Model untuk hasil pemilihan lokasi
 */
@Parcelize
data class LocationResult(
    val countryCode: String,
    val countryName: String,
    val address: String?,
    val latitude: Double? = null,
    val longitude: Double? = null
) : Parcelable

/**
 * Response model untuk edit profile
 */
@Parcelize
data class EditProfileResponse(
    val success: Boolean,
    val message: String,
    val updatedProfile: UserProfile? = null
) : Parcelable

/**
 * Enum untuk sumber foto profil
 */
enum class PhotoSource {
    CAMERA,
    GALLERY
}