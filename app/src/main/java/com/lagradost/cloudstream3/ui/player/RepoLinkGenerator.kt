package com.lagradost.cloudstream3.ui.player

import android.util.Log
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

data class Cache(
    val linkCache: MutableSet<ExtractorLink>,
    val subtitleCache: MutableSet<SubtitleData>,
    /** When it was last updated */
    var lastCachedTimestamp: Long = unixTime,
    /** If it has fully loaded */
    var saturated: Boolean,
)

class RepoLinkGenerator(
    episodes: List<ResultEpisode>,
    val page: LoadResponse? = null,
) : VideoGenerator<ResultEpisode>(episodes) {
    companion object {
        const val TAG = "RepoLink"
        val cache: HashMap<Pair<String, Int>, Cache> = hashMapOf()

        private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val inflightRequests = ConcurrentHashMap<Pair<String, Int>, Deferred<Unit>>()
        private val linkListeners = ConcurrentHashMap<Pair<String, Int>, CopyOnWriteArrayList<(ExtractorLink) -> Unit>>()
        private val subtitleListeners = ConcurrentHashMap<Pair<String, Int>, CopyOnWriteArrayList<(SubtitleData) -> Unit>>()

        /** Shares links resolved by the result/download screen with playback. */
        fun seedPlaybackCache(apiName: String, episodeId: Int?, links: List<ExtractorLink>) {
            if (episodeId == null || links.isEmpty()) return
            val entry = synchronized(cache) {
                cache[apiName to episodeId] ?: Cache(mutableSetOf(), mutableSetOf(), unixTime, false)
                    .also { cache[apiName to episodeId] = it }
            }
            synchronized(entry) {
                entry.linkCache.addAll(links)
                entry.saturated = entry.linkCache.isNotEmpty()
                entry.lastCachedTimestamp = unixTime
            }
        }

        /** Returns a snapshot for the download cache after a shared request completes. */
        fun getCachedLinks(apiName: String, episodeId: Int): List<ExtractorLink> =
            synchronized(cache) { cache[apiName to episodeId]?.let { synchronized(it) { it.linkCache.toList() } } }
                ?: emptyList()

        /** Start one provider request, or attach to the request already resolving this episode. */
        fun beginOrAttachLinkRequest(
            apiName: String,
            episodeId: Int,
            data: String,
            api: MainAPI,
        ): Deferred<Unit> {
            val key = apiName to episodeId
            inflightRequests[key]?.let { return it }

            val candidate = requestScope.async(start = CoroutineStart.LAZY) {
                val entry = synchronized(cache) {
                    cache[key] ?: Cache(mutableSetOf(), mutableSetOf(), unixTime, false)
                        .also { cache[key] = it }
                }
                try {
                    Log.d(TAG, "Starting shared link request for $key")
                    APIRepository(api).loadLinks(
                        data = data,
                        isCasting = false,
                        subtitleCallback = { subtitle ->
                            synchronized(entry) {
                                if (entry.subtitleCache.add(subtitle)) {
                                    entry.lastCachedTimestamp = unixTime
                                    subtitleListeners[key]?.forEach { it(subtitle) }
                                }
                            }
                        },
                        callback = { link ->
                            if (link.url.isBlank()) return@loadLinks
                            synchronized(entry) {
                                if (entry.linkCache.add(link)) {
                                    entry.lastCachedTimestamp = unixTime
                                    linkListeners[key]?.forEach { it(link) }
                                }
                            }
                        },
                    )
                    synchronized(entry) {
                        entry.saturated = entry.linkCache.isNotEmpty()
                        entry.lastCachedTimestamp = unixTime
                    }
                    Log.d(TAG, "Shared link request completed for $key with ${entry.linkCache.size} links")
                } catch (error: Exception) {
                    Log.d(TAG, "Shared link request failed for $key", error)
                } finally {
                    inflightRequests.remove(key)
                }
            }
            val existing = inflightRequests.putIfAbsent(key, candidate)
            if (existing != null) {
                candidate.cancel()
                return existing
            }
            candidate.start()
            return candidate
        }

        fun hasInflightRequest(apiName: String, episodeId: Int): Boolean =
            inflightRequests.containsKey(apiName to episodeId)

        private fun addLinkListener(key: Pair<String, Int>, listener: (ExtractorLink) -> Unit) {
            linkListeners.getOrPut(key) { CopyOnWriteArrayList() }.add(listener)
        }

        private fun removeLinkListener(key: Pair<String, Int>, listener: (ExtractorLink) -> Unit) {
            linkListeners[key]?.let { listeners ->
                listeners.remove(listener)
                if (listeners.isEmpty()) linkListeners.remove(key, listeners)
            }
        }

        private fun addSubtitleListener(key: Pair<String, Int>, listener: (SubtitleData) -> Unit) {
            subtitleListeners.getOrPut(key) { CopyOnWriteArrayList() }.add(listener)
        }

        private fun removeSubtitleListener(key: Pair<String, Int>, listener: (SubtitleData) -> Unit) {
            subtitleListeners[key]?.let { listeners ->
                listeners.remove(listener)
                if (listeners.isEmpty()) subtitleListeners.remove(key, listeners)
            }
        }
    }

    override val hasCache = true
    override val canSkipLoading = true
    override fun getId(index: Int): Int? = videos.getOrNull(index)?.id

    @Throws
    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean,
    ): Boolean {
        val current = videos.getOrNull(offset) ?: return false
        val currentApi = getApiFromNameNull(current.apiName) ?: return false
        val key = current.apiName to current.id
        val currentCache = synchronized(cache) {
            cache[key] ?: Cache(mutableSetOf(), mutableSetOf(), unixTime, false).also { cache[key] = it }
        }
        val deliveredLinkUrls = ConcurrentHashMap.newKeySet<String>()
        val deliveredSubtitleUrls = ConcurrentHashMap.newKeySet<String>()
        fun deliverLink(link: ExtractorLink) {
            if (sourceTypes.contains(link.type) && deliveredLinkUrls.add(link.url)) callback(link to null)
        }
        fun deliverSubtitle(subtitle: SubtitleData) {
            if (deliveredSubtitleUrls.add(subtitle.url)) subtitleCallback(subtitle)
        }

        synchronized(currentCache) {
            val outdated = unixTime - currentCache.lastCachedTimestamp > 60 * 20
            if (outdated || clearCache) {
                currentCache.linkCache.clear()
                currentCache.subtitleCache.clear()
                currentCache.saturated = false
            }
            currentCache.linkCache.forEach { link ->
                deliverLink(link)
            }
            currentCache.subtitleCache.forEach { deliverSubtitle(it) }
            if (currentCache.saturated && !clearCache) return true
        }

        val linkListener: (ExtractorLink) -> Unit = ::deliverLink
        val subtitleListener: (SubtitleData) -> Unit = ::deliverSubtitle
        addLinkListener(key, linkListener)
        addSubtitleListener(key, subtitleListener)
        try {
            // Replay anything delivered between the initial snapshot and listener registration.
            synchronized(currentCache) {
                currentCache.linkCache.forEach { link ->
                    deliverLink(link)
                }
                currentCache.subtitleCache.forEach { deliverSubtitle(it) }
            }
            beginOrAttachLinkRequest(current.apiName, current.id, current.data, currentApi).await()
            synchronized(currentCache) {
                currentCache.linkCache.forEach(::deliverLink)
                return currentCache.saturated
            }
        } finally {
            removeLinkListener(key, linkListener)
            removeSubtitleListener(key, subtitleListener)
        }
    }
}
