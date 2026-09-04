package com.lagradost.cloudstream3.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class KinoSearchResult(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?,
    val type: TvType?,
    val year: String? = null,
    val quality: String? = null
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
    private val preferences by lazy {
        CloudStreamApp.context?.getSharedPreferences("kino_search", Context.MODE_PRIVATE)
    }

    init {
        _recentSearches.value = loadRecentSearches()
        viewModelScope.launch {
            query.collect { value ->
                if (value.length >= 2) search(value) else _results.value = emptyList()
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
        ?.toList()?.sorted() ?: emptyList()

    fun search(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _results.value = emptyList()
            val cncDeferred = async { searchProvider("CNC Verse", query) }
            val cineDeferred = async { searchProvider("CineFreak", query) }
            val cncResults = cncDeferred.await()
            val cineResults = cineDeferred.await()
            _results.value = cncResults + cineResults
            _isLoading.value = false
        }
    }

    private suspend fun searchProvider(apiName: String, query: String): List<KinoSearchResult> {
        return try {
            val api = APIHolder.apis.find { it.name.equals(apiName, ignoreCase = true) } ?: return emptyList()
            val response = APIRepository(api).search(query, page = 1)
            if (response is Resource.Success) {
                response.value.items.map { sr ->
                    KinoSearchResult(
                        name = sr.name,
                        url = sr.url,
                        apiName = sr.apiName,
                        posterUrl = sr.posterUrl,
                        type = sr.type,
                        quality = sr.quality?.name
                    )
                }
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
