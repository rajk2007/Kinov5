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
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class KinoLibraryViewModel : ViewModel() {
    private val _continueWatching = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val continueWatching: StateFlow<List<KinoLibraryItem>> = _continueWatching

    private val _downloads = MutableStateFlow<List<KinoLibraryItem>>(emptyList())
    val downloads: StateFlow<List<KinoLibraryItem>> = _downloads

    fun loadData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resumeIds = DataStoreHelper.getAllResumeStateIds() ?: emptyList()
                val resumeList = resumeIds.mapNotNull { id ->
                    val resume = DataStoreHelper.getLastWatched(id) ?: return@mapNotNull null
                    val headerCache = context.getKey<DownloadObjects.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE,
                        resume.parentId.toString()
                    ) ?: return@mapNotNull null
                    val watchPos = DataStoreHelper.getViewPos(resume.episodeId)
                    KinoLibraryItem(
                        name = headerCache.name,
                        url = headerCache.url,
                        apiName = headerCache.apiName,
                        type = headerCache.type,
                        posterUrl = headerCache.poster,
                        episodeId = resume.episodeId,
                        position = watchPos?.position ?: 0L,
                        duration = watchPos?.duration ?: 0L,
                    )
                }
                _continueWatching.value = resumeList

                val downloadedItems = context.getKeys(DOWNLOAD_HEADER_CACHE)
                    .mapNotNull { key -> context.getKey<DownloadObjects.DownloadHeaderCached>(key) }
                    .distinctBy { it.id }
                    .map { header ->
                        KinoLibraryItem(
                            name = header.name,
                            url = header.url,
                            apiName = header.apiName,
                            type = header.type,
                            posterUrl = header.poster,
                            id = header.id,
                        )
                    }
                _downloads.value = downloadedItems
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
    val position: Long = 0L,
    val duration: Long = 0L,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: com.lagradost.cloudstream3.SearchQuality? = null,
    override var score: com.lagradost.cloudstream3.Score? = null,
) : SearchResponse
