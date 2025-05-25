package com.example.purrytify.test

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.ui.theme.PurrytifyTheme
import com.example.purrytify.util.ShareUtils

/**
 * Test Activity for testing deep link functionality
 * This can be used during development to test deep links
 */
class DeepLinkTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PurrytifyTheme {
                DeepLinkTestScreen()
            }
        }
    }
}

@Composable
fun DeepLinkTestScreen() {
    val context = LocalContext.current
    var testSongId by remember { mutableStateOf("71") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Deep Link Test",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = testSongId,
            onValueChange = { testSongId = it },
            label = { Text("Song ID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val deepLink = ShareUtils.createSongDeepLink(testSongId.toIntOrNull() ?: 71)
                Log.d("DeepLinkTest", "Generated deep link: $deepLink")

                // Test the deep link by creating an intent
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Deep Link")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                // Create a test OnlineSong
                val testSong = OnlineSong(
                    id = testSongId.toIntOrNull() ?: 71,
                    title = "Die With A Smile",
                    artist = "Lady Gaga, Bruno Mars",
                    artworkUrl = "https://storage.googleapis.com/mad-public-bucket/cover/Die%20With%20A%20Smile.png",
                    audioUrl = "https://storage.googleapis.com/mad-public-bucket/mp3/Lady%20Gaga%2C%20Bruno%20Mars%20-%20Die%20With%20A%20Smile%20(Lyrics).mp3",
                    durationString = "4:12",
                    country = "GLOBAL",
                    rank = 1,
                    createdAt = "2025-05-08T02:16:53.192Z",
                    updatedAt = "2025-05-08T02:16:53.192Z"
                )

                ShareUtils.shareSongUrl(context, testSong)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test Share Song")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Deep Link Format: purrytify://song/{song_id}",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Example: purrytify://song/71",
            style = MaterialTheme.typography.bodySmall
        )
    }
}