package com.lagradost.cloudstream3.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KinoLibraryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // AMOLED black background
            .padding(16.dp)
    ) {
        Text(
            text = "LIBRARY",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LibraryItem(
            emoji = "▶",
            title = "Continue Watching",
            subtitle = "Resume movies, TV shows, anime, and K-dramas."
        )
        Divider(color = Color(0xFF1A1A1A), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        LibraryItem(
            emoji = "⬇",
            title = "Downloads",
            subtitle = "Your offline content."
        )
        Divider(color = Color(0xFF1A1A1A), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        LibraryItem(
            emoji = "❤️",
            title = "Watchlist",
            subtitle = "Saved content to watch later."
        )
        Divider(color = Color(0xFF1A1A1A), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        LibraryItem(
            emoji = "🕒",
            title = "History",
            subtitle = "Recently watched content."
        )
        Divider(color = Color(0xFF1A1A1A), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

        LibraryItem(
            emoji = "⭐",
            title = "Liked",
            subtitle = "Your favorite movies and TV shows."
        )
    }
}

@Composable
fun LibraryItem(emoji: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Handle item click */ }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier.width(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color.Gray, fontSize = 13.sp)
        }
    }
}
