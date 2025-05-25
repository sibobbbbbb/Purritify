package com.example.purrytify.util

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.util.Log
import com.example.purrytify.models.AudioDevice
import com.example.purrytify.models.AudioDeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Manager untuk menjaga persistence audio routing
 * Akan terus memonitor dan mempertahankan routing yang dipilih user
 */
class AudioRoutingManager(
    private val context: Context,
    private val audioDeviceManager: AudioDeviceManager
) {
    private val TAG = "AudioRoutingManager"

    private val prefs: SharedPreferences = context.getSharedPreferences("audio_routing", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var monitoringJob: Job? = null
    private var isMonitoring = false

    companion object {
        private const val PREF_SELECTED_DEVICE_ID = "selected_device_id"
        private const val PREF_SELECTED_DEVICE_TYPE = "selected_device_type"
        private const val PREF_SELECTED_DEVICE_NAME = "selected_device_name"
        private const val PREF_AUTO_ROUTING_ENABLED = "auto_routing_enabled"

        private const val MONITORING_INTERVAL_MS = 2000L // Check every 2 seconds
    }

    /**
     * Save pilihan routing user
     */
    fun saveSelectedDevice(device: AudioDevice) {
        Log.d(TAG, "Saving selected device: ${device.name} (${device.type})")

        prefs.edit().apply {
            putString(PREF_SELECTED_DEVICE_ID, device.id)
            putString(PREF_SELECTED_DEVICE_TYPE, device.type.name)
            putString(PREF_SELECTED_DEVICE_NAME, device.name)
            putBoolean(PREF_AUTO_ROUTING_ENABLED, true)
            apply()
        }

        // Start monitoring untuk maintain routing
        startMonitoring()
    }

    /**
     * Get saved device preference
     */
    fun getSavedDevice(): AudioDevice? {
        if (!prefs.getBoolean(PREF_AUTO_ROUTING_ENABLED, false)) {
            return null
        }

        val deviceId = prefs.getString(PREF_SELECTED_DEVICE_ID, null) ?: return null
        val deviceTypeName = prefs.getString(PREF_SELECTED_DEVICE_TYPE, null) ?: return null
        val deviceName = prefs.getString(PREF_SELECTED_DEVICE_NAME, null) ?: return null

        return try {
            val deviceType = AudioDeviceType.valueOf(deviceTypeName)
            AudioDevice(
                id = deviceId,
                name = deviceName,
                type = deviceType,
                connectionState = com.example.purrytify.models.ConnectionState.CONNECTED,
                isActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing saved device: ${e.message}")
            null
        }
    }

    /**
     * Clear saved preferences
     */
    fun clearSavedDevice() {
        Log.d(TAG, "Clearing saved device preferences")
        prefs.edit().clear().apply()
        stopMonitoring()
    }

    /**
     * Start monitoring dan maintain selected routing
     */
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already active")
            return
        }

        val savedDevice = getSavedDevice()
        if (savedDevice == null) {
            Log.d(TAG, "No saved device to monitor")
            return
        }

        Log.d(TAG, "Starting audio routing monitoring for: ${savedDevice.name}")
        isMonitoring = true

        monitoringJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && isMonitoring) {
                try {
                    checkAndMaintainRouting(savedDevice)
                    delay(MONITORING_INTERVAL_MS)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in monitoring loop: ${e.message}")
                    delay(MONITORING_INTERVAL_MS * 2) // Wait longer on error
                }
            }
        }
    }

    /**
     * Stop monitoring
     */
    fun stopMonitoring() {
        Log.d(TAG, "Stopping audio routing monitoring")
        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Check dan maintain routing sesuai pilihan user
     */
    private suspend fun checkAndMaintainRouting(targetDevice: AudioDevice) {
        try {
            val currentRouting = getCurrentAudioRouting()
            val expectedRouting = getExpectedRouting(targetDevice.type)

            Log.v(TAG, "Current: $currentRouting, Expected: $expectedRouting for ${targetDevice.name}")

            // Jika routing tidak sesuai, perbaiki
            if (currentRouting != expectedRouting) {
                Log.d(TAG, "Routing mismatch detected. Correcting routing to ${targetDevice.name}")

                // Switch kembali ke device yang dipilih
                val success = audioDeviceManager.switchToDevice(targetDevice)

                if (success) {
                    Log.d(TAG, "Successfully restored routing to ${targetDevice.name}")
                } else {
                    Log.w(TAG, "Failed to restore routing to ${targetDevice.name}")

                    // Jika gagal beberapa kali, mungkin device sudah disconnect
                    // Check apakah device masih available
                    if (!isDeviceStillAvailable(targetDevice)) {
                        Log.w(TAG, "Target device no longer available, clearing saved preference")
                        clearSavedDevice()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in checkAndMaintainRouting: ${e.message}")
        }
    }

    /**
     * Get current audio routing state
     */
    private fun getCurrentAudioRouting(): AudioRoutingState {
        return when {
            audioManager.isBluetoothA2dpOn -> AudioRoutingState.BLUETOOTH_A2DP
            audioManager.isBluetoothScoOn -> AudioRoutingState.BLUETOOTH_SCO
            audioManager.isSpeakerphoneOn -> AudioRoutingState.SPEAKER
            isWiredHeadsetConnected() -> AudioRoutingState.WIRED_HEADSET
            else -> AudioRoutingState.SPEAKER // Default
        }
    }

    /**
     * Get expected routing berdasarkan device type
     */
    private fun getExpectedRouting(deviceType: AudioDeviceType): AudioRoutingState {
        return when (deviceType) {
            AudioDeviceType.INTERNAL_SPEAKER -> AudioRoutingState.SPEAKER
            AudioDeviceType.BLUETOOTH_A2DP -> AudioRoutingState.BLUETOOTH_A2DP
            AudioDeviceType.WIRED_HEADSET -> AudioRoutingState.WIRED_HEADSET
            AudioDeviceType.USB_DEVICE -> AudioRoutingState.WIRED_HEADSET
            else -> AudioRoutingState.SPEAKER
        }
    }

    /**
     * Check apakah wired headset masih connected
     */
    private fun isWiredHeadsetConnected(): Boolean {
        val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return audioDevices.any {
            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    /**
     * Check apakah target device masih tersedia
     */
    private fun isDeviceStillAvailable(targetDevice: AudioDevice): Boolean {
        return when (targetDevice.type) {
            AudioDeviceType.INTERNAL_SPEAKER -> true // Always available
            AudioDeviceType.WIRED_HEADSET -> isWiredHeadsetConnected()
            AudioDeviceType.BLUETOOTH_A2DP -> {
                // Check if Bluetooth device masih paired dan connected
                try {
                    val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                    bluetoothAdapter?.bondedDevices?.any {
                        it.address == targetDevice.address
                    } ?: false
                } catch (e: Exception) {
                    false
                }
            }
            else -> false
        }
    }

    /**
     * Force apply routing immediately (untuk saat app startup)
     */
    fun applyStoredRouting() {
        val savedDevice = getSavedDevice()
        if (savedDevice != null) {
            Log.d(TAG, "Applying stored routing: ${savedDevice.name}")

            CoroutineScope(Dispatchers.IO).launch {
                // Small delay untuk let audio system initialize
                delay(1000)

                val success = audioDeviceManager.switchToDevice(savedDevice)
                if (success) {
                    Log.d(TAG, "Successfully applied stored routing")
                    // Start monitoring untuk maintain
                    startMonitoring()
                } else {
                    Log.w(TAG, "Failed to apply stored routing")
                }
            }
        }
    }

    /**
     * Handle app lifecycle - pause monitoring tapi tidak clear preferences
     */
    fun onAppPause() {
        Log.d(TAG, "App paused - stopping monitoring temporarily")
        stopMonitoring()
    }

    /**
     * Handle app lifecycle - resume monitoring
     */
    fun onAppResume() {
        Log.d(TAG, "App resumed - resuming monitoring")
        startMonitoring()
    }

    /**
     * Enum untuk routing states
     */
    private enum class AudioRoutingState {
        SPEAKER,
        BLUETOOTH_A2DP,
        BLUETOOTH_SCO,
        WIRED_HEADSET
    }
}