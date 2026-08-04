package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.BookmarkedData
import com.lagradost.cloudstream3.utils.DataStoreHelper.FavoritesData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Explicitly define the data class used for the UI
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
                // 1. Continue Watching — use getBookmarkedData to get metadata
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                val resumeList = resumeIds.mapNotNull { id ->
                    val data = DataStoreHelper.getBookmarkedData(id)
                    if (data != null) {
                        val res = data as SearchResponse
                        KinoLibraryItem(
                            name = res.name,
                            url = res.url,
                            apiName = res.apiName,
                            posterUrl = res.posterUrl
                        )
                    } else null
                }
                _continueWatching.value = resumeList

                // 2. Watchlist — BookmarkedData extends SearchResponse, access directly
                val allBookmarks = DataStoreHelper.getAllBookmarkedData()
                _watchlist.value = allBookmarks.map { bookmark ->
                    val res = bookmark as SearchResponse
                    KinoLibraryItem(
                        name = res.name,
                        url = res.url,
                        apiName = res.apiName,
                        posterUrl = res.posterUrl
                    )
                }

                // 3. History — same bookmarks, sorted by most recent
                _history.value = allBookmarks.sortedByDescending { it.bookmarkedTime }.map { bookmark ->
                    val res = bookmark as SearchResponse
                    KinoLibraryItem(
                        name = res.name,
                        url = res.url,
                        apiName = res.apiName,
                        posterUrl = res.posterUrl
                    )
                }

                // 4. Liked — FavoritesData extends SearchResponse
                val allFavorites = DataStoreHelper.getAllFavorites()
                _liked.value = allFavorites.map { favorite ->
                    val res = favorite as SearchResponse
                    KinoLibraryItem(
                        name = res.name,
                        url = res.url,
                        apiName = res.apiName,
                        posterUrl = res.posterUrl
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
