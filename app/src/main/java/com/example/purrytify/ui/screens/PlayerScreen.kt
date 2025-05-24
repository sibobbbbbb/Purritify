package com.example.purrytify.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.purrytify.R
import com.example.purrytify.ui.navigation.Destinations
import com.example.purrytify.ui.theme.BACKGROUND_COLOR
import com.example.purrytify.ui.theme.GREEN_COLOR
import com.example.purrytify.viewmodels.MainViewModel
import com.example.purrytify.viewmodels.ViewModelFactory
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import com.example.purrytify.ui.components.AudioDeviceIndicator

@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    navController: NavController? = null,
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = ViewModelFactory.getInstance(LocalContext.current)
    )
) {
    val currentSong by mainViewModel.currentSong.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val currentPosition by mainViewModel.currentPosition.collectAsState()
    val duration by mainViewModel.duration.collectAsState()
    val queue by mainViewModel.queue.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Modified to track if the song is liked
    val isLiked = remember { mutableStateOf(currentSong?.isLiked ?: false) }

    // Keep isLiked in sync with currentSong
    LaunchedEffect(currentSong) {
        currentSong?.let {
            isLiked.value = it.isLiked
        }
    }

    // For repeat mode (bonus feature)
    val repeatMode by mainViewModel.repeatMode.collectAsState()
    val shuffleEnabled by mainViewModel.shuffleEnabled.collectAsState()

    // Get local context for intent
    val context = LocalContext.current

    // Calculate slider position
    val sliderPosition = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
    val animatedSliderPosition by animateFloatAsState(targetValue = sliderPosition, label = "")

    if (currentSong == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(BACKGROUND_COLOR),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No song is currently playing",
                color = Color.White,
                fontSize = 16.sp
            )
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color(0xFF5A1E33) // Background color matching the Figma design
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .systemBarsPadding() // Add system bars padding to ensure content isn't hidden
        ) {
            // Top Bar with back button and options
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Queue button
                IconButton(onClick = {
                    navController?.navigate(Destinations.QUEUE_ROUTE)
                    onDismiss() // Close player screen when navigating to queue
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_queue_music),
                        contentDescription = "Queue",
                        tint = Color.White
                    )
                }
            }

            // Album Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Display the album artwork
                val coverImage = if (currentSong?.coverUrl?.startsWith("http") == true) {
                    // URL image
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentSong?.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Cover",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else if (currentSong?.coverUrl?.isNotEmpty() == true && File(currentSong?.coverUrl ?: "").exists()) {
                    // Local file
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(currentSong?.coverUrl ?: ""))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Cover",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Fallback
                    Image(
                        painter = painterResource(id = R.drawable.logo_aplikasi),
                        contentDescription = "Album Cover",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Song title and artist
            currentSong?.let { song ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontFamily = FontFamily(Font(R.font.poppins_bold))
                        )

                        Text(
                            text = song.artist,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            fontFamily = FontFamily(Font(R.font.poppins_regular))
                        )
                    }

                    // Add to queue button
                    IconButton(
                        onClick = {
                            mainViewModel.addToQueue(song)
                            scope.launch {
                                snackbarHostState.showSnackbar("Added to queue: ${song.title}")
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_queue_music),
                            contentDescription = "Add to Queue",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Like button
                    IconButton(
                        onClick = {
                            isLiked.value = !isLiked.value
                            mainViewModel.toggleLike(song.id, isLiked.value)
                        }
                    ) {
                        Icon(
                            imageVector = if (isLiked.value) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isLiked.value) "Unlike" else "Like",
                            tint = if (isLiked.value) Color.White else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Slider(
                    value = animatedSliderPosition,
                    onValueChange = { value ->
                        val seekPosition = (value * duration).toInt()
                        mainViewModel.seekTo(seekPosition)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = GREEN_COLOR,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Time indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Current position
                    Text(
                        text = formatDuration(currentPosition.toLong()),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )

                    // Total duration
                    Text(
                        text = formatDuration(duration.toLong()),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Playback Controls
            // Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle button (bonus feature)
                IconButton(onClick = {
                    mainViewModel.setShuffleEnabled(!shuffleEnabled)
                }) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleEnabled) GREEN_COLOR else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous button
                IconButton(
                    onClick = {
                        // Call previous function in view model
                        mainViewModel.playPrevious()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Play/Pause button
                IconButton(
                    onClick = {
                        mainViewModel.togglePlayPause()
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GREEN_COLOR)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Next button
                IconButton(
                    onClick = {
                        // Call next function in view model
                        mainViewModel.playNext()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Repeat button (bonus feature)
                IconButton(onClick = {
                    // Cycle through repeat modes: Off -> Repeat All -> Repeat One -> Off
                    val newMode = (repeatMode + 1) % 3
                    // Implement repeat functionality in MainViewModel
                    mainViewModel.setRepeatMode(newMode)
                }) {
                    when (repeatMode) {
                        0 -> Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = "Repeat Off",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        1 -> Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = "Repeat All",
                            tint = GREEN_COLOR,
                            modifier = Modifier.size(24.dp)
                        )
                        2 -> Icon(
                            imageVector = Icons.Filled.RepeatOne,
                            contentDescription = "Repeat One",
                            tint = GREEN_COLOR,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Snackbar host for feedback
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Audio device indicator
//        AudioDeviceIndicator(
//            onClick = {
//                navController?.navigate("audio_devices")
//            },
//            modifier = Modifier.align(Alignment.Center)
//        )
    }
}

// Helper function to format duration
private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%d:%02d", minutes, seconds)
}