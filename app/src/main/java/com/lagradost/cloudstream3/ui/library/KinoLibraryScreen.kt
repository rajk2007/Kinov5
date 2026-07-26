package com.lagradost.cloudstream3.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.SearchResponse

@Composable
fun KinoLibraryScreen(
    viewModel: KinoLibraryViewModel = viewModel(),
    onItemClick: (String) -> Unit,
    onMediaClick: (SearchResponse) -> Unit
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val history by viewModel.history.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val liked by viewModel.liked.collectAsState()
    var selectedScreen by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.loadData() }

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

        if (selectedScreen == null) {
            // Menu View
            item { LibraryCard("Downloads", "Your offline content.", "⬇") { onItemClick("Downloads") } ; Spacer(modifier = Modifier.height(16.dp)) }
            item { LibraryCard("Continue Watching", "Resume where you left off.", "▶") { selectedScreen = "Continue Watching" } ; Spacer(modifier = Modifier.height(16.dp)) }
            item { LibraryCard("Watchlist", "Saved to watch later.", "❤") { selectedScreen = "Watchlist" } ; Spacer(modifier = Modifier.height(16.dp)) }
            item { LibraryCard("History", "Recently watched.", "🕒") { selectedScreen = "History" } ; Spacer(modifier = Modifier.height(16.dp)) }
            item { LibraryCard("Liked", "Your favorites.", "⭐") { selectedScreen = "Liked" } }
        } else {
            // Detail View
            item {
                Text(selectedScreen!!, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
            }
            val dataList = when (selectedScreen) {
                "Continue Watching" -> continueWatching
                "History" -> history
                "Watchlist" -> watchlist
                "Liked" -> liked
                else -> emptyList()
            }

            if (dataList.isEmpty()) {
                item { Text("No items found.", color = Color.Gray, fontSize = 16.sp) }
            } else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(dataList) { media ->
                            Column(modifier = Modifier.width(120.dp).clickable { onMediaClick(media) }) {
                                AsyncImage(
                                    model = media.posterUrl ?: "",
                                    contentDescription = media.name,
                                    modifier = Modifier.size(width = 120.dp, height = 180.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A))
                                )
                                Text(media.name, color = Color.White, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
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
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0x22E50914)), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.Gray, fontSize = 14.sp)
        }
    }
}
