package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DataStoreHelper
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
                // 1. Continue Watching
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                val resumeList = resumeIds.mapNotNull { id ->
                    val resume = DataStoreHelper.getLastWatched(id)
                    resume?.let {
                        object : SearchResponse {
                            override val name = "Unknown"
                            override val url = ""
                            override val apiName = ""
                            override var type: TvType? = null
                            override var posterUrl: String? = null
                            override var posterHeaders: Map<String, String>? = null
                            override var id: Int? = it.parentId
                            override var quality: com.lagradost.cloudstream3.SearchQuality? = null
                            override var score: com.lagradost.cloudstream3.Score? = null
                        }
                    }
                }
                _continueWatching.value = resumeList

                // 2. Bookmarks (Watchlist + Liked)
                val allBookmarks = DataStoreHelper.getAllBookmarkedData()
                _watchlist.value = allBookmarks.filter {
                    it.type == TvType.Movie || it.type == TvType.TvSeries
                }
                _liked.value = allBookmarks.filter {
                    it.type == TvType.Anime || it.type == TvType.AsianDrama
                }

                // 3. History
                _history.value = emptyList()

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
