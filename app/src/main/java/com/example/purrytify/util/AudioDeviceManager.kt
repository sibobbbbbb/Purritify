package com.example.purrytify.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.BuildConfig
import com.example.purrytify.models.AudioDevice
import com.example.purrytify.models.AudioDeviceType
import com.example.purrytify.models.ConnectionState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

/**
 * Manager class untuk mengelola deteksi, koneksi, dan switching perangkat audio
 */
class AudioDeviceManager(private val context: Context) {
    private val TAG = "AudioDeviceManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    // UUIDs untuk Bluetooth audio profiles
    private val AUDIO_SINK_UUID = UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb")

    /**
     * Flow yang emit list perangkat audio setiap kali ada perubahan
     * Menggunakan callbackFlow untuk convert callback-based API ke Flow
     */
    val audioDevices: Flow<List<AudioDevice>> = callbackFlow {
        val devices = mutableListOf<AudioDevice>()

        // Fungsi untuk update dan emit devices
        fun updateDevices() {
            devices.clear()
            devices.addAll(getAvailableDevices())
            trySend(devices.toList())
        }

        // Broadcast receiver untuk mendeteksi perubahan Bluetooth
        val bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    BluetoothAdapter.ACTION_STATE_CHANGED,
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        Log.d(TAG, "Bluetooth state changed: ${intent.action}")
                        updateDevices()
                    }
                }
            }
        }

        // Broadcast receiver untuk mendeteksi headset kabel
        val headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        Log.d(TAG, "Headset plug state: $state")
                        updateDevices()
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        // Audio akan berpindah ke speaker karena headset dicabut
                        Log.d(TAG, "Audio becoming noisy - headset unplugged")
                        updateDevices()
                    }
                }
            }
        }

        // Register receivers
        val bluetoothFilter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }

        val headsetFilter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }

        ContextCompat.registerReceiver(
            context,
            bluetoothReceiver,
            bluetoothFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            context,
            headsetReceiver,
            headsetFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Initial scan
        updateDevices()

        // Cleanup saat flow di-cancel
        awaitClose {
            context.unregisterReceiver(bluetoothReceiver)
            context.unregisterReceiver(headsetReceiver)
        }
    }

    /**
     * Mendapatkan semua perangkat audio yang tersedia
     */
    private fun getAvailableDevices(): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        // 1. Internal Speaker (selalu tersedia)
        devices.add(
            AudioDevice(
                id = "internal_speaker",
                name = "Phone Speaker",
                type = AudioDeviceType.INTERNAL_SPEAKER,
                connectionState = ConnectionState.CONNECTED,
                isActive = !isHeadsetConnected() && !isBluetoothAudioConnected()
            )
        )

        // 2. Wired Headset
        if (isHeadsetConnected()) {
            devices.add(
                AudioDevice(
                    id = "wired_headset",
                    name = "Wired Headset",
                    type = AudioDeviceType.WIRED_HEADSET,
                    connectionState = ConnectionState.CONNECTED,
                    isActive = true // Wired headset takes priority when connected
                )
            )
        }

        // 3. Bluetooth Devices
        if (hasBluetoothPermission()) {
            devices.addAll(getBluetoothDevices())
        }

        return devices
    }

    /**
     * Check apakah headset kabel terhubung
     * Menggunakan AudioManager deprecated API untuk compatibility
     */
    private fun isHeadsetConnected(): Boolean {
        val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return audioDevices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    /**
     * Check apakah ada Bluetooth audio device yang terhubung
     */
    private fun isBluetoothAudioConnected(): Boolean {
        if (!hasBluetoothPermission()) return false

        return try {
            val a2dp = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED
            val headset = bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
            a2dp || headset
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception checking Bluetooth: ${e.message}")
            false
        }
    }

    /**
     * Mendapatkan daftar Bluetooth devices
     */
    private fun getBluetoothDevices(): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        if (!hasBluetoothPermission()) {
            Log.w(TAG, "No Bluetooth permission")
            return devices
        }

        try {
            // Get paired devices
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                // Check if it's an audio device using major device class
                val deviceClass = device.bluetoothClass?.majorDeviceClass
                val isAudioDevice = when (deviceClass) {
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> true
                    BluetoothClass.Device.Major.WEARABLE -> {
                        // Check if it's a wearable headset
                        device.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) == true
                    }
                    BluetoothClass.Device.Major.PERIPHERAL -> {
                        // Some audio devices might be classified as peripheral
                        device.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) == true
                    }
                    else -> {
                        // Fallback: check if device supports audio service
                        device.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) == true
                    }
                }

                if (isAudioDevice) {
                    val isConnected = isDeviceConnected(device)
                    devices.add(
                        AudioDevice(
                            id = device.address,
                            name = device.name ?: "Unknown Bluetooth Device",
                            type = AudioDeviceType.BLUETOOTH_A2DP,
                            address = device.address,
                            connectionState = if (isConnected) ConnectionState.CONNECTED else ConnectionState.AVAILABLE,
                            isActive = isConnected && isBluetoothAudioConnected()
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting Bluetooth devices: ${e.message}")
        }

        return devices
    }

    /**
     * Check apakah specific Bluetooth device terhubung
     */
    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            // Use reflection to call isConnected method (hidden API)
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "Error checking device connection: ${e.message}")
            false
        }
    }

    /**
     * Switch audio output ke device tertentu
     *
     * @param device Device yang akan diaktifkan
     * @return true jika berhasil, false jika gagal
     */
    fun switchToDevice(device: AudioDevice): Boolean {
        Log.d(TAG, "Switching to device: ${device.name}")

        return try {
            when (device.type) {
                AudioDeviceType.INTERNAL_SPEAKER -> {
                    // Route audio to speaker
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // For Android 12+, use setCommunicationDevice
                        audioManager.mode = AudioManager.MODE_NORMAL
                        audioManager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }?.let { speakerDevice ->
                            audioManager.setCommunicationDevice(speakerDevice)
                        }
                    } else {
                        // For older versions
                        audioManager.mode = AudioManager.MODE_NORMAL
                        // Clear any bluetooth routes
                        if (audioManager.isBluetoothScoOn) {
                            audioManager.isBluetoothScoOn = false
                        }
                    }
                    true
                }

                AudioDeviceType.BLUETOOTH_A2DP -> {
                    // For Bluetooth audio routing
                    if (device.connectionState == ConnectionState.CONNECTED) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            // Modern approach for Android 12+
                            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                            audioManager.availableCommunicationDevices.firstOrNull { audioDevice ->
                                audioDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                                        audioDevice.address == device.address
                            }?.let { btDevice ->
                                audioManager.setCommunicationDevice(btDevice)
                                true
                            } ?: false
                        } else {
                            // Legacy approach for older Android versions
                            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                            @Suppress("DEPRECATION")
                            if (!audioManager.isBluetoothScoOn) {
                                audioManager.isBluetoothScoOn = true
                                audioManager.startBluetoothSco()
                            }
                            true
                        }
                    } else {
                        Log.w(TAG, "Cannot switch to disconnected Bluetooth device")
                        false
                    }
                }

                AudioDeviceType.WIRED_HEADSET -> {
                    // Wired headset is automatically routed when connected
                    // Just ensure we're in normal mode
                    audioManager.mode = AudioManager.MODE_NORMAL
                    true
                }

                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching device: ${e.message}")
            false
        }
    }

    /**
     * Check apakah app punya Bluetooth permission
     */
    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permission tidak diperlukan di Android < 12
        }
    }

    /**
     * Get currently active audio device
     */
    fun getActiveDevice(): AudioDevice? {
        return getAvailableDevices().firstOrNull { it.isActive }
    }

    /**
     * Clear any custom audio routing and return to default
     * Should be called when app is paused or stopped
     */
    fun clearAudioRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing audio routing: ${e.message}")
        }
    }

    private fun getMockDevicesForTesting(): List<AudioDevice> {
        return if (BuildConfig.DEBUG) {
            listOf(
                AudioDevice(
                    id = "mock_bluetooth_1",
                    name = "Mock AirPods",
                    type = AudioDeviceType.BLUETOOTH_A2DP,
                    address = "00:00:00:00:00:01",
                    connectionState = ConnectionState.AVAILABLE,
                    isActive = false
                ),
                AudioDevice(
                    id = "mock_wired",
                    name = "Mock Wired Headset",
                    type = AudioDeviceType.WIRED_HEADSET,
                    connectionState = ConnectionState.CONNECTED,
                    isActive = false
                )
            )
        } else {
            emptyList()
        }
    }
}