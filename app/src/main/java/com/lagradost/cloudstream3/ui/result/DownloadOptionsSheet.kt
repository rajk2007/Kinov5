package com.lagradost.cloudstream3.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.lagradost.cloudstream3.utils.downloader.cleanDownloadUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

fun parseQualityFromLinkName(name: String): String? {
    val match = Regex("\\b(144|240|360|480|720|1080|1440|2160)p\\b|\\b4[kK]\\b", RegexOption.IGNORE_CASE)
        .find(name)?.value ?: return null
    return if (match.equals("4k", true)) "4K" else match.lowercase()
}

fun parseLanguageFromLinkName(name: String): String? {
    val afterDot = Regex("""[•·]\s*(?:MB\s+)?([A-Za-z][\w\s-]{1,20})""", RegexOption.IGNORE_CASE)
        .find(name)?.groupValues?.getOrNull(1)?.trim()
    if (!afterDot.isNullOrBlank() && !afterDot.matches(Regex("""\d{3,4}p|4k""", RegexOption.IGNORE_CASE))) {
        return afterDot.split(Regex("\\s+")).firstOrNull()?.replaceFirstChar { it.uppercase() }
    }
    listOf("Hindi", "English", "Tamil", "Telugu", "Malayalam", "Kannada", "Bengali", "Marathi", "Gujarati", "Punjabi", "Urdu", "Dual", "Multi")
        .firstOrNull { name.contains(it, ignoreCase = true) }?.let { return it }
    return null
}

fun ExtractorLink.effectiveQuality(): Int {
    if (quality != Qualities.Unknown.value && quality != 0) return quality
    return parseQualityFromLinkName(name)?.let(::getQualityFromName) ?: Qualities.Unknown.value
}

fun ExtractorLink.languageKey(): String {
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
fun DownloadOptionsSheet(links: List<ExtractorLink>, onDownload: (ExtractorLink, Int) -> Unit, onDismiss: () -> Unit) {
    val normalized = remember(links) { links.map { link -> if (link.effectiveQuality() != link.quality) ExtractorLink(link.source, link.name, link.url, link.referer, link.effectiveQuality(), link.headers, link.extractorData, link.type, link.audioTracks) else link } }
    val groupedLinks = remember(normalized) { normalized.groupBy { it.languageKey() }.mapValues { (_, group) -> group.distinctBy { it.url }.sortedByDescending { it.effectiveQuality() } }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    val languages = groupedLinks.keys.toList()
    var selectedLanguage by remember(languages) { mutableStateOf(languages.firstOrNull() ?: "") }
    val selectedLinks = groupedLinks[selectedLanguage].orEmpty()
    var probedQualities by remember(selectedLanguage) { mutableStateOf<Map<ExtractorLink, List<ProbedQuality>>>(emptyMap()) }
    var isProbing by remember(selectedLanguage) { mutableStateOf(true) }

    LaunchedEffect(selectedLanguage, selectedLinks) {
        isProbing = true
        try {
            withTimeout(8_000L) {
                probedQualities = selectedLinks.map { link -> async { link to QualityProbe.probeVideoQualities(link) } }.awaitAll().toMap()
            }
        } catch (_: TimeoutCancellationException) {
            probedQualities = selectedLinks.associateWith { link ->
                listOf(ProbedQuality(0, 0, null, link.url, parseQualityFromLinkName(link.name) ?: "Auto", selectionKey = "timeout-${link.url.hashCode()}"))
            }
        } finally {
            isProbing = false
        }
    }

    val qualityOptions = remember(probedQualities, selectedLinks) {
        probedQualities.flatMap { (link, qualities) -> qualities.map { probed -> QualityOption("${link.url}|${probed.selectionKey}", probed.label, probed.width, probed.height, link, probed.variantUrl, probed.estimatedSizeBytes) } }
            .distinctBy { it.id }.sortedByDescending { it.height }.ifEmpty {
                selectedLinks.map { link -> QualityOption("${link.url}|fallback", parseQualityFromLinkName(link.name) ?: "Original Quality", 0, 0, link, link.url) }
            }
    }
    var selectedId by remember(selectedLanguage, qualityOptions) { mutableStateOf(qualityOptions.firstOrNull()?.id.orEmpty()) }
    val selectedOption = qualityOptions.firstOrNull { it.id == selectedId }

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
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualityOptions.forEach { option ->
                    val selected = option.id == selectedId
                    val display = if (option.height > 0) "${option.width}×${option.height} (${option.label})" else option.label
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).border(1.dp, if (selected) Color(0xFFE50914) else Color(0xFF333333), RoundedCornerShape(8.dp)).background(if (selected) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)).clickable { selectedId = option.id }.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(display, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            option.estimatedSizeBytes?.let { bytes ->
                                Text("≈ ${formatFileSize(bytes)}", color = Color.Gray, fontSize = 12.sp)
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
                onDownload(downloadLink, option.height)
            }
        }, enabled = selectedOption != null && !isProbing, modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Download", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
