package com.example.purrytify.util

import com.example.purrytify.data.entity.Song as EntitySong
import com.example.purrytify.models.Song as ModelSong


object SongMapper {


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


    fun fromEntityList(entities: List<EntitySong>, playingSongId: Long = -1): List<ModelSong> {
        return entities.map { fromEntity(it, it.id == playingSongId) }
    }
}