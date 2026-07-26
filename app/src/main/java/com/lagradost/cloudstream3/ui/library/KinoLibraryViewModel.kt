package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.DataStoreHelper
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.APIHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class KinoLibraryViewModel : ViewModel() {
    private val _continueWatching = MutableStateFlow<List<SearchResponse>>(emptyList())
    val continueWatching: StateFlow<List<SearchResponse>> = _continueWatching

    private val _history = MutableStateFlow<List<SearchResponse>>(emptyList())
    val history: StateFlow<List<SearchResponse>> = _history

    private val _watchlist = MutableStateFlow<List<SearchResponse>>(emptyList())
    val watchlist: StateFlow<List<SearchResponse>> = _watchlist

    private val _liked = MutableStateFlow<List<SearchResponse>>(emptyList())
    val liked: StateFlow<List<SearchResponse>> = _liked

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch Continue Watching
                val resumeList = DataStoreHelper.getAllResumeStateIds().mapNotNull { id ->
                    val resume = DataStoreHelper.getLastWatched(id)
                    if (resume != null) {
                        val api = APIHolder.getApiFromNameNull(resume.apiName)
                        if (api != null) APIRepository(api).load(resume.url).value as? SearchResponse
                        else null
                    } else null
                }
                _continueWatching.value = resumeList

                // Fetch Bookmarks (Watchlist/Liked)
                val bookmarks = DataStoreHelper.getBookmarkedData()
                _watchlist.value = bookmarks.filter { it.type == com.lagradost.cloudstream3.TvType.Movie || it.type == com.lagradost.cloudstream3.TvType.TvSeries }
                _liked.value = bookmarks.filter { it.type == com.lagradost.cloudstream3.TvType.Anime || it.type == com.lagradost.cloudstream3.TvType.AsianDrama }

                // Fetch Search History
                val searchHistory = DataStoreHelper.getSearchHistory()
                _history.value = searchHistory.map { it.searchResponse }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
