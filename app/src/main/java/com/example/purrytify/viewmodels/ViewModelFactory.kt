package com.example.purrytify.viewmodels

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.PurrytifyApp
import com.example.purrytify.repository.AuthRepository
import com.example.purrytify.repository.OnlineSongsRepository
import com.example.purrytify.repository.RecommendationRepository
import com.example.purrytify.repository.UserRepository
import com.example.purrytify.util.TokenManager
import java.lang.ref.WeakReference

class ViewModelFactory(appContext: Context) : ViewModelProvider.Factory {
    private val contextRef = WeakReference(appContext)

    // Lazy repositories to avoid initializing all of them at once
    private val tokenManager by lazy {
        TokenManager(contextRef.get() ?: throw IllegalStateException("Context is null"))
    }

    private val authRepository by lazy {
        AuthRepository(tokenManager)
    }

    private val userRepository by lazy {
        UserRepository(tokenManager)
    }

    private val app by lazy {
        (contextRef.get() as? PurrytifyApp) ?: throw IllegalStateException("Context is not PurrytifyApp")
    }

    private val onlineSongsRepository by lazy {
        OnlineSongsRepository(app.database.songDao()) // Fixed to use songDao
    }

    private val recommendationRepository by lazy {
        RecommendationRepository(app.database.songDao(), onlineSongsRepository)
    }

    // ADD: Analytics repository
    private val analyticsRepository by lazy {
        app.analyticsRepository
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(authRepository, contextRef.get() as android.app.Application) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(authRepository, userRepository, app.songRepository) as T
            }
            modelClass.isAssignableFrom(EditProfileViewModel::class.java) -> {
                EditProfileViewModel(
                    contextRef.get() as android.app.Application,
                    userRepository
                ) as T
            }
            modelClass.isAssignableFrom(SongViewModel::class.java) -> {
                SongViewModel(app.songRepository) as T
            }
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(app.songRepository) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(app.songRepository) as T
            }
            modelClass.isAssignableFrom(MainViewModel::class.java) -> {
                // UPDATED: Pass analytics repository and token manager to MainViewModel
                MainViewModel(app.songRepository, analyticsRepository, tokenManager) as T
            }
            modelClass.isAssignableFrom(RecommendationViewModel::class.java) -> {
                RecommendationViewModel(recommendationRepository, tokenManager) as T
            }
            modelClass.isAssignableFrom(AudioDeviceViewModel::class.java) -> {
                AudioDeviceViewModel(contextRef.get() as android.app.Application) as T
            }
            // ADD: Analytics ViewModel
            modelClass.isAssignableFrom(AnalyticsViewModel::class.java) -> {
                AnalyticsViewModel(analyticsRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: ViewModelFactory? = null

        fun getInstance(context: Context): ViewModelFactory {
            return INSTANCE ?: synchronized(this) {
                val instance = ViewModelFactory(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}