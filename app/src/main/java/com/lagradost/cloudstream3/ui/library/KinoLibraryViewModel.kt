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
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadStatus
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
                    resume.updateTime to KinoLibraryItem(
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
                    .sortedByDescending { it.first }
                    .take(10)
                    .map { it.second }
                _continueWatching.value = resumeList

                // Keep the legacy queue entries for pending items, but use the direct
                // download manager for byte/progress state in KinoLibraryScreen.
                val queuedWrappers = try {
                    com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager.queue.value.toList()
                } catch (_: Exception) {
                    emptyList()
                }
                val activeWrappers = try {
                    DownloadQueueService.downloadInstances.value.map { it.downloadQueueWrapper }
                } catch (_: Exception) {
                    emptyList()
                }
                val allQueueWrappers = (queuedWrappers + activeWrappers).distinctBy { it.id }
                val queueAndActiveIds = allQueueWrappers.map { it.id }.toSet()

                val episodeKeys = context.getKeys(DOWNLOAD_EPISODE_CACHE)
                val cachedItems = episodeKeys.mapNotNull { key ->
                    val episode = context.getKey<DownloadObjects.DownloadEpisodeCached>(key)
                        ?: return@mapNotNull null
                    val header = context.getKey<DownloadObjects.DownloadHeaderCached>(
                        DOWNLOAD_HEADER_CACHE,
                        episode.parentId.toString()
                    ) ?: return@mapNotNull null
                    val info = getDownloadFileInfo(context, episode.id)
                    val downloaded = info?.fileLength ?: 0L
                    val total = info?.totalBytes ?: 0L
                    // The episode cache can exist before download metadata is written.
                    if (downloaded <= 1L && episode.id !in queueAndActiveIds) {
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
                        id = header.id,
                        localUri = info?.path?.toString(),
                        downloadStatus = if (info != null) DirectDownloadStatus.COMPLETED else null,
                    )
                }

                val cachedIds = episodeKeys.mapNotNull {
                    context.getKey<DownloadObjects.DownloadEpisodeCached>(it)?.id
                }.toSet()
                val queueItems = allQueueWrappers
                    .filter { it.id !in cachedIds }
                    .mapNotNull { wrapper ->
                        val downloadItem = wrapper.downloadItem ?: return@mapNotNull null
                        KinoLibraryItem(
                            name = downloadItem.resultName,
                            url = downloadItem.resultUrl,
                            apiName = downloadItem.apiName,
                            type = downloadItem.resultType,
                            posterUrl = downloadItem.resultPoster,
                            episodeId = downloadItem.episode.id,
                            id = downloadItem.resultId,
                            downloadStatus = DirectDownloadStatus.PENDING,
                        )
                    }

                _downloads.value = cachedItems + queueItems
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
    val localUri: String? = null,
    val downloadStatus: DirectDownloadStatus? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var id: Int? = null,
    override var quality: com.lagradost.cloudstream3.SearchQuality? = null,
    override var score: com.lagradost.cloudstream3.Score? = null,
) : SearchResponse
