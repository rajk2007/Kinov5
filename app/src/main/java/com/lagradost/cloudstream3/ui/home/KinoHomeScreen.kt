
package com.lagradost.cloudstream3.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.ui.home.KinoHomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KinoHomeScreen(viewModel: KinoHomeViewModel = viewModel()) {
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val popularMovies by viewModel.popularMovies.collectAsState()
    val topRatedMovies by viewModel.topRatedMovies.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val popularTV by viewModel.popularTV.collectAsState()
    val topRatedTV by viewModel.topRatedTV.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = { TopNavBar() },
        bottomBar = { BottomNavBar() }
    ) { paddingValues ->
        if (isLoading) {
            // Skeleton loading placeholders or shimmer effect
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${error}", color = Color.Red)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A))
                    .padding(paddingValues)
            ) {
                item { HeroBanner(trendingMovies) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { ContentRow("Continue Watching", trendingMovies) } // Placeholder
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { ContentRow("Trending Now", trendingMovies) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { Top10TodayRow(topRatedMovies) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { ContentRow("New Releases", nowPlaying, showNewBadge = true) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { ContentRow("Hindi Dubbed For You", popularMovies) } // Placeholder
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { ContentRow("Anime Spotlight", popularTV) } // Placeholder
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { PremiumBanner() }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { ContentRow("Popular Movies", popularMovies) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { ContentRow("Popular TV Shows", popularTV) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
                item { ContentRow("Weekend Picks", upcoming) }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopNavBar() {
    TopAppBar(
        title = { Text("KINO", color = Color(0xFFE50914), fontWeight = FontWeight.Bold, fontSize = 24.sp) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        actions = {
            IconButton(onClick = { /*TODO: Handle search*/ }) {
                Icon(Icons.Filled.Search, "Search", tint = Color.White)
            }
            IconButton(onClick = { /*TODO: Handle notifications*/ }) {
                BadgedBox(badge = { Badge { Text("9+") } }) {
                    Icon(Icons.Filled.Notifications, "Notifications", tint = Color.White)
                }
            }
            // Profile Avatar Placeholder
            Box(modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50)))
            {
                AsyncImage(model = "https://via.placeholder.com/150", contentDescription = "Profile", contentScale = ContentScale.Crop)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroBanner(movies: List<MovieResult>) {
    if (movies.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = 0) { movies.size }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
            pagerState.animateScrollToPage(nextPage)
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier
        .fillMaxWidth()
        .height(400.dp))
    {
        page ->
        val movie = movies[page]
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/original" + movie.backdrop_path,
                contentDescription = movie.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Animated gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                            startY = 300f
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .align(Alignment.BottomStart),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = movie.displayTitle,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = movie.year, color = Color(0xFFB3B3B3), fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "• 2h 30m •", color = Color(0xFFB3B3B3), fontSize = 12.sp) // Placeholder for runtime
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "★", color = Color(0xFFFFD700), fontSize = 12.sp)
                    Text(text = "${movie.vote_average?.let { "%.1f".format(it) } ?: "N/A"}", color = Color(0xFFFFD700), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = movie.overview ?: "No description available.",
                    color = Color(0xFFB3B3B3),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Button(
                        onClick = { /*TODO: Handle Play*/ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Play", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedButton(
                        onClick = { /*TODO: Handle More Info*/ },
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("More Info", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ContentRow(title: String, movies: List<MovieResult>, showNewBadge: Boolean = false) {
    if (movies.isEmpty()) return

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "See All ›",
                color = Color(0xFFB3B3B3),
                fontSize = 14.sp,
                modifier = Modifier.clickable { /*TODO: Handle See All*/ }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(movies) {
                movie ->
                MovieCard(movie, showNewBadge)
            }
        }
    }
}

@Composable
fun MovieCard(movie: MovieResult, showNewBadge: Boolean) {
    Card(
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp), ambientColor = Color.Black.copy(alpha = 0.5f))
            .clickable { /*TODO: Handle movie click*/ },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = "https://image.tmdb.org/t/p/w500" + movie.poster_path,
                contentDescription = movie.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (showNewBadge) {
                Text(
                    text = "NEW",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            // Rating badge
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "★", color = Color(0xFFFFD700), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "${movie.vote_average?.let { "%.1f".format(it) } ?: "N/A"}", color = Color(0xFFFFD700), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun Top10TodayRow(movies: List<MovieResult>) {
    if (movies.isEmpty()) return

    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Top 10 Today",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "See All ›",
                color = Color(0xFFB3B3B3),
                fontSize = 14.sp,
                modifier = Modifier.clickable { /*TODO: Handle See All*/ }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        val top10Movies = movies.take(10)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(top10Movies.size) { index ->
                val movie = top10Movies[index]
                Top10MovieCard(movie, index + 1)
            }
        }
    }
}

@Composable
fun Top10MovieCard(movie: MovieResult, rank: Int) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp), ambientColor = Color.Black.copy(alpha = 0.5f))
            .clickable { /*TODO: Handle movie click*/ },
        contentAlignment = Alignment.BottomStart
    ) {
        AsyncImage(
            model = "https://image.tmdb.org/t/p/w500" + movie.poster_path,
            contentDescription = movie.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
        )
        Text(
            text = "#$rank",
            fontSize = 48.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFFFFD700),
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
    }
}

@Composable
fun PremiumBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 16.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFE50914))), RoundedCornerShape(12.dp))
            .clickable { /*TODO: Handle upgrade click*/ },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text("👑", fontSize = 48.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("KINO Premium", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Upgrade for exclusive content", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun BottomNavBar() {
    NavigationBar(
        containerColor = Color(0x66FFFFFF), // Glassmorphism effect
        modifier = Modifier.clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        val items = listOf("Home", "Search", "Library", "Profile")
        val icons = listOf(Icons.Filled.Home, Icons.Filled.Search, Icons.Filled.Menu, Icons.Filled.Person)

        items.forEachIndexed { index, item ->
            NavigationBarItem(
                icon = { Icon(icons[index], contentDescription = item) },
                label = { Text(item) },
                selected = index == 0, // Home is selected by default
                onClick = { /*TODO: Handle navigation*/ },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFFE50914),
                    selectedTextColor = Color(0xFFE50914),
                    unselectedIconColor = Color.White.copy(alpha = 0.7f),
                    unselectedTextColor = Color.White.copy(alpha = 0.7f),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
