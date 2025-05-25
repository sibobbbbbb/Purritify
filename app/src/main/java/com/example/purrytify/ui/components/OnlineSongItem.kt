// Location: app/src/main/java/com/example/purrytify/ui/components/OnlineSongItem.kt

package com.example.purrytify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.ui.theme.GREEN_COLOR

@Composable
fun OnlineSongItem(
    song: OnlineSong,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    showRank: Boolean = false,
    rank: Int = 0,
    onSongClick: (OnlineSong) -> Unit,
    onDownloadClick: ((OnlineSong) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSongClick(song) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank (if shown)
        if (showRank) {
            Text(
                text = "$rank",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(30.dp)
            )
        }

        // Cover Art
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "${song.title} artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Playing indicator overlay
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Now Playing",
                        tint = GREEN_COLOR,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Song info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = song.title,
                color = if (isPlaying) GREEN_COLOR else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song.artist,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Download button or status
        if (onDownloadClick != null) {
            IconButton(
                onClick = { if (!isDownloaded && !isDownloading) onDownloadClick(song) },
                modifier = Modifier.size(48.dp)
            ) {
                when {
                    isDownloaded -> {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = GREEN_COLOR,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    isDownloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.Yellow,
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}