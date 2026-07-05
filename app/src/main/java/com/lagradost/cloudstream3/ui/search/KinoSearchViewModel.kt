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
        val allResults = mutableListOf<KinoSearchResult>()

        // ONLY search these 5 providers to avoid lag
        val allowedProviders = listOf("moviebox", "castletv", "cinetv", "pikashow", "multimovies")
        val providers = APIHolder.apis.filter { api -> 
            allowedProviders.any { api.name.lowercase().contains(it) }
        }

        coroutineScope {
            // Search all 5 providers IN PARALLEL for maximum speed
            val deferredResults = providers.map { api ->
                async(Dispatchers.IO) {
                    try {
                        val repo = APIRepository(api)
                        // Strict 2-second timeout. If a provider is slow, ignore it.
                        val resource = withTimeoutOrNull(2000L) {
                            repo.search(query, page = 1)
                        }
                        if (resource is Resource.Success) {
                            resource.value.items.map { response ->
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
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }

            // Wait for all parallel searches to finish (max 2 seconds)
            deferredResults.awaitAll().forEach { resultList ->
                allResults.addAll(resultList)
            }
        }

        // Sort final results so MovieBox is 1st, CastleTV is 2nd, CineTV is 3rd
        _results.value = allResults.sortedBy { result ->
            getProviderPriority(result.apiName)
        }
        _isLoading.value = false
    }
}
