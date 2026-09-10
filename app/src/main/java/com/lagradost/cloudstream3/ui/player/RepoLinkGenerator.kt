package com.lagradost.cloudstream3.ui.player

import android.util.Log
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.utils.AppContextUtils.html
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import java.util.concurrent.ConcurrentHashMap
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
        val cache: HashMap<Pair<String, Int>, Cache> =
            hashMapOf()
    }

    override val hasCache = true
    override val canSkipLoading = true
    override fun getId(index: Int): Int? = videos.getOrNull(index)?.id

    // this is a simple array that is used to instantly load links if they are already loaded
    //var linkCache = Array<Set<ExtractorLink>>(size = episodes.size, init = { setOf() })
    //var subsCache = Array<Set<SubtitleData>>(size = episodes.size, init = { setOf() })

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

        Log.e("VIDEO_DEBUG", "═══════════════════════════════")
        Log.e("VIDEO_DEBUG", "🎬 Title: ${current.name}")
        Log.e("VIDEO_DEBUG", "📡 API: ${current.apiName}")
        Log.e("VIDEO_DEBUG", "🔗 Data: ${current.data}")
        val currentApi = getApiFromNameNull(current.apiName)
        Log.e("VIDEO_DEBUG", "🔌 API Found: ${currentApi?.name ?: "NULL"}")
        if (currentApi == null) {
            Log.e("VIDEO_DEBUG", "❌ Provider not found!")
            return false
        }

        val currentCache = synchronized(cache) {
            cache[current.apiName to current.id] ?: Cache(
                mutableSetOf(),
                mutableSetOf(),
                unixTime,
                false
            ).also {
                cache[current.apiName to current.id] = it
            }
        }

        // These act as a general filter to prevent duplication of links or names
        // Avoid any possible ConcurrentModificationException
        val currentLinksUrls = ConcurrentHashMap.newKeySet<String>()
        val currentSubsUrls = ConcurrentHashMap.newKeySet<String>()
        // Use atomics as otherwise we get race conditions when incrementing, while rare it did actually happen!
        val lastCountedSuffix = ConcurrentHashMap<String, AtomicInteger>()

        synchronized(currentCache) {
            val outdatedCache =
                unixTime - currentCache.lastCachedTimestamp > 60 * 20 // 20 minutes

            if (outdatedCache || clearCache) {
                currentCache.linkCache.clear()
                currentCache.subtitleCache.clear()
                currentCache.saturated = false
            } else if (currentCache.linkCache.isNotEmpty()) {
                Log.d(
                    TAG,
                    "Resumed previous loading from ${unixTime - currentCache.lastCachedTimestamp}s ago"
                )
            }

            // call all callbacks
            currentCache.linkCache.forEach { link ->
                currentLinksUrls.add(link.url)
                if (sourceTypes.contains(link.type)) {
                    callback(link to null)
                }
            }

            currentCache.subtitleCache.forEach { sub ->
                currentSubsUrls.add(sub.url)
                lastCountedSuffix.getOrPut(sub.originalName) { AtomicInteger(0) }.incrementAndGet()
                subtitleCallback(sub)
            }

            // this stops all execution if links are cached
            // no extra get requests
            if (currentCache.saturated) {
                return true
            }
        }

        val result = try {
            APIRepository(currentApi).loadLinks(
            current.data,
            isCasting = isCasting,
            subtitleCallback = { file ->
                Log.d(TAG, "Loaded SubtitleFile: $file")
                Log.e("VIDEO_DEBUG", "📝 Subtitle: ${file.url.take(50)}")
                val correctFile = PlayerSubtitleHelper.getSubtitleData(file)
                if (correctFile.url.isBlank() || !currentSubsUrls.add(correctFile.url)) {
                    return@loadLinks
                }

                // this part makes sure that all names are unique for UX
                val nameDecoded = correctFile.originalName.html().toString()
                    .trim() // `%3Ch1%3Esub%20name…` → `<h1>sub name…` → `sub name…`
                val suffixCount =
                    lastCountedSuffix.getOrPut(nameDecoded) { AtomicInteger(0) }.incrementAndGet()

                val updatedFile =
                    correctFile.copy(originalName = nameDecoded, nameSuffix = "$suffixCount")

                synchronized(currentCache) {
                    if (currentCache.subtitleCache.add(updatedFile)) {
                        subtitleCallback(updatedFile)
                        currentCache.lastCachedTimestamp = unixTime
                    }
                }
            },
            callback = { link ->
                Log.d(TAG, "Loaded ExtractorLink: $link")
                Log.e("VIDEO_DEBUG", "✅ LINK FOUND: ${link.url.take(100)}")
                Log.e("VIDEO_DEBUG", "   Type: ${link.type}")
                Log.e("VIDEO_DEBUG", "   Headers: ${link.headers}")
                Log.e("VIDEO_DEBUG", "   Referer: ${link.referer}")
                if (link.url.isBlank() || !currentLinksUrls.add(link.url)) {
                    return@loadLinks
                }

                synchronized(currentCache) {
                    if (currentCache.linkCache.add(link)) {
                        if (sourceTypes.contains(link.type)) {
                            callback(Pair(link, null))
                        }

                        currentCache.linkCache.add(link)
                        currentCache.lastCachedTimestamp = unixTime
                    }
                }
            }
            )
        } catch (e: Exception) {
            Log.e("VIDEO_DEBUG", "❌ Error: ${e.message}", e)
            false
        }

        synchronized(currentCache) {
            currentCache.saturated = currentCache.linkCache.isNotEmpty()
            currentCache.lastCachedTimestamp = unixTime
        }

        Log.e("VIDEO_DEBUG", "📊 Result: $result")
        Log.e("VIDEO_DEBUG", "═══════════════════════════════")
        return result
    }
}
