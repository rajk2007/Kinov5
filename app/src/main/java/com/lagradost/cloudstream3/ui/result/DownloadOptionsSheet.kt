package com.lagradost.cloudstream3.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ProbedQuality
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.QualityProbe
import com.lagradost.cloudstream3.utils.heightToQualitiesInt
import com.lagradost.cloudstream3.utils.formatFileSize
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.getActualLinkType
import com.lagradost.cloudstream3.utils.downloader.cleanDownloadUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private suspend fun getActualFileSize(link: ExtractorLink): Long? = withContext(Dispatchers.IO) {
    runCatching {
        (URL(link.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 5_000
            readTimeout = 5_000
            instanceFollowRedirects = true
            link.getAllHeaders().forEach { (key, value) -> setRequestProperty(key, value) }
        }.let { connection ->
            try {
                connection.connect()
                connection.contentLengthLong.takeIf { it > 0L }
            } finally {
                connection.disconnect()
            }
        }
    }.getOrNull()
}

fun parseQualityFromLegacyName(name: String): String? =
    Regex("""\.(\d{3,4}p)$""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)

fun parseSizeFromLegacyName(name: String): String? =
    Regex("""(\d+\.?\d*\s*(?:GB|MB|KB))""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)

fun parseLegacyLinkName(name: String): Pair<String?, String?> =
    (parseQualityFromLegacyName(name) ?: parseQualityFromLinkName(name)) to parseSizeFromLegacyName(name)

fun parseKdesaLanguage(name: String): String? =
    Regex("""Orion\s*[·•.]\s*([A-Za-z][\w-]*)""", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.getOrNull(1)?.replaceFirstChar { it.uppercaseChar() }

fun parseKdesaQuality(name: String): String? =
    Regex("""\b(\d{3,4}p)\b""", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)

fun filterKdesaLinks(links: List<ExtractorLink>): List<ExtractorLink> {
    // Kdesa responses can be mixed with other providers. Only apply the Orion
    // restriction when an Orion source is present, so other providers retain
    // their existing download options.
    val hasOrion = links.any { it.name.contains("Orion", ignoreCase = true) }
    return links
        .filter { !hasOrion || it.name.contains("Orion", ignoreCase = true) }
}

fun displayLinkName(name: String): String {
    parseKdesaLanguage(name)?.let { return it }
    if (name.contains("Orion", ignoreCase = true)) {
        return name
            .replace(Regex("""Orion\s*[·•.]\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\d{3,4}p\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }
    val (quality, size) = parseLegacyLinkName(name)
    return when {
        quality != null && size != null -> "$quality · $size"
        quality != null -> quality
        size != null -> size
        else -> name
    }
}

fun parseQualityFromLinkName(name: String): String? {
    val match = Regex("\\b(144|240|360|480|720|1080|1440|2160)p\\b|\\b4[kK]\\b", RegexOption.IGNORE_CASE)
        .find(name)?.value ?: return null
    return if (match.equals("4k", true)) "4K" else match.lowercase()
}

fun parseLanguageFromLinkName(name: String): String? {
    parseKdesaLanguage(name)?.let { return it }
    val afterDot = Regex("""[•·]\s*(?:MB\s+)?([A-Za-z][\w\s-]{1,20})""", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.getOrNull(1)?.trim()
    if (!afterDot.isNullOrBlank() && !afterDot.matches(Regex("""\d{3,4}p|4k""", RegexOption.IGNORE_CASE))) {
        return afterDot.split(Regex("\\s+")).firstOrNull()?.replaceFirstChar { it.uppercase() }
    }
    Regex("\\((\\w+)\\s+Audio\\)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.getOrNull(1)?.let {
        return it.replaceFirstChar { char -> char.uppercase() }
    }
    listOf("Hindi", "English", "Tamil", "Telugu", "Malayalam", "Kannada", "Bengali", "Marathi", "Gujarati", "Punjabi", "Urdu", "Dual", "Multi")
        .firstOrNull { name.contains(it, ignoreCase = true) }?.let { return it }
    return null
}

fun ExtractorLink.effectiveQuality(): Int {
    if (quality != Qualities.Unknown.value && quality != 0) return quality
    return (parseKdesaQuality(name) ?: parseQualityFromLinkName(name))?.let(::getQualityFromName)
        ?: Qualities.Unknown.value
}

fun ExtractorLink.languageKey(): String {
    parseKdesaLanguage(name)?.let { return it }
    parseLanguageFromLinkName(name)?.let { return it }
    val qStr = Qualities.getStringByInt(effectiveQuality()).ifBlank { parseQualityFromLinkName(name) ?: "" }
    return name.replace(source, "", ignoreCase = true).replace(qStr, "", ignoreCase = true)
        .replace(Regex("""\b\d{3,4}\s*p\b|\b4[kK]\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[•·\-–—_|\[\](){ } :]"""), " ")
        .replace(Regex("\\s+"), " ").trim().ifBlank { source.ifBlank { "Unknown" } }
}

data class QualityOption(
    val id: String,
    val label: String,
    val width: Int,
    val height: Int,
    val link: ExtractorLink,
    val variantUrl: String,
    val estimatedSizeBytes: Long? = null,
)

@Composable
fun DownloadOptionsSheet(
    links: List<ExtractorLink>,
    title: String = "",
    apiName: String = "",
    isMovie: Boolean = false,
    onDownload: (ExtractorLink, Int, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val normalized = remember(links) {
        filterKdesaLinks(links).map { link ->
            if (link.effectiveQuality() != link.quality) ExtractorLink(link.source, link.name, link.url, link.referer, link.effectiveQuality(), link.headers, link.extractorData, link.type, link.audioTracks) else link
        }
    }
    val groupedLinks = remember(normalized) { normalized.groupBy { it.languageKey() }.mapValues { (_, group) -> group.distinctBy { it.url }.sortedByDescending { it.effectiveQuality() } }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val languages = groupedLinks.keys.toList()
    var selectedLanguage by remember(languages) { mutableStateOf(languages.firstOrNull() ?: "") }
    val selectedLinks = groupedLinks[selectedLanguage].orEmpty()
    var probedQualities by remember(selectedLanguage) { mutableStateOf<Map<ExtractorLink, List<ProbedQuality>>>(emptyMap()) }
    var actualSizes by remember(selectedLanguage) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var isProbing by remember(selectedLanguage) { mutableStateOf(true) }

    LaunchedEffect(selectedLanguage, selectedLinks) {
        isProbing = true
        android.util.Log.e("QUALITY_UI", "title=$title type=${if (isMovie) "Movie" else "Episode"} api=$apiName")
        android.util.Log.e("QUALITY_UI", "links count=${selectedLinks.size}")
        try {
            probedQualities = selectedLinks.map { link ->
                async {
                    android.util.Log.e("QUALITY_UI", "probing url=${link.url} type=${getActualLinkType(link)}")
                    link to QualityProbe.probeVideoQualities(link)
                }
            }.awaitAll().toMap()
            actualSizes = selectedLinks.mapNotNull { link ->
                async { link.url to getActualFileSize(link) }
            }.awaitAll().mapNotNull { (url, size) -> size?.let { url to it } }.toMap()
        } finally {
            isProbing = false
        }
    }

    val qualityOptions = remember(probedQualities, selectedLinks, actualSizes) {
        probedQualities.flatMap { (link, qualities) ->
            val unknownOnly = qualities.size == 1 && qualities.first().height <= 0
            val shouldOfferPresets = isMovie && unknownOnly && getActualLinkType(link) == "HLS"
            if (shouldOfferPresets) {
                listOf(0, 480, 720, 1080).map { height ->
                    QualityOption(
                        "${link.url}|preset-$height",
                        if (height == 0) "Auto (best available)" else "${height}p",
                        0, height, link, link.url, actualSizes[link.url],
                    )
                }
            } else {
                qualities.map { probed -> QualityOption("${link.url}|${probed.selectionKey}", probed.label, probed.width, probed.height, link, probed.variantUrl, actualSizes[link.url] ?: probed.estimatedSizeBytes) }
            }
        }
            .distinctBy { it.id }.sortedByDescending { it.height }.ifEmpty {
                selectedLinks.map { link ->
                    val parsedDisplay = displayLinkName(link.name)
                    val fallbackQuality = if (parsedDisplay != link.name) parsedDisplay else when {
                        link.quality >= 2160 -> "2160p"
                        link.quality >= 1080 -> "1080p"
                        link.quality >= 720 -> "720p"
                        link.quality >= 480 -> "480p"
                        else -> "Original Quality"
                    }
                    QualityOption("${link.url}|fallback", fallbackQuality, 0, 0, link, link.url, actualSizes[link.url])
                }
            }
    }
    var selectedId by remember(selectedLanguage, qualityOptions) { mutableStateOf(qualityOptions.firstOrNull()?.id.orEmpty()) }
    val selectedOption = qualityOptions.firstOrNull { it.id == selectedId }
    LaunchedEffect(qualityOptions) {
        android.util.Log.e("QUALITY_UI", "displaying options=${qualityOptions.map { it.label }}")
    }

    Column(Modifier.fillMaxWidth().background(Color(0xFF121212)).clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))) {
        Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("Download Options", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
        }
        Text("Language", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(languages) { language ->
                val selected = language == selectedLanguage
                Text(language, color = if (selected) Color.White else Color.LightGray, modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, if (selected) Color(0xFFE50914) else Color(0xFF333333), RoundedCornerShape(8.dp)).background(if (selected) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)).clickable { selectedLanguage = language }.padding(12.dp, 8.dp))
            }
        }
        Text("Quality", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp))
        if (isProbing) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), color = Color(0xFFE50914)); Text("Detecting available qualities…", color = Color.Gray) }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(qualityOptions, key = { it.id }) { option ->
                    val selected = option.id == selectedId
                    val display = if (option.height > 0) "${option.width}×${option.height} (${option.label})" else option.label
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, if (selected) Color(0xFFE50914) else Color(0xFF333333), RoundedCornerShape(8.dp)).background(if (selected) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)).clickable { selectedId = option.id }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(display, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            option.estimatedSizeBytes?.let { bytes ->
                                Text("~${formatFileSize(bytes)} (est.)", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) Color(0xFFE50914) else Color.Gray, CircleShape).padding(3.dp)) { if (selected) Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFE50914))) }
                    }
                }
            }
        }
        Button(onClick = {
            selectedOption?.let { option ->
                val realUrl = cleanDownloadUrl(option.variantUrl)
                val downloadLink = if (realUrl != option.link.url || option.height > 0) ExtractorLink(option.link.source, option.link.name, realUrl, option.link.referer, heightToQualitiesInt(option.height), option.link.headers, option.link.extractorData, option.link.type, option.link.audioTracks) else option.link
                onDownload(downloadLink, option.height, option.estimatedSizeBytes ?: 0L)
            }
        }, enabled = selectedOption != null && !isProbing, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Download", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
