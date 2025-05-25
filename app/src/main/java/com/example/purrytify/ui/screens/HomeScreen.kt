package com.example.purrytify.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.purrytify.R
import com.example.purrytify.models.Song
import com.example.purrytify.models.RecommendationPlaylist
import com.example.purrytify.ui.components.NewSongs
import com.example.purrytify.ui.components.SongItem
import com.example.purrytify.ui.components.RecommendationSection
import com.example.purrytify.ui.components.RecommendationPlaylistView
import com.example.purrytify.ui.theme.BACKGROUND_COLOR
import com.example.purrytify.ui.theme.GREEN_COLOR
import com.example.purrytify.viewmodels.HomeViewModel
import com.example.purrytify.viewmodels.MainViewModel
import com.example.purrytify.viewmodels.RecommendationViewModel
import com.example.purrytify.viewmodels.ViewModelFactory
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.navigation.NavController
import com.example.purrytify.ui.navigation.Destinations

// UPDATE signature HomeScreen untuk menerima navController:import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    navController: NavController? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val isTablet = screenWidth > 600.dp

    // ViewModels
    val homeViewModel: HomeViewModel = viewModel(
        factory = ViewModelFactory.getInstance(context)
    )

    // MainViewModel untuk managing current song dan playback state
    val mainViewModel: MainViewModel = viewModel(
        viewModelStoreOwner = context as ComponentActivity,
        factory = ViewModelFactory.getInstance(context)
    )

    val recommendationViewModel: RecommendationViewModel = viewModel(
        factory = ViewModelFactory.getInstance(context)
    )

    // Observe data
    val newSongs by homeViewModel.newSongs.observeAsState(emptyList())
    val recentlyPlayed by homeViewModel.recentlyPlayed.observeAsState(emptyList())
    val currentSong by mainViewModel.currentSong.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()

    // Recommendation data
    val recommendations by recommendationViewModel.recommendations.collectAsState()
    val isRecommendationsLoading by recommendationViewModel.isLoading.collectAsState()
    val recommendationError by recommendationViewModel.error.collectAsState()

    // For showing feedback when adding to queue
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State for showing playlist view
    var selectedPlaylist by remember { mutableStateOf<RecommendationPlaylist?>(null) }

    // Get current time for greeting
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = remember(currentHour) {
        when (currentHour) {
            in 0..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Load recommendations on first composition
    LaunchedEffect(key1 = Unit) {
        recommendationViewModel.loadRecommendations()
    }

    // Show error message if any
    LaunchedEffect(recommendationError) {
        recommendationError?.let { error ->
            snackbarHostState.showSnackbar("Error loading recommendations: $error")
            recommendationViewModel.clearError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BACKGROUND_COLOR)
    ) {
        if (selectedPlaylist != null) {
            // Show playlist view
            RecommendationPlaylistView(
                playlist = selectedPlaylist!!,
                onSongClick = { song ->
                    // This will be handled by the component itself with playlist context
                    // Fallback for when MainViewModel is not available
                    mainViewModel.playSong(song)
                },
                onAddToQueue = { song ->
                    mainViewModel.addToQueue(song)
                    scope.launch {
                        snackbarHostState.showSnackbar("Added to queue: ${song.title}")
                    }
                },
                onBackClick = {
                    selectedPlaylist = null
                },
                mainViewModel = mainViewModel // Pass MainViewModel for playlist context
            )
        } else {
            // Show main home screen
            if (isTablet) {
                TabletHomeLayout(
                    greeting = greeting,
                    recommendations = recommendations,
                    isRecommendationsLoading = isRecommendationsLoading,
                    newSongs = newSongs,
                    recentlyPlayed = recentlyPlayed,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onPlaylistClick = { selectedPlaylist = it }, // This opens playlist view
                    onSongClick = { mainViewModel.playSong(it) }, // This plays individual song
                    onAddToQueue = { song ->
                        mainViewModel.addToQueue(song)
                        scope.launch {
                            snackbarHostState.showSnackbar("Added to queue: ${song.title}")
                        }
                    },
                    onToggleLike = { song, isLiked ->
                        mainViewModel.toggleLike(song.id, isLiked)
                    },
                    onRefreshRecommendations = {
                        recommendationViewModel.refreshRecommendations()
                    },
                    onQRScanClick = {
                        navController?.navigate(Destinations.QR_SCANNER_ROUTE)
                    },
                    mainViewModel = mainViewModel // Pass MainViewModel for playlist context
                )
            } else {
                PhoneHomeLayout(
                    greeting = greeting,
                    recommendations = recommendations,
                    isRecommendationsLoading = isRecommendationsLoading,
                    newSongs = newSongs,
                    recentlyPlayed = recentlyPlayed,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onPlaylistClick = { selectedPlaylist = it }, // This opens playlist view
                    onSongClick = { mainViewModel.playSong(it) }, // This plays individual song
                    onAddToQueue = { song ->
                        mainViewModel.addToQueue(song)
                        scope.launch {
                            snackbarHostState.showSnackbar("Added to queue: ${song.title}")
                        }
                    },
                    onToggleLike = { song, isLiked ->
                        mainViewModel.toggleLike(song.id, isLiked)
                    },
                    onRefreshRecommendations = {
                        recommendationViewModel.refreshRecommendations()
                    },
                    onQRScanClick = {
                        navController?.navigate(Destinations.QR_SCANNER_ROUTE)
                    },
                    mainViewModel = mainViewModel // Pass MainViewModel for playlist context
                )
            }
        }

        // Snackbar for showing queue messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}

@Composable
private fun PhoneHomeLayout(
    greeting: String,
    recommendations: List<RecommendationPlaylist>,
    isRecommendationsLoading: Boolean,
    newSongs: List<Song>,
    recentlyPlayed: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlaylistClick: (RecommendationPlaylist) -> Unit,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleLike: (Song, Boolean) -> Unit,
    onRefreshRecommendations: () -> Unit,
    onQRScanClick: () -> Unit,
    mainViewModel: MainViewModel? = null // ADD this parameter

) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
    ) {
        item {
            // Welcome Header with Gradient and QR Scanner button
            WelcomeHeader(
                greeting = greeting,
                onQRScanClick = onQRScanClick
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            // Recommendations Section
            RecommendationSection(
                recommendations = recommendations,
                onPlaylistClick = onPlaylistClick,
                onSongClick = onSongClick,
                onRefreshClick = onRefreshRecommendations,
                isLoading = isRecommendationsLoading,
                mainViewModel = mainViewModel // PASS MainViewModel

            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            // New Songs Section
            NewSongsSection(
                songs = newSongs,
                onSongClick = onSongClick,
                onAddToQueue = onAddToQueue
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        item {
            // Recently Played Section Header
            RecentlyPlayedHeader()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Recently Played Songs
        if (recentlyPlayed.isNotEmpty()) {
            items(recentlyPlayed) { song ->
                SongItem(
                    song = song.copy(isPlaying = currentSong?.id == song.id && isPlaying),
                    onSongClick = onSongClick,
                    onAddToQueue = onAddToQueue,
                    onToggleLike = onToggleLike
                )
            }
        } else {
            item {
                EmptyRecentlyPlayedState()
            }
        }

        item {
            // Bottom spacing for better scrolling experience
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun TabletHomeLayout(
    greeting: String,
    recommendations: List<RecommendationPlaylist>,
    isRecommendationsLoading: Boolean,
    newSongs: List<Song>,
    recentlyPlayed: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlaylistClick: (RecommendationPlaylist) -> Unit,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleLike: (Song, Boolean) -> Unit,
    onRefreshRecommendations: () -> Unit,
    onQRScanClick: () -> Unit,
    mainViewModel: MainViewModel? = null // ADD this parameter
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Welcome Header with QR Scanner button
        WelcomeHeader(
            greeting = greeting,
            onQRScanClick = onQRScanClick
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Two column layout for tablet
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Recommendations
                RecommendationSection(
                    recommendations = recommendations,
                    onPlaylistClick = onPlaylistClick,
                    onSongClick = onSongClick,
                    onRefreshClick = onRefreshRecommendations,
                    isLoading = isRecommendationsLoading,
                    mainViewModel = mainViewModel // PASS MainViewModel
                )

                Spacer(modifier = Modifier.height(32.dp))

                // New Songs
                NewSongsSection(
                    songs = newSongs,
                    onSongClick = onSongClick,
                    onAddToQueue = onAddToQueue
                )
            }

            // Right Column - Recently Played
            Column(
                modifier = Modifier.weight(1f)
            ) {
                RecentlyPlayedHeader()
                Spacer(modifier = Modifier.height(16.dp))

                if (recentlyPlayed.isNotEmpty()) {
                    recentlyPlayed.forEach { song ->
                        SongItem(
                            song = song.copy(isPlaying = currentSong?.id == song.id && isPlaying),
                            onSongClick = onSongClick,
                            onAddToQueue = onAddToQueue,
                            onToggleLike = onToggleLike
                        )
                    }
                } else {
                    EmptyRecentlyPlayedState()
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun WelcomeHeader(
    greeting: String,
    onQRScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        GREEN_COLOR.copy(alpha = 0.8f),
                        Color(0xFF1ed760).copy(alpha = 0.6f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Greeting Text
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = greeting,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = FontFamily(Font(R.font.poppins_bold)),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Let's find something to listen to",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontFamily = FontFamily(Font(R.font.poppins_regular))
                )
            }

            // QR Scanner Button
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color.White.copy(alpha = 0.2f)
            ) {
                IconButton(
                    onClick = onQRScanClick,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode,
                        contentDescription = "Scan QR Code",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewSongsSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit
) {
    Column {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = GREEN_COLOR,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Songs",
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (songs.isNotEmpty()) {
                Text(
                    text = "${songs.size} songs",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (songs.isNotEmpty()) {
            EnhancedNewSongs(
                songs = songs,
                onSongClick = onSongClick,
                onAddToQueue = onAddToQueue
            )
        } else {
            EmptyNewSongsState()
        }
    }
}

@Composable
private fun RecentlyPlayedHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = GREEN_COLOR,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Recently Played",
                color = Color.White,
                fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun EmptyRecentlyPlayedState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No recently played songs",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Start listening to see your history here",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyNewSongsState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No new songs yet",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Add some songs to your library",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EnhancedNewSongs(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onAddToQueue: ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val itemWidth = when {
        screenWidth > 900.dp -> 180.dp // Tablet landscape
        screenWidth > 600.dp -> 160.dp // Tablet portrait
        else -> 140.dp // Phone
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = songs,
            key = { it.id }
        ) { song ->
            EnhancedNewSongCard(
                song = song,
                onSongClick = { onSongClick(song) },
                onAddToQueue = onAddToQueue?.let { addFn -> { addFn(song) } },
                modifier = Modifier.width(itemWidth)
            )
        }
    }
}

@Composable
private fun EnhancedNewSongCard(
    song: Song,
    onSongClick: () -> Unit,
    onAddToQueue: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable { onSongClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Cover Art with gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF333333))
            ) {
                // Image
                val context = LocalContext.current
                val imageData = when {
                    song.coverUrl.startsWith("http") -> song.coverUrl
                    song.coverUrl.isNotEmpty() && File(song.coverUrl).exists() -> File(song.coverUrl)
                    else -> null
                }

                if (imageData != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageData)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album Cover",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music",
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Playing indicator
                if (song.isPlaying) {
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
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Add to queue button overlay
                if (onAddToQueue != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            IconButton(
                                onClick = onAddToQueue,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_queue_music),
                                    contentDescription = "Add to Queue",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Song Info
            Text(
                text = song.title,
                color = if (song.isPlaying) GREEN_COLOR else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = song.artist,
                color = Color.Gray,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}