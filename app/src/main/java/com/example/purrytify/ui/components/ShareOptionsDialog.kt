package com.example.purrytify.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.util.ShareUtils

@Composable
fun ShareOptionsDialog(
    onlineSong: OnlineSong,
    onDismiss: () -> Unit
) {
    var showQRDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2A2A2A)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Share Song",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = onlineSong.title,
                    fontSize = 16.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "by ${onlineSong.artist}",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Share via URL option
                ShareOptionItem(
                    icon = Icons.Default.Link,
                    title = "Share Link",
                    description = "Share via URL (WhatsApp, Telegram, etc.)",
                    onClick = {
                        ShareUtils.shareSongUrl(context, onlineSong)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Share via QR Code option
                ShareOptionItem(
                    icon = Icons.Default.QrCode,
                    title = "Share QR Code",
                    description = "Generate QR code for offline sharing",
                    onClick = {
                        showQRDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Cancel button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("Cancel")
                }
            }
        }
    }

    // Show QR dialog if requested
    if (showQRDialog) {
        QRCodeDialog(
            onlineSong = onlineSong,
            onDismiss = {
                showQRDialog = false
                onDismiss() // Also dismiss the parent dialog
            }
        )
    }
}

@Composable
fun ShareOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3A3A3A)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )

                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}