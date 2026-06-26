package com.lagradost.cloudstream3.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.api.TMDBApi

@Composable
fun KinoHomeScreen(
    viewModel: KinoHomeViewModel = viewModel()
) {
    val trending by viewModel.trendingMovies.collectAsState()
    val popular by viewModel.popularMovies.collectAsState()
    val topRated by viewModel.topRatedMovies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Surface(color = Color(0xFF080808)) {
        when {
            isLoading && trending.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                }
            }
            error != null && trending.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("Error: $error", color = Color.White)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    item { 
                        Header() 
                    }
                    if (trending.isNotEmpty()) {
                        item { 
                            HeroBanner(movie = trending.first()) 
                        }
                    }
                    item { MovieSection("Trending Now", trending) }
                    item { MovieSection("Popular Movies", popular) }
                    item { MovieSection("Top Rated", topRated) }
                }
            }
        }
    }
}

@Composable
fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("KINO", color = Color(0xFFE50914), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("Cinema. Redefined.", color = Color(0xFF8A8A8A), fontSize = 12.sp)
    }
}

@Composable
fun HeroBanner(movie: MovieResult) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        AsyncImage(
            model = "${TMDBApi.IMAGE_BASE_URL}${movie.backdrop_path ?: movie.poster_path}",
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF080808)),
                        startY = 300f
                    )
                )
        )
        // Content
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
        ) {
            Text(movie.displayTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { /* TODO: Play */ },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("▶ Watch Now", color = Color.White)
            }
        }
    }
}

@Composable
fun MovieSection(title: String, movies: List<MovieResult>) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(movies) { movie ->
                MovieCard(movie)
            }
        }
    }
}

@Composable
fun MovieCard(movie: MovieResult) {
    Column(
        modifier = Modifier.width(120.dp)
    ) {
        AsyncImage(
            model = "${TMDBApi.IMAGE_BASE_URL}${movie.poster_path}",
            contentDescription = movie.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 120.dp, height = 180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A1A))
        )
        Text(
            movie.displayTitle,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
