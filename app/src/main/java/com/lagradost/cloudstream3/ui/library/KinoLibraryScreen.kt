package com.lagradost.cloudstream3.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KinoLibraryScreen(
    onItemClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Text("My Library", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
        }
        
        // Summary Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF141414))
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("❤️", fontSize = 24.sp); Text("42", color = Color.White, fontWeight = FontWeight.Bold); Text("Saved", color = Color.Gray, fontSize = 12.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("⬇", fontSize = 24.sp); Text("18", color = Color.White, fontWeight = FontWeight.Bold); Text("Downloads", color = Color.Gray, fontSize = 12.sp) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("▶", fontSize = 24.sp); Text("236", color = Color.White, fontWeight = FontWeight.Bold); Text("Hours", color = Color.Gray, fontSize = 12.sp) }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Section Cards
        val sections = listOf(
            Triple("Downloads", "Your offline content.", "⬇"),
            Triple("Continue Watching", "Resume where you left off.", "▶"),
            Triple("Watchlist", "Saved to watch later.", "❤"),
            Triple("History", "Recently watched.", "🕒"),
            Triple("Liked", "Your favorites.", "⭐")
        )

        items(sections.size) { index ->
            val (title, subtitle, emoji) = sections[index]
            LibraryCard(title, subtitle, emoji) {
                onItemClick(title)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun LibraryCard(title: String, subtitle: String, emoji: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141414))
            .clickable { onClick() }
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0x22E50914)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.Gray, fontSize = 14.sp)
        }
    }
}
