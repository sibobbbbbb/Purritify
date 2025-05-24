package com.example.purrytify.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import com.example.purrytify.models.LocationResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Helper class untuk mengelola lokasi pengguna
 */
class LocationHelper(private val context: Context) {
    private val TAG = "LocationHelper"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val geocoder = Geocoder(context, Locale.getDefault())

    /**
     * Fungsi untuk mendapatkan lokasi pengguna saat iniurn LocationResult dengan country code
     *
     * @return LocationResult atau null jika gagal
     */
    suspend fun getCurrentLocation(): LocationResult? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            Log.e(TAG, "Location permission not granted")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        if (!isLocationEnabled()) {
            Log.e(TAG, "Location services disabled")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            // Try to get last known location first
            val lastKnownLocation = getLastKnownLocation()
            if (lastKnownLocation != null) {
                Log.d(TAG, "Using last known location: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}")
                val result = locationToCountryCode(lastKnownLocation.latitude, lastKnownLocation.longitude)
                continuation.resume(result)
                return@suspendCancellableCoroutine
            }

            // If no last known location, request current location
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    Log.d(TAG, "Got current location: ${location.latitude}, ${location.longitude}")
                    locationManager.removeUpdates(this)

                    val result = locationToCountryCode(location.latitude, location.longitude)
                    continuation.resume(result)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    Log.w(TAG, "Location provider disabled: $provider")
                    continuation.resume(null)
                }
            }

            // Request location updates
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> {
                    Log.e(TAG, "No location provider available")
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }
            }

            locationManager.requestLocationUpdates(
                provider,
                1000L, // 1 second
                0f, // 0 meters
                locationListener
            )

            // Set timeout untuk location request
            continuation.invokeOnCancellation {
                locationManager.removeUpdates(locationListener)
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting location: ${e.message}")
            continuation.resume(null)
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting location: ${e.message}")
            continuation.resume(null)
        }
    }

    /**
     * Fungsi untuk mendapatkan last known location
     *
     * @return Location atau null jika tidak ada
     */
    private fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        return try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            providers.mapNotNull { provider ->
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.getLastKnownLocation(provider)
                } else null
            }.maxByOrNull { it.time } // Get most recent location
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last known location: ${e.message}")
            null
        }
    }

    /**
     * Fungsi untuk konversi koordinat ke country code
     *
     * @param latitude Latitude koordinat
     * @param longitude Longitude koordinat
     * @return LocationResult atau null jika gagal
     */
    private fun locationToCountryCode(latitude: Double, longitude: Double): LocationResult? {
        return try {
            val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1) ?: emptyList()

            if (addresses.isNotEmpty()) {
                val address = addresses[0]
                val countryCode = address.countryCode ?: "US" // Default to US if null
                val countryName = address.countryName ?: "Unknown"
                val fullAddress = address.getAddressLine(0)

                Log.d(TAG, "Location resolved to country: $countryName ($countryCode)")

                LocationResult(
                    countryCode = countryCode,
                    countryName = countryName,
                    address = fullAddress,
                    latitude = latitude,
                    longitude = longitude
                )
            } else {
                Log.w(TAG, "No address found for coordinates")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting location to country code: ${e.message}")
            null
        }
    }

    /**
     * Fungsi untuk membuka Google Maps untuk pemilihan lokasi manual
     *
     * @param currentLatitude Latitude saat ini (optional)
     * @param currentLongitude Longitude saat ini (optional)
     * @return Intent untuk Google Maps
     */
    fun createGoogleMapsIntent(currentLatitude: Double? = null, currentLongitude: Double? = null): Intent {
        val uri = if (currentLatitude != null && currentLongitude != null) {
            // Open maps with current location
            Uri.parse("geo:$currentLatitude,$currentLongitude?q=$currentLatitude,$currentLongitude")
        } else {
            // Open maps without specific location
            Uri.parse("geo:0,0?q=")
        }

        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
    }

    /**
     * Fungsi untuk parse hasil dari Google Maps intent
     *
     * @param data Intent data dari onActivityResult
     * @return LocationResult atau null jika parsing gagal
     */
    fun parseGoogleMapsResult(data: Intent?): LocationResult? {
        return try {
            data?.data?.let { uri ->
                // Parse koordinat dari URI
                val coordinates = uri.toString()
                Log.d(TAG, "Received Google Maps result: $coordinates")

                // Extract lat,lng dari URI format geo:lat,lng
                val regex = Regex("geo:(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
                val matchResult = regex.find(coordinates)

                if (matchResult != null) {
                    val latitude = matchResult.groupValues[1].toDouble()
                    val longitude = matchResult.groupValues[2].toDouble()
                    locationToCountryCode(latitude, longitude)
                } else {
                    Log.w(TAG, "Could not parse coordinates from Maps result")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Google Maps result: ${e.message}")
            null
        }
    }

    /**
     * Check apakah app memiliki permission lokasi
     */
    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check apakah location services enabled
     */
    fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        const val GOOGLE_MAPS_REQUEST_CODE = 1002
    }
}