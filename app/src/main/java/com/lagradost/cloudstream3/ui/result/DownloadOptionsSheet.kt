package com.lagradost.cloudstream3.ui.result

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

fun ExtractorLink.languageKey(): String {
    val q = Qualities.getStringByInt(quality)
    return name
        .replace(source, "", ignoreCase = true)
        .replace(q, "", ignoreCase = true)
        .replace(Regex("""\b\d{3,4}\s*p\b""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifBlank { source.ifBlank { "Unknown" } }
}

@Composable
fun DownloadOptionsSheet(
    links: List<ExtractorLink>,
    onDownload: (ExtractorLink) -> Unit,
    onDismiss: () -> Unit,
) {
    val byLang = remember(links) {
        links.groupBy { it.languageKey() }
            .mapValues { (_, g) -> g.distinctBy { it.quality to it.url }.sortedByDescending { it.quality } }
    }
    var language by remember { mutableStateOf(byLang.keys.firstOrNull().orEmpty()) }
    val qualities = byLang[language].orEmpty()
    var selected by remember(language) { mutableStateOf(qualities.firstOrNull()) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Download Options", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text("Language")
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            byLang.keys.sorted().forEach { lang ->
                FilterChip(
                    selected = language == lang,
                    onClick = {
                        language = lang
                        selected = byLang[lang]?.firstOrNull()
                    },
                    label = { Text(lang) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Quality")
        qualities.forEach { link ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { selected = link }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${Qualities.getStringByInt(link.quality)} · ${link.source}")
                RadioButton(
                    selected = selected?.url == link.url && selected?.quality == link.quality,
                    onClick = { selected = link },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { selected?.let(onDownload) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Download") }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}
