package com.example.purrytify.models

data class AudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val address: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isActive: Boolean = false
)

enum class AudioDeviceType {
    INTERNAL_SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH_A2DP,
    USB_DEVICE,
    UNKNOWN
}

enum class ConnectionState {
    CONNECTED,          // Terhubung dan siap digunakan
    CONNECTING,         // Sedang dalam proses koneksi
    DISCONNECTED,       // Tidak terhubung
    AVAILABLE          // Tersedia untuk koneksi (khusus Bluetooth)
}