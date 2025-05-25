package com.example.purrytify.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.size
import com.example.purrytify.models.OnlineSong
import com.example.purrytify.util.ShareUtils

@Composable
fun ShareButton(
    onlineSong: OnlineSong?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    tint: Color = Color.White
) {
    val context = LocalContext.current

    if (onlineSong != null) {
        IconButton(
            onClick = {
                ShareUtils.shareSongUrl(context, onlineSong)
            },
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share Song",
                tint = tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}