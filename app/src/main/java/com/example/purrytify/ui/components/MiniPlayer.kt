package com.example.purrytify.ui.components

import androidx.compose.runtime.Composable
import com.example.purrytify.models.Song
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.purrytify.R
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.purrytify.ui.components.AudioDeviceIndicatorMini


@Composable
fun MiniPlayer(
    currentSong: Song?,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPlayerClick: () -> Unit,
    onAudioDeviceClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    if (currentSong != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color(0xFF5A1E33))
                .clickable { onPlayerClick() }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art - using simple Image with placeholder
            // Note: For better image loading, use the SongCoverImage component
            if (currentSong.coverUrl.isNotEmpty()) {
                // Ideal: Use Coil or Glide untuk loading image dari file path
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(currentSong.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${currentSong.title} album art",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.ic_image_placeholder),
                    contentDescription = "${currentSong.title} album art",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Song Info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = currentSong.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong.artist,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ✅ PERBAIKAN: Share button with proper state management
            if (currentSong.isOnline && currentSong.onlineId != null) {
                var showShareDialog by remember { mutableStateOf(false) }

                IconButton(onClick = {
                    showShareDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }

                // Show share options dialog
                if (showShareDialog) {
                    // Helper function to convert milliseconds to mm:ss format
                    fun formatDurationFromMs(durationMs: Long): String {
                        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMs)
                        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durationMs) -
                                java.util.concurrent.TimeUnit.MINUTES.toSeconds(minutes)
                        return String.format("%d:%02d", minutes, seconds)
                    }

                    val onlineSong = com.example.purrytify.models.OnlineSong(
                        id = currentSong.onlineId!!,
                        title = currentSong.title,
                        artist = currentSong.artist,
                        artworkUrl = currentSong.coverUrl,
                        audioUrl = currentSong.filePath,
                        durationString = formatDurationFromMs(currentSong.duration),
                        country = "",
                        rank = 0,
                        createdAt = "",
                        updatedAt = ""
                    )

                    ShareOptionsDialog(
                        onlineSong = onlineSong,
                        onDismiss = { showShareDialog = false }
                    )
                }
            }

            // Play/Pause Button
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White
                )
            }

            if (onAudioDeviceClick != null) {
                AudioDeviceIndicatorMini(
                    onClick = onAudioDeviceClick
                )
            }
        }
    }
}