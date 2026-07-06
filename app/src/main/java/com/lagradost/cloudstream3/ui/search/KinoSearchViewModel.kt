package com.lagradost.cloudstream3.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    var query = MutableStateFlow("")

    private fun getProviderPriority(apiName: String): Int {
        val name = apiName.lowercase()
        return when {
            name.contains("moviebox") -> 1
            name.contains("castle") -> 2
            name.contains("cine") -> 3
            name.contains("dooflix") -> 4
            name.contains("netmirror") -> 5
            name.contains("pikashow") -> 6
            name.contains("multimovies") -> 7
            else -> Int.MAX_VALUE
        }
    }

    init {
        viewModelScope.launch {
            query.collect { q ->
                if (q.length >= 2) {
                    searchProviders(q)
                } else {
                    _results.value = emptyList()
                }
            }
        }
    }

    private suspend fun searchProviders(query: String) {
        _isLoading.value = true
        _results.value = emptyList() // Clear previous results immediately
        
        val masterList = mutableListOf<KinoSearchResult>()
        val allowedProviders = listOf("moviebox", "castle", "cine", "dooflix", "netmirror", "pikashow", "multimovies")
        val providers = APIHolder.apis.filter { api -> 
            allowedProviders.any { api.name.lowercase().contains(it) }
        }

        coroutineScope {
            providers.forEach { api ->
                launch(Dispatchers.IO) {
                    try {
                        val repo = APIRepository(api)
                        val resource = withTimeoutOrNull(6000L) { repo.search(query, page = 1) }
                        if (resource is Resource.Success) {
                            val mapped = resource.value.items.map { response ->
                                KinoSearchResult(
                                    name = response.name,
                                    url = response.url,
                                    apiName = response.apiName,
                                    posterUrl = response.posterUrl,
                                    type = response.type,
                                    year = null,
                                    quality = response.quality?.name
                                )
                            }
                            // Add results and update UI INSTANTLY as each provider finishes
                            synchronized(masterList) {
                                masterList.addAll(mapped)
                                _results.value = masterList.sortedBy { getProviderPriority(it.apiName) }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore failures, let other providers populate
                    }
                }
            }
        }
        _isLoading.value = false
    }
}
