package com.example.purrytify.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.data.repository.SongRepository
import com.example.purrytify.models.UserProfile
import com.example.purrytify.repository.AuthRepository
import com.example.purrytify.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    // LiveData untuk data profil
    private val _profileData = MutableLiveData<UserProfile>()
    val profileData: LiveData<UserProfile> = _profileData

    // LiveData untuk status loading
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // LiveData untuk pesan error
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    // Stats untuk music collection
    private val _totalSongs = MutableStateFlow(0)
    val totalSongs: StateFlow<Int> = _totalSongs

    private val _likedSongs = MutableStateFlow(0)
    val likedSongs: StateFlow<Int> = _likedSongs

    private val _listenedSongs = MutableStateFlow(0)
    val listenedSongs: StateFlow<Int> = _listenedSongs

    init {
        // Load stats on initialization
        loadMusicStats()
    }

    fun loadUserProfile() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val result = userRepository.getUserProfile()
                result.onSuccess {
                    _profileData.value = it
                }.onFailure {
                    _error.value = it.message ?: "Unknown error"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }


    private fun loadMusicStats() {
        viewModelScope.launch {
            try {
                // Get total songs count
                songRepository.getAllSongsCount().collect { count ->
                    _totalSongs.value = count
                }

                // Get liked songs count
                songRepository.getLikedSongsCount().collect { count ->
                    _likedSongs.value = count
                }

                // Get listened songs count (songs with lastPlayed not null)
                songRepository.getListenedSongsCount().collect { count ->
                    _listenedSongs.value = count
                }
            } catch (e: Exception) {
                // Just log the error, don't disrupt the UI
                e.printStackTrace()
            }
        }
    }

    // Refresh all data
    fun refreshData() {
        loadUserProfile()
        loadMusicStats()
    }

    fun logout() {
        authRepository.logout()
    }
}