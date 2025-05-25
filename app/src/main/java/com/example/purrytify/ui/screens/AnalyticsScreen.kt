package com.example.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.purrytify.R
import com.example.purrytify.PurrytifyApp
import com.example.purrytify.data.dao.AnalyticsDao
import com.example.purrytify.data.entity.SongStreak
import com.example.purrytify.ui.theme.BACKGROUND_COLOR
import com.example.purrytify.ui.theme.GREEN_COLOR
import com.example.purrytify.viewmodels.AnalyticsViewModel
import com.example.purrytify.viewmodels.ViewModelFactory
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import com.android.volley.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    navController: NavController,
    analyticsViewModel: AnalyticsViewModel = viewModel(
        factory = ViewModelFactory.getInstance(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State from ViewModel
    val isLoading by analyticsViewModel.isLoading.collectAsState()
    val isExporting by analyticsViewModel.isExporting.collectAsState()
    val exportMessage by analyticsViewModel.exportMessage.collectAsState()
    val selectedMonth by analyticsViewModel.selectedMonth.collectAsState()
    val totalListeningTime by analyticsViewModel.totalListeningTime.collectAsState()
    val topArtist by analyticsViewModel.topArtist.collectAsState()
    val topSong by analyticsViewModel.topSong.collectAsState()
    val topArtists by analyticsViewModel.topArtists.collectAsState()
    val topSongs by analyticsViewModel.topSongs.collectAsState()
    val dayStreaks by analyticsViewModel.dayStreaks.collectAsState()
    val availableMonths by analyticsViewModel.availableMonths.collectAsState()
    val hasData by analyticsViewModel.hasData.collectAsState()
    val errorMessage by analyticsViewModel.errorMessage.collectAsState()

    // UI State
    var showMonthSelector by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    val refreshState = rememberSwipeRefreshState(isLoading)

    LaunchedEffect(Unit) {
        try {
            val app = context.applicationContext as PurrytifyApp
            val tokenManager = app.tokenManager
            val userId = tokenManager.getUserId().toLong()

            Log.d("AnalyticsScreen", " Initializing analytics for user: $userId")
            analyticsViewModel.initializeForUser(userId)
        } catch (e: Exception) {
            Log.e("AnalyticsScreen", " Error getting user ID", e)
            // Fallback to user ID 1 if unable to get real user ID
            analyticsViewModel.initializeForUser(1L)
        }
    }

    SwipeRefresh(
        state = refreshState,
        onRefresh = { analyticsViewModel.refreshAnalytics() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BACKGROUND_COLOR)
        ) {
            // Top App Bar
            TopAppBar(
                title = {
                    Text(
                        text = "Sound Capsule",
                        color = Color.White,
                        fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Debug Force Update Button (only show in debug)
                    if (BuildConfig.DEBUG) {
                        IconButton(onClick = {
                            analyticsViewModel.forceUpdateAnalytics()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Force Update",
                                tint = Color.Yellow
                            )
                        }
                    }

                    // Month Selector
                    IconButton(onClick = { showMonthSelector = true }) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Select Month",
                            tint = GREEN_COLOR
                        )
                    }

                    // Export Menu
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = GREEN_COLOR,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = GREEN_COLOR
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                            modifier = Modifier.background(Color(0xFF333333))
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Export CSV", color = Color.White)
                                    }
                                },
                                onClick = {
                                    showExportMenu = false
                                    analyticsViewModel.exportCurrentMonth(context)
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Share Analytics", color = Color.White)
                                    }
                                },
                                onClick = {
                                    showExportMenu = false
                                    analyticsViewModel.shareCurrentMonth(context)
                                }
                            )
                        }
                    }

                    // Refresh
                    IconButton(onClick = { analyticsViewModel.refreshAnalytics() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BACKGROUND_COLOR
                )
            )

            // Export Message Snackbar
            exportMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.contains("successfully")) GREEN_COLOR else Color.Red
                    )
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        fontFamily = FontFamily(Font(R.font.poppins_regular))
                    )
                }

                // Auto-hide after 5 seconds
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(5000)
                    analyticsViewModel.clearExportMessage()
                }
            }

            if (isLoading) {
                LoadingContent()
            } else if (errorMessage != null) {
                ErrorContent(
                    error = errorMessage!!,
                    onRetry = { analyticsViewModel.refreshAnalytics() },
                    onDismiss = { analyticsViewModel.clearError() }
                )
            } else if (!hasData) {
                NoDataContent()
            } else {
                AnalyticsContent(
                    selectedMonth = selectedMonth,
                    totalListeningTime = totalListeningTime,
                    topArtist = topArtist,
                    topSong = topSong,
                    topArtists = topArtists,
                    topSongs = topSongs,
                    dayStreaks = dayStreaks,
                    formatListeningTime = analyticsViewModel::formatListeningTime,
                    getMonthDisplayName = analyticsViewModel::getMonthDisplayName
                )
            }
        }
    }

    // Month Selector Dialog
    if (showMonthSelector) {
        MonthSelectorDialog(
            availableMonths = availableMonths,
            selectedMonth = selectedMonth,
            onMonthSelected = { month ->
                analyticsViewModel.selectMonth(month)
                showMonthSelector = false
            },
            onDismiss = { showMonthSelector = false },
            getMonthDisplayName = analyticsViewModel::getMonthDisplayName
        )
    }
}

@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = GREEN_COLOR,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Loading your analytics...",
                color = Color.White,
                fontFamily = FontFamily(Font(R.font.poppins_regular)),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_error),
                    contentDescription = "Error",
                    tint = Color.Red,
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Error Loading Analytics",
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = error,
                    color = Color.Gray,
                    fontFamily = FontFamily(Font(R.font.poppins_regular)),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        )
                    ) {
                        Text("Dismiss")
                    }

                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GREEN_COLOR
                        )
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun NoDataContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_analytics_placeholder),
                contentDescription = "No Data",
                tint = Color.Gray,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "No Data Available",
                color = Color.White,
                fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Start listening to music to see your analytics here!",
                color = Color.Gray,
                fontFamily = FontFamily(Font(R.font.poppins_regular)),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AnalyticsContent(
    selectedMonth: String,
    totalListeningTime: Long,
    topArtist: String?,
    topSong: String?,
    topArtists: List<AnalyticsDao.ArtistStats>,
    topSongs: List<AnalyticsDao.SongStats>,
    dayStreaks: List<SongStreak>,
    formatListeningTime: (Long) -> String,
    getMonthDisplayName: (String) -> String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Month Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = GREEN_COLOR
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getMonthDisplayName(selectedMonth),
                        color = Color.Black,
                        fontFamily = FontFamily(Font(R.font.poppins_bold)),
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Summary Stats
        item {
            SummaryStatsSection(
                totalListeningTime = totalListeningTime,
                topArtist = topArtist,
                topSong = topSong,
                formatListeningTime = formatListeningTime
            )
        }

        // Top Artists Section
        if (topArtists.isNotEmpty()) {
            item {
                SectionHeader(title = "Top Artists")
            }

            item {
                TopArtistsSection(
                    topArtists = topArtists,
                    formatListeningTime = formatListeningTime
                )
            }
        }

        // Top Songs Section
        if (topSongs.isNotEmpty()) {
            item {
                SectionHeader(title = "Top Songs")
            }

            item {
                TopSongsSection(
                    topSongs = topSongs,
                    formatListeningTime = formatListeningTime
                )
            }
        }

        // Day Streaks Section
        if (dayStreaks.isNotEmpty()) {
            item {
                SectionHeader(title = "Day Streaks (2+ Days)")
            }

            item {
                DayStreaksSection(dayStreaks = dayStreaks)
            }
        }

        // Bottom spacing
        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SummaryStatsSection(
    totalListeningTime: Long,
    topArtist: String?,
    topSong: String?,
    formatListeningTime: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "Your Music Summary",
                color = Color.White,
                fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Total Listening Time
            SummaryStatItem(
                label = "Time Listened",
                value = formatListeningTime(totalListeningTime),
                icon = "🎵"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Top Artist
            SummaryStatItem(
                label = "Top Artist",
                value = topArtist ?: "No data",
                icon = "🎤"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Top Song
            SummaryStatItem(
                label = "Top Song",
                value = topSong ?: "No data",
                icon = "🎶"
            )
        }
    }
}

@Composable
fun SummaryStatItem(
    label: String,
    value: String,
    icon: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(GREEN_COLOR),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                color = Color.Gray,
                fontFamily = FontFamily(Font(R.font.poppins_regular)),
                fontSize = 14.sp
            )

            Text(
                text = value,
                color = Color.White,
                fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
        fontSize = 18.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun TopArtistsSection(
    topArtists: List<AnalyticsDao.ArtistStats>,
    formatListeningTime: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            topArtists.forEachIndexed { index, artist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rank
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when (index) {
                                    0 -> Color(0xFFFFD700) // Gold
                                    1 -> Color(0xFFC0C0C0) // Silver
                                    2 -> Color(0xFFCD7F32) // Bronze
                                    else -> GREEN_COLOR
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = Color.Black,
                            fontFamily = FontFamily(Font(R.font.poppins_bold)),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Artist Info
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = artist.artistName,
                            color = Color.White,
                            fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${formatListeningTime(artist.totalDuration)} • ${artist.playCount} plays",
                            color = Color.Gray,
                            fontFamily = FontFamily(Font(R.font.poppins_regular)),
                            fontSize = 14.sp
                        )
                    }
                }

                if (index < topArtists.size - 1) {
                    Divider(
                        color = Color(0xFF333333),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TopSongsSection(
    topSongs: List<AnalyticsDao.SongStats>,
    formatListeningTime: (Long) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            topSongs.forEachIndexed { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rank
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when (index) {
                                    0 -> Color(0xFFFFD700) // Gold
                                    1 -> Color(0xFFC0C0C0) // Silver
                                    2 -> Color(0xFFCD7F32) // Bronze
                                    else -> GREEN_COLOR
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            color = Color.Black,
                            fontFamily = FontFamily(Font(R.font.poppins_bold)),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Song Info
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = song.songTitle,
                            color = Color.White,
                            fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = song.artistName,
                            color = GREEN_COLOR,
                            fontFamily = FontFamily(Font(R.font.poppins_regular)),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${formatListeningTime(song.totalDuration)} • ${song.playCount} plays",
                            color = Color.Gray,
                            fontFamily = FontFamily(Font(R.font.poppins_regular)),
                            fontSize = 12.sp
                        )
                    }
                }

                if (index < topSongs.size - 1) {
                    Divider(
                        color = Color(0xFF333333),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DayStreaksSection(dayStreaks: List<SongStreak>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (dayStreaks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "🔥",
                            fontSize = 48.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "No Active Streaks",
                            color = Color.White,
                            fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                            fontSize = 16.sp
                        )

                        Text(
                            text = "Listen to the same song for 2+ days!",
                            color = Color.Gray,
                            fontFamily = FontFamily(Font(R.font.poppins_regular)),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                dayStreaks.forEach { streak ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Streak Fire Icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        streak.currentStreak >= 7 -> Color(0xFFFF4444) // Red for 7+ days
                                        streak.currentStreak >= 5 -> Color(0xFFFF8800) // Orange for 5+ days
                                        else -> GREEN_COLOR // Green for 2-4 days
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🔥",
                                fontSize = 20.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Song Info
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = streak.songTitle,
                                color = Color.White,
                                fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = streak.artistName,
                                color = GREEN_COLOR,
                                fontFamily = FontFamily(Font(R.font.poppins_regular)),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Streak Count
                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${streak.currentStreak}",
                                color = Color.White,
                                fontFamily = FontFamily(Font(R.font.poppins_bold)),
                                fontSize = 20.sp
                            )

                            Text(
                                text = if (streak.currentStreak == 1) "day" else "days",
                                color = Color.Gray,
                                fontFamily = FontFamily(Font(R.font.poppins_regular)),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSelectorDialog(
    availableMonths: List<String>,
    selectedMonth: String,
    onMonthSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    getMonthDisplayName: (String) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Month",
                color = Color.White,
                fontFamily = FontFamily(Font(R.font.poppins_semi_bold))
            )
        },
        text = {
            LazyColumn {
                items(availableMonths) { month ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMonthSelected(month) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = month == selectedMonth,
                            onClick = { onMonthSelected(month) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = GREEN_COLOR,
                                unselectedColor = Color.Gray
                            )
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = getMonthDisplayName(month),
                            color = Color.White,
                            fontFamily = FontFamily(Font(R.font.poppins_regular)),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = "Close",
                    color = GREEN_COLOR
                )
            }
        },
        containerColor = Color(0xFF1E1E1E),
        shape = RoundedCornerShape(16.dp)
    )
}