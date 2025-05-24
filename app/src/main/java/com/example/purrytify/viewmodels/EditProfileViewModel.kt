package com.example.purrytify.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.models.LocationResult
import com.example.purrytify.models.PhotoSource
import com.example.purrytify.models.UserProfile
import com.example.purrytify.repository.UserRepository
import com.example.purrytify.util.LocationHelper
import com.example.purrytify.util.PhotoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel untuk mengelola edit profile
 *
 * Fungsi utama:
 * 1. Mengelola state UI untuk edit profile
 * 2. Handle location selection (GPS dan manual)
 * 3. Handle photo selection (camera dan gallery)
 * 4. Kirim perubahan ke server
 * 5. Validasi input dan error handling
 */
class EditProfileViewModel(
    application: Application,
    private val userRepository: UserRepository
) : AndroidViewModel(application) {

    private val TAG = "EditProfileViewModel"
    private val context = getApplication<Application>().applicationContext

    // Helper classes
    val locationHelper = LocationHelper(context)
    val photoHelper = PhotoHelper(context)

    // Current profile data
    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    // UI State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Location state
    private val _selectedLocation = MutableStateFlow<LocationResult?>(null)
    val selectedLocation: StateFlow<LocationResult?> = _selectedLocation.asStateFlow()

    private val _isLoadingLocation = MutableStateFlow(false)
    val isLoadingLocation: StateFlow<Boolean> = _isLoadingLocation.asStateFlow()

    // Photo state
    private val _selectedPhotoUri = MutableStateFlow<Uri?>(null)
    val selectedPhotoUri: StateFlow<Uri?> = _selectedPhotoUri.asStateFlow()

    // Permission state
    private val _needLocationPermission = MutableStateFlow(false)
    val needLocationPermission: StateFlow<Boolean> = _needLocationPermission.asStateFlow()

    private val _needCameraPermission = MutableStateFlow(false)
    val needCameraPermission: StateFlow<Boolean> = _needCameraPermission.asStateFlow()

    /**
     * Fungsi untuk load current profile data
     *
     * Alur kerja:
     * 1. Set loading state
     * 2. Call repository untuk get profile
     * 3. Update current profile state
     * 4. Handle error jika ada
     */
    fun loadCurrentProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val result = userRepository.getUserProfile()
                result.onSuccess { profile ->
                    _currentProfile.value = profile
                    Log.d(TAG, "Profile loaded: ${profile.username}")
                }.onFailure { exception ->
                    _errorMessage.value = exception.message
                    Log.e(TAG, "Failed to load profile: ${exception.message}")
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load profile: ${e.message}"
                Log.e(TAG, "Exception loading profile: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fungsi untuk mendapatkan lokasi current menggunakan GPS
     *
     * Alur kerja:
     * 1. Check location permission
     * 2. Check location services enabled
     * 3. Request current location dari GPS
     * 4. Convert koordinat ke country code
     * 5. Update selected location state
     */
    fun getCurrentLocation() {
        viewModelScope.launch {
            try {
                if (!locationHelper.hasLocationPermission()) {
                    _needLocationPermission.value = true
                    return@launch
                }

                if (!locationHelper.isLocationEnabled()) {
                    _errorMessage.value = "Please enable location services"
                    return@launch
                }

                _isLoadingLocation.value = true
                _errorMessage.value = null

                val locationResult = locationHelper.getCurrentLocation()
                if (locationResult != null) {
                    _selectedLocation.value = locationResult
                    _successMessage.value = "Location detected: ${locationResult.countryName}"
                    Log.d(TAG, "Location obtained: ${locationResult.countryCode}")
                } else {
                    _errorMessage.value = "Failed to get current location"
                }

            } catch (e: Exception) {
                _errorMessage.value = "Error getting location: ${e.message}"
                Log.e(TAG, "Exception getting location: ${e.message}")
            } finally {
                _isLoadingLocation.value = false
            }
        }
    }

    /**
     * Fungsi untuk handle hasil dari Google Maps location picker
     *
     * @param locationResult Hasil dari Google Maps
     */
    fun setManualLocation(locationResult: LocationResult) {
        _selectedLocation.value = locationResult
        _successMessage.value = "Location selected: ${locationResult.countryName}"
        Log.d(TAG, "Manual location set: ${locationResult.countryCode}")
    }

    /**
     * Fungsi untuk clear selected location
     */
    fun clearSelectedLocation() {
        _selectedLocation.value = null
        Log.d(TAG, "Selected location cleared")
    }

    /**
     * Fungsi untuk handle photo selection dari camera
     *
     * FIXED: Better debugging dan error messages
     */
    fun setPhotoFromCamera() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== CAMERA PHOTO PROCESSING START ===")

                // Ambil URI dari PhotoHelper
                val photoUri = photoHelper.getCameraPhotoUri()

                Log.d(TAG, "Retrieved camera photo URI: $photoUri")

                if (photoUri == null) {
                    val errorMsg = "Failed to capture photo - no photo URI available"
                    Log.e(TAG, errorMsg)
                    _errorMessage.value = errorMsg
                    return@launch
                }

                Log.d(TAG, "Validating photo URI...")
                if (!photoHelper.isValidPhotoUri(photoUri)) {
                    val errorMsg = "Invalid photo file or file doesn't exist"
                    Log.e(TAG, errorMsg)
                    _errorMessage.value = errorMsg
                    return@launch
                }

                Log.d(TAG, "Getting file size...")
                val fileSize = photoHelper.getFileSize(photoUri)
                Log.d(TAG, "Photo file size: $fileSize bytes")

                if (fileSize <= 0) {
                    val errorMsg = "Photo file is empty or corrupted (size: $fileSize)"
                    Log.e(TAG, errorMsg)
                    _errorMessage.value = errorMsg
                    return@launch
                }

                if (fileSize > PhotoHelper.MAX_PHOTO_SIZE_BYTES) {
                    val errorMsg = "Photo file too large (${fileSize / 1024 / 1024}MB, max 5MB)"
                    Log.e(TAG, errorMsg)
                    _errorMessage.value = errorMsg
                    return@launch
                }

                Log.d(TAG, "Photo validation successful, setting selected photo URI")
                _selectedPhotoUri.value = photoUri
                _successMessage.value = "Photo captured successfully"

                Log.d(TAG, "=== CAMERA PHOTO PROCESSING SUCCESS ===")
                Log.d(TAG, "Final URI: $photoUri, Size: $fileSize bytes")

            } catch (e: Exception) {
                val errorMsg = "Error processing camera photo: ${e.message}"
                Log.e(TAG, "=== CAMERA PHOTO PROCESSING ERROR ===")
                Log.e(TAG, errorMsg, e)
                _errorMessage.value = errorMsg
            }
        }
    }

    /**
     * Fungsi untuk handle photo selection dari gallery
     *
     * @param photoUri URI foto dari gallery
     */
    fun setPhotoFromGallery(photoUri: Uri?) {
        viewModelScope.launch {
            try {
                if (photoUri == null) {
                    _errorMessage.value = "No photo selected"
                    return@launch
                }

                if (!photoHelper.isValidPhotoUri(photoUri)) {
                    _errorMessage.value = "Invalid photo file"
                    return@launch
                }

                val fileSize = photoHelper.getFileSize(photoUri)
                if (fileSize > PhotoHelper.MAX_PHOTO_SIZE_BYTES) {
                    _errorMessage.value = "Photo file too large (max 5MB)"
                    return@launch
                }

                _selectedPhotoUri.value = photoUri
                _successMessage.value = "Photo selected successfully"
                Log.d(TAG, "Photo from gallery set: $photoUri")

            } catch (e: Exception) {
                _errorMessage.value = "Error processing photo: ${e.message}"
                Log.e(TAG, "Exception processing gallery photo: ${e.message}")
            }
        }
    }

    /**
     * Fungsi untuk clear selected photo
     */
    fun clearSelectedPhoto() {
        _selectedPhotoUri.value = null
        photoHelper.clearCameraPhotoUri()
        Log.d(TAG, "Selected photo cleared")
    }

    /**
     * Fungsi untuk save profile changes ke server
     *
     * Alur kerja:
     * 1. Validate changes (minimal ada location atau photo)
     * 2. Prepare data untuk request
     * 3. Call repository untuk edit profile
     * 4. Update current profile dengan data terbaru
     * 5. Clear selected changes
     * 6. Show success message
     */
    fun saveProfileChanges() {
        viewModelScope.launch {
            try {
                val locationCode = _selectedLocation.value?.countryCode
                val photoUri = _selectedPhotoUri.value

                // Validate ada perubahan
                if (locationCode == null && photoUri == null) {
                    _errorMessage.value = "No changes to save"
                    return@launch
                }

                _isLoading.value = true
                _errorMessage.value = null

                Log.d(TAG, "Saving profile changes - Location: $locationCode, Photo: $photoUri")

                // Call repository untuk edit profile
                val result = userRepository.editProfile(
                    context = context,
                    location = locationCode,
                    profilePhotoUri = photoUri?.toString()
                )

                result.onSuccess { response ->
                    // Update current profile dengan data terbaru
                    response.updatedProfile?.let { updatedProfile ->
                        _currentProfile.value = updatedProfile
                    }

                    // Clear selected changes
                    _selectedLocation.value = null
                    _selectedPhotoUri.value = null
                    photoHelper.clearCameraPhotoUri()

                    _successMessage.value = response.message
                    Log.d(TAG, "Profile updated successfully")

                }.onFailure { exception ->
                    _errorMessage.value = exception.message
                    Log.e(TAG, "Failed to update profile: ${exception.message}")
                }

            } catch (e: Exception) {
                _errorMessage.value = "Error saving profile: ${e.message}"
                Log.e(TAG, "Exception saving profile: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fungsi untuk check apakah ada perubahan yang belum disave
     *
     * @return true jika ada perubahan, false jika tidak
     */
    fun hasUnsavedChanges(): Boolean {
        return _selectedLocation.value != null || _selectedPhotoUri.value != null
    }

    /**
     * Fungsi untuk clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Fungsi untuk clear success message
     */
    fun clearSuccess() {
        _successMessage.value = null
    }

    /**
     * Fungsi untuk handle location permission granted
     */
    fun onLocationPermissionGranted() {
        _needLocationPermission.value = false
        getCurrentLocation()
    }

    /**
     * Fungsi untuk handle location permission denied
     */
    fun onLocationPermissionDenied() {
        _needLocationPermission.value = false
        _errorMessage.value = "Location permission required to detect current location"
    }

    /**
     * Fungsi untuk handle camera permission granted
     */
    fun onCameraPermissionGranted() {
        _needCameraPermission.value = false
    }

    /**
     * Fungsi untuk handle camera permission denied
     */
    fun onCameraPermissionDenied() {
        _needCameraPermission.value = false
        _errorMessage.value = "Camera permission required to take photos"
    }

    /**
     * Debug function untuk check camera state
     */
    fun debugCameraState() {
        Log.d(TAG, "=== CAMERA STATE DEBUG ===")
        Log.d(TAG, "Has camera permission: ${photoHelper.hasCameraPermission()}")
        Log.d(TAG, "Has camera hardware: ${photoHelper.hasCamera()}")
        Log.d(TAG, "Current camera URI: ${photoHelper.getCameraPhotoUri()}")
        photoHelper.debugPersistentState()
        Log.d(TAG, "========================")
    }

    /**
     * Fungsi untuk request camera permission
     */
    fun requestCameraPermission() {
        if (!photoHelper.hasCameraPermission()) {
            _needCameraPermission.value = true
        }
    }
}