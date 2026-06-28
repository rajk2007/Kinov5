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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.api.TMDBApi
import com.lagradost.cloudstream3.api.MovieResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class KinoSearchViewModel : ViewModel() {
    private val api = TMDBApi.create()
    private val _results = MutableStateFlow<List<MovieResult>>(emptyList())
    val results: StateFlow<List<MovieResult>> = _results
    var query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query.collect { q ->
                if (q.length >= 2) {
                    try { _results.value = api.searchMulti(TMDBApi.API_KEY, q).results } catch (e: Exception) {}
                } else {
                    _results.value = emptyList()
                }
            }
        }
    }
}

@Composable
fun KinoSearchScreen(
    viewModel: KinoSearchViewModel = viewModel(),
    onMovieClick: (MovieResult) -> Unit = {}
) {
    val context = LocalContext.current
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val trendingSearches = listOf("Jawan", "One Piece", "Oppenheimer", "Attack on Titan", "RRR", "The Boys")

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF000000)).padding(24.dp)
    ) {
        Text("Search", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        // Premium Search Bar
        TextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Movies, TV Shows, Actors...", color = Color.Gray) },
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
        
        if (results.isEmpty()) {
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
        } else {
            LazyColumn {
                items(results) { movie ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp).clickable {
                            Toast.makeText(context, "Details screen coming soon", Toast.LENGTH_SHORT).show()
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val imageUrl = if (movie.poster_path != null) "${TMDBApi.IMAGE_BASE_URL}${movie.poster_path}" else ""
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = movie.displayTitle(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(80.dp, 120.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1A1A1A))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(movie.displayTitle(), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
