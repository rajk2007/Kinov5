package com.lagradost.cloudstream3.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                if (q.length >= 2) { // Auto-search on 2 characters
                    try {
                        _results.value = api.searchMulti(TMDBApi.API_KEY, q).results
                    } catch (e: Exception) {}
                } else {
                    _results.value = emptyList()
                }
            }
        }
    }
}

@Composable
fun KinoSearchScreen(viewModel: KinoSearchViewModel = viewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080808))
            .padding(16.dp)
    ) {
        TextField(
            value = query,
            onValueChange = { viewModel.query.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search movies, shows...", color = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF1A1A1A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFFE50914)
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(results) { movie ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            android.widget.Toast.makeText(context, "Loading ${movie.getTitle()}...", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val imageUrl: String = if (movie.poster_path != null) {
                        "${TMDBApi.IMAGE_BASE_URL}${movie.poster_path}"
                    } else {
                        ""
                    }
                    
                    val title: String = movie.getTitle()

                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 80.dp, height = 120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1A1A1A))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (movie.release_date != null) {
                            Text(
                                text = movie.release_date.take(4), // Just the year
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
