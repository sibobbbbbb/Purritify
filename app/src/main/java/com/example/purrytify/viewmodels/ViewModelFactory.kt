package com.example.purrytify.viewmodels

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.PurrytifyApp
import com.example.purrytify.repository.AuthRepository
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

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) -> {
                LoginViewModel(authRepository, contextRef.get() as android.app.Application) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(authRepository, userRepository, app.songRepository) as T
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
                MainViewModel(app.songRepository) as T
            }
            modelClass.isAssignableFrom(AudioDeviceViewModel::class.java) -> {
                AudioDeviceViewModel(contextRef.get() as android.app.Application) as T
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