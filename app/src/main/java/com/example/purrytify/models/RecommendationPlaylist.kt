package com.example.purrytify.models

data class RecommendationPlaylist(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val songs: List<Song>,
    val type: String // "daily_mix", "discover_weekly", "genre_mix"
)