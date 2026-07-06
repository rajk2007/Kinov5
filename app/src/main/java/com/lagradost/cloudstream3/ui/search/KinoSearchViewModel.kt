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

    private fun getProviderPriority(apiName: String, className: String): Int {
        val name = apiName.lowercase()
        val cls = className.lowercase()
        return when {
            name.contains("moviebox") || cls.contains("moviebox") -> 1
            name.contains("castle") || cls.contains("castle") -> 2
            name.contains("netmirror") || cls.contains("netmirror") -> 3
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
        _results.value = emptyList()
        
        val masterList = mutableListOf<KinoSearchResult>()
        val allowedProviders = listOf("moviebox", "castle", "netmirror")
        
        val providers = APIHolder.apis.filter { api ->
            val nameLower = api.name.lowercase()
            val classNameLower = api::class.java.simpleName.lowercase()
            allowedProviders.any { pattern ->
                nameLower.contains(pattern) || classNameLower.contains(pattern)
            }
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
                            synchronized(masterList) {
                                masterList.addAll(mapped)
                                _results.value = masterList.sortedBy { getProviderPriority(it.apiName, "") }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore failures
                    }
                }
            }
        }
        _isLoading.value = false
    }
}
