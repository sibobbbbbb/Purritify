package com.example.purrytify.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.purrytify.models.Song
import com.example.purrytify.ui.components.*
import com.example.purrytify.ui.theme.BACKGROUND_COLOR
import com.example.purrytify.ui.theme.GREEN_COLOR
import com.example.purrytify.viewmodels.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = viewModel(
        factory = ViewModelFactory.getInstance(LocalContext.current)
    ),
    mainViewModel: MainViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = ViewModelFactory.getInstance(LocalContext.current)
    ),
    onSongSelected: (Song) -> Unit = {}
) {
    // Configuration
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // State
    var selectedTab by remember { mutableStateOf("All") }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedSongForEdit by remember { mutableStateOf<Song?>(null) }
    var selectedSongForDelete by remember { mutableStateOf<Song?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Data
    val songs by libraryViewModel.songs.collectAsState()
    val currentSong by mainViewModel.currentSong.collectAsState()
    val isPlaying by mainViewModel.isPlaying.collectAsState()
    val operationStatus by libraryViewModel.operationStatus.collectAsState()

    // Handle operation status
    operationStatus?.let { status ->
        LaunchedEffect(status) {
            when (status) {
                is OperationStatus.Success -> snackbarHostState.showSnackbar(status.message)
                is OperationStatus.Error -> snackbarHostState.showSnackbar(status.message)
            }
            libraryViewModel.clearOperationStatus()
        }
    }

    // Filter songs
    val filteredSongs = remember(songs, searchQuery, selectedTab) {
        val filteredByTab = if (selectedTab == "All") songs else songs.filter { it.isLiked }
        if (searchQuery.isBlank()) {
            filteredByTab
        } else {
            filteredByTab.filter { song ->
                song.title.contains(searchQuery, ignoreCase = true) ||
                        song.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BACKGROUND_COLOR)
    ) {
        if (isLandscape) {
            // Landscape Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .background(Color(0xFF1A1A1A))
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Library",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showUploadDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Song",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Search
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            isSearchActive = it.isNotEmpty()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search", color = Color.Gray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, "Search", tint = Color.Gray)
                        },
                        trailingIcon = {
                            if (isSearchActive) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearchActive = false
                                }) {
                                    Icon(Icons.Default.Clear, "Clear", tint = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GREEN_COLOR,
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tabs
                    LibraryTabs(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }

                // Right Panel - Songs Grid
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 200.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (filteredSongs.isEmpty()) {
                        item {
                            EmptyState(isSearchActive, searchQuery)
                        }
                    } else {
                        items(filteredSongs) { song ->
                            GridSongCard(
                                song = song.copy(isPlaying = currentSong?.id == song.id && isPlaying),
                                onSongClick = {
                                    mainViewModel.playSong(it)
                                    onSongSelected(it)
                                },
                                onAddToQueue = {
                                    mainViewModel.addToQueue(it)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Added to queue: ${it.title}")
                                    }
                                },
                                onToggleLike = { s, liked ->
                                    mainViewModel.toggleLike(s.id, liked)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Portrait Layout
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Your Library",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showUploadDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Song",
                            tint = Color.White
                        )
                    }
                }

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        isSearchActive = it.isNotEmpty()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Search songs or artists", color = Color.Gray) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, "Search", tint = Color.Gray)
                    },
                    trailingIcon = {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                searchQuery = ""
                                isSearchActive = false
                            }) {
                                Icon(Icons.Default.Clear, "Clear", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GREEN_COLOR,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs
                LibraryTabs(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                // Songs List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        bottom = if (currentSong != null) 80.dp else 16.dp
                    )
                ) {
                    if (filteredSongs.isEmpty()) {
                        item {
                            EmptyState(isSearchActive, searchQuery)
                        }
                    } else {
                        items(filteredSongs) { song ->
                            SongItem(
                                song = song.copy(isPlaying = currentSong?.id == song.id && isPlaying),
                                onSongClick = {
                                    mainViewModel.playSong(it)
                                    onSongSelected(it)
                                },
                                onAddToQueue = {
                                    mainViewModel.addToQueue(it)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Added to queue: ${it.title}")
                                    }
                                },
                                onToggleLike = { s, liked ->
                                    mainViewModel.toggleLike(s.id, liked)
                                },
                                onEditSong = {
                                    selectedSongForEdit = it
                                    showEditDialog = true
                                },
                                onDeleteSong = {
                                    selectedSongForDelete = it
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // Dialogs
        if (showUploadDialog) {
            UploadSongDialog(
                onDismiss = { showUploadDialog = false },
                onSaveClick = { title, artist, filePath, artworkPath, duration ->
                    libraryViewModel.addSong(title, artist, filePath, artworkPath, duration)
                    showUploadDialog = false
                }
            )
        }

        if (showEditDialog && selectedSongForEdit != null) {
            EditSongDialog(
                song = selectedSongForEdit!!,
                onDismiss = { showEditDialog = false },
                onSaveClick = { title, artist, filePath, artworkPath, duration ->
                    libraryViewModel.editSong(
                        selectedSongForEdit!!.id,
                        title,
                        artist,
                        filePath,
                        artworkPath,
                        duration,
                        mainViewModel
                    )
                    showEditDialog = false
                }
            )
        }

        if (showDeleteDialog && selectedSongForDelete != null) {
            DeleteSongDialog(
                song = selectedSongForDelete!!,
                onDismiss = { showDeleteDialog = false },
                onConfirmDelete = {
                    libraryViewModel.deleteSong(it.id, mainViewModel)
                    showDeleteDialog = false
                }
            )
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}

@Composable
private fun GridSongCard(
    song: Song,
    onSongClick: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onToggleLike: (Song, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        onClick = { onSongClick(song) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SongCoverImage(
                    imageUrl = song.coverUrl,
                    contentDescription = song.title,
                    modifier = Modifier.fillMaxSize()
                )

                // Playing indicator
                if (song.isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            "Playing",
                            tint = GREEN_COLOR,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Like button
                IconButton(
                    onClick = { onToggleLike(song, !song.isLiked) },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        if (song.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "Like",
                        tint = if (song.isLiked) Color.Red else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Info
            Text(
                text = song.title,
                color = if (song.isPlaying) GREEN_COLOR else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = song.artist,
                    color = Color.Gray,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { onAddToQueue(song) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = com.example.purrytify.R.drawable.ic_queue_music),
                        contentDescription = "Add to Queue",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(isSearchActive: Boolean, searchQuery: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isSearchActive) Icons.Default.SearchOff else Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isSearchActive)
                    "No songs found for \"$searchQuery\""
                else
                    "No songs in your library",
                color = Color.Gray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}