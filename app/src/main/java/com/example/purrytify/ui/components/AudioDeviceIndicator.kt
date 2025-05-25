package com.example.purrytify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.purrytify.models.AudioDeviceType
import com.example.purrytify.viewmodels.AudioDeviceViewModel


@Composable
fun AudioDeviceIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudioDeviceViewModel = viewModel()
) {
    val activeDevice by viewModel.activeDevice.collectAsState()

    activeDevice?.let { device ->
        Surface(
            modifier = modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable { onClick() },
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Device icon
                Icon(
                    imageVector = when (device.type) {
                        AudioDeviceType.INTERNAL_SPEAKER -> Icons.Default.PhoneAndroid
                        AudioDeviceType.WIRED_HEADSET -> Icons.Default.Headset
                        AudioDeviceType.BLUETOOTH_A2DP -> Icons.Default.Bluetooth
                        AudioDeviceType.USB_DEVICE -> Icons.Default.Usb
                        AudioDeviceType.UNKNOWN -> Icons.Default.Speaker
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )

                // Device name
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 150.dp)
                )

                // Arrow indicator
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select device",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}


@Composable
fun AudioDeviceIndicatorMini(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudioDeviceViewModel = viewModel()
) {
    val activeDevice by viewModel.activeDevice.collectAsState()

    activeDevice?.let { device ->

        if (device.type != AudioDeviceType.INTERNAL_SPEAKER) {
            IconButton(
                onClick = onClick,
                modifier = modifier.size(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (device.type) {
                            AudioDeviceType.WIRED_HEADSET -> Icons.Default.Headset
                            AudioDeviceType.BLUETOOTH_A2DP -> Icons.Default.Bluetooth
                            AudioDeviceType.USB_DEVICE -> Icons.Default.Usb
                            else -> Icons.Default.Speaker
                        },
                        contentDescription = "Audio output: ${device.name}",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}