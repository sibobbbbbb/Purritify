package com.example.purrytify.ui.components

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.purrytify.R
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun UploadSongDialog(
    onDismiss: () -> Unit,
    onSaveClick: (title: String, artist: String, filePath: String, artworkPath: String, duration: Long) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf<Long>(0) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isAudioSelected by remember { mutableStateOf(false) }
    var isImageSelected by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Format duration to display
    val formattedDuration = remember(duration) {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(duration)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(minutes)
        String.format("%d:%02d", minutes, seconds)
    }

    // Activity launcher for audio file
    val audioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedAudioUri = it
            isAudioSelected = true

            // Extract metadata from audio file
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, it)

                // Get audio title if available
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { extractedTitle ->
                    if (extractedTitle.isNotEmpty()) {
                        title = extractedTitle
                    }
                }

                // Get artist if available
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { extractedArtist ->
                    if (extractedArtist.isNotEmpty()) {
                        artist = extractedArtist
                    }
                }

                // Get duration
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let { durationStr ->
                    duration = durationStr.toLongOrNull() ?: 0
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Error reading audio file: ${e.message}"
            } finally {
                retriever.release()
            }
        }
    }

    // Activity launcher for image file
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            isImageSelected = true
        }
    }

    // Function to copy URI content to internal storage
    fun copyUriToInternalStorage(uri: Uri, fileName: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, fileName)

            inputStream?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "Error copying file: ${e.message}"
            null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.7f else 0.9f)
                .fillMaxHeight(if (isLandscape) 0.9f else 0.6f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF121212)
        ) {
            if (isLandscape) {
                // Landscape Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Left Column - Upload buttons
                    Column(
                        modifier = Modifier
                            .weight(0.4f)
                            .fillMaxHeight()
                            .padding(end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Upload Song",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Upload Photo Box
                        UploadBox(
                            title = "Upload Photo",
                            isSelected = isImageSelected,
                            onClick = { imageLauncher.launch("image/*") },
                            icon = R.drawable.ic_image_placeholder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Upload File Box
                        UploadBox(
                            title = "Upload File",
                            isSelected = isAudioSelected,
                            onClick = { audioLauncher.launch("audio/*") },
                            icon = R.drawable.ic_audio_placeholder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        // Display duration if audio is selected
                        if (isAudioSelected && duration > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1E1E1E)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Duration", color = Color.Gray, fontSize = 14.sp)
                                    Text(formattedDuration, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // Right Column - Form fields
                    Column(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Display error message if any
                        errorMessage?.let {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Red.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = it,
                                    color = Color.Red,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Title field
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Gray,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color.Gray,
                                unfocusedLabelColor = Color.Gray
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Artist field
                        OutlinedTextField(
                            value = artist,
                            onValueChange = { artist = it },
                            label = { Text("Artist") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Gray,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color.Gray,
                                unfocusedLabelColor = Color.Gray
                            )
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Cancel button
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF333333)
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Cancel", color = Color.White)
                            }

                            // Save button
                            Button(
                                onClick = {
                                    selectedAudioUri?.let { audioUri ->
                                        val audioFileName = "song_${System.currentTimeMillis()}.mp3"
                                        val artworkFileName = "artwork_${System.currentTimeMillis()}.jpg"

                                        val audioFilePath = copyUriToInternalStorage(audioUri, audioFileName)
                                        val artworkPath = selectedImageUri?.let { imgUri ->
                                            copyUriToInternalStorage(imgUri, artworkFileName)
                                        } ?: ""

                                        if (audioFilePath != null) {
                                            onSaveClick(title, artist, audioFilePath, artworkPath, duration)
                                            onDismiss()
                                        }
                                    }
                                },
                                enabled = isAudioSelected && title.isNotBlank() && artist.isNotBlank(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF1DB954)
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Save", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                // Portrait Layout (Original)
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Upload Song",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Display error message if any
                    errorMessage?.let {
                        Text(
                            text = it,
                            color = Color.Red,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Upload fields container
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Upload Photo Box
                        UploadBox(
                            title = "Upload Photo",
                            isSelected = isImageSelected,
                            onClick = { imageLauncher.launch("image/*") },
                            icon = R.drawable.ic_image_placeholder,
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp)
                                .padding(end = 8.dp)
                        )

                        // Upload File Box
                        UploadBox(
                            title = "Upload File",
                            isSelected = isAudioSelected,
                            onClick = { audioLauncher.launch("audio/*") },
                            icon = R.drawable.ic_audio_placeholder,
                            modifier = Modifier
                                .weight(1f)
                                .height(100.dp)
                                .padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Title field
                    Text(
                        text = "Title",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Title", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Artist field
                    Text(
                        text = "Artist",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Artist", color = Color.Gray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Gray,
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color.White,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Display duration if audio is selected
                    if (isAudioSelected && duration > 0) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Duration",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = formattedDuration,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Cancel button
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .padding(end = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF333333)
                            ),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Text("Cancel", color = Color.White)
                        }

                        // Save button
                        Button(
                            onClick = {
                                selectedAudioUri?.let { audioUri ->
                                    val audioFileName = "song_${System.currentTimeMillis()}.mp3"
                                    val artworkFileName = "artwork_${System.currentTimeMillis()}.jpg"

                                    val audioFilePath = copyUriToInternalStorage(audioUri, audioFileName)
                                    val artworkPath = selectedImageUri?.let { imgUri ->
                                        copyUriToInternalStorage(imgUri, artworkFileName)
                                    } ?: ""

                                    if (audioFilePath != null) {
                                        onSaveClick(title, artist, audioFilePath, artworkPath, duration)
                                        onDismiss()
                                    }
                                }
                            },
                            enabled = isAudioSelected && title.isNotBlank() && artist.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .padding(start = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1DB954)
                            ),
                            shape = RoundedCornerShape(25.dp)
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadBox(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(
                border = BorderStroke(1.dp, if (isSelected) Color.Green else Color.Gray),
                shape = RoundedCornerShape(8.dp)
            )
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF1E3E1E) else Color(0xFF1E1E1E))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.Green,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = title,
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}