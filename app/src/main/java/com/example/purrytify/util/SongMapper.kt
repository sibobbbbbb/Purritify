package com.example.purrytify.util

import com.example.purrytify.data.entity.Song as EntitySong
import com.example.purrytify.models.Song as ModelSong

/**
 * Mapper untuk mengkonversi antara entity Song (Room) dan model Song (UI)
 */
object SongMapper {

    /**
     * Mengkonversi dari entity Song ke model Song
     * @param entity Entity Song dari database
     * @param isPlaying Status apakah lagu sedang diputar
     * @return Model Song untuk UI
     */
    fun fromEntity(entity: com.example.purrytify.data.entity.Song, isPlaying: Boolean = false): com.example.purrytify.models.Song {
        return com.example.purrytify.models.Song(
            id = entity.id,
            title = entity.title,
            artist = entity.artist,
            coverUrl = entity.artworkPath,
            filePath = entity.filePath,
            duration = entity.duration,
            isPlaying = isPlaying,
            isLiked = entity.isLiked,
            isOnline = entity.isOnline,
            onlineId = entity.onlineId,
            lastPlayed = entity.lastPlayed,
            addedAt = entity.addedAt,
            userId = entity.userId
        )
    }

    /**
     * Mengkonversi dari model Song ke entity Song
     * @param model Model Song dari UI
     * @return Entity Song untuk database
     */
    fun toEntity(model: com.example.purrytify.models.Song): com.example.purrytify.data.entity.Song {
        return com.example.purrytify.data.entity.Song(
            id = model.id,
            title = model.title,
            artist = model.artist,
            artworkPath = model.coverUrl,
            filePath = model.filePath,
            duration = model.duration,
            isLiked = model.isLiked,
            lastPlayed = model.lastPlayed,
            addedAt = model.addedAt,
            userId = model.userId,
            isOnline = model.isOnline,
            onlineId = model.onlineId
        )
    }

    /**
     * Mengkonversi list entity Song ke list model Song
     * @param entities List entity Song dari database
     * @param playingSongId ID lagu yang sedang diputar
     * @return List model Song untuk UI
     */
    fun fromEntityList(entities: List<EntitySong>, playingSongId: Long = -1): List<ModelSong> {
        return entities.map { fromEntity(it, it.id == playingSongId) }
    }
}