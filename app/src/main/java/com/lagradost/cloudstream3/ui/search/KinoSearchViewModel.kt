package com.lagradost.cloudstream3.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.TvType
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

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    var query = MutableStateFlow("")

    private val mutex = Mutex()

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

        val priorityNames = listOf("moviebox", "castle", "netmirror", "netflix", "pikashow")
        val providers = APIHolder.apis.sortedBy { api ->
            val nameLower = api.name.lowercase()
            val index = priorityNames.indexOfFirst { nameLower.contains(it) }
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
                        val repo = APIRepository(api)
                        val resource = withTimeoutOrNull(8000L) { repo.search(query, page = 1) }
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
                            mutex.withLock {
                                masterList.addAll(mapped)
                                _results.value = masterList.sortedBy { getProviderPriority(it.apiName) }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        _isLoading.value = false
    }
}
