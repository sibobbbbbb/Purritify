package com.example.purrytify.repository

import android.util.Log
import com.example.purrytify.data.dao.SongDao
import com.example.purrytify.data.entity.Song
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.models.RecommendationPlaylist
import com.example.purrytify.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class RecommendationRepository(
    private val songDao: SongDao,
    private val onlineSongsRepository: OnlineSongsRepository
) {
    private val TAG = "RecommendationRepository"

    suspend fun generateDailyRecommendations(userId: Int): Result<List<RecommendationPlaylist>> {
        return withContext(Dispatchers.IO) {
            try {
                val recommendations = mutableListOf<RecommendationPlaylist>()

                // Get user's songs from database directly
                val allUserSongs = if (userId > 0) {
                    songDao.getSongsByUserIdSync(userId)
                } else {
                    songDao.getAllSongsSync()
                }

                val likedSongs = allUserSongs.filter { it.isLiked }
                val recentlyPlayed = allUserSongs.filter { it.lastPlayed != null }
                    .sortedByDescending { it.lastPlayed }
                    .take(10)

                val dailyMix = generateDailyMix(likedSongs, allUserSongs, userId)
                recommendations.add(dailyMix)

                val discoverWeekly = generateDiscoverWeekly(recentlyPlayed, allUserSongs, userId)
                recommendations.add(discoverWeekly)

                val popMix = generateSpecificGenreMix("Pop Mix", likedSongs, allUserSongs, userId)
                recommendations.add(popMix)

                val chillMix = generateSpecificGenreMix("Chill Mix", likedSongs, allUserSongs, userId)
                recommendations.add(chillMix)

                Result.success(recommendations)
            } catch (e: Exception) {
                Log.e(TAG, "Error generating recommendations", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun generateDailyMix(
        likedSongs: List<Song>,
        allUserSongs: List<Song>,
        userId: Int
    ): RecommendationPlaylist {
        val songs = mutableListOf<com.example.purrytify.models.Song>()

        // Add liked songs (up to 10)
        val shuffledLiked = likedSongs.shuffled().take(10)
        songs.addAll(shuffledLiked.map { convertToUiSong(it) })

        // Add similar songs from user's library based on artist matching
        val similarSongs = findSimilarSongs(likedSongs, allUserSongs).take(5)
        songs.addAll(similarSongs.map { convertToUiSong(it) })

        // Add popular online songs
        try {
            val globalSongs = RetrofitClient.apiService.getGlobalTopSongs()
            if (globalSongs.isSuccessful && globalSongs.body() != null) {
                val onlineSongs = globalSongs.body()!!.shuffled().take(5)
                songs.addAll(onlineSongs.map { convertOnlineToUiSong(it) })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch online songs for daily mix", e)
        }

        val coverUrl = when {
            likedSongs.isNotEmpty() && likedSongs.first().artworkPath.isNotEmpty() -> likedSongs.first().artworkPath
            songs.isNotEmpty() && songs.first().coverUrl.isNotEmpty() -> songs.first().coverUrl
            else -> "https://picsum.photos/200" // Default cover
        }

        return RecommendationPlaylist(
            id = "daily_mix_${System.currentTimeMillis()}",
            title = "Daily Mix",
            description = "Your top songs",
            coverUrl = coverUrl,
            songs = songs.shuffled().take(20),
            type = "daily_mix"
        )
    }

    private suspend fun generateDiscoverWeekly(
        recentlyPlayed: List<Song>,
        allUserSongs: List<Song>,
        userId: Int
    ): RecommendationPlaylist {
        val songs = mutableListOf<com.example.purrytify.models.Song>()

        // Get songs user hasn't played recently
        val recentIds = recentlyPlayed.map { it.id }.toSet()
        val unplayedSongs = allUserSongs.filter { it.id !in recentIds && it.lastPlayed == null }
            .shuffled().take(10)
        songs.addAll(unplayedSongs.map { convertToUiSong(it) })

        // Add online songs from top charts for discovery
        try {
            val globalSongs = RetrofitClient.apiService.getGlobalTopSongs()
            if (globalSongs.isSuccessful && globalSongs.body() != null) {
                val onlineSongs = globalSongs.body()!!.shuffled().take(15)
                songs.addAll(onlineSongs.map { convertOnlineToUiSong(it) })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch online songs for discover weekly", e)
        }

        return RecommendationPlaylist(
            id = "discover_weekly_${System.currentTimeMillis()}",
            title = "Discover Weekly",
            description = "New music for you",
            coverUrl = "https://i.scdn.co/image/ab67706f00000002ca5a7517156021292e5663a6",
            songs = songs.shuffled().take(25),
            type = "discover_weekly"
        )
    }

    private suspend fun generateSpecificGenreMix(
        genreName: String,
        likedSongs: List<Song>,
        allUserSongs: List<Song>,
        userId: Int
    ): RecommendationPlaylist {
        val genreSongs = mutableListOf<com.example.purrytify.models.Song>()

        // Get genre keywords
        val genreKeywords = getGenreKeywords(genreName)

        // Find matching songs from user's library
        val matchingSongs = allUserSongs.filter { song ->
            genreKeywords.any { keyword ->
                song.title.contains(keyword, ignoreCase = true) ||
                        song.artist.contains(keyword, ignoreCase = true)
            }
        }.shuffled().take(10)

        genreSongs.addAll(matchingSongs.map { convertToUiSong(it) })

        // Add online songs for variety
        try {
            val globalSongs = RetrofitClient.apiService.getGlobalTopSongs()
            if (globalSongs.isSuccessful && globalSongs.body() != null) {
                // Filter online songs by genre keywords if possible
                val filteredOnlineSongs = globalSongs.body()!!.filter { song ->
                    genreKeywords.any { keyword ->
                        song.title.contains(keyword, ignoreCase = true) ||
                                song.artist.contains(keyword, ignoreCase = true)
                    }
                }.take(5)

                // If not enough filtered songs, add random ones
                val additionalSongs = if (filteredOnlineSongs.size < 5) {
                    globalSongs.body()!!.shuffled().take(5 - filteredOnlineSongs.size)
                } else {
                    emptyList()
                }

                genreSongs.addAll(filteredOnlineSongs.map { convertOnlineToUiSong(it) })
                genreSongs.addAll(additionalSongs.map { convertOnlineToUiSong(it) })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not fetch online songs for $genreName", e)
        }

        // Ensure we have at least some songs
        if (genreSongs.isEmpty()) {
            // Add some random songs if no genre matches found
            val randomSongs = allUserSongs.shuffled().take(10)
            genreSongs.addAll(randomSongs.map { convertToUiSong(it) })
        }

        // Use the first song's cover from the mix, or default image
        val coverUrl = if (genreSongs.isNotEmpty() && genreSongs.first().coverUrl.isNotEmpty()) {
            genreSongs.first().coverUrl
        } else {
            getGenreCoverUrl(genreName)
        }

        return RecommendationPlaylist(
            id = "${genreName.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}",
            title = genreName,
            description = "Your $genreName playlist",
            coverUrl = coverUrl,
            songs = genreSongs.shuffled().take(20),
            type = "genre_mix"
        )
    }

    private fun findSimilarSongs(likedSongs: List<Song>, allSongs: List<Song>): List<Song> {
        val likedArtists = likedSongs.map { it.artist.lowercase() }.toSet()
        val likedIds = likedSongs.map { it.id }.toSet()

        return allSongs.filter { song ->
            song.id !in likedIds && // Not already liked
                    likedArtists.any { artist ->
                        song.artist.lowercase().contains(artist) ||
                                artist.contains(song.artist.lowercase())
                    }
        }
    }

    private fun getGenreKeywords(genreName: String): List<String> {
        return when (genreName) {
            "Pop Mix" -> listOf("pop", "taylor", "dua", "ariana", "bruno", "ed", "justin", "selena", "shawn", "charlie")
            "Chill Mix" -> listOf("chill", "relax", "acoustic", "soft", "ambient", "jazz", "lo-fi", "calm", "sleep", "meditation")
            else -> emptyList()
        }
    }

    private fun getGenreCoverUrl(genreName: String): String {
        return when (genreName) {
            "Pop Mix" -> "https://i.scdn.co/image/ab67706f000000027ea4d505212b9de1f72c5112"
            "Chill Mix" -> "https://i.scdn.co/image/ab67706f000000028b5575c8d9c62d9701a84c36"
            else -> "https://i.scdn.co/image/ab67706f00000002ca5a7517156021292e5663a6"
        }
    }

    private fun convertToUiSong(entitySong: Song): com.example.purrytify.models.Song {
        return com.example.purrytify.models.Song(
            id = entitySong.id,
            title = entitySong.title,
            artist = entitySong.artist,
            coverUrl = entitySong.artworkPath,
            filePath = entitySong.filePath,
            duration = entitySong.duration,
            isLiked = entitySong.isLiked,
            isOnline = entitySong.isOnline,
            onlineId = entitySong.onlineId
        )
    }

    private fun convertOnlineToUiSong(onlineSong: OnlineSong): com.example.purrytify.models.Song {
        return com.example.purrytify.models.Song(
            id = -onlineSong.id.toLong(), // Negative ID for online songs
            title = onlineSong.title,
            artist = onlineSong.artist,
            coverUrl = onlineSong.artworkUrl,
            filePath = onlineSong.audioUrl,
            duration = onlineSong.getDurationInMillis(),
            isOnline = true,
            onlineId = onlineSong.id
        )
    }
}