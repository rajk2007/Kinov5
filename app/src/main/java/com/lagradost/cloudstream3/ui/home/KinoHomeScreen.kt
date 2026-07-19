package com.lagradost.cloudstream3.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.lagradost.cloudstream3.ui.search.KinoSearchResult

@Composable
fun KinoHomeScreen(
    viewModel: KinoHomeViewModel = viewModel(),
    onMovieClick: (MovieResult) -> Unit = {},
    onLiveClick: (KinoSearchResult) -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val trending by viewModel.trendingMovies.collectAsState()
    val popular by viewModel.popularMovies.collectAsState()
    val topRated by viewModel.topRatedMovies.collectAsState()
    val liveEventsMap by viewModel.liveEvents.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // State collections moved outside LazyColumn
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val hindiDubbedMovies by viewModel.hindiDubbedMovies.collectAsState()
    val animeSpotlightTv by viewModel.animeSpotlightTv.collectAsState()
    val kDramaSpotlightTv by viewModel.kDramaSpotlightTv.collectAsState()
    val hiddenGemsMovies by viewModel.hiddenGemsMovies.collectAsState()
    val popularTV by viewModel.popularTV.collectAsState()
    val criticallyAcclaimedMovies by viewModel.criticallyAcclaimedMovies.collectAsState()
    val actionAdventureMovies by viewModel.actionAdventureMovies.collectAsState()
    val comedyMovies by viewModel.comedyMovies.collectAsState()
    val thrillerHorrorMovies by viewModel.thrillerHorrorMovies.collectAsState()
    val familyKidsMovies by viewModel.familyKidsMovies.collectAsState()
    val internationalHitsMovies by viewModel.internationalHitsMovies.collectAsState()
    val trendingAnimeThisWeekTv by viewModel.trendingAnimeThisWeekTv.collectAsState()
    val topRatedTV by viewModel.topRatedTV.collectAsState()
    val trendingTv by viewModel.trendingTv.collectAsState()
    val actionAnimeTv by viewModel.actionAnimeTv.collectAsState()
    val popularHindiMovies by viewModel.popularHindiMovies.collectAsState()
    val topRatedHindiMovies by viewModel.topRatedHindiMovies.collectAsState()
    val popularKoreanTv by viewModel.popularKoreanTv.collectAsState()
    val liveEventsError by viewModel.liveEventsError.collectAsState()
    
    val categories = listOf("All", "Live", "Movies", "Series", "Anime", "Hindi")
    var selectedCategory by remember { mutableStateOf(categories[0]) }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory == "Live" && liveEventsMap.isEmpty()) {
            viewModel.loadLiveEvents()
        }
    }

    val pagerState = rememberPagerState(pageCount = { trending.take(7).size })
    val currentMovie = if (trending.isNotEmpty() && pagerState.pageCount > 0) trending[pagerState.currentPage % trending.size] else null

    Surface(
        color = Color(0xFF080808),
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Dynamic Blurred Background
        if (currentMovie != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                val backdropUrl = currentMovie.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                    ?: currentMovie.poster_path?.let { "https://image.tmdb.org/t/p/original$it" }
                    ?: ""
                AsyncImage(
                    model = backdropUrl,
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
                            onCategorySelected = { 
                                selectedCategory = it 
                                if (it == "Live" && liveEventsMap.isEmpty()) {
                                    viewModel.loadLiveEvents()
                                }
                            }
                        )
                    }
                }
                
                item {
                    Column {
                        when (selectedCategory) {
                            "All" -> {
                                if (trending.isNotEmpty()) {
                                    HeroBanner(movies = trending.take(7), onMovieClick = onMovieClick, pagerState = pagerState)
                                }
                                MovieSection("🔥 Trending Now", trending, onMovieClick)

                                MovieSection("🆕 New Releases", nowPlaying, onMovieClick)
                                MovieSection("🇮🇳 Hindi Dubbed For You", hindiDubbedMovies, onMovieClick)
                                MovieSection("🌸 Anime Spotlight", animeSpotlightTv, onMovieClick)
                                MovieSection("🇰🇷 K-Drama Spotlight", kDramaSpotlightTv, onMovieClick)
                                MovieSection("❤️ Recommended For You", popular, onMovieClick)
                                MovieSection("💎 Hidden Gems", hiddenGemsMovies, onMovieClick)
                                MovieSection("🎥 Popular Movies", popular, onMovieClick)
                                MovieSection("📺 Popular TV Shows", popularTV, onMovieClick)
                                MovieSection("🍿 Weekend Picks", popular, onMovieClick)
                                MovieSection("⭐ Critically Acclaimed", criticallyAcclaimedMovies, onMovieClick)
                                MovieSection("🎭 Action & Adventure", actionAdventureMovies, onMovieClick)
                                MovieSection("😂 Comedy Picks", comedyMovies, onMovieClick)
                                MovieSection("😱 Thriller & Horror", thrillerHorrorMovies, onMovieClick)
                                MovieSection("👨‍👩‍👧 Family & Kids", familyKidsMovies, onMovieClick)
                                MovieSection("🌍 International Hits", internationalHitsMovies, onMovieClick)
                                MovieSection("🎌 Trending Anime This Week", trendingAnimeThisWeekTv, onMovieClick)
                            }
                            "Live" -> {
                                // Handled in a separate logic below to avoid nesting item inside item
                            }
                            "Movies" -> {
                                MovieSection("New Releases", nowPlaying, onMovieClick)
                                MovieSection("Popular Movies", popular, onMovieClick)
                                MovieSection("Top Rated", topRated, onMovieClick)
                                MovieSection("Action", actionAdventureMovies, onMovieClick)
                                MovieSection("Comedy", comedyMovies, onMovieClick)
                                MovieSection("Horror", thrillerHorrorMovies, onMovieClick)
                            }
                            "Series" -> {
                                MovieSection("Popular TV", popularTV, onMovieClick)
                                MovieSection("Top Rated TV", topRatedTV, onMovieClick)
                                MovieSection("Trending TV", trendingTv, onMovieClick)
                                MovieSection("K-Drama", kDramaSpotlightTv, onMovieClick)
                            }
                            "Anime" -> {
                                MovieSection("Anime Spotlight", animeSpotlightTv, onMovieClick)
                                MovieSection("Trending Anime", trendingAnimeThisWeekTv, onMovieClick)
                                MovieSection("Action Anime", actionAnimeTv, onMovieClick)
                            }
                            "Hindi" -> {
                                MovieSection("Hindi Dubbed For You", hindiDubbedMovies, onMovieClick)
                                MovieSection("Popular Hindi", popularHindiMovies, onMovieClick)
                                MovieSection("Top Rated Hindi", topRatedHindiMovies, onMovieClick)
                            }
                            "K-Drama" -> {
                                MovieSection("K-Drama Spotlight", kDramaSpotlightTv, onMovieClick)
                                MovieSection("Popular Korean TV", popularKoreanTv, onMovieClick)
                            }
                            "Trending" -> {
                                MovieSection("Trending Now", trending, onMovieClick)
                                MovieSection("Trending TV", trendingTv, onMovieClick)
                            }
                            "New" -> {
                                MovieSection("New Releases", nowPlaying, onMovieClick)
                            }
                            "Top Rated" -> {
                                MovieSection("Critically Acclaimed", criticallyAcclaimedMovies, onMovieClick)
                                MovieSection("Hidden Gems", hiddenGemsMovies, onMovieClick)
                            }
                            "Genres" -> {
                                MovieSection("Action", actionAdventureMovies, onMovieClick)
                                MovieSection("Comedy", comedyMovies, onMovieClick)
                                MovieSection("Horror", thrillerHorrorMovies, onMovieClick)
                                MovieSection("Family", familyKidsMovies, onMovieClick)
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

                // When selectedCategory == "Live", show grouped live events
                if (selectedCategory == "Live") {
                    if (liveEventsError) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No live events available. Check your internet connection.", color = Color.White)
                            }
                        }
                    } else if (liveEventsMap.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFE50914))
                            }
                        }
                    } else {
                        liveEventsMap.forEach { (sportName, events) ->
                            item {
                                LiveEventsSection(
                                    title = sportName,
                                    liveEvents = events,
                                    onLiveClick = onLiveClick
                                )
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
            val isSelected = category == selectedCategory
            Surface(
                modifier = Modifier.clickable { onCategorySelected(category) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) Color(0xFFE50914) else Color(0xFF1A1A1A),
                border = if (isSelected) null else BorderStroke(1.dp, Color(0xFF333333))
            ) {
                Text(
                    text = category,
                    color = if (isSelected) Color.White else Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun HeroBanner(movies: List<MovieResult>, onMovieClick: (MovieResult) -> Unit, pagerState: androidx.compose.foundation.pager.PagerState) {
    Column {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp)
        ) { page ->
            val movie = movies[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMovieClick(movie) }
            ) {
                AsyncImage(
                    model = "https://image.tmdb.org/t/p/original${movie.poster_path}",
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        movie.title ?: movie.name ?: "",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFE50914),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "TOP 10",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Number 1 in India Today",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // Pager Indicator
        Row(
            Modifier
                .height(20.dp)
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(movies.size) { iteration ->
                val color = if (pagerState.currentPage == iteration) Color(0xFFE50914) else Color.Gray
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(6.dp)
                )
            }
        }
    }
}

@Composable
fun MovieSection(title: String, movies: List<MovieResult>, onMovieClick: (MovieResult) -> Unit) {
    if (movies.isEmpty()) return
    
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                MovieCard(movie, onMovieClick)
            }
        }
    }
}

@Composable
fun MovieCard(movie: MovieResult, onClick: (MovieResult) -> Unit) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick(movie) }
    ) {
        AsyncImage(
            model = "${TMDBApi.IMAGE_BASE_URL}${movie.poster_path}",
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(160.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        )
        Text(
            movie.title ?: movie.name ?: "",
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

@Composable
fun LiveEventsSection(
    title: String,
    liveEvents: List<KinoSearchResult>,
    onLiveClick: (KinoSearchResult) -> Unit
) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            "🔴 $title",
            color = Color(0xFFE50914),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(liveEvents) { event ->
                LiveEventCard(event, onLiveClick)
            }
        }
    }
}

@Composable
fun LiveEventCard(event: KinoSearchResult, onClick: (KinoSearchResult) -> Unit) {
    Column(
        modifier = Modifier.width(160.dp).clickable { onClick(event) }
    ) {
        Box(
            modifier = Modifier
                .size(width = 160.dp, height = 100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A1A))
        ) {
            AsyncImage(
                model = event.posterUrl ?: "",
                contentDescription = event.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // LIVE badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE50914))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            event.name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
