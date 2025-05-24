package com.example.purrytify.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.purrytify.models.AudioDevice
import com.example.purrytify.models.AudioDeviceType
import com.example.purrytify.models.ConnectionState
import com.example.purrytify.viewmodels.AudioDeviceViewModel

/**
 * Screen untuk menampilkan dan memilih perangkat audio
 *
 * Alur UI:
 * 1. Header dengan tombol back dan refresh
 * 2. Active device card (device yang sedang aktif)
 * 3. List available devices
 * 4. Loading indicator saat switching
 * 5. Error/Success snackbar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDeviceScreen(
    onBackClick: () -> Unit,
    viewModel: AudioDeviceViewModel = viewModel()
) {
    val audioDevices by viewModel.audioDevices.collectAsState()
    val activeDevice by viewModel.activeDevice.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    // Snackbar host state
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error message
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    // Show success message
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Output Device") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = { viewModel.refreshDevices() },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active device section
                activeDevice?.let { device ->
                    item {
                        ActiveDeviceCard(device = device)
                    }
                }

                // Available devices section
                item {
                    Text(
                        text = "Available Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                if (audioDevices.isEmpty()) {
                    item {
                        EmptyStateMessage()
                    }
                } else {
                    items(audioDevices) { device ->
                        AudioDeviceItem(
                            device = device,
                            isActive = device.id == activeDevice?.id,
                            onClick = {
                                if (!isLoading && device.id != activeDevice?.id) {
                                    viewModel.switchToDevice(device)
                                }
                            }
                        )
                    }
                }
            }

            // Loading overlay
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) { },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Card untuk menampilkan active device
 */
@Composable
fun ActiveDeviceCard(device: AudioDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Currently Playing On",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Item untuk setiap audio device dalam list
 */
@Composable
fun AudioDeviceItem(
    device: AudioDevice,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isActive) {
            CardDefaults.outlinedCardBorder()
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device icon
            Icon(
                imageVector = getDeviceIcon(device.type),
                contentDescription = null,
                tint = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(24.dp)
            )

            // Device info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )

                // Connection status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(getStatusColor(device.connectionState))
                    )
                    Text(
                        text = getStatusText(device.connectionState),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Active indicator
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Empty state message
 */
@Composable
fun EmptyStateMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SpeakerGroup,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No audio devices found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = "Make sure your Bluetooth is on or connect a wired headset",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

/**
 * Helper function untuk mendapatkan icon berdasarkan device type
 */
@Composable
fun getDeviceIcon(type: AudioDeviceType) = when (type) {
    AudioDeviceType.INTERNAL_SPEAKER -> Icons.Default.PhoneAndroid
    AudioDeviceType.WIRED_HEADSET -> Icons.Default.Headset
    AudioDeviceType.BLUETOOTH_A2DP -> Icons.Default.Bluetooth
    AudioDeviceType.USB_DEVICE -> Icons.Default.Usb
    AudioDeviceType.UNKNOWN -> Icons.Default.DeviceUnknown
}

/**
 * Helper function untuk mendapatkan status color
 */
@Composable
fun getStatusColor(state: ConnectionState) = when (state) {
    ConnectionState.CONNECTED -> Color.Green
    ConnectionState.CONNECTING -> Color.Yellow
    ConnectionState.AVAILABLE -> Color.Gray
    ConnectionState.DISCONNECTED -> Color.Red
}

/**
 * Helper function untuk mendapatkan status text
 */
fun getStatusText(state: ConnectionState) = when (state) {
    ConnectionState.CONNECTED -> "Connected"
    ConnectionState.CONNECTING -> "Connecting..."
    ConnectionState.AVAILABLE -> "Available"
    ConnectionState.DISCONNECTED -> "Disconnected"
}