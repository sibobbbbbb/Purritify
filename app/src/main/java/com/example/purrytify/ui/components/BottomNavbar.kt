package com.example.purrytify.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

import com.example.purrytify.ui.navigation.Destinations


@Composable
fun BottomNavbar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(Color.Black),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavBarItem(
            icon = Icons.Outlined.Home,
            label = "Home",
            isSelected = currentRoute == Destinations.HOME_ROUTE,
            onClick = {
                if (currentRoute != Destinations.HOME_ROUTE) {
                    navController.navigate(Destinations.HOME_ROUTE) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            }
        )

        NavBarItem(
            icon = Icons.Outlined.LibraryMusic ,
            label = "Your Library",
            isSelected = currentRoute == Destinations.LIBRARY_ROUTE,
            onClick = {
                if (currentRoute != Destinations.LIBRARY_ROUTE) {
                    navController.navigate(Destinations.LIBRARY_ROUTE) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            }
        )

        NavBarItem(
            icon = Icons.Outlined.MusicNote, // Use appropriate icon
            label = "Online",
            isSelected = currentRoute == Destinations.ONLINE_SONGS_ROUTE,
            onClick = {
                if (currentRoute != Destinations.ONLINE_SONGS_ROUTE) {
                    navController.navigate(Destinations.ONLINE_SONGS_ROUTE) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            }
        )

        NavBarItem(
            icon = Icons.Outlined.Person,
            label = "Profile",
            isSelected = currentRoute == Destinations.PROFILE_ROUTE,
            onClick = {
                if (currentRoute != Destinations.PROFILE_ROUTE) {
                    navController.navigate(Destinations.PROFILE_ROUTE) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }
            }
        )


    }
}

@Composable
fun NavBarItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else Color.Gray,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Text(
            text = label,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
        )
    }
}