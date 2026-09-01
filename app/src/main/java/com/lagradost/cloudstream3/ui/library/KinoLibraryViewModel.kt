package com.lagradost.cloudstream3.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class KinoLibraryViewModel : ViewModel() {
    private val _continueWatching = MutableStateFlow<List<SearchResponse>>(emptyList())
    val continueWatching: StateFlow<List<SearchResponse>> = _continueWatching

    private val _downloads = MutableStateFlow<List<SearchResponse>>(emptyList())
    val downloads: StateFlow<List<SearchResponse>> = _downloads

    fun loadData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                _continueWatching.value = resumeIds.mapNotNull { id ->
                    val resume = DataStoreHelper.getLastWatched(id)
                    val bookmark = resume?.parentId?.let { DataStoreHelper.getBookmarkedData(it) }
                    if (resume != null && bookmark != null) {
                        KinoLibraryItem(
                            name = bookmark.name,
                            url = bookmark.url,
                            apiName = bookmark.apiName,
                            type = bookmark.type,
                            posterUrl = bookmark.posterUrl,
                            episodeId = resume.episodeId
                        )
                    } else null
                }

                _downloads.value = context.getKeys(DOWNLOAD_HEADER_CACHE).mapNotNull { key ->
                    context.getKey<com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadHeaderCached>(key)
                        ?.let { header ->
                            KinoLibraryItem(
                                name = header.name,
                                url = header.url,
                                apiName = header.apiName,
                                type = header.type,
                                posterUrl = header.poster
                            )
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

data class KinoLibraryItem(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override var type: TvType? = null,
    override var posterUrl: String? = null,
    val episodeId: Int? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: com.lagradost.cloudstream3.SearchQuality? = null,
    override var score: com.lagradost.cloudstream3.Score? = null,
) : SearchResponse
