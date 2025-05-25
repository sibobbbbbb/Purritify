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
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.coroutines.resume

class LocationHelper(private val context: Context) {
    private val TAG = "LocationHelper"
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val geocoder = if (Geocoder.isPresent()) {
        Geocoder(context, Locale("id", "ID"))
    } else {
        null
    }

    init {
        // Initialize Places SDK
        if (!Places.isInitialized()) {
            // Use a default API key or request it from your backend
            Places.initialize(context, "AIzaSyDNMbD-13cjo0Cbdj8EWjP3DyxBenupkbY")
        }
    }

    /**
     * Fungsi untuk membuat Intent Place Autocomplete
     */
    fun createLocationPickerIntent(): Intent {
        Log.d(TAG, "Creating location picker intent")

        // Fields dari place yang dipilih
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS_COMPONENTS
        )

        // Buat intent untuk Autocomplete
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
            .build(context)

        Log.d(TAG, "Location picker intent created")
        return intent
    }

    /**
     * Parse hasil dari Place Autocomplete
     */
    fun parsePlacePickerResult(resultCode: Int, data: Intent?): LocationResult? {
        Log.d(TAG, "=== PARSING PLACE PICKER RESULT ===")
        Log.d(TAG, "Result code: $resultCode")

        when (resultCode) {
            AutocompleteActivity.RESULT_OK -> {
                data?.let {
                    val place = Autocomplete.getPlaceFromIntent(data)
                    Log.d(TAG, "Place: ${place.name}")
                    Log.d(TAG, "Address: ${place.address}")
                    Log.d(TAG, "LatLng: ${place.latLng}")
                    Log.d(TAG, "Address Components: ${place.addressComponents}")

                    // Extract country code dari address components
                    var countryCode = "ID" // Default
                    var countryName = "Indonesia" // Default

                    place.addressComponents?.asList()?.forEach { component ->
                        Log.d(TAG, "Component: ${component.name}, Types: ${component.types}")
                        if (component.types.contains("country")) {
                            countryName = component.name
                            countryCode = component.shortName ?: "ID"
                            Log.d(TAG, "Found country: $countryName ($countryCode)")
                        }
                    }

                    // Jika tidak ada address components, coba dengan geocoder
                    if (place.latLng != null && countryCode == "ID") {
                        val geocoderResult = locationToCountryCode(
                            place.latLng!!.latitude,
                            place.latLng!!.longitude
                        )
                        if (geocoderResult != null) {
                            countryCode = geocoderResult.countryCode
                            countryName = geocoderResult.countryName
                        }
                    }

                    return LocationResult(
                        countryCode = countryCode,
                        countryName = countryName,
                        address = place.address ?: place.name,
                        latitude = place.latLng?.latitude,
                        longitude = place.latLng?.longitude
                    )
                }
            }
            AutocompleteActivity.RESULT_ERROR -> {
                data?.let {
                    val status = Autocomplete.getStatusFromIntent(data)
                    Log.e(TAG, "Error: ${status.statusMessage}")
                }
            }
            AutocompleteActivity.RESULT_CANCELED -> {
                Log.d(TAG, "User canceled place selection")
            }
        }

        return null
    }

    /**
     * Alternatif: Buat intent untuk memilih lokasi dengan maps biasa
     * Ini adalah fallback jika Place Picker tidak tersedia
     */
    fun createMapsSelectorIntent(): Intent {
        Log.d(TAG, "Creating maps selector intent")

        // Gunakan action PICK dengan data geo
        val intent = Intent(Intent.ACTION_PICK).apply {
            setDataAndType(Uri.parse("content://com.google.android.maps/"), "vnd.android.cursor.dir/vnd.google.android.maps")
        }

        // Jika intent tidak bisa di-resolve, gunakan VIEW action
        if (intent.resolveActivity(context.packageManager) == null) {
            Log.d(TAG, "ACTION_PICK not available, using ACTION_VIEW")
            return Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:0,0?q=")
                setPackage("com.google.android.apps.maps")
            }
        }

        return intent
    }

    /**
     * Mendapatkan lokasi current user
     */
    suspend fun getCurrentLocation(): LocationResult? {
        return try {
            withTimeout(15000L) {
                getCurrentLocationInternal()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Timeout getting location: ${e.message}")
            null
        }
    }

    private suspend fun getCurrentLocationInternal(): LocationResult? = suspendCancellableCoroutine { continuation ->
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
            val lastKnownLocation = getBestLastKnownLocation()
            if (lastKnownLocation != null && isLocationFresh(lastKnownLocation)) {
                Log.d(TAG, "Using last known location: ${lastKnownLocation.latitude}, ${lastKnownLocation.longitude}")
                val result = locationToCountryCode(lastKnownLocation.latitude, lastKnownLocation.longitude)
                continuation.resume(result)
                return@suspendCancellableCoroutine
            }

            var locationReceived = false
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!locationReceived) {
                        locationReceived = true
                        Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                        locationManager.removeUpdates(this)
                        val result = locationToCountryCode(location.latitude, location.longitude)
                        continuation.resume(result)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            val providers = mutableListOf<String>()
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                providers.add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                providers.add(LocationManager.NETWORK_PROVIDER)
            }

            if (providers.isEmpty()) {
                Log.e(TAG, "No location providers available")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            providers.forEach { provider ->
                try {
                    locationManager.requestLocationUpdates(provider, 1000L, 0f, locationListener)
                    Log.d(TAG, "Requested updates from: $provider")
                } catch (e: SecurityException) {
                    Log.e(TAG, "Security exception for $provider: ${e.message}")
                }
            }

            continuation.invokeOnCancellation {
                try {
                    locationManager.removeUpdates(locationListener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing updates: ${e.message}")
                }
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!locationReceived) {
                    locationReceived = true
                    locationManager.removeUpdates(locationListener)
                    val staleLocation = getBestLastKnownLocation()
                    if (staleLocation != null) {
                        val result = locationToCountryCode(staleLocation.latitude, staleLocation.longitude)
                        continuation.resume(result)
                    } else {
                        continuation.resume(null)
                    }
                }
            }, 10000L)

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception: ${e.message}")
            continuation.resume(null)
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            continuation.resume(null)
        }
    }

    private fun getBestLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        return try {
            val locations = mutableListOf<Location>()

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                    locations.add(it)
                }
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                    locations.add(it)
                }
            }

            locations.maxByOrNull { it.time }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception getting last known location: ${e.message}")
            null
        }
    }

    private fun isLocationFresh(location: Location): Boolean {
        val age = System.currentTimeMillis() - location.time
        return age < 5 * 60 * 1000L
    }

    private fun locationToCountryCode(latitude: Double, longitude: Double): LocationResult? {
        if (geocoder == null) {
            Log.e(TAG, "Geocoder not available")
            return createFallbackLocation(latitude, longitude)
        }

        return try {
            Log.d(TAG, "Converting coordinates: $latitude, $longitude")

            var addresses: List<Address>? = null

            try {
                addresses = geocoder.getFromLocation(latitude, longitude, 1)
            } catch (e: Exception) {
                Log.w(TAG, "Geocoder failed: ${e.message}")
            }

            if (addresses.isNullOrEmpty()) {
                try {
                    val defaultGeocoder = Geocoder(context, Locale.getDefault())
                    addresses = defaultGeocoder.getFromLocation(latitude, longitude, 1)
                } catch (e: Exception) {
                    Log.w(TAG, "Default geocoder failed: ${e.message}")
                }
            }

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val countryCode = address.countryCode ?: "ID"
                val countryName = address.countryName ?: CountryCodeHelper.getCountryName(countryCode)
                val fullAddress = address.getAddressLine(0)

                Log.d(TAG, "Location resolved: $countryName ($countryCode)")

                LocationResult(
                    countryCode = countryCode,
                    countryName = countryName,
                    address = fullAddress,
                    latitude = latitude,
                    longitude = longitude
                )
            } else {
                Log.w(TAG, "No address found, using fallback")
                createFallbackLocation(latitude, longitude)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting location: ${e.message}")
            createFallbackLocation(latitude, longitude)
        }
    }

    private fun createFallbackLocation(latitude: Double, longitude: Double): LocationResult? {
        return if (isCoordinatesInIndonesia(latitude, longitude)) {
            LocationResult(
                countryCode = "ID",
                countryName = "Indonesia",
                address = "Location in Indonesia",
                latitude = latitude,
                longitude = longitude
            )
        } else {
            null
        }
    }

    private fun isCoordinatesInIndonesia(latitude: Double, longitude: Double): Boolean {
        return latitude >= -11.0 && latitude <= 6.0 && longitude >= 95.0 && longitude <= 141.0
    }

    /**
     * Create Google Maps intent
     */
    fun createGoogleMapsIntent(currentLatitude: Double? = null, currentLongitude: Double? = null): Intent {
        Log.d(TAG, "Creating Google Maps intent")

        val lat = currentLatitude ?: -6.2088
        val lng = currentLongitude ?: 106.8456

        val uri = "geo:$lat,$lng?q=$lat,$lng"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

        if (isGoogleMapsAvailable()) {
            intent.setPackage("com.google.android.apps.maps")
            Log.d(TAG, "Using Google Maps package")
        } else {
            Log.d(TAG, "Google Maps not available")
        }

        Log.d(TAG, "Created intent with URI: $uri")
        return intent
    }

    /**
     * Parse Google Maps result
     */
    fun parseGoogleMapsResult(data: Intent?): LocationResult? {
        Log.d(TAG, "=== PARSING GOOGLE MAPS RESULT ===")

        if (data?.data == null) {
            Log.w(TAG, "No URI data")
            return null
        }

        val uri = data.data!!
        val uriString = uri.toString()

        Log.d(TAG, "URI: $uriString")
        Log.d(TAG, "Scheme: ${uri.scheme}")
        Log.d(TAG, "Query: ${uri.query}")

        return try {
            var latitude: Double? = null
            var longitude: Double? = null

            // Pattern 1: geo:lat,lng
            val geoPattern = Regex("geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
            geoPattern.find(uriString)?.let { match ->
                latitude = match.groupValues[1].toDoubleOrNull()
                longitude = match.groupValues[2].toDoubleOrNull()
                Log.d(TAG, "Found geo pattern: $latitude, $longitude")
            }

            // Pattern 2: q=lat,lng
            if (latitude == null || longitude == null) {
                uri.query?.let { query ->
                    val queryPattern = Regex("q=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
                    queryPattern.find(query)?.let { match ->
                        latitude = match.groupValues[1].toDoubleOrNull()
                        longitude = match.groupValues[2].toDoubleOrNull()
                        Log.d(TAG, "Found query pattern: $latitude, $longitude")
                    }
                }
            }

            // Pattern 3: @lat,lng (Google Maps URL)
            if (latitude == null || longitude == null) {
                val urlPattern = Regex("@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)")
                urlPattern.find(uriString)?.let { match ->
                    latitude = match.groupValues[1].toDoubleOrNull()
                    longitude = match.groupValues[2].toDoubleOrNull()
                    Log.d(TAG, "Found URL pattern: $latitude, $longitude")
                }
            }

            if (latitude != null && longitude != null &&
                latitude!! >= -90.0 && latitude!! <= 90.0 &&
                longitude!! >= -180.0 && longitude!! <= 180.0) {

                Log.d(TAG, "Valid coordinates: $latitude, $longitude")
                locationToCountryCode(latitude!!, longitude!!)
            } else {
                Log.w(TAG, "No valid coordinates found")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing: ${e.message}")
            null
        }
    }

    /**
     * Check Google Maps availability
     */
    fun isGoogleMapsAvailable(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
            intent.setPackage("com.google.android.apps.maps")
            intent.resolveActivity(context.packageManager) != null
        } catch (e: Exception) {
            false
        }
    }

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

    fun isLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}