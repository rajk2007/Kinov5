package com.lagradost.cloudstream3.utils

import android.media.MediaMetadataRetriever
import android.util.Log
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/** A quality discovered from the media stream rather than from an extractor link name. */
data class ProbedQuality(
    val width: Int,
    val height: Int,
    val bandwidth: Int?,
    val variantUrl: String,
    val label: String,
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

object QualityProbe {
    suspend fun probeVideoQualities(link: ExtractorLink): List<ProbedQuality> = withContext(Dispatchers.IO) {
        runCatching {
            when {
                link.type == ExtractorLinkType.M3U8 || link.url.contains(".m3u8", true) -> probeHls(link)
                link.type == ExtractorLinkType.DASH || link.url.contains(".mpd", true) -> listOf(
                    ProbedQuality(0, 0, null, link.url, "Auto (DASH)")
                )
                else -> probeProgressive(link)
            }
        }.getOrElse { error ->
            Log.w("QualityProbe", "Probe failed for ${link.url.take(120)}", error)
            listOf(ProbedQuality(0, 0, null, link.url, parseQualityFromName(link.name) ?: "Original Quality"))
        }
    }

    private suspend fun probeHls(link: ExtractorLink): List<ProbedQuality> {
        val headers = link.headers + mapOf("User-Agent" to (link.headers["User-Agent"] ?: USER_AGENT)) +
                if (link.referer.isBlank()) emptyMap() else mapOf("Referer" to link.referer)
        val playlist = app.get(link.url, headers = headers).text
        val lines = playlist.lines()
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
                results += ProbedQuality(width, height, bandwidth, resolve(link.url, next), heightToQualityLabel(height))
            }
        }
        return results.distinctBy { it.height to it.variantUrl }.sortedByDescending { it.height }.ifEmpty {
            listOf(ProbedQuality(0, 0, null, link.url, parseQualityFromName(link.name) ?: "Auto"))
        }
    }

    private fun probeProgressive(link: ExtractorLink): List<ProbedQuality> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(link.url, link.headers +
                    if (link.referer.isBlank()) emptyMap() else mapOf("Referer" to link.referer))
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            listOf(ProbedQuality(width, height, null, link.url,
                if (height > 0) heightToQualityLabel(height) else parseQualityFromName(link.name) ?: "Original Quality"))
        } finally {
            retriever.release()
        }
    }

    private fun resolve(base: String, child: String): String = runCatching { URI(base).resolve(child).toString() }.getOrDefault(child)

    private fun parseQualityFromName(name: String): String? {
        val match = Regex("\\b(144|240|360|480|720|1080|1440|2160)p\\b|\\b4[kK]\\b").find(name)
            ?.value ?: return null
        return if (match.equals("4k", true)) "4K" else match.lowercase()
    }
}
