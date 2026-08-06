package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DownloadObjects
import com.lagradost.cloudstream3.ui.result.ResultViewModel2.Companion.WatchType
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

    private val _watchlist = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val watchlist: StateFlow<List<KinoLibraryItem>> = _watchlist

    private val _history = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val history: StateFlow<List<KinoLibraryItem>> = _history

    private val _liked = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val liked: StateFlow<List<KinoLibraryItem>> = _liked

    fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Continue Watching
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                _continueWatching.value = resumeIds.mapNotNull { id ->
                    val resume: DownloadObjects.ResumeWatching? = DataStoreHelper.getLastWatched(id)
                    if (resume != null && !resume.name.isNullOrBlank() && !resume.url.isNullOrBlank()) {
                        KinoLibraryItem(
                            name = resume.name!!,
                            url = resume.url!!,
                            apiName = resume.apiName ?: "",
                            posterUrl = resume.posterUrl
                        )
                    } else null
                }

                // 2. Watchlist + History (from bookmarks filtered by status)
                val allBookmarks = DataStoreHelper.getAllBookmarkedData()

                _watchlist.value = allBookmarks.filter {
                    it.id != null && DataStoreHelper.getResultWatchState(it.id!!) in listOf(
                        WatchType.Watching,
                        WatchType.PlanToWatch
                    )
                }.map {
                    KinoLibraryItem(name = it.name, url = it.url, apiName = it.apiName, posterUrl = it.posterUrl)
                }

                _history.value = allBookmarks.filter {
                    it.id != null && DataStoreHelper.getResultWatchState(it.id!!) == WatchType.Completed
                }.map {
                    KinoLibraryItem(name = it.name, url = it.url, apiName = it.apiName, posterUrl = it.posterUrl)
                }

                // 3. Liked (favorites)
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
