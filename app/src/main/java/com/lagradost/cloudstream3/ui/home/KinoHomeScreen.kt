package com.lagradost.cloudstream3.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.ui.search.KinoSearchResult
import kotlinx.coroutines.delay

private val KinoBackground = Color(0xFF08090C)
private val KinoSurface = Color(0xFF14161C)
private val KinoRed = Color(0xFFE50914)

@Composable
fun KinoHomeScreen(
    viewModel: KinoHomeViewModel = viewModel(),
    onMovieClick: (MovieResult) -> Unit = {},
    onLiveClick: (KinoSearchResult) -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    val rows by viewModel.homeRows.collectAsState()
    val heroItems by viewModel.heroBannerItems.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val networkState by viewModel.networkState.collectAsState()

    Surface(color = KinoBackground, modifier = Modifier.fillMaxSize()) {
        if (loading && rows.isEmpty()) {
            LoadingHome()
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                item { HomeHeader(onSearchClick) }
                if (heroItems.isNotEmpty()) item { HeroBanner(heroItems, onMovieClick) }
                if (networkState != KinoHomeViewModel.NetworkState.Online && !loading) {
                    item { NetworkHint(networkState) }
                }
                items(rows, key = { it.sectionType.name }) { row ->
                    if (row.sectionType == HomeSectionType.TOP_10_TODAY) {
                        Top10Section(row, onMovieClick)
                    } else {
                        MovieSection(row, onMovieClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(onSearchClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("KINO", color = KinoRed, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Your next obsession", color = Color.White.copy(alpha = .55f), fontSize = 12.sp)
        }
        IconButton(onClick = onSearchClick) { Icon(Icons.Filled.Search, "Search", tint = Color.White) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroBanner(items: List<HeroBannerItem>, onMovieClick: (MovieResult) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    LaunchedEffect(items) {
        if (items.size > 1) while (true) {
            delay(5_000)
            pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
        }
    }
    Column(modifier = Modifier.padding(bottom = 18.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 10.dp,
            modifier = Modifier.fillMaxWidth().height(330.dp)
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).clickable { onMovieClick(item.movie) }
            ) {
                AsyncImage(
                    model = item.backdropUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE08090C)))
                    )
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                    Text("NEW RELEASE", color = KinoRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(item.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2)
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        item.year?.let { Text(it, color = Color.White.copy(alpha = .72f), fontSize = 13.sp) }
                        item.rating?.let { Text("★ $it", color = Color(0xFFFFD54F), fontSize = 13.sp) }
                        item.genre?.let { Text(it, color = Color.White.copy(alpha = .72f), fontSize = 13.sp) }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            items.indices.forEach { index ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (pagerState.currentPage == index) 18.dp else 6.dp, 6.dp)
                        .clip(CircleShape).background(if (pagerState.currentPage == index) KinoRed else Color.White.copy(alpha = .35f))
                )
            }
        }
    }
}

@Composable
private fun MovieSection(row: HomeRow, onMovieClick: (MovieResult) -> Unit) {
    SectionShell(row) {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(row.items, key = { "${it.providerApiName}:${it.providerUrl ?: it.id}" }) { movie -> MovieCard(movie, onMovieClick) }
        }
    }
}

@Composable
private fun Top10Section(row: HomeRow, onMovieClick: (MovieResult) -> Unit) {
    SectionShell(row) {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(row.items.take(10), key = { "top:${it.providerApiName}:${it.providerUrl ?: it.id}" }) { movie ->
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.width(142.dp)) {
                    Text((row.items.indexOf(movie) + 1).toString(), color = Color.White.copy(alpha = .9f), fontSize = 58.sp, fontWeight = FontWeight.Black, modifier = Modifier.offset(x = 4.dp, y = 6.dp))
                    MovieCard(movie, onMovieClick, Modifier.width(104.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionShell(row: HomeRow, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (row.isPersonalized) {
                Text("  FOR YOU", color = KinoRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        content()
    }
}

@Composable
private fun MovieCard(movie: MovieResult, onClick: (MovieResult) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .94f else 1f, label = "cardScale")
    Column(modifier = modifier.width(112.dp).scale(scale).clickable(interactionSource = interaction, indication = null) { onClick(movie) }) {
        AsyncImage(
            model = movie.poster_path,
            contentDescription = movie.displayTitle(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(10.dp)).background(KinoSurface)
        )
        Text(movie.displayTitle(), color = Color.White.copy(alpha = .9f), fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
    }
}

@Composable
private fun NetworkHint(state: KinoHomeViewModel.NetworkState) {
    val text = if (state == KinoHomeViewModel.NetworkState.Offline) "Offline mode · Showing what is cached" else "Connection is slow · Showing available releases"
    Text(text, color = Color.White.copy(alpha = .6f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
}

@Composable
private fun LoadingHome() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = KinoRed)
        Spacer(Modifier.height(14.dp))
        Text("Curating your home", color = Color.White.copy(alpha = .7f))
    }
}
