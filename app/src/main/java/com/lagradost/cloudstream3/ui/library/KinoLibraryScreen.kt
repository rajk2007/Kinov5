package com.lagradost.cloudstream3.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    onDownloadsClick: () -> Unit = {},
    onMediaClick: (SearchResponse) -> Unit = {}
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val history by viewModel.history.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val liked by viewModel.liked.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadData() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
    ) {
        item {
            Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
            Text(
                "My Library",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }

        // Downloads row
        item {
            LibrarySectionRow(
                title = "Downloads",
                items = emptyList(),
                onHeaderClick = onDownloadsClick,
                onMediaClick = onMediaClick,
                showHeaderOnly = true
            )
        }

        // Continue Watching
        if (continueWatching.isNotEmpty()) {
            item {
                LibrarySectionRow(
                    title = "Continue Watching",
                    items = continueWatching,
                    onMediaClick = onMediaClick
                )
            }
        }

        // Watchlist
        if (watchlist.isNotEmpty()) {
            item {
                LibrarySectionRow(
                    title = "Watchlist",
                    items = watchlist,
                    onMediaClick = onMediaClick
                )
            }
        }

        // Liked
        if (liked.isNotEmpty()) {
            item {
                LibrarySectionRow(
                    title = "Liked",
                    items = liked,
                    onMediaClick = onMediaClick
                )
            }
        }

        // History
        if (history.isNotEmpty()) {
            item {
                LibrarySectionRow(
                    title = "History",
                    items = history,
                    onMediaClick = onMediaClick
                )
            }
        }
    }
}

@Composable
fun LibrarySectionRow(
    title: String,
    items: List<SearchResponse>,
    onHeaderClick: (() -> Unit)? = null,
    onMediaClick: (SearchResponse) -> Unit = {},
    showHeaderOnly: Boolean = false
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(if (onHeaderClick != null) Modifier.clickable { onHeaderClick() } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (onHeaderClick != null) {
                Text("View All", color = Color(0xFFE50914), fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!showHeaderOnly) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(items) { media ->
                    MediaPosterCard(media = media, onClick = { onMediaClick(media) })
                }
            }
        } else {
            // Downloads placeholder row
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF141414))
                    .clickable { onHeaderClick?.invoke() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("⬇ Open Downloads", color = Color.Gray, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun MediaPosterCard(media: SearchResponse, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = media.posterUrl ?: "",
            contentDescription = media.name,
            modifier = Modifier
                .size(width = 120.dp, height = 180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A))
        )
        Text(
            text = media.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
