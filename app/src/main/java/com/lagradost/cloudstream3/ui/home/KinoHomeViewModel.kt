package com.lagradost.cloudstream3.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.api.MovieResult
import com.lagradost.cloudstream3.api.TMDBApi
import com.lagradost.cloudstream3.mvvm.ioSafe
import com.lagradost.cloudstream3.network.Retrofit2

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KinoHomeViewModel : ViewModel() {
    private val tmdbApi = Retrofit2.api<TMDBApi>("https://api.themoviedb.org/3/")
    private val TMDB_API_KEY = "cf5a2b948bb3cbe03332dc70594b4ba7"

    private val _trendingMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val trendingMovies = _trendingMovies.asStateFlow()

    private val _popularMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val popularMovies = _popularMovies.asStateFlow()

    private val _topRatedMovies = MutableStateFlow<List<MovieResult>>(emptyList())
    val topRatedMovies = _topRatedMovies.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            ioSafe {
                try {
                    val trending = tmdbApi.getTrending(TMDB_API_KEY)
                    val popular = tmdbApi.getPopular(TMDB_API_KEY)
                    val topRated = tmdbApi.getTopRated(TMDB_API_KEY)

                    _trendingMovies.value = trending.results
                    _popularMovies.value = popular.results
                    _topRatedMovies.value = topRated.results
                } catch (e: Exception) {
                    log("TMDB Error: $e")
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
}