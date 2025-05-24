package com.example.purrytify.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.models.AudioDevice
import com.example.purrytify.util.AudioDeviceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel untuk mengelola state audio devices
 *
 * Alur kerja:
 * 1. Initialize AudioDeviceManager
 * 2. Collect audio devices dari manager
 * 3. Expose devices dan loading state ke UI
 * 4. Handle device selection dan error
 */
class AudioDeviceViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "AudioDeviceViewModel"

    private val audioDeviceManager = AudioDeviceManager(application)

    // State untuk daftar devices
    private val _audioDevices = MutableStateFlow<List<AudioDevice>>(emptyList())
    val audioDevices: StateFlow<List<AudioDevice>> = _audioDevices.asStateFlow()

    // State untuk active device
    private val _activeDevice = MutableStateFlow<AudioDevice?>(null)
    val activeDevice: StateFlow<AudioDevice?> = _activeDevice.asStateFlow()

    // State untuk loading
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // State untuk error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // State untuk success message
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    init {
        // Start collecting audio devices
        collectAudioDevices()
    }

    /**
     * Collect audio devices dari manager
     * Akan terus listening untuk perubahan devices
     */
    private fun collectAudioDevices() {
        viewModelScope.launch {
            audioDeviceManager.audioDevices.collect { devices ->
                Log.d(TAG, "Received ${devices.size} audio devices")

                devices.forEach { device ->
                    Log.d(TAG, "  - Name: ${device.name}, Type: ${device.type}, State: ${device.connectionState}, IsActive: ${device.isActive}, ID: ${device.id}")
                }

                _audioDevices.value = devices

                // Update active device
                _activeDevice.value = devices.firstOrNull { it.isActive }
                if (_activeDevice.value != null) {
                    Log.d(TAG, "Active device updated: Name: ${_activeDevice.value?.name}, Type: ${_activeDevice.value?.type}")
                } else {
                    Log.d(TAG, "No active device found in the new list.")
                }
                // Check for disconnection of previously active device
                checkForDisconnection(devices)
            }
        }
    }

    /**
     * Check apakah ada device yang disconnect
     * Jika active device disconnect, show error dan fallback
     */
    private fun checkForDisconnection(currentDevices: List<AudioDevice>) {
        val activeDevice = _activeDevice.value ?: return

        // Skip check untuk internal speaker
        if (activeDevice.type == com.example.purrytify.models.AudioDeviceType.INTERNAL_SPEAKER) {
            return
        }

        // Check if active device masih connected
        val stillConnected = currentDevices.any {
            it.id == activeDevice.id &&
                    it.connectionState == com.example.purrytify.models.ConnectionState.CONNECTED
        }

        if (!stillConnected) {
            Log.w(TAG, "Active device disconnected: ${activeDevice.name}")
            _errorMessage.value = "${activeDevice.name} disconnected. Switching to phone speaker."

            // Clear error message after 3 seconds
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                _errorMessage.value = null
            }
        }
    }

    /**
     * Switch ke device tertentu
     *
     * @param device Device yang akan diaktifkan
     */
    fun switchToDevice(device: AudioDevice) {
        Log.d(TAG, "User requested switch to: ${device.name}")

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Check connection state
                when (device.connectionState) {
                    com.example.purrytify.models.ConnectionState.DISCONNECTED,
                    com.example.purrytify.models.ConnectionState.AVAILABLE -> {
                        _errorMessage.value = "Cannot switch to ${device.name}. Device is not connected."
                        _isLoading.value = false
                        return@launch
                    }

                    com.example.purrytify.models.ConnectionState.CONNECTING -> {
                        _errorMessage.value = "${device.name} is still connecting. Please wait."
                        _isLoading.value = false
                        return@launch
                    }

                    com.example.purrytify.models.ConnectionState.CONNECTED -> {
                        // Proceed with switching
                    }
                }

                // Attempt to switch
                val success = audioDeviceManager.switchToDevice(device)

                if (success) {
                    _successMessage.value = "Switched to ${device.name}"

                    // Clear success message after 2 seconds
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(2000)
                        _successMessage.value = null
                    }

                    // Force update active device
                    _activeDevice.value = device.copy(isActive = true)

                    // Update devices list
                    _audioDevices.value = _audioDevices.value.map {
                        it.copy(isActive = it.id == device.id)
                    }
                } else {
                    _errorMessage.value = "Failed to switch to ${device.name}"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error switching device: ${e.message}")
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
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
     * Refresh devices list
     * Useful jika user ingin manual refresh
     */
    fun refreshDevices() {
        Log.d(TAG, "Refreshing devices...")
        // The Flow akan otomatis update, tapi kita bisa trigger UI feedback
        viewModelScope.launch {
            _isLoading.value = true
            kotlinx.coroutines.delay(500) // Small delay for UI feedback
            _isLoading.value = false
        }
    }
}