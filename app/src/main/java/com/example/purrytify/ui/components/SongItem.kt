package com.example.purrytify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.purrytify.models.Song
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.size
import com.example.purrytify.util.ShareUtils
import java.util.concurrent.TimeUnit
import androidx.compose.ui.platform.LocalContext

@Composable
fun SongItem(
    song: Song,
    onSongClick: (Song) -> Unit,
    onAddToQueue: ((Song) -> Unit)? = null,
    onRemoveSong: ((Song) -> Unit)? = null,
    onToggleLike: ((Song, Boolean) -> Unit)? = null,
    onEditSong: ((Song) -> Unit)? = null,
    onDeleteSong: ((Song) -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showAddToQueueDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Use a more dramatic right shift for the dropdown
    val dropdownOffset = remember { DpOffset(x = (-24).dp, y = 0.dp) }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSongClick(song) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art using our custom component
            SongCoverImage(
                imageUrl = song.coverUrl,
                contentDescription = "${song.title} album art",
                modifier = Modifier.size(50.dp)
            )

            // Song Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 8.dp)
            ) {
                Text(
                    text = song.title,
                    color = if (song.isPlaying) Color.Green else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = song.artist,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            // Only show if this is the currently playing song
            if (song.isPlaying) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Now Playing",
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
            }

            // More options button with Box wrapper to position dropdown better
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Dropdown positioned relative to its Box parent
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF333333)),
                    offset = dropdownOffset
                ) {
                    // Add to Queue option (if handler is provided)
                    if (onAddToQueue != null) {
                        QueueDropdownItem(
                            song = song,
                            onAddToQueue = onAddToQueue,
                            onDropdownDismiss = { showMenu = false }
                        )
                    }

                    // Like/Unlike option
                    if (onToggleLike != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (song.isLiked) "Remove from Liked" else "Add to Liked",
                                    color = Color.White
                                )
                            },
                            onClick = {
                                onToggleLike(song, !song.isLiked)
                                showMenu = false
                            }
                        )
                    }

                    if (song.isOnline && song.onlineId != null) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Share Song", color = Color.White)
                                }
                            },
                            onClick = {
                                // Set a state to show ShareOptionsDialog
                                // This would need to be handled by the parent component
                                showMenu = false

                                // For now, keep the direct sharing - but ideally use ShareOptionsDialog
                                val formatDurationFromMs = { durationMs: Long ->
                                    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
                                    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) -
                                            TimeUnit.MINUTES.toSeconds(minutes)
                                    String.format("%d:%02d", minutes, seconds)
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
                            }
                        )
                    }

                    // Edit song option (if handler is provided)
                    if (onEditSong != null) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Edit Song", color = Color.White)
                                }
                            },
                            onClick = {
                                onEditSong(song)
                                showMenu = false
                            }
                        )
                    }

                    // Delete song option (if handler is provided)
                    if (onDeleteSong != null) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color.Red
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Delete Song", color = Color.Red)
                                }
                            },
                            onClick = {
                                onDeleteSong(song)
                                showMenu = false
                            }
                        )
                    }

                    // Remove song option (if handler is provided)
                    if (onRemoveSong != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from Queue", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onRemoveSong(song)
                            }
                        )
                    }
                }
            }
        }
    }

    // Show Add to Queue dialog if requested
    if (showAddToQueueDialog && onAddToQueue != null) {
        AddToQueueDialog(
            song = song,
            onDismiss = { showAddToQueueDialog = false },
            onAddToQueue = {
                onAddToQueue(it)
                showAddToQueueDialog = false
            }
        )
    }
}