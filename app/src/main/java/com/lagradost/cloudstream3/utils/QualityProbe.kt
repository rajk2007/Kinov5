package com.lagradost.cloudstream3.utils

import android.util.Log
import android.util.Xml
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import java.util.Locale

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

fun calculateEstimatedSize(bandwidth: Int?, durationSeconds: Long?): Long? {
    if (bandwidth == null || durationSeconds == null || durationSeconds <= 0) return null
    return bandwidth.toLong().coerceAtMost(Long.MAX_VALUE / durationSeconds) * durationSeconds / 8
}

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> String.format(Locale.US, "%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

object QualityProbe {
    private const val TAG = "QualityProbe"
    private const val PROBE_TIMEOUT_MS = 5_000L

    suspend fun probeVideoQualities(link: ExtractorLink): List<ProbedQuality> = withContext(Dispatchers.IO) {
        logLink(link)
        try {
            withTimeout(PROBE_TIMEOUT_MS) {
                when {
                    link.type == ExtractorLinkType.M3U8 || link.url.contains(".m3u8", true) -> probeHlsQualities(link)
                    link.type == ExtractorLinkType.DASH || link.url.contains(".mpd", true) -> probeDashQualities(link)
                    link.url.contains("manifest", true) || link.url.contains("playlist", true) -> {
                        runCatching { probeHlsQualities(link) }
                            .getOrElse { runCatching { probeDashQualities(link) }.getOrElse { probeProgressiveSafe(link) } }
                    }
                    else -> {
                        // HDH and similar providers sometimes omit the type and extension.
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
    }

    private fun logLink(link: ExtractorLink) {
        Log.e(TAG, "═════════════════════════════")
        Log.e(TAG, "Probing link: name=${link.name}, type=${link.type}, source=${link.source}")
        Log.e(TAG, "URL: ${link.url}")
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
        Log.e(TAG, "Probe content: length=${content.length}, first200=${content.take(200)}")

        if (content.contains("<MPD", true) || content.contains("<?xml", true) && content.contains("MPD", true)) {
            Log.w(TAG, "Response is DASH despite HLS/unknown link metadata; switching parser")
            return parseDashQualities(link, content)
        }
        if (!content.contains("#EXTM3U", true)) {
            throw IllegalArgumentException("Response is not an HLS playlist")
        }

        val lines = content.lines()
        val results = mutableListOf<ProbedQuality>()
        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:", true)) return@forEachIndexed
            val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)", RegexOption.IGNORE_CASE).find(line)
            val bandwidth = Regex("(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\\d+)", RegexOption.IGNORE_CASE)
                .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (resolution != null && next != null) {
                val width = resolution.groupValues[1].toInt()
                val height = resolution.groupValues[2].toInt()
                val variantUrl = resolve(response.url, next)
                val duration = fetchHlsDuration(variantUrl, headers)
                results += ProbedQuality(width, height, bandwidth, variantUrl, heightToQualityLabel(height), calculateEstimatedSize(bandwidth, duration), "hls-${height}-${variantUrl.hashCode()}")
            }
        }
        return results.distinctBy { it.selectionKey }.sortedByDescending { it.height }.ifEmpty { fallback(link) }
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
        var manifestDuration: Long? = null
        var adaptationWidth = 0
        var adaptationHeight = 0
        var adaptationBandwidth: Int? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (parser.name) {
                "MPD" -> manifestDuration = parseIsoDurationSeconds(parser.getAttributeValue(null, "mediaPresentationDuration"))
                "AdaptationSet" -> {
                    adaptationWidth = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0
                    adaptationHeight = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                    adaptationBandwidth = parser.getAttributeValue(null, "bandwidth")?.toIntOrNull()
                }
                "Representation" -> {
                    val width = parser.getAttributeValue(null, "width")?.toIntOrNull() ?: adaptationWidth
                    val height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: adaptationHeight
                    val bandwidth = parser.getAttributeValue(null, "bandwidth")?.toIntOrNull() ?: adaptationBandwidth
                    if (height > 0) {
                        val selectionKey = "dash-${height}-${bandwidth ?: 0}-${results.size}"
                        results += ProbedQuality(width, height, bandwidth, "${link.url}#track=$height", heightToQualityLabel(height), calculateEstimatedSize(bandwidth, manifestDuration), selectionKey)
                    }
                }
            }
        }
        return results.distinctBy { it.selectionKey }.sortedByDescending { it.height }.ifEmpty { fallback(link) }
    }

    private suspend fun fetchHlsDuration(url: String, headers: Map<String, String>): Long? = runCatching {
        val playlist = app.get(url, headers = headers, allowRedirects = true).text
        Regex("#EXTINF:([\\d.]+),").findAll(playlist).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }.toLong().takeIf { it > 0 }
    }.getOrNull()

    private suspend fun probeProgressiveSafe(link: ExtractorLink): List<ProbedQuality> {
        return try {
            val response = app.head(link.url, headers = requestHeaders(link), timeout = PROBE_TIMEOUT_MS / 1000)
            val contentLength = response.headers["Content-Length"]?.toLongOrNull()
            listOf(ProbedQuality(0, 0, null, link.url, parseQualityFromLinkName(link.name) ?: "Original Quality", contentLength, "prog-${link.url.hashCode()}"))
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
        ProbedQuality(0, 0, null, link.url, parseQualityFromLinkName(link.name) ?: "Auto", selectionKey = "fallback-${link.url.hashCode()}")
    )

    private fun resolve(base: String, child: String): String = runCatching { URI(base).resolve(child).toString() }.getOrDefault(child)

    private fun parseIsoDurationSeconds(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val match = Regex("PT(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?").matchEntire(value) ?: return null
        val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[3].toDoubleOrNull() ?: 0.0
        return (hours * 3600 + minutes * 60 + seconds).toLong().takeIf { it > 0 }
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
