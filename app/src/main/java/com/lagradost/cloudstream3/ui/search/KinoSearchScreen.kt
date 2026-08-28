package com.lagradost.cloudstream3.ui.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.TvType

private val SearchBackground = Color(0xFF08090B)
private val SearchSurface = Color(0xFF17191D)
private val SearchAccent = Color(0xFFE50914)

@Composable
fun KinoSearchScreen(
    viewModel: KinoSearchViewModel = viewModel(),
    initialQuery: String = "",
    onResultClick: (KinoSearchResult) -> Unit = {},
    onExploreAllResults: () -> Unit = {}
) {
    val context = LocalContext.current
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val trending by viewModel.trending.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categories = listOf("Movies" to "M", "TV Shows" to "TV", "Anime" to "A", "K-Drama" to "K", "Sports" to "S")

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.query.value = initialQuery
            viewModel.submitQuery()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchBackground)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(22.dp))
        Text("Search", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Find movies, shows, anime and more.", color = Color(0xFF9B9DA3), fontSize = 15.sp)
        Spacer(Modifier.height(22.dp))

        TextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What do you want to watch?", color = Color(0xFF777A82)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFB6B8BF)) },
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { viewModel.query.value = "" }) { Text("Clear", color = Color(0xFFB6B8BF)) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.submitQuery() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SearchSurface,
                unfocusedContainerColor = SearchSurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = SearchAccent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(20.dp),
            singleLine = true
        )
        Spacer(Modifier.height(24.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SearchAccent)
            }
        } else if (query.isBlank()) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (recentSearches.isNotEmpty()) {
                    item {
                        SectionHeading("Recent Searches", "Clear All") { viewModel.clearRecentSearches() }
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recentSearches) { search ->
                                SearchChip(search) {
                                    viewModel.query.value = search
                                    viewModel.submitQuery()
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Browse Categories", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(categories) { (name, icon) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { viewModel.query.value = name; viewModel.submitQuery() }) {
                                Box(Modifier.size(56.dp).clip(CircleShape).background(SearchSurface), contentAlignment = Alignment.Center) {
                                    Text(icon, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Spacer(Modifier.height(7.dp))
                                Text(name, color = Color(0xFFB9BBC2), fontSize = 12.sp)
                            }
                        }
                    }
                }
                item {
                    Text("Trending Searches", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                items(trending) { result ->
                    ProviderResultCard(result = result, onClick = { viewModel.query.value = result.name; viewModel.submitQuery() })
                }
            }
        } else if (results.isEmpty()) {
            Text("No results found", color = Color(0xFF9B9DA3), fontSize = 16.sp)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(results) { result -> ProviderResultCard(result = result, onClick = { onResultClick(result) }) }
                item {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = onExploreAllResults, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Text("Explore all results")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAction) { Text(action, color = SearchAccent, fontSize = 13.sp) }
    }
}

@Composable
private fun SearchChip(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(SearchSurface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
fun ProviderResultCard(result: KinoSearchResult, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "result_scale")
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).scale(scale).clickable(interactionSource = interactionSource, indication = null, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = result.posterUrl ?: "", contentDescription = result.name, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp, 120.dp).clip(RoundedCornerShape(10.dp)).background(SearchSurface))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(result.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 7.dp)) {
                val typeText = when (result.type) { TvType.Movie -> "Movie"; TvType.TvSeries -> "TV"; TvType.Anime -> "Anime"; TvType.AsianDrama -> "Drama"; else -> result.type?.name ?: "Video" }
                Badge(typeText, Color(0xFF33363D))
                result.quality?.let { Badge(it, SearchAccent) }
            }
        }
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}
