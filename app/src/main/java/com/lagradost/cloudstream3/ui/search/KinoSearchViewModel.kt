package com.lagradost.cloudstream3.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.api.TMDBApi
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

data class KinoSearchResult(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
    val type: TvType?,
    val year: String?,
    val quality: String?
)

class KinoSearchViewModel : ViewModel() {
    private val _results = MutableStateFlow<List<KinoSearchResult>>(emptyList())
    val results: StateFlow<List<KinoSearchResult>> = _results

    private val _trending = MutableStateFlow<List<KinoSearchResult>>(emptyList())
    val trending: StateFlow<List<KinoSearchResult>> = _trending

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    var query = MutableStateFlow("")

    private val mutex = Mutex()
    private val preferences by lazy {
        CloudStreamApp.context?.getSharedPreferences("kino_search", Context.MODE_PRIVATE)
    }

    private fun getProviderPriority(apiName: String): Int {
        val name = apiName.lowercase()
        return when {
            name.contains("moviebox") -> 1
            name.contains("castle") -> 2
            name.contains("netmirror") || name.contains("netflix") -> 3
            name.contains("pikashow") -> 4
            else -> 100
        }
    }

    init {
        _recentSearches.value = loadRecentSearches()
        viewModelScope.launch(Dispatchers.IO) { loadTrending() }
        viewModelScope.launch {
            query.collect { q ->
                if (q.length >= 2) searchProviders(q) else _results.value = emptyList()
            }
        }
    }

    fun submitQuery() {
        val value = query.value.trim()
        if (value.length < 2) return
        val updated = listOf(value) + _recentSearches.value.filterNot { it.equals(value, true) }
        _recentSearches.value = updated.take(10)
        preferences?.edit()?.putStringSet("recent_searches", _recentSearches.value.toSet())?.apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        preferences?.edit()?.remove("recent_searches")?.apply()
    }

    private fun loadRecentSearches(): List<String> = preferences?.getStringSet("recent_searches", emptySet())
        ?.toList()
        ?.sorted() ?: emptyList()

    private suspend fun loadTrending() {
        runCatching {
            TMDBApi.create().getPopular(TMDBApi.API_KEY).results.take(10).map { movie ->
                KinoSearchResult(
                    name = movie.displayTitle(),
                    url = movie.providerUrl ?: "",
                    apiName = movie.providerApiName ?: "TMDB",
                    posterUrl = movie.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    type = if (movie.media_type.equals("tv", true)) TvType.TvSeries else TvType.Movie,
                    year = movie.release_date?.take(4) ?: movie.first_air_date?.take(4),
                    quality = null
                )
            }
        }.onSuccess { _trending.value = it }
    }

    private suspend fun searchProviders(query: String) {
        _isLoading.value = true
        _results.value = emptyList()
        val masterList = mutableListOf<KinoSearchResult>()
        val priorityNames = listOf("moviebox", "castle", "netmirror", "netflix", "pikashow")
        val providers = APIHolder.apis.sortedBy { api ->
            val index = priorityNames.indexOfFirst { api.name.lowercase().contains(it) }
            if (index >= 0) index else Int.MAX_VALUE
        }
        if (providers.isEmpty()) {
            _isLoading.value = false
            return
        }
        coroutineScope {
            providers.forEach { api ->
                launch(Dispatchers.IO) {
                    try {
                        val resource = withTimeoutOrNull(6000L) { APIRepository(api).search(query, page = 1) }
                        if (resource is Resource.Success) {
                            val mapped = resource.value.items.map { response ->
                                KinoSearchResult(response.name, response.url, response.apiName, response.posterUrl, response.type, null, response.quality?.name)
                            }
                            mutex.withLock {
                                masterList.addAll(mapped)
                                _results.value = masterList.sortedBy { getProviderPriority(it.apiName) }
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        }
        _isLoading.value = false
    }
}
