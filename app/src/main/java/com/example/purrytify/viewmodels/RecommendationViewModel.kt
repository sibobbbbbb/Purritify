package com.example.purrytify.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purrytify.models.RecommendationPlaylist
import com.example.purrytify.repository.RecommendationRepository
import com.example.purrytify.util.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecommendationViewModel(
    private val recommendationRepository: RecommendationRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val TAG = "RecommendationViewModel"

    private val _recommendations = MutableStateFlow<List<RecommendationPlaylist>>(emptyList())
    val recommendations: StateFlow<List<RecommendationPlaylist>> = _recommendations.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadRecommendations() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val userId = tokenManager.getUserId()
                if (userId > 0) {
                    val result = recommendationRepository.generateDailyRecommendations(userId)

                    result.onSuccess { recommendationList ->
                        _recommendations.value = recommendationList
                        Log.d(TAG, "Loaded ${recommendationList.size} recommendations")
                    }.onFailure { exception ->
                        _error.value = exception.message
                        Log.e(TAG, "Error loading recommendations", exception)
                    }
                } else {
                    _error.value = "User not logged in"
                    Log.e(TAG, "Cannot load recommendations: User not logged in")
                }
            } catch (e: Exception) {
                _error.value = e.message
                Log.e(TAG, "Exception loading recommendations", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshRecommendations() {
        Log.d(TAG, "Refreshing recommendations")
        loadRecommendations()
    }

    fun clearError() {
        _error.value = null
    }
}