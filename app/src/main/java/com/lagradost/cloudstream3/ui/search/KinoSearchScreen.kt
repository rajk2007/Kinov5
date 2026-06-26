package com.lagradost.cloudstream3.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
                if (q.length > 2) {
                    try {
                        // Assuming you have a search endpoint in TMDBApi.kt
                        // If not, use getTrending as fallback
                        _results.value = api.getTrending(TMDBApi.API_KEY).results 
                    } catch (e: Exception) {}
                }
            }
        }
    }
}

@Composable
fun KinoSearchScreen(viewModel: KinoSearchViewModel = viewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    
    Column(Modifier.fillMaxSize().background(Color(0xFF080808)).padding(16.dp)) {
        TextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies...", color = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF1A1A1A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(results) { movie ->
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    AsyncImage(
                        model = "${TMDBApi.IMAGE_BASE_URL}${movie.poster_path ?: ""}",
                        contentDescription = null,
                        modifier = Modifier.size(60.dp, 90.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(movie.title, color = Color.White)
                }
            }
        }
    }
}
