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

    val providers = APIHolder.apis.sortedBy { api ->
        providerPriority[api.name] ?: Int.MAX_VALUE
    }

    coroutineScope {
        val deferredResults = providers.map { api ->
            async(Dispatchers.IO) {
                try {
                    val repo = APIRepository(api)
                    // Add a 5-second timeout so a slow provider doesn't block the UI
                    val resource = withTimeoutOrNull(5000L) {
                        repo.search(query, page = 1)
                    }
                    when (resource) {
                        is Resource.Success -> {
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
                        }
                        else -> emptyList()
                    }
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }

        deferredResults.awaitAll().forEach { resultList ->
            allResults.addAll(resultList)
        }
    }

    // Sort final results by priority
    _results.value = allResults.sortedBy { result ->
        providerPriority[result.apiName] ?: Int.MAX_VALUE
    }
    _isLoading.value = false
}
}
