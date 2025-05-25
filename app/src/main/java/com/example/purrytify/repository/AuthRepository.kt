package com.example.purrytify.repository

import android.util.Log
import com.example.purrytify.models.LoginRequest
import com.example.purrytify.models.RefreshTokenRequest
import com.example.purrytify.network.RetrofitClient
import com.example.purrytify.util.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepository(private val tokenManager: TokenManager) {

    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val response = RetrofitClient.apiService.login(LoginRequest(email, password))

            if (response.isSuccessful && response.body() != null) {
                val accessToken = response.body()!!.accessToken
                val refreshToken = response.body()!!.refreshToken
                tokenManager.saveTokens(accessToken, refreshToken)
                Result.success(accessToken)
            } else {
                Result.failure(Exception("Login failed: ${response.errorBody()?.string() ?: "Unknown error"}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Login failed: ${e.message}"))
        }
    }

    suspend fun verifyToken(): Result<Boolean> = withContext(Dispatchers.IO) {
        Log.i("TokenRefreshService","Access Token Now: ${tokenManager.getToken()}")
        Log.i("TokenRefreshService","Refresh Token Now: ${tokenManager.getRefreshToken()}")
        try {
            val response = RetrofitClient.apiService.verifyToken()
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    suspend fun refreshToken(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.i("TokenRefreshService","TRY TO REFRESH TOKEN")
            Log.i("TokenRefreshService","Access Token Before Update: ${tokenManager.getToken()}")
            Log.i("TokenRefreshService","Refresh Token Before Update: ${tokenManager.getRefreshToken()}")

            val refreshToken = tokenManager.getRefreshToken()
                ?: return@withContext Result.failure(Exception("No refresh token available"))

            val response = RetrofitClient.apiService.refreshToken(RefreshTokenRequest(refreshToken))

            if (response.isSuccessful && response.body() != null) {
                // Update the access and refresh tokens
                val newAccessToken = response.body()!!.accessToken
                val newRefreshToken = response.body()!!.refreshToken

                tokenManager.saveTokens(newAccessToken, newRefreshToken)
                Log.i("TokenRefreshService","Access Token After Update: ${tokenManager.getToken()}")
                Log.i("TokenRefreshService","Refresh Token After Update: ${tokenManager.getRefreshToken()}")
                Result.success(true)
            } else {
                // If refresh token fails, log the user out
                tokenManager.deleteTokens()
                Result.failure(Exception("Failed to refresh token: ${response.errorBody()?.string() ?: "Unknown error"}"))
            }
        } catch (e: Exception) {
            // On network error, keep the user logged in
            Result.failure(e)
        }
    }

    fun isLoggedIn(): Boolean {
        return tokenManager.isLoggedIn()
    }

    fun logout() {
        tokenManager.deleteTokens()
    }
}