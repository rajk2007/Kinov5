package com.lagradost.cloudstream3.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    onMediaClick: (SearchResponse) -> Unit
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadData(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Text("My Library", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Continue Watching", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (continueWatching.isEmpty()) {
            item { Text("Nothing to resume.", color = Color.Gray, fontSize = 15.sp) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continueWatching) { media -> LibraryPoster(media, onMediaClick) }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(28.dp))
            Text("Downloads", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (downloads.isEmpty()) {
            item { Text("No downloads available.", color = Color.Gray, fontSize = 15.sp) }
        } else {
            items(downloads) { media -> LibraryDownloadRow(media, onMediaClick) }
        }
        item { Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun LibraryPoster(media: SearchResponse, onMediaClick: (SearchResponse) -> Unit) {
    Column(modifier = Modifier.width(120.dp).clickable { onMediaClick(media) }) {
        AsyncImage(
            model = media.posterUrl ?: "",
            contentDescription = media.name,
            modifier = Modifier.size(width = 120.dp, height = 180.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A))
        )
        Text(media.name, color = Color.White, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun LibraryDownloadRow(media: SearchResponse, onMediaClick: (SearchResponse) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onMediaClick(media) }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = media.posterUrl ?: "",
            contentDescription = media.name,
            modifier = Modifier.size(width = 72.dp, height = 104.dp)
                .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A))
        )
        Text(media.name, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
