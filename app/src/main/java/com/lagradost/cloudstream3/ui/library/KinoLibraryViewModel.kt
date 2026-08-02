package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                // 1. Continue Watching
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                val resumeList = resumeIds.mapNotNull { id ->
                    val resume = DataStoreHelper.getLastWatched(id)
                    if (resume != null && !resume.name.isNullOrBlank() && !resume.url.isNullOrBlank() && !resume.apiName.isNullOrBlank()) {
                        KinoLibraryItem(
                            name = resume.name!!,
                            url = resume.url!!,
                            apiName = resume.apiName!!,
                            posterUrl = resume.posterUrl
                        )
                    } else {
                        null
                    }
                }
                _continueWatching.value = resumeList

                // 2. Watchlist & History (from Bookmarks)
                val allBookmarks = DataStoreHelper.getAllBookmarkedData()
                _watchlist.value = allBookmarks.map {
                    KinoLibraryItem(name = it.name, url = it.url, apiName = it.apiName, posterUrl = it.posterUrl)
                }
                _history.value = allBookmarks.sortedByDescending { it.bookmarkedTime }.map {
                    KinoLibraryItem(name = it.name, url = it.url, apiName = it.apiName, posterUrl = it.posterUrl)
                }

                // 3. Liked (from Favorites)
                val allFavorites = DataStoreHelper.getAllFavorites()
                _liked.value = allFavorites.map {
                    KinoLibraryItem(name = it.name, url = it.url, apiName = it.apiName, posterUrl = it.posterUrl)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
