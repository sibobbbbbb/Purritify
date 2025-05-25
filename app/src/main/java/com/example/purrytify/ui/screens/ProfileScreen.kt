package com.example.purrytify.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.purrytify.MainActivity
import com.example.purrytify.R
import com.example.purrytify.models.UserProfile
import com.example.purrytify.ui.theme.BACKGROUND_COLOR
import com.example.purrytify.ui.theme.GREEN_COLOR
import com.example.purrytify.viewmodels.ProfileViewModel
import com.example.purrytify.viewmodels.ViewModelFactory
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@Composable
fun ProfileScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel = viewModel(
        factory = ViewModelFactory.getInstance(LocalContext.current)
    )
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current

    // State untuk menyimpan data
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Stats
    val totalSongs by profileViewModel.totalSongs.collectAsState()
    val likedSongs by profileViewModel.likedSongs.collectAsState()
    val listenedSongs by profileViewModel.listenedSongs.collectAsState()

    // For pull-to-refresh functionality
    val refreshState = rememberSwipeRefreshState(isLoading)

    // Scroll state
    val scrollState = rememberScrollState()

    // Observe LiveData dari ViewModel
    DisposableEffect(lifecycleOwner) {
        // Observer untuk profile data
        val profileObserver = androidx.lifecycle.Observer<UserProfile> { profile ->
            userProfile = profile
        }

        // Observer untuk loading state
        val loadingObserver = androidx.lifecycle.Observer<Boolean> { loading ->
            isLoading = loading
            refreshState.isRefreshing = loading
        }

        // Observer untuk error message
        val errorObserver = androidx.lifecycle.Observer<String> { errorMsg ->
            error = errorMsg
        }

        // Register observers
        profileViewModel.profileData.observe(lifecycleOwner, profileObserver)
        profileViewModel.isLoading.observe(lifecycleOwner, loadingObserver)
        profileViewModel.error.observe(lifecycleOwner, errorObserver)

        // Load profile data when screen is shown
        profileViewModel.loadUserProfile()

        // Clean up observers when leaving the screen
        onDispose {
            profileViewModel.profileData.removeObserver(profileObserver)
            profileViewModel.isLoading.removeObserver(loadingObserver)
            profileViewModel.error.removeObserver(errorObserver)
        }
    }

    SwipeRefresh(
        state = refreshState,
        onRefresh = { profileViewModel.refreshData() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BACKGROUND_COLOR)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with refresh and edit buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile",
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                    fontSize = 24.sp
                )

                Row {
                    // Edit Profile Button
                    IconButton(onClick = {
                        navController.navigate("edit_profile")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = GREEN_COLOR
                        )
                    }

                    // Refresh Button
                    IconButton(onClick = { profileViewModel.refreshData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Profile",
                            tint = Color.White
                        )
                    }
                }
            }

            if (isLoading && userProfile == null) {
                // Show loading indicator only on initial load
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    color = GREEN_COLOR
                )
            } else if (error != null && userProfile == null) {
                // Show error message
                ErrorContent(error = error!!, onRetry = { profileViewModel.loadUserProfile() })
            } else if (userProfile != null) {
                // Show profile information
                ProfileContent(
                    userProfile = userProfile!!,
                    totalSongs = totalSongs,
                    likedSongs = likedSongs,
                    listenedSongs = listenedSongs,
                    onLogout = {
                        (context as? MainActivity)?.logout()
                    },
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun ErrorContent(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_error),
            contentDescription = "Error",
            tint = Color.Red,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Error: $error",
            color = Color.Red,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = GREEN_COLOR)
        ) {
            Text("Retry")
        }
    }
}

@Composable
fun ProfileContent(
    userProfile: UserProfile,
    totalSongs: Int,
    likedSongs: Int,
    listenedSongs: Int,
    onLogout: () -> Unit,
    navController: NavController
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Photo
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("http://34.101.226.132:3000/uploads/profile-picture/${userProfile.profilePhoto}")
                    .crossfade(true)
                    .build(),
                contentDescription = "Profile Photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.ic_person_placeholder),
                fallback = painterResource(id = R.drawable.ic_person_placeholder)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Username
        Text(
            text = userProfile.username,
            color = Color.White,
            fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Email
        Text(
            text = userProfile.email,
            color = Color.White,
            fontFamily = FontFamily(Font(R.font.poppins_regular)),
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Location
        Text(
            text = "Location: ${userProfile.location}",
            color = Color.White,
            fontFamily = FontFamily(Font(R.font.poppins_regular)),
            fontSize = 16.sp
        )

        // Joined date
        Text(
            text = "Joined: ${formatDate(userProfile.createdAt)}",
            color = Color.Gray,
            fontFamily = FontFamily(Font(R.font.poppins_regular)),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Stats section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Your Music Stats",
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Stats cards in a row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Added Songs
                    StatCard(count = totalSongs.toString(), label = "Added")

                    // Liked Songs
                    StatCard(count = likedSongs.toString(), label = "Liked")

                    // Listened Songs
                    StatCard(count = listenedSongs.toString(), label = "Listened")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // NAVIGATION BUTTONS SECTION
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Features",
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Sound Capsule (Analytics) Button
                NavigationItem(
                    icon = Icons.Default.Analytics,
                    title = "Sound Capsule",
                    subtitle = "View your music analytics and listening habits",
                    onClick = {
                        navController.navigate("analytics")
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Audio Output Device Setting
                NavigationItem(
                    icon = Icons.Default.Speaker,
                    title = "Audio Output Device",
                    subtitle = "Select audio output device",
                    onClick = {
                        navController.navigate("audio_devices")
                    }
                )
            }
        }

        // Spacer to push the logout button to the bottom
        Spacer(modifier = Modifier.height(48.dp))

        // Logout Button
        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red,
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Logout")
        }
    }
}

@Composable
fun NavigationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF282828)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = GREEN_COLOR,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                    fontSize = 16.sp
                )

                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontFamily = FontFamily(Font(R.font.poppins_regular)),
                    fontSize = 14.sp
                )
            }

            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_more),
                contentDescription = "Navigate",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StatCard(count: String, label: String) {
    Box(
        modifier = Modifier
            .size(width = 90.dp, height = 90.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF282828)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count,
                color = GREEN_COLOR,
                fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                fontSize = 24.sp
            )

            Text(
                text = label,
                color = Color.White,
                fontFamily = FontFamily(Font(R.font.poppins_regular)),
                fontSize = 14.sp
            )
        }
    }
}


private fun formatDate(dateString: String): String {

    return try {
        val parts = dateString.split("T")
        parts[0]
    } catch (e: Exception) {
        dateString
    }
}