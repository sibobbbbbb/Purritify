package com.example.purrytify.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.purrytify.models.Song
import com.example.purrytify.util.ShareUtils

@Composable
fun SongOverflowMenu(
    song: Song,
    onAddToQueue: () -> Unit,
    onToggleLike: (Boolean) -> Unit,
    onEditSong: (() -> Unit)? = null,
    onDeleteSong: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color.White
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Add to Queue
            DropdownMenuItem(
                text = { Text("Add to Queue") },
                onClick = {
                    onAddToQueue()
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                }
            )

            // Toggle Like
            DropdownMenuItem(
                text = { Text(if (song.isLiked) "Unlike" else "Like") },
                onClick = {
                    onToggleLike(!song.isLiked)
                    expanded = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (song.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null
                    )
                }
            )

            // Share (only for online songs)
            if (song.isOnline && song.onlineId != null) {
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        // Helper function to convert milliseconds to mm:ss format
                        fun formatDurationFromMs(durationMs: Long): String {
                            val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMs)
                            val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durationMs) -
                                    java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
                            return String.format("%d:%02d", minutes, seconds)
                        }

                        val onlineSong = com.example.purrytify.models.OnlineSong(
                            id = song.onlineId!!,
                            title = song.title,
                            artist = song.artist,
                            artworkUrl = song.coverUrl,
                            audioUrl = song.filePath,
                            durationString = formatDurationFromMs(song.duration),
                            country = "",
                            rank = 0,
                            createdAt = "",
                            updatedAt = ""
                        )
                        ShareUtils.shareSongUrl(context, onlineSong)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null
                        )
                    }
                )
            }

            // Edit (only for local songs)
            if (!song.isOnline && onEditSong != null) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        onEditSong()
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null
                        )
                    }
                )
            }

            // Delete (only for local songs)
            if (!song.isOnline && onDeleteSong != null) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        onDeleteSong()
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.Red
                        )
                    }
                )
            }
        }
    }
}