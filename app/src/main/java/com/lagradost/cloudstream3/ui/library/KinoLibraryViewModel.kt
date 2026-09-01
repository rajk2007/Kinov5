package com.lagradost.cloudstream3.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.DOWNLOAD_EPISODE_CACHE
import com.lagradost.cloudstream3.utils.DOWNLOAD_HEADER_CACHE
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadFileInfo
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
                val resumeList = resumeIds.mapNotNull { id ->
                    val resume = DataStoreHelper.getLastWatched(id) ?: return@mapNotNull null
                    val headerCache = context.getKey<DownloadObjects.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE,
                        resume.parentId.toString()
                    ) ?: return@mapNotNull null
                    KinoLibraryItem(
                        name = headerCache.name,
                        url = headerCache.url,
                        apiName = headerCache.apiName,
                        type = headerCache.type,
                        posterUrl = headerCache.poster,
                        episodeId = resume.episodeId
                    )
                }
                _continueWatching.value = resumeList

                val headers = context.getKeys(DOWNLOAD_HEADER_CACHE)
                    .mapNotNull { key -> context.getKey<DownloadObjects.DownloadHeaderCached>(key) }
                    .associateBy { it.id }
                val downloadedFiles = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                    .mapNotNull { key -> context.getKey<DownloadObjects.DownloadEpisodeCached>(key) }
                    .mapNotNull { file ->
                        val info = getDownloadFileInfo(context, file.id) ?: return@mapNotNull null
                        if (info.fileLength <= 0L) return@mapNotNull null
                        val header = headers[file.parentId] ?: return@mapNotNull null
                        KinoLibraryItem(
                            name = file.name ?: header.name,
                            url = header.url,
                            apiName = header.apiName,
                            type = header.type,
                            posterUrl = file.poster ?: header.poster,
                            id = file.id
                        )
                    }
                _downloads.value = downloadedFiles
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
