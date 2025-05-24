package com.example.purrytify.models

/**
 * Model untuk merepresentasikan perangkat audio yang tersedia
 *
 * @property id Unique identifier untuk perangkat
 * @property name Nama perangkat yang akan ditampilkan ke user
 * @property type Tipe perangkat (INTERNAL_SPEAKER, BLUETOOTH, WIRED_HEADSET, dll)
 * @property address MAC address untuk Bluetooth device, null untuk device lain
 * @property connectionState Status koneksi perangkat
 * @property isActive True jika perangkat ini sedang aktif sebagai output
 */
data class AudioDevice(
    val id: String,
    val name: String,
    val type: AudioDeviceType,
    val address: String? = null,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isActive: Boolean = false
)

/**
 * Enum untuk tipe-tipe perangkat audio
 */
enum class AudioDeviceType {
    INTERNAL_SPEAKER,    // Speaker internal device
    WIRED_HEADSET,      // Headset kabel
    BLUETOOTH_A2DP,     // Bluetooth audio device (speaker, headphone)
    USB_DEVICE,         // USB audio device
    UNKNOWN
}

/**
 * Enum untuk status koneksi perangkat
 */
enum class ConnectionState {
    CONNECTED,          // Terhubung dan siap digunakan
    CONNECTING,         // Sedang dalam proses koneksi
    DISCONNECTED,       // Tidak terhubung
    AVAILABLE          // Tersedia untuk koneksi (khusus Bluetooth)
}