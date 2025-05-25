package com.example.purrytify.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.purrytify.R
import com.example.purrytify.ui.theme.GREEN_COLOR
import com.example.purrytify.util.CountryCodeHelper

/**
 * Dialog untuk memilih metode location selection
 *
 */
@Composable
fun LocationSelectionDialog(
    onGoogleMapsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                Text(
                    text = "Select Location Method",
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Choose how you want to select your location:",
                    color = Color.Gray,
                    fontFamily = FontFamily(Font(R.font.poppins_regular)),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Google Maps option dengan instruksi
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onGoogleMapsClick()
                            onDismiss()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2A2A2A)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Maps",
                                tint = GREEN_COLOR,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Use Google Maps",
                                    color = Color.White,
                                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Select location directly on map",
                                    color = Color.Gray,
                                    fontFamily = FontFamily(Font(R.font.poppins_regular)),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Instruksi penggunaan
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1E3A8A).copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Instructions:",
                                        color = Color(0xFF60A5FA),
                                        fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "1. Tap and hold on the map to drop a pin\n2. Tap the pin to see options\n3. Tap Coordinates to copy coordinates\n4. Return to this app",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontFamily = FontFamily(Font(R.font.poppins_regular)),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Cancel",
                            color = Color.Gray,
                            fontFamily = FontFamily(Font(R.font.poppins_regular))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog untuk memilih country dari daftar yang didukung
 */
@Composable
fun CountrySelectionDialog(
    onCountrySelected: (String, String) -> Unit, // (countryCode, countryName)
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allCountries = remember { CountryCodeHelper.getSupportedCountriesList() }

    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allCountries
        } else {
            allCountries.filter { (_, name) ->
                name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A1A)
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                Text(
                    text = "Select Country",
                    color = Color.White,
                    fontFamily = FontFamily(Font(R.font.poppins_semi_bold)),
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search country") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GREEN_COLOR,
                        focusedLabelColor = GREEN_COLOR,
                        cursorColor = GREEN_COLOR
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Country list
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredCountries) { (countryCode, countryName) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onCountrySelected(countryCode, countryName)
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2A2A2A)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = countryName,
                                    color = Color.White,
                                    fontFamily = FontFamily(Font(R.font.poppins_regular)),
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = countryCode,
                                    color = Color.Gray,
                                    fontFamily = FontFamily(Font(R.font.poppins_regular)),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Cancel button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            "Cancel",
                            color = Color.Gray,
                            fontFamily = FontFamily(Font(R.font.poppins_regular))
                        )
                    }
                }
            }
        }
    }
}