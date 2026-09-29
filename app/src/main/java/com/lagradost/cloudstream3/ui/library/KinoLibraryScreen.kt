package com.lagradost.cloudstream3.ui.library

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadItem
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadManager
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadStatus

private val KinoBlack = Color(0xFF0A0A0A)
private val KinoCard = Color(0xFF1A1A1A)
private val KinoRed = Color(0xFFE50914)
private val KinoMuted = Color(0xFF929292)
private val KinoGreen = Color(0xFF69C174)

@Composable
fun KinoLibraryScreen(
    viewModel: KinoLibraryViewModel = viewModel(),
    onMediaClick: (KinoLibraryItem) -> Unit,
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val legacyDownloads by viewModel.downloads.collectAsState()
    val directDownloads by DirectDownloadManager.activeDownloads.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { viewModel.loadData(context) }

    val directItems = remember(directDownloads) { directDownloads.values.toList() }
    val directTitles = remember(directItems) { directItems.map { it.title }.toSet() }
    val legacyOnly = remember(legacyDownloads, directTitles) {
        legacyDownloads.filterNot { it.name in directTitles }
    }
    val downloading = remember(directItems, legacyOnly) {
        directItems.filter { it.status == DirectDownloadStatus.DOWNLOADING ||
            it.status == DirectDownloadStatus.PENDING || it.status == DirectDownloadStatus.PAUSED } +
            legacyOnly.filter { it.downloadStatus != DirectDownloadStatus.COMPLETED }
    }
    val downloaded = remember(directItems, legacyOnly) {
        directItems.filter { it.status == DirectDownloadStatus.COMPLETED } +
            legacyOnly.filter { it.downloadStatus == DirectDownloadStatus.COMPLETED ||
                it.downloadStatus == null && it.localUri != null }
    }
    val availableBytes = remember(context) {
        runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
    }
    val usedBytes = remember(directItems, legacyDownloads) {
        (directItems.sumOf { it.downloadedBytes } + legacyDownloads.sumOf { it.downloadedBytes }).coerceAtLeast(0L)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(KinoBlack),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Spacer(Modifier.height(12.dp))
                Text("My Library", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(22.dp))
                Text("Continue Watching", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
            }
        }
        if (continueWatching.isEmpty()) {
            item { EmptyMessage("Your watchlist is empty. Start watching something epic.") }
        } else {
            item {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(continueWatching) { media ->
                        ContinueWatchingCard(media) { onMediaClick(media) }
                    }
                }
            }
        }
        item { DownloadsHeader(usedBytes, availableBytes) }
        item {
            DownloadTabs(
                selectedTab = selectedTab,
                downloadingCount = downloading.size,
                downloadedCount = downloaded.size,
                onTabSelected = { selectedTab = it },
            )
        }
        if (selectedTab == 0) {
            if (downloading.isEmpty()) item { EmptyMessage("No active downloads. Download something to watch offline.") }
            items(downloading) { item ->
                when (item) {
                    is DirectDownloadItem -> ActiveDownloadCard(item)
                    is KinoLibraryItem -> QueueDownloadCard(item)
                }
            }
        } else {
            if (downloaded.isEmpty()) item { EmptyMessage("No downloads yet. Your offline library will appear here.") }
            items(groupDownloads(downloaded)) { group ->
                DownloadedCard(group) { group.items.firstOrNull()?.let(onMediaClick) }
            }
        }
        item { SmartDownloadsSection() }
        item { DownloadSettingsSection() }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun ContinueWatchingCard(media: KinoLibraryItem, onClick: () -> Unit) {
    val progress = if (media.duration > 0L) (media.position.toFloat() / media.duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = media.posterUrl ?: "", contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(140.dp, 196.dp).clip(RoundedCornerShape(10.dp)).background(KinoCard),
        )
        Spacer(Modifier.height(7.dp))
        Text(media.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(5.dp))
        LinearProgressIndicator(
            progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = KinoRed, trackColor = Color(0xFF353535),
        )
        Spacer(Modifier.height(4.dp))
        Text(continueWatchingLabel(media), color = KinoMuted, fontSize = 11.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

private fun continueWatchingLabel(media: KinoLibraryItem): String {
    val remaining = (media.duration - media.position).coerceAtLeast(0L)
    val time = formatDuration(remaining)
    return if (media.episodeId != null) "S1 E${media.episodeId} · $time left" else "$time left"
}

@Composable
private fun DownloadsHeader(used: Long, available: Long) {
    val total = (used + available).coerceAtLeast(1L)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Downloads", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("⚙", color = Color.White, fontSize = 22.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text("${formatBytes(used)} used · ${formatBytes(available)} available", color = KinoMuted, fontSize = 12.sp)
        Spacer(Modifier.height(9.dp))
        LinearProgressIndicator(
            progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = KinoRed, trackColor = Color(0xFF333333),
        )
    }
}

@Composable
private fun DownloadTabs(selectedTab: Int, downloadingCount: Int, downloadedCount: Int, onTabSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TabItem("Downloading" + if (downloadingCount > 0) " ($downloadingCount)" else "", selectedTab == 0,
            { onTabSelected(0) }, Modifier.weight(1f))
        TabItem("Downloaded" + if (downloadedCount > 0) " ($downloadedCount)" else "", selectedTab == 1,
            { onTabSelected(1) }, Modifier.weight(1f))
    }
}

@Composable
private fun TabItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = if (selected) Color.White else KinoMuted, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.width(if (selected) 48.dp else 0.dp).height(2.dp).background(KinoRed))
    }
}

@Composable
private fun ActiveDownloadCard(item: DirectDownloadItem) {
    DownloadCardShell {
        AsyncImage(model = item.posterUrl ?: "", contentDescription = item.title, contentScale = ContentScale.Crop,
            modifier = Modifier.size(70.dp, 92.dp).clip(RoundedCornerShape(8.dp)).background(KinoCard))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(downloadQuality(item), color = KinoMuted, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text("${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}", color = KinoMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = KinoRed, trackColor = Color(0xFF3A3A3A))
            Spacer(Modifier.height(4.dp))
            Text(if (item.speed.isBlank()) downloadStatusText(item.status) else "${item.speed} · ${downloadEta(item)}", color = KinoMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(4.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (item.status == DirectDownloadStatus.DOWNLOADING) "⏸" else "▶", color = Color.White, fontSize = 18.sp,
                modifier = Modifier.clickable {
                    if (item.status == DirectDownloadStatus.DOWNLOADING) DirectDownloadManager.pauseDownload(item.id)
                    else DirectDownloadManager.resumeDownload(item.id)
                }.padding(5.dp))
            Text("⋮", color = KinoMuted, fontSize = 20.sp, modifier = Modifier.padding(5.dp))
        }
    }
}

@Composable
private fun QueueDownloadCard(item: KinoLibraryItem) {
    DownloadCardShell {
        AsyncImage(model = item.posterUrl ?: "", contentDescription = item.name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(54.dp, 70.dp).clip(RoundedCornerShape(7.dp)).background(KinoCard))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (item.downloadStatus == DirectDownloadStatus.PAUSED) "Paused" else "Waiting in queue", color = KinoMuted, fontSize = 12.sp)
        }
        Text("✕", color = KinoMuted, fontSize = 17.sp)
    }
}

@Composable
private fun DownloadedCard(group: DownloadGroup, onPlay: () -> Unit) {
    DownloadCardShell {
        AsyncImage(model = group.posterUrl ?: "", contentDescription = group.title, contentScale = ContentScale.Crop,
            modifier = Modifier.size(88.dp, 112.dp).clip(RoundedCornerShape(8.dp)).background(KinoCard))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (group.items.size > 1) "Season 1 · ${group.items.size} episodes" else downloadInfo(group.items.first()), color = KinoMuted, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            Text("✓ Downloaded · Available offline", color = KinoGreen, fontSize = 12.sp)
        }
        Text("▶", color = KinoRed, fontSize = 25.sp, modifier = Modifier.clickable(onClick = onPlay).padding(7.dp))
        Text("⋮", color = KinoMuted, fontSize = 20.sp, modifier = Modifier.padding(5.dp))
    }
}

@Composable
private fun DownloadCardShell(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(KinoCard).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun SmartDownloadsSection() {
    var nextEpisode by remember { mutableStateOf(true) }
    var personalized by remember { mutableStateOf(false) }
    SectionCard("Smart Downloads") {
        ToggleRow("Download Next Episode", "Auto-download next episode", nextEpisode) { nextEpisode = it }
        HorizontalDivider(color = Color(0xFF303030))
        ToggleRow("Downloads for You", "Personalized on Wi-Fi", personalized) { personalized = it }
    }
}

@Composable
private fun DownloadSettingsSection() {
    var wifiOnly by remember { mutableStateOf(true) }
    SectionCard("Download Settings", trailing = "⚙") {
        SettingRow("Download Quality", "Auto")
        SettingRow("Wi-Fi Only", if (wifiOnly) "On" else "Off") { wifiOnly = !wifiOnly }
        SettingRow("Storage Location", "Internal")
        SettingRow("Delete Watched Downloads", "›")
    }
}

@Composable
private fun SectionCard(title: String, trailing: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(KinoCard).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (trailing.isNotBlank()) Text(trailing, color = Color.White, fontSize = 19.sp)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            Text(description, color = KinoMuted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KinoRed))
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null, onClick = { onClick?.invoke() }).padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 13.sp)
        Text(value, color = KinoMuted, fontSize = 12.sp)
    }
}

private data class DownloadGroup(val title: String, val posterUrl: String?, val items: List<KinoLibraryItem>)

private fun groupDownloads(items: List<Any>): List<DownloadGroup> = items.mapNotNull { item ->
    when (item) {
        is KinoLibraryItem -> DownloadGroup(item.name, item.posterUrl, listOf(item))
        is DirectDownloadItem -> DownloadGroup(item.title, item.posterUrl, listOf(item.toLibraryItem()))
        else -> null
    }
}.groupBy { it.title }.map { (title, groups) -> DownloadGroup(title, groups.first().posterUrl, groups.flatMap { it.items }) }

private fun DirectDownloadItem.toLibraryItem() = KinoLibraryItem(name = title, url = url, apiName = apiName, posterUrl = posterUrl,
    downloadedBytes = downloadedBytes, totalBytes = totalBytes, progress = progress / 100f, localUri = filePath, downloadStatus = status)

private fun downloadQuality(item: DirectDownloadItem): String = if (item.selectedHeight > 0) "${item.selectedHeight}p · Direct" else "Direct download"
private fun downloadInfo(item: KinoLibraryItem): String = "${formatBytes(item.totalBytes)} · ${if (item.localUri != null) "Offline" else "Downloaded"}"
private fun downloadStatusText(status: DirectDownloadStatus): String = when (status) {
    DirectDownloadStatus.DOWNLOADING -> "Downloading..."
    DirectDownloadStatus.PAUSED -> "Paused"
    DirectDownloadStatus.PENDING -> "Waiting in queue"
    DirectDownloadStatus.FAILED -> "Download failed"
    DirectDownloadStatus.COMPLETED -> "Completed"
}
private fun downloadEta(item: DirectDownloadItem): String = if (item.totalBytes > 0L && item.speed.isNotBlank()) "${formatDuration(((item.totalBytes - item.downloadedBytes).coerceAtLeast(0L) * 1000L) / 1_000_000L)} left" else "Calculating ETA"
private fun formatDuration(milliseconds: Long): String {
    val minutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    val remainder = minutes % 60L
    return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
}
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
    else -> "$bytes B"
}

@Composable
private fun EmptyMessage(text: String) {
    Text(text, color = KinoMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
