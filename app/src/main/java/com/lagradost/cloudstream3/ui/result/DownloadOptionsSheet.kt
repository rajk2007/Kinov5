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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.HighQuality
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
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities

// Helper to group links by language without splitting by quality
fun ExtractorLink.languageKey(): String {
    val q = Qualities.getStringByInt(quality)
    return name
        .replace(source, "", ignoreCase = true)
        .replace(q, "", ignoreCase = true)
        .replace(Regex("""\b\d{3,4}\s*p\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\b\d{3,4}\b"""), "")
        .replace(Regex("""[-–—_|\[\](){}:]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { source.ifBlank { "Unknown" } }
}

@Composable
fun DownloadOptionsSheet(
    links: List<ExtractorLink>,
    onDownload: (ExtractorLink) -> Unit,
    onDismiss: () -> Unit
) {
    // Group by language, then deduplicate by quality within that language
    val groupedLinks = remember(links) {
        links.groupBy { it.languageKey() }
            .mapValues { (_, g) -> g.distinctBy { it.quality }.sortedByDescending { it.quality } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    val languages = groupedLinks.keys.toList()
    var selectedLanguage by remember { mutableStateOf(languages.firstOrNull() ?: "") }
    val qualities = groupedLinks[selectedLanguage] ?: emptyList()
    var selectedQuality by remember(selectedLanguage) { mutableStateOf(qualities.firstOrNull()?.quality ?: 0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212)) // Near black background
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        // Drag Handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Gray)
        )

        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Download Options", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Language Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Language, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Language", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(languages) { lang ->
                val isSelected = lang == selectedLanguage
                val bgColor = if (isSelected) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)
                val borderColor = if (isSelected) Color(0xFFE50914) else Color(0xFF333333)
                val textColor = if (isSelected) Color.White else Color.LightGray

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .clickable { selectedLanguage = lang }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier.size(16.dp).clip(CircleShape).background(Color(0xFFE50914))
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(lang, color = textColor, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quality Section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.HighQuality, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Quality", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            qualities.forEach { link ->
                val isSelected = link.quality == selectedQuality
                val bgColor = if (isSelected) Color(0xFF2A2A2A) else Color(0xFF1A1A1A)
                val borderColor = if (isSelected) Color(0xFFE50914) else Color(0xFF333333)
                val qualityStr = Qualities.getStringByInt(link.quality)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .background(bgColor)
                        .clickable { selectedQuality = link.quality }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(qualityStr, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(link.source, color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    // Radio Indicator
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (isSelected) Color(0xFFE50914) else Color.Gray, CircleShape)
                            .padding(3.dp)
                    ) {
                        if (isSelected) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xFFE50914)))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Download Button
        Button(
            onClick = {
                qualities.find { it.quality == selectedQuality }?.let { onDownload(it) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Download", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
