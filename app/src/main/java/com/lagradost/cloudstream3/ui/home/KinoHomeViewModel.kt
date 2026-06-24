package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.api.TMDBApi
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KinoHomeViewModel : ViewModel() {
    private val tmdbApi = TMDBApi.create()

    private val _trendingMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val trendingMovies: StateFlow<List<MovieResult>> = _trendingMovies.asStateFlow()

    private val _popularMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val popularMovies: StateFlow<List<MovieResult>> = _popularMovies.asStateFlow()

    private val _topRatedMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val topRatedMovies: StateFlow<List<MovieResult>> = _topRatedMovies.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val trending = tmdbApi.getTrending(TMDBApi.API_KEY)
                val popular = tmdbApi.getPopular(TMDBApi.API_KEY)
                val topRated = tmdbApi.getTopRated(TMDBApi.API_KEY)

                _trendingMovies.value = trending.results
                _popularMovies.value = popular.results
                _topRatedMovies.value = topRated.results
            } catch (e: Exception) {
                logError(e)
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() {
        loadData()
    }
}