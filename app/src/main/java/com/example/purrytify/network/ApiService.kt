package com.example.purrytify.network

import com.example.purrytify.models.LoginRequest
import com.example.purrytify.models.LoginResponse
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.models.RefreshTokenRequest
import com.example.purrytify.models.RefreshTokenResponse
import com.example.purrytify.models.UserProfile
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Part

interface ApiService {
    @POST("/api/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>

    @GET("api/profile")
    suspend fun getUserProfile(@Header("Authorization") token: String): Response<UserProfile>

    @GET("/api/verify-token")
    suspend fun verifyToken(): Response<Unit>

    @POST("/api/refresh-token")
    suspend fun refreshToken(@Body refreshTokenRequest: RefreshTokenRequest): Response<RefreshTokenResponse>

    // Online songs endpoints
    @GET("api/top-songs/global")
    suspend fun getGlobalTopSongs(): Response<List<OnlineSong>>

    @GET("api/top-songs/{country_code}")
    suspend fun getCountryTopSongs(@Path("country_code") countryCode: String): Response<List<OnlineSong>>

    @GET("api/songs/{song_id}")
    suspend fun getSongById(@Path("song_id") songId: Int): Response<OnlineSong>

    @Multipart
    @PATCH("/api/profile")
    suspend fun editProfile(
        @Header("Authorization") token: String,
        @Part("location") location: RequestBody? = null,
        @Part profilePhoto: MultipartBody.Part? = null
    ): Response<UserProfile>
}