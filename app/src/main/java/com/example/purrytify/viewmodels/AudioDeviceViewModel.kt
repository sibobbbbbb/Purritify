package com.example.purrytify.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.models.AudioDevice
import com.example.purrytify.util.AudioDeviceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


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


    private fun collectAudioDevices() {
        viewModelScope.launch {
            audioDeviceManager.audioDevices.collect { devices ->
                Log.d(TAG, "Received ${devices.size} audio devices")

                devices.forEach { device ->
                    Log.d(TAG, "  - Name: ${device.name}, Type: ${device.type}, State: ${device.connectionState}, IsActive: ${device.isActive}, ID: ${device.id}")
                }

                _audioDevices.value = devices

                // Update active device
                val newActiveDevice = devices.firstOrNull { it.isActive }
                if (newActiveDevice != _activeDevice.value) {
                    _activeDevice.value = newActiveDevice
                    if (newActiveDevice != null) {
                        Log.d(TAG, "Active device updated: Name: ${newActiveDevice.name}, Type: ${newActiveDevice.type}")
                    } else {
                        Log.d(TAG, "No active device found in the new list.")
                    }
                }

                // Check for disconnection of previously active device
                checkForDisconnection(devices)
            }
        }
    }


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


    fun switchToDevice(device: AudioDevice) {
        Log.d(TAG, "User requested switch to: ${device.name} (${device.type})")

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                // Check connection state
                when (device.connectionState) {
                    com.example.purrytify.models.ConnectionState.DISCONNECTED,
                    com.example.purrytify.models.ConnectionState.AVAILABLE -> {
                        if (device.type != com.example.purrytify.models.AudioDeviceType.BLUETOOTH_A2DP) {
                            _errorMessage.value = "Cannot switch to ${device.name}. Device is not connected."
                            _isLoading.value = false
                            return@launch
                        }
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


                var success = false
                var attempts = 0
                val maxAttempts = 3

                while (!success && attempts < maxAttempts) {
                    attempts++
                    Log.d(TAG, "Switch attempt $attempts/$maxAttempts")

                    // Attempt to switch
                    success = audioDeviceManager.switchToDevice(device)

                    if (!success && attempts < maxAttempts) {
                        Log.w(TAG, "Switch attempt $attempts failed, retrying...")
                        delay(300) // Wait before retry

                        // Force refresh audio routing on retry
                        if (attempts == 2) {
                            audioDeviceManager.forceRefreshAudioRouting()
                            delay(200)
                        }
                    }
                }

                if (success) {
                    _successMessage.value = "Switched to ${device.name}"
                    Log.d(TAG, "Successfully switched to ${device.name}")

                    // Wait a bit for the change to propagate
                    delay(500)

                    // Force update active device immediately
                    val updatedDevices = _audioDevices.value.map {
                        it.copy(isActive = it.id == device.id)
                    }
                    _audioDevices.value = updatedDevices
                    _activeDevice.value = device.copy(isActive = true)

                    // Clear success message after 2 seconds
                    viewModelScope.launch {
                        delay(2000)
                        _successMessage.value = null
                    }

                    // Notify MediaPlayerService about the device switch
                    notifyMediaServiceOfDeviceSwitch(device)

                } else {
                    val errorMsg = "Failed to switch to ${device.name}. Please try again."
                    _errorMessage.value = errorMsg
                    Log.e(TAG, errorMsg)

                    // If switching to speaker failed, try to force it
                    if (device.type == com.example.purrytify.models.AudioDeviceType.INTERNAL_SPEAKER) {
                        Log.d(TAG, "Attempting to force switch to speaker")
                        viewModelScope.launch {
                            delay(500)
                            forceSwithToSpeaker()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error switching device: ${e.message}")
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }


    private suspend fun forceSwithToSpeaker() {
        try {
            Log.d(TAG, "Force switching to speaker")

            // Clear all audio routing first
            audioDeviceManager.clearAudioRouting()
            delay(200)

            // Try switching again
            val success = audioDeviceManager.switchToDevice(
                com.example.purrytify.models.AudioDevice(
                    id = "internal_speaker",
                    name = "Phone Speaker",
                    type = com.example.purrytify.models.AudioDeviceType.INTERNAL_SPEAKER,
                    connectionState = com.example.purrytify.models.ConnectionState.CONNECTED,
                    isActive = false
                )
            )

            if (success) {
                _successMessage.value = "Switched to Phone Speaker"
                _errorMessage.value = null

                // Update UI
                val updatedDevices = _audioDevices.value.map {
                    it.copy(isActive = it.type == com.example.purrytify.models.AudioDeviceType.INTERNAL_SPEAKER)
                }
                _audioDevices.value = updatedDevices
                _activeDevice.value = updatedDevices.find { it.isActive }

            } else {
                Log.e(TAG, "Force switch to speaker also failed")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in force switch to speaker: ${e.message}")
        }
    }


    private fun notifyMediaServiceOfDeviceSwitch(device: AudioDevice) {
        try {
            val context = getApplication<Application>()
            val intent = android.content.Intent("com.example.purrytify.AUDIO_DEVICE_SWITCHED")
            intent.putExtra("device_type", device.type.name)
            intent.putExtra("device_name", device.name)

            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(context)
                .sendBroadcast(intent)

            Log.d(TAG, "Sent device switch notification to MediaPlayerService")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying MediaPlayerService: ${e.message}")
        }
    }


    fun clearError() {
        _errorMessage.value = null
    }


    fun clearSuccess() {
        _successMessage.value = null
    }


    fun refreshDevices() {
        Log.d(TAG, "Refreshing devices...")
        viewModelScope.launch {
            _isLoading.value = true

            try {
                // Force refresh audio routing
                audioDeviceManager.forceRefreshAudioRouting()
                delay(500) // Wait for changes to propagate

                // Devices will be automatically updated via Flow
                Log.d(TAG, "Device refresh completed")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing devices: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun emergencyFallbackToSpeaker() {
        Log.d(TAG, "Emergency fallback to speaker requested")

        viewModelScope.launch {
            try {
                audioDeviceManager.clearAudioRouting()
                delay(200)

                val speakerDevice = _audioDevices.value.find {
                    it.type == com.example.purrytify.models.AudioDeviceType.INTERNAL_SPEAKER
                }

                speakerDevice?.let { device ->
                    switchToDevice(device)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in emergency fallback: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up any audio routing when ViewModel is destroyed
        try {
            audioDeviceManager.clearAudioRouting()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing audio routing on cleanup: ${e.message}")
        }
    }
}