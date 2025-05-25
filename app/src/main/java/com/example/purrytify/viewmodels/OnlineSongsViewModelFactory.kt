package com.example.purrytify.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.purrytify.data.database.AppDatabase
import com.example.purrytify.repository.OnlineSongsRepository
import com.example.purrytify.repository.UserRepository
import com.example.purrytify.util.TokenManager

class OnlineSongsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OnlineSongsViewModel::class.java)) {
            // Initialize dependencies
            val songDao = AppDatabase.getDatabase(context).songDao()
            val tokenManager = TokenManager(context)
            val userRepository = UserRepository(tokenManager)
            val onlineSongsRepository = OnlineSongsRepository(songDao)

            return OnlineSongsViewModel(onlineSongsRepository, userRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}