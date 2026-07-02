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
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

@Composable
fun KinoHomeScreen(
    viewModel: KinoHomeViewModel = viewModel(),
    onMovieClick: (MovieResult) -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val trending by viewModel.trendingMovies.collectAsState()
    val popular by viewModel.popularMovies.collectAsState()
    val topRated by viewModel.topRatedMovies.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    val categories = listOf(
        "All", "Movies", "TV Shows", "Anime", "K-Drama", "Hindi Dubbed",
        "Trending", "New", "Top Rated", "Genres", "My List", "Under 2 Hours"
    )
    var selectedCategory by remember { mutableStateOf(categories[0]) }

    val pagerState = rememberPagerState(pageCount = { trending.take(5).size })
    val currentMovie = if (trending.isNotEmpty()) trending[pagerState.currentPage % trending.size] else null

    Surface(color = Color(0xFF080808)) {
        // Dynamic Blurred Background
        if (currentMovie != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = "${TMDBApi.IMAGE_BASE_URL}${currentMovie.backdrop_path ?: currentMovie.poster_path}",
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(25.dp)
                        .graphicsLayer(alpha = 0.2f)
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }
        }

        if (isLoading && trending.isEmpty()) {
            ShimmerLoading()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                item {
                    Column(Modifier.background(Color(0xFF080808))) {
                        Header(onSearchClick)
                        QuickDiscoveryChips(
                            categories = categories,
                            selectedCategory = selectedCategory,
                            onCategorySelected = { selectedCategory = it }
                        )
                    }
                }
                
                item {
                    Column {
                        when (selectedCategory) {
                            "All" -> {
                                if (trending.isNotEmpty()) {
                                    HeroBanner(movies = trending.take(5), onMovieClick = onMovieClick, pagerState = pagerState)
                                }
                                MovieSection("🔥 Trending Now", trending, onMovieClick)
                                Top10Section("🏆 Top 10 Today", trending.take(10), onMovieClick)
                                MovieSection("🆕 New Releases", viewModel.nowPlaying.collectAsState().value, onMovieClick)
                                MovieSection("🇮🇳 Hindi Dubbed For You", viewModel.hindiDubbedMovies.collectAsState().value, onMovieClick)
                                MovieSection("🌸 Anime Spotlight", viewModel.animeSpotlightTv.collectAsState().value, onMovieClick)
                                MovieSection("🇰🇷 K-Drama Spotlight", viewModel.kDramaSpotlightTv.collectAsState().value, onMovieClick)
                                MovieSection("❤️ Recommended For You", popular, onMovieClick)
                                MovieSection("💎 Hidden Gems", viewModel.hiddenGemsMovies.collectAsState().value, onMovieClick)
                                MovieSection("🎥 Popular Movies", popular, onMovieClick)
                                MovieSection("📺 Popular TV Shows", viewModel.popularTV.collectAsState().value, onMovieClick)
                                MovieSection("🍿 Weekend Picks", popular, onMovieClick)
                                MovieSection("⭐ Critically Acclaimed", viewModel.criticallyAcclaimedMovies.collectAsState().value, onMovieClick)
                                MovieSection("🎭 Action & Adventure", viewModel.actionAdventureMovies.collectAsState().value, onMovieClick)
                                MovieSection("😂 Comedy Picks", viewModel.comedyMovies.collectAsState().value, onMovieClick)
                                MovieSection("😱 Thriller & Horror", viewModel.thrillerHorrorMovies.collectAsState().value, onMovieClick)
                                MovieSection("👨‍👩‍👧 Family & Kids", viewModel.familyKidsMovies.collectAsState().value, onMovieClick)
                                MovieSection("🌍 International Hits", viewModel.internationalHitsMovies.collectAsState().value, onMovieClick)
                                MovieSection("🎌 Trending Anime This Week", viewModel.trendingAnimeThisWeekTv.collectAsState().value, onMovieClick)
                            }
                            "Movies" -> {
                                MovieSection("New Releases", viewModel.nowPlaying.collectAsState().value, onMovieClick)
                                MovieSection("Popular Movies", popular, onMovieClick)
                                MovieSection("Top Rated", topRated, onMovieClick)
                                MovieSection("Action", viewModel.actionAdventureMovies.collectAsState().value, onMovieClick)
                                MovieSection("Comedy", viewModel.comedyMovies.collectAsState().value, onMovieClick)
                                MovieSection("Horror", viewModel.thrillerHorrorMovies.collectAsState().value, onMovieClick)
                            }
                            "TV Shows" -> {
                                MovieSection("Popular TV", viewModel.popularTV.collectAsState().value, onMovieClick)
                                MovieSection("Top Rated TV", viewModel.topRatedTV.collectAsState().value, onMovieClick)
                                MovieSection("Trending TV", viewModel.trendingTv.collectAsState().value, onMovieClick)
                                MovieSection("K-Drama", viewModel.kDramaSpotlightTv.collectAsState().value, onMovieClick)
                            }
                            "Anime" -> {
                                MovieSection("Anime Spotlight", viewModel.animeSpotlightTv.collectAsState().value, onMovieClick)
                                MovieSection("Trending Anime", viewModel.trendingAnimeThisWeekTv.collectAsState().value, onMovieClick)
                                MovieSection("Action Anime", viewModel.actionAnimeTv.collectAsState().value, onMovieClick)
                            }
                            "Hindi Dubbed" -> {
                                MovieSection("Hindi Dubbed For You", viewModel.hindiDubbedMovies.collectAsState().value, onMovieClick)
                                MovieSection("Popular Hindi", viewModel.popularHindiMovies.collectAsState().value, onMovieClick)
                                MovieSection("Top Rated Hindi", viewModel.topRatedHindiMovies.collectAsState().value, onMovieClick)
                            }
                            "K-Drama" -> {
                                MovieSection("K-Drama Spotlight", viewModel.kDramaSpotlightTv.collectAsState().value, onMovieClick)
                                MovieSection("Popular Korean TV", viewModel.popularKoreanTv.collectAsState().value, onMovieClick)
                            }
                            "Trending" -> {
                                MovieSection("Trending Now", trending, onMovieClick)
                                MovieSection("Trending TV", viewModel.trendingTv.collectAsState().value, onMovieClick)
                            }
                            "New" -> {
                                MovieSection("New Releases", viewModel.nowPlaying.collectAsState().value, onMovieClick)
                            }
                            "Top Rated" -> {
                                MovieSection("Critically Acclaimed", viewModel.criticallyAcclaimedMovies.collectAsState().value, onMovieClick)
                                MovieSection("Hidden Gems", viewModel.hiddenGemsMovies.collectAsState().value, onMovieClick)
                            }
                            "Genres" -> {
                                MovieSection("Action", viewModel.actionAdventureMovies.collectAsState().value, onMovieClick)
                                MovieSection("Comedy", viewModel.comedyMovies.collectAsState().value, onMovieClick)
                                MovieSection("Horror", viewModel.thrillerHorrorMovies.collectAsState().value, onMovieClick)
                                MovieSection("Family", viewModel.familyKidsMovies.collectAsState().value, onMovieClick)
                            }
                            "My List" -> {
                                MovieSection("Watchlist", emptyList(), onMovieClick)
                            }
                            "Under 2 Hours" -> {
                                MovieSection("Popular Movies", popular, onMovieClick)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Header(onSearchClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp)
            .background(Color.Transparent),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("KINO", color = Color(0xFFE50914), fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.background(Color.Transparent)
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { /* TODO: Handle notifications click */ },
                modifier = Modifier.background(Color.Transparent)
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = Color.White)
            }
        }
    }
}


@Composable
fun QuickDiscoveryChips(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
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
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(category, color = if (isSelected) Color.White else Color.Gray)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HeroBanner(
    movies: List<MovieResult>, 
    onMovieClick: (MovieResult) -> Unit = {},
    pagerState: androidx.compose.foundation.pager.PagerState = rememberPagerState(pageCount = { movies.size })
) {
    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % movies.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val movie = movies[page]
                    Box(modifier = Modifier.fillMaxSize()) {
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
                                        Color(0x99000000),
                                        Color(0xFF000000)
                                    ),
                                    startY = 300f
                                )
                            )
                        )

                        // Content
                        Column(
                            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
                        ) {
                            Text(movie.displayTitle(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (movie.release_date != null && movie.release_date.length >= 4) {
                                    Text(movie.release_date.take(4), color = Color.Gray, fontSize = 14.sp)
                                }
                                if (movie.vote_average != null) {
                                    Text("⭐ ${movie.vote_average}", color = Color(0xFFF5C518), fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row {
                                Button(
                                    onClick = { onMovieClick(movie) },
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

                // Dot Indicators
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(movies.size) { index ->
                        val color = if (pagerState.currentPage == index) Color(0xFFE50914) else Color(0x55FFFFFF)
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MovieSection(title: String, movies: List<MovieResult>, onMovieClick: (MovieResult) -> Unit = {}) {
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
                PremiumMovieCard(movie, onMovieClick = onMovieClick)
            }
        }
    }
}

@Composable
fun Top10Section(title: String, movies: List<MovieResult>, onMovieClick: (MovieResult) -> Unit = {}) {
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
                    PremiumMovieCard(movie, modifier = Modifier.offset(x = -16.dp), onMovieClick = onMovieClick)
                }
            }
        }
    }
}

@Composable
fun PremiumMovieCard(movie: MovieResult, modifier: Modifier = Modifier, onMovieClick: (MovieResult) -> Unit = {}) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "scale")

    Column(
        modifier = modifier
            .width(140.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material.ripple.rememberRipple()
            ) {
                onMovieClick(movie)
            }
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
