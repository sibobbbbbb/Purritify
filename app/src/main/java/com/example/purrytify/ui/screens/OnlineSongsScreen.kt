package com.example.purrytify.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.ui.components.NoInternetScreen
import com.example.purrytify.ui.components.OnlineSongItem
import com.example.purrytify.ui.theme.BACKGROUND_COLOR
import com.example.purrytify.ui.theme.GREEN_COLOR
import com.example.purrytify.util.NetworkUtils
import com.example.purrytify.util.SongDownloadManager
import com.example.purrytify.util.toSong
import com.example.purrytify.viewmodels.OnlineSongsViewModel
import com.example.purrytify.viewmodels.OnlineSongsViewModelFactory
import kotlinx.coroutines.launch
import com.example.purrytify.viewmodels.LibraryViewModel
import com.example.purrytify.viewmodels.ViewModelFactory
import com.example.purrytify.viewmodels.MainViewModel
import com.example.purrytify.viewmodels.PlaylistContext
import com.example.purrytify.viewmodels.PlaylistType

@Composable
fun OnlineSongsScreen(
    onSongSelected: (OnlineSong) -> Unit,
    viewModel: OnlineSongsViewModel = viewModel(
        factory = OnlineSongsViewModelFactory(LocalContext.current)
    ),
    mainViewModel: MainViewModel? = null // ADD: MainViewModel parameter
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Initialize download manager
    val downloadManager = remember { SongDownloadManager(context) }

    // Check network connection
    val isNetworkAvailable = remember { mutableStateOf(NetworkUtils.isNetworkAvailable(context)) }

    // Observe data from ViewModel
    val globalTopSongs by viewModel.globalTopSongs.observeAsState(emptyList())
    val countryTopSongs by viewModel.countryTopSongs.observeAsState(emptyList())
    val isLoading by viewModel.isLoading.observeAsState(false)
    val error by viewModel.error.observeAsState(null)
    val downloadedSongIds by viewModel.downloadedSongIds.observeAsState(emptySet())
    val downloadingSongs by viewModel.downloadingSongs.observeAsState(emptySet())

    // Observe download states
    val downloadStates by downloadManager.downloadStates.collectAsState()

    // Tab selection state
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Global Top 50", "Country Top 10")

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = ViewModelFactory.getInstance(LocalContext.current)
    )

    // Load data on first composition
    LaunchedEffect(key1 = Unit) {
        viewModel.loadData()
    }

    // Handle download completion
    LaunchedEffect(downloadStates) {
        downloadStates.forEach { (songId, state) ->
            when (state) {
                is SongDownloadManager.DownloadState.Completed -> {
                    // If we haven't processed this completion yet
                    if (!downloadedSongIds.contains(songId)) {
                        Log.d("OnlineSongsScreen", "Download completed for song $songId, saving to database")

                        // Find the song in either global or country list
                        val song = globalTopSongs.find { it.id == songId }
                            ?: countryTopSongs.find { it.id == songId }

                        // Save the downloaded song to the database
                        song?.let {
                            coroutineScope.launch {
                                val result = viewModel.saveDownloadedSong(it, state.localFilePath)
                                Log.d("OnlineSongsScreen", "Save result for song $songId: $result")
                                if (result > 0) {
                                    viewModel.markSongAsDownloaded(songId)
                                    // Force refresh downloaded songs list
                                    viewModel.loadDownloadedSongIds()
                                    // Debug database content
                                    libraryViewModel.debugDatabaseContent()
                                }

                            }
                        }
                    }
                }
                is SongDownloadManager.DownloadState.Failed -> {
                    Log.e("OnlineSongsScreen", "Download failed for song $songId: ${state.reason}")
                    // Remove from downloading state
                    viewModel.removeFromDownloading(songId)
                }
                else -> {} // Downloading state is handled elsewhere
            }
        }
    }

    // Show no internet screen if not connected
    if (!isNetworkAvailable.value) {
        NoInternetScreen(modifier = Modifier.fillMaxSize())
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BACKGROUND_COLOR)
    ) {
        Column {
            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF121212),
                contentColor = GREEN_COLOR
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }

            // Content based on selected tab
            when (selectedTabIndex) {
                0 -> {
                    // Global Top 50
                    if (isLoading && globalTopSongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GREEN_COLOR)
                        }
                    } else if (error != null && globalTopSongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Error: $error",
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = "Global Top 50",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            itemsIndexed(globalTopSongs) { index, song ->
                                val isDownloaded = downloadedSongIds.contains(song.id)
                                val isDownloading = downloadingSongs.contains(song.id) ||
                                        downloadStates[song.id] is SongDownloadManager.DownloadState.Downloading

                                OnlineSongItem(
                                    song = song,
                                    isPlaying = false, // Update based on currently playing song
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading,
                                    showRank = true,
                                    rank = index + 1,
                                    onSongClick = { clickedSong ->
                                        // UPDATED: Use playlist context when MainViewModel is available
                                        if (mainViewModel != null) {
                                            // Convert OnlineSong to Song
                                            val songModel = clickedSong.toSong()

                                            // Create playlist context for Global Top 50
                                            val playlistContext = PlaylistContext(
                                                type = PlaylistType.GLOBAL_TOP_50,
                                                songs = globalTopSongs.map { it.toSong() },
                                                title = "Global Top 50",
                                                id = "global_top_50"
                                            )

                                            // Play with playlist context
                                            mainViewModel.playSongWithPlaylist(songModel, playlistContext)
                                        } else {
                                            // Fallback to original behavior
                                            onSongSelected(clickedSong)
                                        }
                                    },
                                    onDownloadClick = { clickedSong ->
                                        if (!isDownloaded && !isDownloading) {
                                            // Start download
                                            viewModel.markSongAsDownloading(clickedSong.id)
                                            downloadManager.downloadSong(clickedSong)

                                            // Update progress periodically
                                            coroutineScope.launch {
                                                while (downloadStates[clickedSong.id] is SongDownloadManager.DownloadState.Downloading) {
                                                    downloadManager.checkDownloadProgress(clickedSong.id)
                                                    kotlinx.coroutines.delay(500)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    // Country Top 10
                    if (isLoading && countryTopSongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GREEN_COLOR)
                        }
                    } else if (error != null && countryTopSongs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Error: $error",
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Text(
                                    text = "Country Top 10",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }

                            itemsIndexed(countryTopSongs) { index, song ->
                                val isDownloaded = downloadedSongIds.contains(song.id)
                                val isDownloading = downloadingSongs.contains(song.id) ||
                                        downloadStates[song.id] is SongDownloadManager.DownloadState.Downloading

                                OnlineSongItem(
                                    song = song,
                                    isPlaying = false, // Update based on currently playing song
                                    isDownloaded = isDownloaded,
                                    isDownloading = isDownloading,
                                    showRank = true,
                                    rank = index + 1,
                                    onSongClick = { clickedSong ->
                                        // UPDATED: Use playlist context when MainViewModel is available
                                        if (mainViewModel != null) {
                                            // Convert OnlineSong to Song
                                            val songModel = clickedSong.toSong()

                                            // Create playlist context for Country Top 10
                                            val playlistContext = PlaylistContext(
                                                type = PlaylistType.COUNTRY_TOP_10,
                                                songs = countryTopSongs.map { it.toSong() },
                                                title = "Country Top 10",
                                                id = "country_top_10"
                                            )

                                            // Play with playlist context
                                            mainViewModel.playSongWithPlaylist(songModel, playlistContext)
                                        } else {
                                            // Fallback to original behavior
                                            onSongSelected(clickedSong)
                                        }
                                    },
                                    onDownloadClick = { clickedSong ->
                                        if (!isDownloaded && !isDownloading) {
                                            // Start download
                                            viewModel.markSongAsDownloading(clickedSong.id)
                                            downloadManager.downloadSong(clickedSong)

                                            // Update progress periodically
                                            coroutineScope.launch {
                                                while (downloadStates[clickedSong.id] is SongDownloadManager.DownloadState.Downloading) {
                                                    downloadManager.checkDownloadProgress(clickedSong.id)
                                                    kotlinx.coroutines.delay(500)
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Cleanup downloads when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            downloadManager.release()
        }
    }
}