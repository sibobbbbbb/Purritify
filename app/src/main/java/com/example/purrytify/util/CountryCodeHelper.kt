package com.example.purrytify.util

import java.util.Locale

/**
 * Helper class untuk mengelola country code dan country name mapping
 */
object CountryCodeHelper {

    /**
     * Daftar country yang didukung oleh server berdasarkan spesifikasi
     * Server hanya menyediakan top 10 songs untuk negara-negara ini
     */
    val SUPPORTED_COUNTRIES = mapOf(
        "ID" to "Indonesia",
        "MY" to "Malaysia",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CH" to "Switzerland",
        "DE" to "Germany",
        "BR" to "Brazil"
    )

    /**
     * Fungsi untuk mendapatkan nama negara dari country code
     *
     * @param countryCode Country code dalam format ISO 3166-1 alpha-2
     * @return Nama negara atau country code jika tidak ditemukan
     */
    fun getCountryName(countryCode: String?): String {
        if (countryCode.isNullOrBlank()) return "Unknown"

        val upperCaseCode = countryCode.uppercase()

        // Check in supported countries first
        SUPPORTED_COUNTRIES[upperCaseCode]?.let { return it }

        // Use Locale to get display name
        return try {
            val locale = Locale("", upperCaseCode)
            val displayName = locale.displayCountry
            if (displayName.isNotBlank() && displayName != upperCaseCode) {
                displayName
            } else {
                upperCaseCode
            }
        } catch (e: Exception) {
            upperCaseCode
        }
    }

    /**
     * Fungsi untuk validasi format country code
     *
     * @param countryCode Country code yang akan divalidasi
     * @return true jika format valid (2 huruf), false jika tidak
     */
    fun isValidCountryCode(countryCode: String?): Boolean {
        if (countryCode.isNullOrBlank()) return false
        return countryCode.matches(Regex("^[A-Z]{2}$"))
    }

    /**
     * Fungsi untuk normalize country code ke uppercase
     *
     * @param countryCode Country code input
     * @return Country code dalam uppercase atau null jika invalid
     */
    fun normalizeCountryCode(countryCode: String?): String? {
        if (countryCode.isNullOrBlank()) return null
        val normalized = countryCode.uppercase().trim()
        return if (isValidCountryCode(normalized)) normalized else null
    }

    /**
     * Fungsi untuk mendapatkan formatted display text
     * Format: "Country Name (CC)"
     *
     * @param countryCode Country code
     * @return Formatted string untuk display
     */
    fun getDisplayText(countryCode: String?): String {
        if (countryCode.isNullOrBlank()) return "Unknown Location"

        val normalizedCode = normalizeCountryCode(countryCode) ?: return "Invalid Location"
        val countryName = getCountryName(normalizedCode)

        return "$countryName ($normalizedCode)"
    }

    /**
     * Fungsi untuk check apakah country didukung oleh server
     * (untuk fitur top songs berdasarkan negara)
     *
     * @param countryCode Country code
     * @return true jika supported, false jika tidak
     */
    fun isCountrySupported(countryCode: String?): Boolean {
        if (countryCode.isNullOrBlank()) return false
        return SUPPORTED_COUNTRIES.containsKey(countryCode.uppercase())
    }

    /**
     * Fungsi untuk mendapatkan daftar semua supported countries
     * untuk keperluan UI selection
     *
     * @return List of Pair<CountryCode, CountryName>
     */
    fun getSupportedCountriesList(): List<Pair<String, String>> {
        return SUPPORTED_COUNTRIES.map { (code, name) ->
            code to name
        }.sortedBy { it.second } // Sort by country name
    }

    /**
     * Fungsi untuk mendapatkan country code dari device locale
     * sebagai fallback default
     *
     * @return Country code dari system locale atau "US" sebagai default
     */
    fun getDeviceCountryCode(): String {
        return try {
            val locale = Locale.getDefault()
            val countryCode = locale.country
            if (isValidCountryCode(countryCode)) {
                countryCode
            } else {
                "US" // Default fallback
            }
        } catch (e: Exception) {
            "US" // Default fallback
        }
    }
}