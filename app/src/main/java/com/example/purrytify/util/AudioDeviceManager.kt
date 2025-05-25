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
import android.media.AudioFocusRequest
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
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


class AudioDeviceManager(private val context: Context) {
    private val TAG = "AudioDeviceManager"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    // UUIDs untuk Bluetooth audio profiles
    private val AUDIO_SINK_UUID = UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb")

    // Bluetooth profile proxy untuk A2DP control
    private var bluetoothA2dp: BluetoothProfile? = null

    init {
        // Initialize Bluetooth A2DP profile untuk advanced control
        if (hasBluetoothPermission()) {
            try {
                bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                        Log.d(TAG, "Bluetooth A2DP profile connected")
                        bluetoothA2dp = proxy
                    }

                    override fun onServiceDisconnected(profile: Int) {
                        Log.d(TAG, "Bluetooth A2DP profile disconnected")
                        bluetoothA2dp = null
                    }
                }, BluetoothProfile.A2DP)
            } catch (e: SecurityException) {
                Log.w(TAG, "No permission to get A2DP profile: ${e.message}")
            }
        }
    }


    val audioDevices: Flow<List<AudioDevice>> = callbackFlow {
        val devices = mutableListOf<AudioDevice>()

        fun updateDevices() {
            devices.clear()
            devices.addAll(getAvailableDevices())
            trySend(devices.toList())
        }

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

        val headsetReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        Log.d(TAG, "Headset plug state: $state")
                        updateDevices()
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        Log.d(TAG, "Audio becoming noisy - headset unplugged")
                        updateDevices()
                    }
                }
            }
        }

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

        updateDevices()

        awaitClose {
            try {
                context.unregisterReceiver(bluetoothReceiver)
                context.unregisterReceiver(headsetReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receivers: ${e.message}")
            }
        }
    }


    private fun getAvailableDevices(): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        val currentActiveDevice = getCurrentActiveAudioDevice()
        Log.d(TAG, "Current active device type: $currentActiveDevice")

        devices.add(
            AudioDevice(
                id = "internal_speaker",
                name = "Phone Speaker",
                type = AudioDeviceType.INTERNAL_SPEAKER,
                connectionState = ConnectionState.CONNECTED,
                isActive = currentActiveDevice == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            )
        )

        if (isHeadsetConnected()) {
            devices.add(
                AudioDevice(
                    id = "wired_headset",
                    name = "Wired Headset",
                    type = AudioDeviceType.WIRED_HEADSET,
                    connectionState = ConnectionState.CONNECTED,
                    isActive = currentActiveDevice == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            currentActiveDevice == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                )
            )
        }

        if (hasBluetoothPermission()) {
            devices.addAll(getBluetoothDevices(currentActiveDevice))
        }

        return devices
    }


    private fun getCurrentActiveAudioDevice(): Int {
        return try {
            val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            if (audioManager.isBluetoothA2dpOn) {
                Log.d(TAG, "Bluetooth A2DP is ON - audio should be on Bluetooth")
                return AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }

            if (audioManager.isBluetoothScoOn) {
                Log.d(TAG, "Bluetooth SCO is ON - audio should be on Bluetooth")
                return AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }

            // Check wired headset
            audioDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }?.let {
                Log.d(TAG, "Wired headset detected as active")
                return it.type
            }

            // Default to speaker
            Log.d(TAG, "Defaulting to builtin speaker")
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current active device: ${e.message}")
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
    }


    private fun isHeadsetConnected(): Boolean {
        val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return audioDevices.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }


    private fun isBluetoothAudioConnected(): Boolean {
        if (!hasBluetoothPermission()) return false

        return try {
            audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn ||
                    bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
                    bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception checking Bluetooth: ${e.message}")
            false
        }
    }


    private fun getBluetoothDevices(currentActiveDevice: Int): List<AudioDevice> {
        val devices = mutableListOf<AudioDevice>()

        if (!hasBluetoothPermission()) {
            Log.w(TAG, "No Bluetooth permission")
            return devices
        }

        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                val deviceClass = device.bluetoothClass?.majorDeviceClass
                val isAudioDevice = when (deviceClass) {
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> true
                    BluetoothClass.Device.Major.WEARABLE -> {
                        device.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) == true
                    }
                    BluetoothClass.Device.Major.PERIPHERAL -> {
                        device.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) == true
                    }
                    else -> {
                        device.bluetoothClass?.hasService(BluetoothClass.Service.AUDIO) == true
                    }
                }

                if (isAudioDevice) {
                    val isConnected = isDeviceConnected(device)
                    val isCurrentlyActive = isConnected &&
                            (currentActiveDevice == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                    audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn)

                    devices.add(
                        AudioDevice(
                            id = device.address,
                            name = device.name ?: "Unknown Bluetooth Device",
                            type = AudioDeviceType.BLUETOOTH_A2DP,
                            address = device.address,
                            connectionState = if (isConnected) ConnectionState.CONNECTED else ConnectionState.AVAILABLE,
                            isActive = isCurrentlyActive
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting Bluetooth devices: ${e.message}")
        }

        return devices
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            try {
                bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
                        bluetoothAdapter?.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
            } catch (ex: Exception) {
                Log.e(TAG, "Error checking device connection: ${ex.message}")
                false
            }
        }
    }


    fun switchToDevice(device: AudioDevice): Boolean {
        Log.d(TAG, "Switching to device: ${device.name} (${device.type})")

        return try {
            when (device.type) {
                AudioDeviceType.INTERNAL_SPEAKER -> {
                    Log.d(TAG, "Switching to internal speaker - AGGRESSIVE APPROACH")

                    disconnectBluetoothAudio()

                    if (audioManager.isBluetoothA2dpOn) {
                        Log.d(TAG, "Force stopping A2DP")
                        try {
                            // Use reflection to disable A2DP routing
                            val method = audioManager.javaClass.getMethod("setBluetoothA2dpOn", Boolean::class.java)
                            method.invoke(audioManager, false)
                            Log.d(TAG, "A2DP disabled via reflection")
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not disable A2DP via reflection: ${e.message}")
                        }
                    }

                    if (audioManager.isBluetoothScoOn) {
                        Log.d(TAG, "Stopping Bluetooth SCO")
                        audioManager.isBluetoothScoOn = false
                        audioManager.stopBluetoothSco()
                        Thread.sleep(200)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            audioManager.clearCommunicationDevice()
                            Log.d(TAG, "Cleared communication device")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to clear communication device: ${e.message}")
                        }
                    }

                    audioManager.mode = AudioManager.MODE_NORMAL
                    Thread.sleep(100)

                    audioManager.isSpeakerphoneOn = true
                    Thread.sleep(200)

                    forceAudioRoutingUpdate()

                    Thread.sleep(300) // Wait longer for changes to take effect

                    val finalA2dpState = audioManager.isBluetoothA2dpOn
                    val finalScoState = audioManager.isBluetoothScoOn
                    val finalSpeakerState = audioManager.isSpeakerphoneOn

                    val success = !finalA2dpState && !finalScoState && finalSpeakerState

                    Log.d(TAG, "FINAL SPEAKER SWITCH RESULT: $success")
                    Log.d(TAG, "  - Final Bluetooth A2DP: $finalA2dpState")
                    Log.d(TAG, "  - Final Bluetooth SCO: $finalScoState")
                    Log.d(TAG, "  - Final Speakerphone: $finalSpeakerState")

                    success
                }

                AudioDeviceType.BLUETOOTH_A2DP -> {
                    Log.d(TAG, "Switching to Bluetooth device")

                    if (device.connectionState != ConnectionState.CONNECTED) {
                        Log.w(TAG, "Cannot switch to disconnected Bluetooth device")
                        return false
                    }

                    // Turn off speakerphone first
                    audioManager.isSpeakerphoneOn = false

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        // Modern approach for Android 12+
                        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                        val btDevice = audioManager.availableCommunicationDevices.firstOrNull { audioDevice ->
                            audioDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP &&
                                    audioDevice.address == device.address
                        }

                        if (btDevice != null) {
                            val success = audioManager.setCommunicationDevice(btDevice)
                            Log.d(TAG, "Set communication device result: $success")
                            return success
                        }
                    }

                    // Legacy approach or fallback
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    if (!audioManager.isBluetoothScoOn) {
                        audioManager.isBluetoothScoOn = true
                        audioManager.startBluetoothSco()
                    }

                    // Wait for SCO connection
                    Thread.sleep(500)

                    val isNowOnBluetooth = audioManager.isBluetoothScoOn || audioManager.isBluetoothA2dpOn
                    Log.d(TAG, "Bluetooth switch result: $isNowOnBluetooth")

                    isNowOnBluetooth
                }

                AudioDeviceType.WIRED_HEADSET -> {
                    Log.d(TAG, "Switching to wired headset")

                    // Disconnect Bluetooth first
                    disconnectBluetoothAudio()

                    // Turn off speakerphone
                    audioManager.isSpeakerphoneOn = false

                    // Set to normal mode - wired headset is automatically routed
                    audioManager.mode = AudioManager.MODE_NORMAL

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        audioManager.clearCommunicationDevice()
                    }

                    true
                }

                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching device: ${e.message}")
            e.printStackTrace()
            false
        }
    }


    private fun disconnectBluetoothAudio() {
        Log.d(TAG, "Disconnecting Bluetooth audio...")

        try {
            // Stop A2DP menggunakan profile proxy jika tersedia
            if (hasBluetoothPermission() && bluetoothA2dp != null) {
                try {
                    val connectedDevices = bluetoothA2dp!!.connectedDevices
                    connectedDevices.forEach { device ->
                        Log.d(TAG, "Attempting to disconnect A2DP from: $device")
                        // Using reflection to call disconnect method
                        val method = bluetoothA2dp!!.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        val result = method.invoke(bluetoothA2dp, device) as Boolean
                        Log.d(TAG, "A2DP disconnect result: $result")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not disconnect A2DP devices: ${e.message}")
                }
            }

            // Force disable A2DP routing
            if (audioManager.isBluetoothA2dpOn) {
                try {
                    val method = audioManager.javaClass.getMethod("setBluetoothA2dpOn", Boolean::class.java)
                    method.invoke(audioManager, false)
                    Log.d(TAG, "Forced A2DP off via reflection")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not force A2DP off: ${e.message}")
                }
            }

            // Stop SCO
            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                Log.d(TAG, "Stopped Bluetooth SCO")
            }

            Thread.sleep(300) // Wait for disconnection to propagate

        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Bluetooth audio: ${e.message}")
        }
    }


    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }


    fun getActiveDevice(): AudioDevice? {
        return getAvailableDevices().firstOrNull { it.isActive }
    }


    fun clearAudioRouting() {
        try {
            Log.d(TAG, "Clearing audio routing")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }

            if (audioManager.isBluetoothScoOn) {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }

            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false

        } catch (e: Exception) {
            Log.e(TAG, "Error clearing audio routing: ${e.message}")
        }
    }


    fun forceRefreshAudioRouting() {
        try {
            Log.d(TAG, "Force refreshing audio routing - AGGRESSIVE")

            // Complete disconnect dari Bluetooth
            disconnectBluetoothAudio()

            // Clear everything
            clearAudioRouting()

            // Wait for system to stabilize
            Thread.sleep(300)

        } catch (e: Exception) {
            Log.e(TAG, "Error force refreshing audio routing: ${e.message}")
        }
    }


    private fun forceAudioRoutingUpdate() {
        try {
            Log.d(TAG, "Forcing audio routing update with multiple approaches")

            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, AudioManager.FLAG_SHOW_UI)
            Thread.sleep(50)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)

            val originalMode = audioManager.mode
            audioManager.mode = AudioManager.MODE_IN_CALL
            Thread.sleep(50)
            audioManager.mode = originalMode

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requestTemporaryAudioFocus()
            }

            Log.d(TAG, "Applied routing update approaches")
        } catch (e: Exception) {
            Log.e(TAG, "Error in force audio routing update: ${e.message}")
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun requestTemporaryAudioFocus() {
        try {
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { /* temporary focus, do nothing */ }
                .build()

            val result = audioManager.requestAudioFocus(focusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Thread.sleep(100)
                audioManager.abandonAudioFocusRequest(focusRequest)
                Log.d(TAG, "Temporary AudioFocus requested and released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not request temporary AudioFocus: ${e.message}")
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
                )
            )
        } else {
            emptyList()
        }
    }
}