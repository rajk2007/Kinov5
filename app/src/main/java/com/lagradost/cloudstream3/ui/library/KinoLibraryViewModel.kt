package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class KinoLibraryItem(
    val name: String,
    val url: String,
    val apiName: String,
    val posterUrl: String?
)

class KinoLibraryViewModel : ViewModel() {
    private val _continueWatching = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val continueWatching: StateFlow<List<KinoLibraryItem>> = _continueWatching

    private val _history = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val history: StateFlow<List<KinoLibraryItem>> = _history

    private val _watchlist = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val watchlist: StateFlow<List<KinoLibraryItem>> = _watchlist

    private val _liked = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val liked: StateFlow<List<KinoLibraryItem>> = _liked

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Continue Watching — from resume data
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                _continueWatching.value = resumeIds.mapNotNull { id ->
                    val resume = DataStoreHelper.getLastWatched(id)
                    if (resume != null && !resume.name.isNullOrBlank() && !resume.url.isNullOrBlank()) {
                        KinoLibraryItem(
                            name = resume.name!!,
                            url = resume.url!!,
                            apiName = resume.apiName ?: "",
                            posterUrl = resume.posterUrl
                        )
                    } else null
                }

                // 2. Get all bookmarks and filter by watch status
                val allBookmarks = DataStoreHelper.getAllBookmarkedData()
                
                // Watchlist = Watching + PlanToWatch
                _watchlist.value = allBookmarks.filter {
                    val status = DataStoreHelper.getResultWatchState(it.id)
                    status == WatchType.Watching || status == WatchType.PlanToWatch
                }.map {
                    val res = it as SearchResponse
                    KinoLibraryItem(name = res.name, url = res.url, apiName = res.apiName, posterUrl = res.posterUrl)
                }

                // History = Completed
                _history.value = allBookmarks.filter {
                    DataStoreHelper.getResultWatchState(it.id) == WatchType.Completed
                }.map {
                    val res = it as SearchResponse
                    KinoLibraryItem(name = res.name, url = res.url, apiName = res.apiName, posterUrl = res.posterUrl)
                }

                // 3. Liked = Favorites (separate system)
                val allFavorites = DataStoreHelper.getAllFavorites()
                _liked.value = allFavorites.map {
                    val res = it as SearchResponse
                    KinoLibraryItem(name = res.name, url = res.url, apiName = res.apiName, posterUrl = res.posterUrl)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
