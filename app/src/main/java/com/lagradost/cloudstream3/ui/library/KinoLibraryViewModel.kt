package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
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
                            override val name = it.name ?: return@let null
                            override val url = it.url ?: return@let null
                            override val apiName = it.apiName ?: return@let null
                            override var type = it.type
                            override var posterUrl = it.posterUrl
                            override var posterHeaders = null
                            override var id = it.parentId
                            override var quality = null
                            override var score = null
                        }
                    }
                }
                _continueWatching.value = resumeList

                // 2. Watchlist
                val allBookmarks = DataStoreHelper.getAllBookmarkedData()
                _watchlist.value = allBookmarks

                // 3. Liked (Favorites)
                val allFavorites = DataStoreHelper.getAllFavorites()
                _liked.value = allFavorites

                // 4. History (Recently bookmarked)
                _history.value = allBookmarks.sortedByDescending { it.bookmarkedTime }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
