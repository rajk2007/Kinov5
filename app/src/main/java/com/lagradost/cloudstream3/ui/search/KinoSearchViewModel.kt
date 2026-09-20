package com.lagradost.cloudstream3.ui.search

import android.content.Context
import android.util.Log
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

/** Scores titles without changing the provider's returned data. */
fun getSearchRelevanceScore(title: String, query: String): Int {
    val titleLower = title.lowercase().trim()
    val queryLower = query.lowercase().trim()
    if (queryLower.isEmpty()) return 0
    return when {
        titleLower == queryLower -> 100
        titleLower.startsWith(queryLower) -> 90
        titleLower.contains(" $queryLower") || titleLower.contains("$queryLower ") -> 80
        titleLower.contains(queryLower) -> 70
        else -> 0
    }
}

fun getProviderPriority(apiName: String): Int {
    val name = apiName.lowercase()
    return when {
        name.contains("bingecloud") || name.contains("binge cloud") -> 1
        else -> 2
    }
}

fun sortKinoSearchResults(results: List<KinoSearchResult>, query: String): List<KinoSearchResult> =
    results.sortedWith(
        compareByDescending<KinoSearchResult> { getSearchRelevanceScore(it.name, query) }
            .thenBy { getProviderPriority(it.apiName) }
            .thenBy { it.name.lowercase() }
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
    private val preferences by lazy { CloudStreamApp.context?.getSharedPreferences("kino_search", Context.MODE_PRIVATE) }

    init {
        _recentSearches.value = loadRecentSearches()
        viewModelScope.launch(Dispatchers.IO) { loadTrending() }
        viewModelScope.launch {
            query.collect { value ->
                if (value.trim().length >= 2) searchProviders(value.trim()) else _results.value = emptyList()
            }
        }
    }

    fun submitQuery() {
        val value = query.value.trim()
        if (value.length < 2) return
        _recentSearches.value = (listOf(value) + _recentSearches.value.filterNot { it.equals(value, true) }).take(10)
        preferences?.edit()?.putStringSet("recent_searches", _recentSearches.value.toSet())?.apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        preferences?.edit()?.remove("recent_searches")?.apply()
    }

    private fun loadRecentSearches(): List<String> = preferences?.getStringSet("recent_searches", emptySet())?.toList()?.sorted() ?: emptyList()

    private suspend fun loadTrending() {
        runCatching {
            TMDBApi.create().getPopular(TMDBApi.API_KEY).results.take(10).map { movie ->
                KinoSearchResult(movie.displayTitle(), movie.providerUrl ?: "", movie.providerApiName ?: "TMDB",
                    movie.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" },
                    if (movie.media_type.equals("tv", true)) TvType.TvSeries else TvType.Movie,
                    movie.release_date?.take(4) ?: movie.first_air_date?.take(4), null)
            }
        }.onSuccess { _trending.value = it }
    }

    private suspend fun searchProviders(searchQuery: String) {
        _isLoading.value = true
        _results.value = emptyList()
        var bingeCloud = APIHolder.apis.firstOrNull { api ->
            api.name.contains("BingeCloud", true) || api.name.contains("Binge Cloud", true)
        }
        repeat(60) {
            if (bingeCloud != null) return@repeat
            kotlinx.coroutines.delay(500)
            bingeCloud = APIHolder.apis.firstOrNull { api ->
                api.name.contains("BingeCloud", true) || api.name.contains("Binge Cloud", true)
            }
        }
        val providers = listOfNotNull(bingeCloud)
        Log.d("SEARCH_DEBUG", "Search providers: ${providers.map { it.name }}")
        val masterList = mutableListOf<KinoSearchResult>()
        try {
            coroutineScope {
                providers.map { api ->
                    launch(Dispatchers.IO) {
                        val providerResults = searchFromProvider(api, searchQuery)
                        if (providerResults.isNotEmpty()) mutex.withLock {
                            masterList.addAll(providerResults)
                            _results.value = sortKinoSearchResults(masterList, searchQuery)
                        }
                    }
                }.forEach { it.join() }
            }
        } finally {
            _results.value = sortKinoSearchResults(masterList, searchQuery)
            _isLoading.value = false
        }
    }

    private suspend fun searchFromProvider(api: com.lagradost.cloudstream3.MainAPI, searchQuery: String): List<KinoSearchResult> {
        return try {
            Log.d("SEARCH_DEBUG", "Searching ${api.name} for: $searchQuery")
            val repository = APIRepository(api)
            val resource = withTimeoutOrNull(8_000L) { repository.search(searchQuery, 1) }
            var results = (resource as? Resource.Success)?.value?.items.orEmpty()
            Log.d("SEARCH_DEBUG", "${api.name} returned ${results.size} results")
            results.map { response ->
                KinoSearchResult(response.name, response.url, response.apiName.ifBlank { api.name }, response.posterUrl,
                    response.type, null, response.quality?.name)
            }
        } catch (error: Exception) {
            Log.e("SEARCH_DEBUG", "${api.name} search error: ${error.message}", error)
            emptyList()
        }
    }
}
