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

    // Priority order: lower number = higher priority
    private val providerPriority = mapOf(
        "MovieBox" to 1,
        "CastleTV" to 2,
        "Pikashow" to 3
    )

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

        // Get ALL providers, sorted by priority
        val providers = APIHolder.apis.sortedBy { api ->
            providerPriority[api.name] ?: Int.MAX_VALUE  // Unprioritized providers go last
        }

        providers.forEach { api ->
            try {
                val repo = APIRepository(api)
                when (val resource = repo.search(query, page = 1)) {
                    is Resource.Success -> {
                        resource.value.items.forEach { response ->
                            allResults.add(
                                KinoSearchResult(
                                    name = response.name,
                                    url = response.url,
                                    apiName = response.apiName,
                                    posterUrl = response.posterUrl,
                                    type = response.type,
                                    year = null,
                                    quality = response.quality?.name
                                )
                            )
                        }
                    }
                    is Resource.Failure -> {
                        // Log error but continue with other providers
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _results.value = allResults
        _isLoading.value = false
    }
}
