package com.lagradost.cloudstream3.utils

import android.util.Log
import android.util.Xml
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** A quality discovered from the media stream rather than from an extractor link name. */
data class ProbedQuality(
    val width: Int,
    val height: Int,
    val bandwidth: Int?,
    val variantUrl: String,
    val label: String,
    val estimatedSizeBytes: Long? = null,
    val selectionKey: String,
)

fun heightToQualityLabel(height: Int): String = when {
    height >= 2160 -> "2160p 4K"
    height >= 1440 -> "1440p QHD"
    height >= 1080 -> "1080p Full HD"
    height >= 720 -> "720p HD"
    height >= 480 -> "480p SD"
    height >= 360 -> "360p"
    height > 0 -> "${height}p"
    else -> "Unknown"
}

fun heightToQualitiesInt(height: Int): Int = when {
    height >= 2160 -> Qualities.P2160.value
    height >= 1440 -> Qualities.P1440.value
    height >= 1080 -> Qualities.P1080.value
    height >= 720 -> Qualities.P720.value
    height >= 480 -> Qualities.P480.value
    else -> Qualities.Unknown.value
}

fun calculateEstimatedSize(
    videoBandwidth: Long?,
    audioBandwidth: Long = 0,
    durationSeconds: Double?,
    hasSeparateAudio: Boolean = false,
    videoBandwidthIsAverage: Boolean = false,
): Long? {
    if (videoBandwidth == null || durationSeconds == null || durationSeconds <= 0.0) return null

    val effectiveAudioBandwidth = if (hasSeparateAudio) audioBandwidth.coerceAtLeast(0) else 0L
    val effectiveVideoBandwidth = if (videoBandwidthIsAverage) videoBandwidth else videoBandwidth / 2L
    val totalBandwidth = effectiveVideoBandwidth + effectiveAudioBandwidth
    val estimatedBytes = (totalBandwidth.toDouble() * durationSeconds / 8.0).toLong()
    Log.e("SIZE_FINAL", "calculateEstimatedSize: videoBandwidth=$videoBandwidth isAverage=$videoBandwidthIsAverage audioBandwidth=$effectiveAudioBandwidth durationSeconds=$durationSeconds estimatedBytes=$estimatedBytes formatted=${formatFileSize(estimatedBytes)}")
    return estimatedBytes
}

/** Returns (AVERAGE-BANDWIDTH, BANDWIDTH) from an HLS stream declaration. */
fun parseHlsBandwidth(line: String): Pair<Long?, Long?> {
    val averageBandwidth = Regex("""AVERAGE-BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
        .find(line)?.groupValues?.get(1)?.toLongOrNull()
    val peakBandwidth = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
        .find(line)?.groupValues?.get(1)?.toLongOrNull()
    return averageBandwidth to peakBandwidth
}

/** Returns the highest declared audio rendition bandwidth in an HLS master playlist. */
fun parseHlsAudioBandwidth(playlist: String): Long? {
    // Stop at the end of the #EXT-X-MEDIA line; the previous character class
    // could cross a newline and capture a video variant's BANDWIDTH.
    val audioMediaRegex = Regex(
        """#EXT-X-MEDIA:TYPE=AUDIO[^>\r\n]*?BANDWIDTH=(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    return audioMediaRegex.findAll(playlist)
        .mapNotNull { it.groupValues[1].toLongOrNull() }
        .maxOrNull()
}

fun parseHlsDuration(mediaPlaylist: String): Double = mediaPlaylist.lineSequence()
    .filter { it.startsWith("#EXTINF:", ignoreCase = true) }
    .sumOf { it.substringAfter(":").substringBefore(",").toDoubleOrNull() ?: 0.0 }

/** Classifies the transport represented by a link, using URL evidence before extractor metadata. */
fun getActualLinkType(link: ExtractorLink): String {
    val url = link.url.lowercase(Locale.US)
    return when {
        // URL extensions are the strongest evidence and must win over host heuristics.
        url.contains(".m3u8") -> "HLS"
        url.contains(".mpd") -> "DASH"
        url.contains(".mkv") -> "DIRECT_MKV"
        url.contains(".mp4") -> "DIRECT_MP4"
        url.contains(".webm") -> "DIRECT_WEBM"
        url.contains("cloudflarestorage.com") ||
            url.contains("x-amz-signature") ||
            url.contains("x-amz-credential") -> "DIRECT_FILE"
        link.type == ExtractorLinkType.M3U8 -> "HLS"
        link.type == ExtractorLinkType.DASH -> "DASH"
        else -> "DIRECT_FILE"
    }
}

fun isDirectFileLink(link: ExtractorLink): Boolean = getActualLinkType(link).startsWith("DIRECT_")

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.US, "%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}
private fun resolve(base: String, child: String): String = runCatching { URI(base).resolve(child).toString() }.getOrDefault(child)

/** Exact byte total for byte-range addressed HLS media, including an init MAP range. */
fun parseByteRangeTotal(playlist: String): Long? {
    val segmentLengths = Regex("""#EXT-X-BYTERANGE:(\d+)""", RegexOption.IGNORE_CASE)
        .findAll(playlist).mapNotNull { it.groupValues[1].toLongOrNull() }
    val mapLengths = Regex("""#EXT-X-MAP:[^\r\n]*?BYTERANGE="(\d+)@"""", RegexOption.IGNORE_CASE)
        .findAll(playlist).mapNotNull { it.groupValues[1].toLongOrNull() }
    return (segmentLengths + mapLengths).toList().takeIf { it.isNotEmpty() }?.sum()?.takeIf { it > 0L }
}

private fun parseHlsAudioRenditionUris(master: String, baseUrl: String): List<String> =
    Regex("""#EXT-X-MEDIA:TYPE=AUDIO[^\r\n]*""", RegexOption.IGNORE_CASE).findAll(master)
        .mapNotNull { line ->
            val bandwidth = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE).find(line.value)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val uri = Regex("""URI="([^"]+)""", RegexOption.IGNORE_CASE).find(line.value)?.groupValues?.get(1) ?: return@mapNotNull null
            bandwidth to resolve(baseUrl, uri)
        }.sortedByDescending { it.first }.map { it.second }.toList()

object QualityProbe {
    private const val TAG = "QualityProbe"
    // KDesa movie manifests can be slow, especially for dubbed links.
    private const val PROBE_TIMEOUT_MS = 20_000L
    private val sessionCache = ConcurrentHashMap<String, List<ProbedQuality>>()
    private val sizeCache = ConcurrentHashMap<String, Long>()
    private val hlsHeadSemaphore = Semaphore(32)

    suspend fun probeVideoQualities(link: ExtractorLink): List<ProbedQuality> = withContext(Dispatchers.IO) {
        sessionCache[link.url]?.let {
            Log.d(TAG, "Using cached qualities for ${link.url.take(120)}")
            return@withContext it
        }
        logLink(link)
        val qualities = try {
            withTimeout(PROBE_TIMEOUT_MS) {
                when {
                    getActualLinkType(link) == "HLS" -> probeHlsQualities(link)
                    getActualLinkType(link) == "DASH" -> probeDashQualities(link)
                    getActualLinkType(link).startsWith("DIRECT_") -> probeProgressiveSafe(link)
                    link.url.contains("manifest", true) || link.url.contains("playlist", true) -> {
                        runCatching { probeHlsQualities(link) }
                            .getOrElse { runCatching { probeDashQualities(link) }.getOrElse { probeProgressiveSafe(link) } }
                    }
                    else -> {
                        runCatching { probeHlsQualities(link) }
                            .getOrElse {
                                runCatching { probeDashQualities(link) }
                                    .getOrElse { probeProgressiveSafe(link) }
                            }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Probe timed out after ${PROBE_TIMEOUT_MS}ms; using immediate fallback for ${link.url.take(120)}")
            fallback(link)
        } catch (e: Exception) {
            Log.e(TAG, "Probe failed for ${link.url.take(120)}; using fallback", e)
            fallback(link)
        }
        sessionCache[link.url] = qualities
        qualities
    }

    private fun logLink(link: ExtractorLink) {
        val url = link.url.lowercase(Locale.US)
        Log.e(TAG, "═════════════════════════════")
        Log.e(TAG, "LINK_DEBUG name=${link.name}")
        Log.e(TAG, "LINK_DEBUG type=${link.type}, actualType=${getActualLinkType(link)}, source=${link.source}")
        Log.e(TAG, "URL: ${link.url}")
        Log.e(TAG, "LINK_DEBUG contains .mpd=${url.contains(".mpd")}, .m3u8=${url.contains(".m3u8")}, .mkv=${url.contains(".mkv")}, cloudflarestorage=${url.contains("cloudflarestorage.com")}, x-amz-signature=${url.contains("x-amz-signature")}")
        Log.e(TAG, "Headers: ${link.headers}")
        Log.e(TAG, "Referer: ${link.referer}")
        Log.e(TAG, "═════════════════════════════")
    }

    private suspend fun probeHlsQualities(link: ExtractorLink): List<ProbedQuality> {
        val headers = requestHeaders(link)
        val response = app.get(link.url, headers = headers, allowRedirects = true)
        val contentType = response.headers["Content-Type"].orEmpty()
        Log.e(TAG, "Probe response: code=${response.code}, contentType=$contentType, finalUrl=${response.url}")
        if (!contentType.contains("mpegurl", true) && !contentType.contains("xml", true) && !contentType.contains("text", true)) {
            Log.w(TAG, "Response is not a playlist content type: $contentType")
            return probeProgressiveSafe(link)
        }
        val content = response.text
        Log.e(TAG, "content first200=${content.take(200)}")

        if (content.contains("<MPD", true) || content.contains("<?xml", true) && content.contains("MPD", true)) {
            Log.w(TAG, "Response is DASH despite HLS/unknown link metadata; switching parser")
            return parseDashQualities(link, content)
        }
        if (!content.contains("#EXTM3U", true)) {
            throw IllegalArgumentException("Response is not an HLS playlist")
        }

        val lines = content.lines()
        val audioBandwidth = parseHlsAudioBandwidth(content)
            ?: if (content.contains("#EXT-X-MEDIA:TYPE=AUDIO", ignoreCase = true)) {
                128_000L
            } else {
                0L
            }
        val hasSeparateAudio = content.contains("#EXT-X-MEDIA:TYPE=AUDIO", ignoreCase = true)
        val results = coroutineScope {
            val audioPlaylistDeferred = if (hasSeparateAudio) async {
                parseHlsAudioRenditionUris(content, response.url).firstOrNull()?.let { audioUrl ->
                    runCatching { app.get(audioUrl, headers = headers, allowRedirects = true).text }.getOrNull()
                }
            } else null
            lines.mapIndexedNotNull { index, line ->
                if (!line.startsWith("#EXT-X-STREAM-INF:", true)) return@mapIndexedNotNull null
                val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)", RegexOption.IGNORE_CASE).find(line)
                val (averageBandwidth, peakBandwidth) = parseHlsBandwidth(line)
                val effectiveVideoBandwidth = averageBandwidth ?: peakBandwidth
                val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                if (resolution == null || next == null) return@mapIndexedNotNull null
                val width = resolution.groupValues[1].toInt()
                val height = resolution.groupValues[2].toInt()
                val variantUrl = resolve(response.url, next)
                async {
                    val variantText = runCatching { app.get(variantUrl, headers = headers, allowRedirects = true).text }.getOrNull().orEmpty()
                    val audioText = audioPlaylistDeferred?.await()
                    val byteRangeEstimate = parseByteRangeTotal(variantText)?.let { video ->
                        video + if (hasSeparateAudio) parseByteRangeTotal(audioText.orEmpty()) ?: 0L else 0L
                    }?.takeIf { it > 0L }
                    val duration = parseHlsDuration(variantText)
                    val instantEstimate = byteRangeEstimate ?: if (averageBandwidth != null && duration > 0.0) {
                        calculateEstimatedSize(averageBandwidth, audioBandwidth, duration, hasSeparateAudio, videoBandwidthIsAverage = true)
                    } else null
                    instantEstimate?.let { sizeCache[variantUrl] = it }
                    ProbedQuality(
                        width, height, effectiveVideoBandwidth?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                        variantUrl, heightToQualityLabel(height), instantEstimate,
                        "hls-${height}-${variantUrl.hashCode()}",
                    )
                }
            }.awaitAll()
        }
        val parsed = results.distinctBy { it.selectionKey }.sortedByDescending { it.height }
        Log.e(TAG, "found variants=${parsed.size} heights=${parsed.map { it.height }}")
        return parsed.ifEmpty { fallback(link) }
    }

    /** Parse the MPD with an XML pull parser, including attributes inherited from AdaptationSet. */
    private suspend fun probeDashQualities(link: ExtractorLink): List<ProbedQuality> {
        val response = app.get(link.url, headers = requestHeaders(link), allowRedirects = true)
        Log.e(TAG, "DASH response: code=${response.code}, contentType=${response.headers["Content-Type"]}, finalUrl=${response.url}")
        return parseDashQualities(link, response.text)
    }

    private fun parseDashQualities(link: ExtractorLink, mpd: String): List<ProbedQuality> {
        Log.d(TAG, "MPD content length=${mpd.length}")
        val parser = Xml.newPullParser().apply { setInput(StringReader(mpd)) }
        val results = mutableListOf<ProbedQuality>()
        var manifestDuration: Double? = null
        var adaptationWidth = 0
        var adaptationHeight = 0
        var adaptationBandwidth: Long? = null
        val audioBandwidth = parseAudioBandwidthFromManifest(mpd)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "MPD" -> manifestDuration = parseIsoDurationSeconds(parser.getAttributeValue(null, "mediaPresentationDuration"))
                "AdaptationSet" -> {
                    adaptationWidth = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                    adaptationHeight = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                    adaptationBandwidth = parser.getAttributeValue(null, "bandwidth")?.toLongOrNull()
                }
                "Representation" -> {
                    val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: adaptationWidth
                    val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: adaptationHeight
                    val bandwidth = parser.getAttributeValue(null, "bandwidth")?.toLongOrNull() ?: adaptationBandwidth
                    if (height > 0) {
                        val selectionKey = "dash-${height}-${bandwidth ?: 0}-${results.size}"
                        val effectiveVideoBandwidth = bandwidth
                        results += ProbedQuality(
                            width, height, effectiveVideoBandwidth?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt(),
                            "${link.url}#track=$height", heightToQualityLabel(height),
                            calculateEstimatedSize(effectiveVideoBandwidth, audioBandwidth, durationSeconds = manifestDuration, videoBandwidthIsAverage = true),
                            selectionKey,
                        )
                    }
                }
            }
        }
        return results.distinctBy { it.selectionKey }.sortedByDescending { it.height }.ifEmpty { fallback(link) }
    }

    private fun parseAudioBandwidthFromManifest(mpd: String): Long {
        val audioAdaptationRegex = Regex(
            """<AdaptationSet[^>]*?(?:contentType|mimeType)=[" ]audio[^>]*>(.*?)</AdaptationSet>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val representationRegex = Regex(
            """<Representation[^>]*bandwidth=[" ](\d+)[" ][^>]*>""",
            RegexOption.IGNORE_CASE,
        )
        val maxFromAdaptation = audioAdaptationRegex.findAll(mpd)
            .flatMap { match -> representationRegex.findAll(match.groupValues[1]) }
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .maxOrNull() ?: 0L
        if (maxFromAdaptation > 0) return maxFromAdaptation
        val fallbackRegex = Regex(
            """<Representation[^>]*(?:mimeType|codecs)=[" ][^" ]*audio[^" ]*[" ][^>]*bandwidth=[" ](\d+)[" ][^>]*>""",
            RegexOption.IGNORE_CASE,
        )
        return fallbackRegex.findAll(mpd).mapNotNull { it.groupValues[1].toLongOrNull() }.maxOrNull() ?: 96_000L
    }

    private class HlsVariantInfo(
        val durationSeconds: Double?,
        val segmentUrls: List<String>,
        val firstSegmentDurationSeconds: Double?,
    )

    fun getCachedSize(variantUrl: String): Long? = sizeCache[variantUrl]

    /** Near-exact size via a small, median-based sample of segment HEAD requests. */
    suspend fun sampleVariantSize(link: ExtractorLink, variantUrl: String): Long? = withContext(Dispatchers.IO) {
        sizeCache[variantUrl]?.let { return@withContext it }
        val headers = requestHeaders(link)
        val result = runCatching {
            withTimeout(1_500L) {
                val variant = app.get(variantUrl, headers = headers, allowRedirects = true)
                val videoText = variant.text
                val videoSegments = videoSegmentEntries(videoText, variant.url)
                if (videoSegments.size < 3) return@withTimeout null
                val indices = listOf(0, videoSegments.size / 4, videoSegments.size / 2, videoSegments.size * 3 / 4, videoSegments.lastIndex).distinct().take(6)
                val sampled = coroutineScope { indices.map { i -> async { headOrRange(videoSegments[i].second, headers) } }.awaitAll().filter { it > 0L } }
                if (sampled.size < 3) return@withTimeout null
                val videoTotal = if (durationsVary(videoSegments.map { it.first })) median(sampled) * videoSegments.size else {
                    val bytesPerSecond = sampled.mapIndexed { n, bytes -> bytes.toDouble() / videoSegments[indices[n]].first }.sorted().let { it[it.size / 2] }
                    (bytesPerSecond * videoSegments.sumOf { it.first }).toLong()
                }
                val master = app.get(link.url, headers = headers, allowRedirects = true).text
                val audioUrl = parseHlsAudioRenditionUris(master, link.url).firstOrNull()
                val audioTotal = audioUrl?.let { audioPlaylist ->
                    val audioResponse = app.get(audioPlaylist, headers = headers, allowRedirects = true)
                    val audioSegments = videoSegmentEntries(audioResponse.text, audioResponse.url)
                    if (audioSegments.size < 3) 0L else {
                        val audioIndices = listOf(0, audioSegments.size / 2, audioSegments.lastIndex).distinct()
                        val audioSampled = coroutineScope { audioIndices.map { i -> async { headOrRange(audioSegments[i].second, headers) } }.awaitAll().filter { it > 0L } }
                        if (audioSampled.size < 3) 0L else median(audioSampled) * audioSegments.size
                    }
                } ?: 0L
                (videoTotal + audioTotal).takeIf { it > 0L }
            }
        }.getOrNull()
        result?.let { sizeCache[variantUrl] = it }
        result
    }

    private fun videoSegmentEntries(text: String, baseUrl: String): List<Pair<Double, String>> {
        var pending = 0.0
        return text.lineSequence().map(String::trim).filter { it.isNotEmpty() }.mapNotNull { line ->
            if (line.startsWith("#EXTINF:", true)) { pending = line.substringAfter(":").substringBefore(",").toDoubleOrNull() ?: 0.0; null }
            else if (line.startsWith("#")) null else resolve(baseUrl, line).let { pending.takeIf { it > 0.0 }?.let { duration -> duration to it } }
        }.toList()
    }
    private fun durationsVary(values: List<Double>): Boolean = values.isNotEmpty() && (values.maxOrNull()!! - values.minOrNull()!!) / values.average() > 0.20
    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]
    private suspend fun headOrRange(url: String, headers: Map<String, String>): Long = runCatching {
        val head = app.head(url, headers = headers, timeout = 2)
        if (head.code !in listOf(405, 501)) head.headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0L } ?: 0L
        else {
            val ranged = app.get(url, headers = headers + ("Range" to "bytes=0-0"), allowRedirects = true)
            Regex("bytes\\s+\\d+-\\d+/(\\d+)", RegexOption.IGNORE_CASE).find(ranged.headers["Content-Range"].orEmpty())?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    /** Measures one already-parsed HLS variant without delaying the quality list. */
    suspend fun measureExactSizeForVariant(link: ExtractorLink, variantUrl: String): Long? = withContext(Dispatchers.IO) {
        sizeCache[variantUrl]?.let { return@withContext it }
        val headers = requestHeaders(link)
        val measured = runCatching {
            withTimeout(15_000L) {
                val info = fetchHlsVariantInfo(variantUrl, headers) ?: return@withTimeout null
                val masterText = app.get(link.url, headers = headers, allowRedirects = true).text
                val audioUrls = Regex("""#EXT-X-MEDIA:TYPE=AUDIO[^>\r\n]*?URI=\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
                    .find(masterText)?.groupValues?.get(1)?.let { uri ->
                        runCatching {
                            val audioPlaylistUrl = resolve(link.url, uri)
                            val audioPlaylistResponse = app.get(audioPlaylistUrl, headers = headers, allowRedirects = true)
                            audioPlaylistResponse.text.lineSequence().map(String::trim)
                                .filter { it.isNotEmpty() && !it.startsWith("#") }
                                .map { resolve(audioPlaylistResponse.url, it) }.toList()
                        }.getOrDefault(emptyList())
                    } ?: emptyList()
                measureExactSize(info.segmentUrls, headers, audioUrls, headers)
            }
        }.getOrNull()
        measured?.let { sizeCache[variantUrl] = it }
        measured
    }
    private suspend fun fetchHlsVariantInfo(url: String, headers: Map<String, String>): HlsVariantInfo? = runCatching {
        val text = app.get(url, headers = headers, allowRedirects = true).text
        if (!text.contains("#EXTM3U", true)) return@runCatching null
        val duration = parseHlsDuration(text).takeIf { it > 0.0 }
        val segmentUrls = text.lineSequence().map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { resolve(url, it) }
            .toList()
        val firstExtinf = Regex("#EXTINF:([\\d.]+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toDoubleOrNull()
        HlsVariantInfo(duration, segmentUrls, firstExtinf?.takeIf { it > 0.0 })
    }.getOrNull()

    private suspend fun measureExactSize(
        videoSegmentUrls: List<String>,
        videoHeaders: Map<String, String>,
        audioSegmentUrls: List<String>,
        audioHeaders: Map<String, String>,
    ): Long? {
        if (videoSegmentUrls.isEmpty()) return null
        val videoTotal = sumContentLengths(videoSegmentUrls, videoHeaders) ?: return null
        val audioTotal = if (audioSegmentUrls.isNotEmpty())
            sumContentLengths(audioSegmentUrls, audioHeaders) ?: 0L else 0L
        val total = videoTotal + audioTotal
        Log.e("SIZE_FINAL", "measureExactSize videoSegs=${videoSegmentUrls.size} audioSegs=${audioSegmentUrls.size} videoTotal=$videoTotal audioTotal=$audioTotal total=$total formatted=${total.let(::formatFileSize)}")
        return total.takeIf { it > 0L }
    }

    private suspend fun sumContentLengths(urls: List<String>, headers: Map<String, String>): Long? {
        val results = withTimeoutOrNull(10_000L) {
            coroutineScope {
                urls.map { url ->
                    async(Dispatchers.IO) {
                        runCatching {
                            hlsHeadSemaphore.withPermit {
                                app.head(url, headers = headers, timeout = 8)
                                    .headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0L }
                            }
                        }.getOrNull()
                    }
                }.awaitAll()
            }
        } ?: emptyList()
        val sum = results.filterNotNull().sum()
        return sum.takeIf { it > 0L }
    }

    private suspend fun probeProgressiveSafe(link: ExtractorLink): List<ProbedQuality> {
        val url = link.url.lowercase(Locale.US)
        // Signed R2/S3 URLs are ephemeral and commonly reject HEAD. Avoid consuming
        // or invalidating the URL during probing and use the original link instead.
        if (url.contains("cloudflarestorage.com") ||
            url.contains("x-amz-signature") ||
            url.contains("x-amz-credential")) {
            Log.d(TAG, "Skipping HEAD for signed URL: ${link.url.take(60)}")
            return fallback(link)
        }
        return try {
            val response = app.head(link.url, headers = requestHeaders(link), timeout = PROBE_TIMEOUT_MS / 1000)
            val contentLength = response.headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0L }
            if (contentLength != null) {
                listOf(ProbedQuality(0, 0, null, link.url, parseQualityFromLinkName(link.name) ?: "Original Quality", contentLength, "prog-${link.url.hashCode()}"))
            } else {
                Log.w(TAG, "Progressive HEAD probe returned no usable Content-Length; using fallback")
                fallback(link)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Progressive HEAD probe failed; using fallback", e)
            fallback(link)
        }
    }

    private fun requestHeaders(link: ExtractorLink): Map<String, String> = buildMap {
        putAll(link.headers)
        put("User-Agent", link.headers["User-Agent"] ?: USER_AGENT)
        if (link.referer.isNotBlank()) put("Referer", link.referer)
    }

    private fun fallback(link: ExtractorLink): List<ProbedQuality> = listOf(
        ProbedQuality(0, 0, null, link.url, "Auto (best available)", selectionKey = "fallback-${link.url.hashCode()}"),
    )

    private fun parseIsoDurationSeconds(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        val match = Regex("PT(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?").matchEntire(value) ?: return null
        val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[3].toDoubleOrNull() ?: 0.0
        return (hours * 3600 + minutes * 60 + seconds).takeIf { it > 0.0 }
    }

    private fun parseQualityFromLinkName(name: String): String? {
        val match = Regex("\\b(144|240|360|480|720|1080|1440|2160)p\\b|\\b4[kK]\\b", RegexOption.IGNORE_CASE).find(name)?.value ?: return null
        return if (match.equals("4k", true)) "4K" else match.lowercase()
    }
}

fun parseQualityFromLinkName(name: String): String? = QualityProbe.run {
    Regex("\\b(144|240|360|480|720|1080|1440|2160)p\\b|\\b4[kK]\\b", RegexOption.IGNORE_CASE).find(name)?.value
        ?.let { if (it.equals("4k", true)) "4K" else it.lowercase() }
}
