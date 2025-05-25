package com.example.purrytify.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.purrytify.ui.navigation.Destinations

@Composable
fun ScanQRButton(navController: NavController) {
    FloatingActionButton(
        onClick = {
            navController.navigate(Destinations.QR_SCANNER_ROUTE)
        },
        containerColor = Color(0xFF1DB954) // Spotify green
    ) {
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = "Scan QR Code",
            tint = Color.White
        )
    }
}

@Composable
fun ScanQRMenuItem(navController: NavController) {
    IconButton(
        onClick = {
            navController.navigate(Destinations.QR_SCANNER_ROUTE)
        }
    ) {
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = "Scan QR Code",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun ScanQRCard(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = {
            navController.navigate(Destinations.QR_SCANNER_ROUTE)
        },
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.QrCode,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Scan QR Code",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}