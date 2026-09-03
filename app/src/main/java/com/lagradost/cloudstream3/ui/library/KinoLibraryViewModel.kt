package com.lagradost.cloudstream3.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.services.DownloadQueueService
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

                // Downloads — show all items that are queued, active, or completed.
                val episodeKeys = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                val queuedIds = try {
                    com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager.queue.value
                        .map { it.id }
                } catch (e: Exception) {
                    emptyList()
                }
                val activeIds = try {
                    DownloadQueueService.downloadInstances.value
                        .map { it.downloadQueueWrapper.id }
                } catch (e: Exception) {
                    emptyList()
                }

                val downloadedItems = episodeKeys.mapNotNull { key ->
                    val episode = context.getKey<DownloadObjects.DownloadEpisodeCached>(key)
                        ?: return@mapNotNull null
                    val header = context.getKey<DownloadObjects.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE,
                        episode.parentId.toString()
                    ) ?: return@mapNotNull null
                    val info = getDownloadFileInfo(context, episode.id)
                    val downloaded = info?.fileLength ?: 0L
                    val total = info?.totalBytes ?: 0L
                    // The episode cache is created before download metadata is written, so
                    // queued or active downloads may legitimately have no bytes yet.
                    if (downloaded <= 1L && episode.id !in queuedIds && episode.id !in activeIds) {
                        return@mapNotNull null
                    }

                    val progress = if (total > 0L) {
                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    KinoLibraryItem(
                        name = header.name,
                        url = header.url,
                        apiName = header.apiName,
                        type = header.type,
                        posterUrl = header.poster,
                        episodeId = episode.id,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        progress = progress,
                        id = header.id
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
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
    val position: Long = 0L,
    val duration: Long = 0L,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: com.lagradost.cloudstream3.SearchQuality? = null,
    override var score: com.lagradost.cloudstream3.Score? = null,
) : SearchResponse
