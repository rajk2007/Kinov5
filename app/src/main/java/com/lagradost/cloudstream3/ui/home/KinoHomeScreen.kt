package com.lagradost.cloudstream3.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.api.TMDBApi
import kotlinx.coroutines.delay

@Composable
fun KinoHomeScreen(
    viewModel: KinoHomeViewModel = viewModel()
) {
    val trending by viewModel.trendingMovies.collectAsState()
    val popular by viewModel.popularMovies.collectAsState()
    val topRated by viewModel.topRatedMovies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Surface(color = Color(0xFF080808)) {
        if (isLoading && trending.isEmpty()) {
            ShimmerLoading()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item { Header() }
                item { QuickDiscoveryChips() }
                if (trending.isNotEmpty()) {
                    item { 
                        HeroBanner(movies = trending.take(5)) 
                    }
                }
                item { MovieSection("Trending Now", trending) }
                item { Top10Section("Top 10 Today", trending.take(10)) }
                item { MovieSection("Popular Movies", popular) }
                item { MovieSection("Top Rated", topRated) }
                item { MovieSection("🆕 New Releases", viewModel.nowPlaying.collectAsState().value) }
                item { MovieSection("🇮🇳 Hindi Dubbed For You", viewModel.hindiDubbedMovies.collectAsState().value) }
                item { MovieSection("🌸 Anime Spotlight", viewModel.animeSpotlightTv.collectAsState().value) }
                item { MovieSection("🇰🇷 K-Drama Spotlight", viewModel.kDramaSpotlightTv.collectAsState().value) }
                item { MovieSection("💎 Hidden Gems", viewModel.hiddenGemsMovies.collectAsState().value) }
                item { MovieSection("🎥 Popular Movies", viewModel.popularMovies.collectAsState().value) }
                item { MovieSection("📺 Popular TV Shows", viewModel.popularTV.collectAsState().value) }
                item { MovieSection("🍿 Weekend Picks", viewModel.popularMovies.collectAsState().value) }
                item { MovieSection("⭐ Critically Acclaimed", viewModel.topRatedMovies.collectAsState().value) }
                item { MovieSection("🎭 Action & Adventure", viewModel.actionAdventureMovies.collectAsState().value) }
                item { MovieSection("😂 Comedy Picks", viewModel.comedyMovies.collectAsState().value) }
                item { MovieSection("😱 Thriller & Horror", viewModel.thrillerHorrorMovies.collectAsState().value) }
                item { MovieSection("👨‍👩‍👧 Family & Kids", viewModel.familyKidsMovies.collectAsState().value) }
                item { MovieSection("🌍 International Hits", viewModel.internationalHitsMovies.collectAsState().value) }
                item { MovieSection("🎌 Trending Anime This Week", viewModel.trendingAnimeThisWeekTv.collectAsState().value) }
            }
        }
    }
}

@Composable
fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color.Transparent),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { /* TODO: Handle search click */ }) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
            }
            IconButton(onClick = { /* TODO: Handle notifications click */ }) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Color.White)
            }
        }
        Text("KINO", color = Color(0xFFE50914), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        // Text("Cinema. Redefined.", color = Color(0xFF8A8A8A), fontSize = 12.sp) // Removed as per instruction to keep KINO on right/center
    }
}


@Composable
fun QuickDiscoveryChips() {
    val categories = listOf(
        "All", "Movies", "TV Shows", "Anime", "K-Drama", "Hindi Dubbed",
        "Trending", "New", "Top Rated", "Genres", "My List", "Under 2 Hours"
    )
    var selectedCategory by remember { mutableStateOf(categories[0]) }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = selectedCategory == category
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFF7B2FBE)))
                        else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF)))
                    )
                    .clickable { selectedCategory = category }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(category, color = if (isSelected) Color.White else Color.Gray)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HeroBanner(movies: List<MovieResult>) {
    val pagerState = rememberPagerState(pageCount = { movies.size })

    // Auto-scroll
    LaunchedEffect(pagerState) {
        while (true) {
            delay(4000)
            val nextPage = (pagerState.currentPage + 1) % movies.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = movies[page]
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val scaleFactor = 1f - kotlin.math.abs(pageOffset) * 0.1f

            Box(modifier = Modifier.graphicsLayer {
                scaleX = scaleFactor
                scaleY = scaleFactor
            }) {
                AsyncImage(
                    model = "${TMDBApi.IMAGE_BASE_URL}${movie.backdrop_path ?: movie.poster_path}",
                    contentDescription = movie.displayTitle(),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Premium Gradient Overlay
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xCC080808),
                                Color(0xFF080808)
                            ),
                            startY = 200f
                        )
                    )
                )

                // Content
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
                ) {
                    Text(movie.displayTitle(), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (movie.release_date != null) {
                            Text(movie.release_date.take(4), color = Color.Gray, fontSize = 14.sp)
                        }
                        if (movie.vote_average != null) {
                            Text("⭐ ${movie.vote_average}", color = Color(0xFFF5C518), fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(
                            onClick = { /* TODO: Play */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("▶ Watch Now", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { /* TODO: Add to Watchlist */ },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+ Watchlist", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        // Page Indicators
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(movies.size) { index ->
                val color = if (pagerState.currentPage == index) Color(0xFFE50914) else Color(0x55FFFFFF)
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .clip(CircleShape)
                        .background(color)
                        .width(if (pagerState.currentPage == index) 24.dp else 8.dp).height(8.dp)
                )
            }
        }
    }
}

@Composable
fun MovieSection(title: String, movies: List<MovieResult>) {
    val listState = rememberLazyListState()
    
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                PremiumMovieCard(movie)
            }
        }
    }
}

@Composable
fun Top10Section(title: String, movies: List<MovieResult>) {
    val listState = rememberLazyListState()
    
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(movies.size) { index ->
                val movie = movies[index]
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        "${index + 1}",
                        color = Color(0xFF1A1A1A),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.offset(x = 12.dp)
                    )
                    PremiumMovieCard(movie, modifier = Modifier.offset(x = -16.dp))
                }
            }
        }
    }
}

@Composable
fun PremiumMovieCard(movie: MovieResult, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "scale")

    Column(
        modifier = modifier
            .width(140.dp)
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .size(width = 140.dp, height = 210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = "${TMDBApi.IMAGE_BASE_URL}${movie.poster_path}",
                contentDescription = movie.displayTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Rating Badge
            if (movie.vote_average != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⭐ ${movie.vote_average}",
                        color = Color(0xFFF5C518),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Text(
            movie.displayTitle(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp)
        )
    }
}

@Composable
fun ShimmerLoading() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1A1A1A),
            Color(0xFF2A2A2A),
            Color(0xFF1A1A1A)
        ),
        start = androidx.compose.ui.geometry.Offset(translateAnim, translateAnim),
        end = androidx.compose.ui.geometry.Offset(translateAnim + 300f, translateAnim + 300f)
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(400.dp).clip(RoundedCornerShape(12.dp)).background(brush))
        Spacer(modifier = Modifier.height(20.dp))
        repeat(3) {
            Box(modifier = Modifier.size(120.dp, 180.dp).clip(RoundedCornerShape(8.dp)).background(brush))
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
