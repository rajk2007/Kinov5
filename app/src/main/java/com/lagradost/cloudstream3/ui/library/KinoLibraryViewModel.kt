package com.lagradost.cloudstream3.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class KinoLibraryItem(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var posterUrl: String?,
    val episodeId: Int? = null,
    val parentId: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    override var type: TvType? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = episodeId ?: parentId,
    override var quality: SearchQuality? = null,
    override var score: Score? = null
) : SearchResponse

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
                val resumeList = HomeViewModel.getResumeWatching().orEmpty()
                _continueWatching.value = resumeList.map { result ->
                    KinoLibraryItem(
                        name = result.name,
                        url = result.url,
                        apiName = result.apiName,
                        posterUrl = result.posterUrl,
                        episodeId = result.id,
                        parentId = result.parentId,
                        season = result.season,
                        episode = result.episode,
                        type = result.type
                    )
                }

                val allBookmarks = DataStoreHelper.getAllBookmarkedData()
                fun DataStoreHelper.BookmarkedData.toLibraryItem() = KinoLibraryItem(
                    name = name,
                    url = url,
                    apiName = apiName,
                    posterUrl = posterUrl,
                    type = type,
                    id = id,
                    quality = quality,
                    posterHeaders = posterHeaders,
                    score = score
                )

                _watchlist.value = allBookmarks
                    .filter { it.type == TvType.Movie || it.type == TvType.TvSeries }
                    .map { it.toLibraryItem() }
                _liked.value = allBookmarks
                    .filter { it.type == TvType.Anime || it.type == TvType.AsianDrama }
                    .map { it.toLibraryItem() }
                _history.value = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
