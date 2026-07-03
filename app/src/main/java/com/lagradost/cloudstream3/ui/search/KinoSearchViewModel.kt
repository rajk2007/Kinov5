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

    // Only search these providers
    private val targetProviders = setOf("MovieBox", "CastleTV")

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

        // Get only MovieBox and CastleTV providers
        val providers = APIHolder.apis.filter { api ->
            api.name in targetProviders
        }

        providers.forEach { api ->
            try {
                val repo = APIRepository(api)
                when (val resource = repo.search(query, page = 1)) {
                    is Resource.Success -> {
                        val searchResponseList = resource.value
                        searchResponseList.items.forEach { response ->
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
