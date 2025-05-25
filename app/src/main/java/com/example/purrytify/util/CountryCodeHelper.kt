package com.example.purrytify.util

import java.util.Locale


object CountryCodeHelper {


    val SUPPORTED_COUNTRIES = mapOf(
        "ID" to "Indonesia",
        "MY" to "Malaysia",
        "US" to "United States",
        "GB" to "United Kingdom",
        "CH" to "Switzerland",
        "DE" to "Germany",
        "BR" to "Brazil"
    )


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


    fun isValidCountryCode(countryCode: String?): Boolean {
        if (countryCode.isNullOrBlank()) return false
        return countryCode.matches(Regex("^[A-Z]{2}$"))
    }


    fun normalizeCountryCode(countryCode: String?): String? {
        if (countryCode.isNullOrBlank()) return null
        val normalized = countryCode.uppercase().trim()
        return if (isValidCountryCode(normalized)) normalized else null
    }


    fun getDisplayText(countryCode: String?): String {
        if (countryCode.isNullOrBlank()) return "Unknown Location"

        val normalizedCode = normalizeCountryCode(countryCode) ?: return "Invalid Location"
        val countryName = getCountryName(normalizedCode)

        return "$countryName ($normalizedCode)"
    }


    fun isCountrySupported(countryCode: String?): Boolean {
        if (countryCode.isNullOrBlank()) return false
        return SUPPORTED_COUNTRIES.containsKey(countryCode.uppercase())
    }


    fun getSupportedCountriesList(): List<Pair<String, String>> {
        return SUPPORTED_COUNTRIES.map { (code, name) ->
            code to name
        }.sortedBy { it.second } // Sort by country name
    }


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