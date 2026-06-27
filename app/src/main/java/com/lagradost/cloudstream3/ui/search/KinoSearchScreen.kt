package com.lagradost.cloudstream3.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
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

    fun performSearch() {
        val q = query.value
        if (q.isNotEmpty()) {
            viewModelScope.launch {
                try {
                    _results.value = api.searchMulti(TMDBApi.API_KEY, q).results
                } catch (e: Exception) {
                    // Handle error silently for now
                }
            }
        }
    }
}

@Composable
fun KinoSearchScreen(viewModel: KinoSearchViewModel = viewModel()) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

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
            placeholder = { Text("Search movies...", color = Color.Gray) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF1A1A1A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.performSearch()
                    keyboardController?.hide()
                }
            ),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(results) { movie ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    val imageUrl: String = if (movie.poster_path != null) {
                        "${TMDBApi.IMAGE_BASE_URL}${movie.poster_path}"
                    } else {
                        ""
                    }
                    
                    val title: String = movie.title ?: "Unknown Title"

                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        modifier = Modifier.size(60.dp, 90.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = Color.White
                    )
                }
            }
        }
    }
}
