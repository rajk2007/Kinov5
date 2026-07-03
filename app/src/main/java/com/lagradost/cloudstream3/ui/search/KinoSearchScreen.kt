package com.lagradost.cloudstream3.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.TvType

@Composable
fun KinoSearchScreen(
    viewModel: KinoSearchViewModel = viewModel(),
    initialQuery: String = "",
    onResultClick: (KinoSearchResult) -> Unit = {}
) {
    val context = LocalContext.current
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val trendingSearches = listOf("Jawan", "One Piece", "Oppenheimer", "Attack on Titan", "RRR", "The Boys")

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.query.value = initialQuery
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000)).padding(24.dp)
    ) {
        Text("Search", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        TextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Movies, TV Shows, Anime...", color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141414),
                unfocusedContainerColor = Color(0xFF141414),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFFE50914),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(24.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        } else if (results.isEmpty() && query.isBlank()) {
            // Show trending when no query
            Text("Trending Searches", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(trendingSearches) { search ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF141414))
                            .clickable { viewModel.query.value = search }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(search, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        } else if (results.isEmpty()) {
            Text("No results found", color = Color.Gray, fontSize = 16.sp)
        } else {
            LazyColumn {
                items(results) { result ->
                    ProviderResultCard(
                        result = result,
                        onClick = { onResultClick(result) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderResultCard(
    result: KinoSearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Poster
        AsyncImage(
            model = result.posterUrl ?: "",
            contentDescription = result.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(80.dp, 120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A))
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // Type + Quality badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                // Type badge (Movie / TV / Anime)
                val typeText = when (result.type) {
                    TvType.Movie -> "Movie"
                    TvType.TvSeries -> "TV"
                    TvType.Anime -> "Anime"
                    TvType.AsianDrama -> "Drama"
                    else -> result.type?.name ?: "Video"
                }
                Badge(text = typeText, color = Color(0xFF333333))

                // Quality badge (HD, 4K, etc.)
                result.quality?.let { quality ->
                    Badge(text = quality, color = Color(0xFFE50914))
                }
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
