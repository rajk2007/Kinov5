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
    
    val categories = listOf("All", "Live", "Movies", "Series", "Anime", "Hindi")
    var selectedCategory by remember { mutableStateOf(categories[0]) }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory == "Live" && liveEventsMap.isEmpty()) {
            viewModel.loadLiveEvents()
        }
    }

    val pagerState = rememberPagerState(pageCount = { trending.take(5).size })
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
                                    HeroBanner(movies = trending.take(5), onMovieClick = onMovieClick, pagerState = pagerState)
                                }
                                MovieSection("🔥 Trending Now", trending, onMovieClick)

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
                            "Live" -> {
                                // Handled in a separate logic below to avoid nesting item inside item
                            }
                            "Movies" -> {
                                MovieSection("New Releases", viewModel.nowPlaying.collectAsState().value, onMovieClick)
                                MovieSection("Popular Movies", popular, onMovieClick)
                                MovieSection("Top Rated", topRated, onMovieClick)
                                MovieSection("Action", viewModel.actionAdventureMovies.collectAsState().value, onMovieClick)
                                MovieSection("Comedy", viewModel.comedyMovies.collectAsState().value, onMovieClick)
                                MovieSection("Horror", viewModel.thrillerHorrorMovies.collectAsState().value, onMovieClick)
                            }
                            "Series" -> {
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
                            "Hindi" -> {
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

                // When selectedCategory == "Live", show grouped live events
                if (selectedCategory == "Live") {
                    if (liveEventsMap.isEmpty()) {
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
            val isSelected = selectedCategory == category
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color(0xFFE50914) else Color(0x22FFFFFF))
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    category,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
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
    // Guaranteed Auto-Scroll
    LaunchedEffect(Unit) {
        while (movies.size > 1) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % movies.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    // ROOT BOX
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .height(320.dp) // Reduced height for better ratio
    ) {
        // HORIZONTAL PAGER (Child 1)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = movies[page % movies.size]
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxSize().clickable { onMovieClick(movie) }
            ) {
                Box {
                    val backdropUrl = movie.backdrop_path?.let { "https://image.tmdb.org/t/p/original$it" }
                        ?: movie.poster_path?.let { "https://image.tmdb.org/t/p/original$it" }
                        ?: ""

                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = movie.displayTitle(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
        // Premium Gradient Overlay
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC080808), Color(0xFF080808)),
                    startY = 100f
                )
            )
        )
        
        // Content (Text)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .padding(bottom = 24.dp) // Extra padding so it doesn't mix with dots
        ) {
            Text(
                movie.displayTitle(),
                color = Color.White,
                fontSize = 26.sp, // Reduced from 32.sp to 26.sp
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            
            // Metadata Row (Year, Rating, Genre)
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
                Text(getGenreName(movie.genre_ids?.firstOrNull()), color = Color.Gray, fontSize = 14.sp)
            }
        }
                }
            }
        }

        // INDICATOR DOTS (Child 2 - direct child of Box, so .align works)
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
                        .size(8.dp)
                )
            }
        }
    }
}

fun getGenreName(genreId: Int?): String {
    return when (genreId) {
        28 -> "Action"
        12 -> "Adventure"
        16 -> "Animation"
        35 -> "Comedy"
        80 -> "Crime"
        18 -> "Drama"
        27 -> "Horror"
        9648 -> "Mystery"
        10749 -> "Romance"
        878 -> "Sci-Fi"
        53 -> "Thriller"
        10752 -> "War"
        else -> "Movie"
    }
}

@Composable
fun MovieSection(title: String, movies: List<MovieResult>, onMovieClick: (MovieResult) -> Unit) {
    if (movies.isEmpty()) return
    
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(movies) { movie ->
                MovieCard(movie, onMovieClick)
            }
        }
    }
}

@Composable
fun Top10Section(title: String, movies: List<MovieResult>, onMovieClick: (MovieResult) -> Unit) {
    if (movies.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(movies.size) { index ->
                Box(modifier = Modifier.width(140.dp)) {
                    Text(
                        text = (index + 1).toString(),
                        fontSize = 100.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset(x = (-10).dp, y = 10.dp)
                            .graphicsLayer(alpha = 0.5f)
                    )
                    MovieCard(
                        movie = movies[index],
                        onClick = onMovieClick,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

@Composable
fun MovieCard(movie: MovieResult, onClick: (MovieResult) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
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
