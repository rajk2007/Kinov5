package com.lagradost.cloudstream3.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
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
        name.contains("castletv") || name.contains("castle tv") -> 2
        name.contains("cinetv") || name.contains("cine tv") -> 3
        name.contains("pikashow") -> 4
        name.contains("multimovies") -> 5
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
    val topProviders = mutableListOf<KinoSearchResult>()
    val otherResults = mutableListOf<KinoSearchResult>()

    val allowedProviders = listOf("moviebox", "castletv", "cinetv", "pikashow", "multimovies")
    val providers = APIHolder.apis.filter { api -> 
        allowedProviders.any { api.name.lowercase().contains(it) }
    }.sortedBy { api -> getProviderPriority(api.name) }

    coroutineScope {
        // Phase 1: Search Top 3 Providers (MovieBox, CastleTV, CineTV) in parallel with 1.5s timeout
        val top3 = providers.take(3)
        val deferredTop = top3.map { api ->
            async(Dispatchers.IO) {
                try {
                    val repo = APIRepository(api)
                    val resource = withTimeoutOrNull(1500L) { repo.search(query, page = 1) }
                    if (resource is Resource.Success) {
                        resource.value.items.map { response ->
                            KinoSearchResult(response.name, response.url, response.apiName, response.posterUrl, response.type, null, response.quality?.name)
                        }
                    } else emptyList()
                } catch (e: Exception) { emptyList() }
            }
        }
        
        deferredTop.awaitAll().forEach { topProviders.addAll(it) }
        
        // Interleave the top 3 results so they alternate
        val movieBoxList = topProviders.filter { it.apiName.lowercase().contains("moviebox") }
        val castleTvList = topProviders.filter { it.apiName.lowercase().contains("castletv") }
        val cineTvList = topProviders.filter { it.apiName.lowercase().contains("cinetv") }
        val remainingTop = topProviders.filter { !it.apiName.lowercase().contains("moviebox") && !it.apiName.lowercase().contains("castletv") && !it.apiName.lowercase().contains("cinetv") }

        val interleavedList = mutableListOf<KinoSearchResult>()
        val maxIndex = maxOf(movieBoxList.size, castleTvList.size, cineTvList.size)

        for (i in 0 until maxIndex) {
            if (i < movieBoxList.size) interleavedList.add(movieBoxList[i])
            if (i < castleTvList.size) interleavedList.add(castleTvList[i])
            if (i < cineTvList.size) interleavedList.add(cineTvList[i])
        }
        interleavedList.addAll(remainingTop)

        // Update UI immediately with interleaved top 3 results
        _results.value = interleavedList

        // Phase 2: Search remaining providers in parallel with 2s timeout
        val rest = providers.drop(3)
        val deferredRest = rest.map { api ->
            async(Dispatchers.IO) {
                try {
                    val repo = APIRepository(api)
                    val resource = withTimeoutOrNull(2000L) { repo.search(query, page = 1) }
                    if (resource is Resource.Success) {
                        resource.value.items.map { response ->
                            KinoSearchResult(response.name, response.url, response.apiName, response.posterUrl, response.type, null, response.quality?.name)
                        }
                    } else emptyList()
                } catch (e: Exception) { emptyList() }
            }
        }
        
        deferredRest.awaitAll().forEach { otherResults.addAll(it) }
        
        // Combine and sort final results
        val finalResults = (interleavedList + otherResults.sortedBy { getProviderPriority(it.apiName) })
        _results.value = finalResults
    }
    
    _isLoading.value = false
}
}
