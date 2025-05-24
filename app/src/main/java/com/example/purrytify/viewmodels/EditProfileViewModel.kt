package com.example.purrytify.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.models.LocationResult
import com.example.purrytify.models.UserProfile
import com.example.purrytify.repository.UserRepository
import com.example.purrytify.util.LocationHelper
import com.example.purrytify.util.PhotoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
     * Load current profile data
     */
    fun loadCurrentProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _errorMessage.value = null

                val result = userRepository.getUserProfile()
                result.onSuccess { profile ->
                    _currentProfile.value = profile
                    Log.d(TAG, "Profile loaded: ${profile.username}, location: ${profile.location}")
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
     * Get current location
     */
    fun getCurrentLocation() {
        viewModelScope.launch {
            try {
                if (!locationHelper.hasLocationPermission()) {
                    _needLocationPermission.value = true
                    return@launch
                }

                if (!locationHelper.isLocationEnabled()) {
                    _errorMessage.value = "Location services are disabled. Please enable GPS or Network location in your device settings."
                    return@launch
                }

                _isLoadingLocation.value = true
                _errorMessage.value = null

                Log.d(TAG, "Starting location detection...")

                val locationResult = locationHelper.getCurrentLocation()

                if (locationResult != null) {
                    _selectedLocation.value = locationResult
                    _successMessage.value = "Location detected: ${locationResult.countryName} (${locationResult.countryCode})"
                    Log.d(TAG, "Location obtained: ${locationResult.countryCode}")
                } else {
                    _errorMessage.value = "Unable to detect your current location. This might be due to weak GPS signal or network issues. Please try again or select your country manually."
                    Log.w(TAG, "Failed to get current location")
                }

            } catch (e: Exception) {
                val errorMsg = "Error detecting location: ${e.message}"
                _errorMessage.value = errorMsg
                Log.e(TAG, "Exception getting location: ${e.message}", e)
            } finally {
                _isLoadingLocation.value = false
            }
        }
    }

    /**
     * Set manual location
     */
    fun setManualLocation(locationResult: LocationResult) {
        _selectedLocation.value = locationResult

        val message = if (locationResult.latitude != null && locationResult.longitude != null) {
            "Location selected: ${locationResult.countryName} (precise location)"
        } else {
            "Country selected: ${locationResult.countryName}"
        }

        _successMessage.value = message
        Log.d(TAG, "Manual location set: ${locationResult.countryCode} - ${locationResult.countryName}")
    }

    /**
     * Clear selected location
     */
    fun clearSelectedLocation() {
        _selectedLocation.value = null
        Log.d(TAG, "Selected location cleared")
    }

    /**
     * Set photo from camera
     */
    fun setPhotoFromCamera() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Processing camera photo")

                val photoUri = photoHelper.getCameraPhotoUri()

                if (photoUri == null) {
                    _errorMessage.value = "Failed to capture photo - no photo available"
                    return@launch
                }

                if (!photoHelper.isValidPhotoUri(photoUri)) {
                    _errorMessage.value = "Invalid photo file"
                    return@launch
                }

                val fileSize = photoHelper.getFileSize(photoUri)
                if (fileSize <= 0) {
                    _errorMessage.value = "Photo file is empty or corrupted"
                    return@launch
                }

                if (fileSize > PhotoHelper.MAX_PHOTO_SIZE_BYTES) {
                    _errorMessage.value = "Photo file too large (max 5MB)"
                    return@launch
                }

                _selectedPhotoUri.value = photoUri
                _successMessage.value = "Photo captured successfully"

                Log.d(TAG, "Camera photo processed successfully")

            } catch (e: Exception) {
                _errorMessage.value = "Error processing camera photo: ${e.message}"
                Log.e(TAG, "Exception processing camera photo: ${e.message}", e)
            }
        }
    }

    /**
     * Set photo from gallery
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
     * Clear selected photo
     */
    fun clearSelectedPhoto() {
        _selectedPhotoUri.value = null
        photoHelper.clearCameraPhotoUri()
        Log.d(TAG, "Selected photo cleared")
    }

    /**
     * Save profile changes
     */
    fun saveProfileChanges() {
        viewModelScope.launch {
            try {
                val locationCode = _selectedLocation.value?.countryCode
                val photoUri = _selectedPhotoUri.value

                if (locationCode == null && photoUri == null) {
                    _errorMessage.value = "No changes to save"
                    return@launch
                }

                _isLoading.value = true
                _errorMessage.value = null

                Log.d(TAG, "Saving profile changes:")
                Log.d(TAG, "- Location: $locationCode (${_selectedLocation.value?.countryName})")
                Log.d(TAG, "- Photo URI: $photoUri")

                val result = userRepository.editProfile(
                    context = context,
                    location = locationCode,
                    profilePhotoUri = photoUri?.toString()
                )

                result.onSuccess { response ->
                    response.updatedProfile?.let { updatedProfile ->
                        _currentProfile.value = updatedProfile
                        Log.d(TAG, "Profile updated - new location: ${updatedProfile.location}")
                    }

                    // Clear selected changes
                    _selectedLocation.value = null
                    _selectedPhotoUri.value = null
                    photoHelper.clearCameraPhotoUri()

                    _successMessage.value = "Profile updated successfully!"
                    Log.d(TAG, "Profile update completed successfully")

                }.onFailure { exception ->
                    _errorMessage.value = "Failed to update profile: ${exception.message}"
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
     * Check if there are unsaved changes
     */
    fun hasUnsavedChanges(): Boolean {
        return _selectedLocation.value != null || _selectedPhotoUri.value != null
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear success message
     */
    fun clearSuccess() {
        _successMessage.value = null
    }

    /**
     * Handle location permission granted
     */
    fun onLocationPermissionGranted() {
        _needLocationPermission.value = false
        getCurrentLocation()
    }

    /**
     * Handle location permission denied
     */
    fun onLocationPermissionDenied() {
        _needLocationPermission.value = false
        _errorMessage.value = "Location permission is required to automatically detect your current location. You can still select your country manually."
    }

    /**
     * Handle camera permission granted
     */
    fun onCameraPermissionGranted() {
        _needCameraPermission.value = false
    }

    /**
     * Handle camera permission denied
     */
    fun onCameraPermissionDenied() {
        _needCameraPermission.value = false
        _errorMessage.value = "Camera permission is required to take photos. You can still select photos from gallery."
    }

    /**
     * Request camera permission
     */
    fun requestCameraPermission() {
        if (!photoHelper.hasCameraPermission()) {
            _needCameraPermission.value = true
        }
    }
}