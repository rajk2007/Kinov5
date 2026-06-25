package com.lagradost.cloudstream3.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.api.TMDBApi
import com.lagradost.cloudstream3.ui.result.ResultFragment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KinoHomeScreen(
    viewModel: KinoHomeViewModel = viewModel()
) {
    val trending by viewModel.trendingMovies.collectAsState()
    val popular by viewModel.popularMovies.collectAsState()
    val topRated by viewModel.topRatedMovies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080808))) {
        when {
            isLoading && trending.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                }
            }
            error != null && trending.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: $error", color = Color.White, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.retry() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                        ) {
                            Text("Retry", color = Color.White)
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Hero Section - First trending movie as hero
                    if (trending.isNotEmpty()) {
                        item {
                            HeroSection(movie = trending.first(), context = context)
                        }
                    }
                    
                    // Content Rows
                    item { ContentRow("Trending Now", trending, context) }
                    item { ContentRow("Popular Movies", popular, context) }
                    item { ContentRow("Top Rated", topRated, context) }
                }
            }
        }
    }
}

@Composable
fun HeroSection(movie: MovieResult, context: Context) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        // Backdrop image
        AsyncImage(
            model = movie.backdrop_path?.let { "${TMDBApi.IMAGE_BASE_URL}$it" } 
                ?: movie.poster_path?.let { "${TMDBApi.IMAGE_BASE_URL}$it" },
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF080808).copy(alpha = 0.7f),
                            Color(0xFF080808)
                        ),
                        startY = 0f,
                        endY = 500f
                    )
                )
        )
        
        // Content info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = movie.title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = movie.overview ?: "",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                maxLines = 3
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row {
                Button(
                    onClick = { 
                        navigateToResult(context, movie)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("▶ Play", color = Color.Black, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { 
                        navigateToResult(context, movie)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("ℹ More Info", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ContentRow(title: String, movies: List<MovieResult>, context: Context) {
    if (movies.isEmpty()) return
    
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies, key = { it.id }) { movie ->
                PremiumMovieCard(movie = movie, context = context)
            }
        }
    }
}

@Composable
fun PremiumMovieCard(movie: MovieResult, context: Context) {
    var isFocused by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(210.dp)
            .scale(if (isFocused) 1.05f else 1f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        onClick = {
            navigateToResult(context, movie)
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (movie.poster_path != null) {
                AsyncImage(
                    model = "${TMDBApi.IMAGE_BASE_URL}${movie.poster_path}",
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Gradient overlay at bottom for title
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
            )
            
            Text(
                text = movie.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}

private fun navigateToResult(context: Context, movie: MovieResult) {
    val fragment = ResultFragment.newInstance(
        movie.title,
        movie.id.toString(),
        movie.poster_path?.let { "${TMDBApi.IMAGE_BASE_URL}$it" } ?: "",
        movie.overview ?: "",
        movie.vote_average?.toString() ?: "0.0"
    )
    (context as? MainActivity)?.supportFragmentManager?.beginTransaction()
        ?.replace(R.id.nav_host_fragment, fragment)
        ?.addToBackStack(null)
        ?.commit()
}
