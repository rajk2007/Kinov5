package com.lagradost.cloudstream3.ui.library

import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadItem
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadManager
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadStatus

private val KinoBackgroundTop = Color(0xFF0D0D0F)
private val KinoBackgroundBottom = Color(0xFF1A1A2E)
private val KinoSurface = Color(0xCC171827)
private val KinoSurfaceSoft = Color(0x991F2032)
private val KinoAmber = Color(0xFFFFB74D)
private val KinoAmberBright = Color(0xFFFFA726)
private val KinoTeal = Color(0xFF26A69A)
private val KinoTealLight = Color(0xFF4DB6AC)
private val KinoMuted = Color(0xFF92939D)
private val KinoTrack = Color(0xFF343442)

@Composable
fun KinoLibraryScreen(
    viewModel: KinoLibraryViewModel = viewModel(),
    onMediaClick: (KinoLibraryItem) -> Unit,
    onSearchClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
) {
    val continueWatching by viewModel.continueWatching.collectAsState()
    val legacyDownloads by viewModel.downloads.collectAsState()
    val directDownloads by DirectDownloadManager.activeDownloads.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var smartDownloadsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.loadData(context) }

    val directItems = remember(directDownloads) { directDownloads.values.toList() }
    val directTitles = remember(directItems) { directItems.map { it.title }.toSet() }
    val legacyOnly = remember(legacyDownloads, directTitles) {
        legacyDownloads.filterNot { it.name in directTitles }
    }
    val downloading: List<Any> = remember(directItems, legacyOnly) {
        directItems.filter {
            it.status == DirectDownloadStatus.DOWNLOADING ||
                it.status == DirectDownloadStatus.PENDING ||
                it.status == DirectDownloadStatus.PAUSED
        } + legacyOnly.filter { it.downloadStatus != DirectDownloadStatus.COMPLETED }
    }
    val downloaded: List<Any> = remember(directItems, legacyOnly) {
        directItems.filter { it.status == DirectDownloadStatus.COMPLETED } +
            legacyOnly.filter {
                it.downloadStatus == DirectDownloadStatus.COMPLETED ||
                    (it.downloadStatus == null && it.localUri != null)
            }
    }
    val availableBytes = remember(context) {
        runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)
    }
    val usedBytes = remember(directItems, legacyDownloads) {
        (directItems.sumOf { it.downloadedBytes } + legacyDownloads.sumOf { it.downloadedBytes })
            .coerceAtLeast(0L)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(KinoBackgroundTop, KinoBackgroundBottom))
        ),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            LibraryHeader(onSearchClick = onSearchClick, onProfileClick = onProfileClick)
        }
        item {
            SectionHeading("Continue Watching", "See All (${continueWatching.size})")
        }
        if (continueWatching.isEmpty()) {
            item { EmptyMessage("Your watchlist is empty. Start watching something epic.") }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(continueWatching) { media ->
                        ContinueWatchingCard(media) { onMediaClick(media) }
                    }
                }
            }
        }
        item {
            DownloadsHeader(
                used = usedBytes,
                available = availableBytes,
                smartDownloadsEnabled = smartDownloadsEnabled,
                onSmartDownloadsToggle = { smartDownloadsEnabled = it },
            )
        }
        item {
            DownloadTabs(
                selectedTab = selectedTab,
                downloadingCount = downloading.size,
                downloadedCount = downloaded.size,
                onTabSelected = { selectedTab = it },
            )
        }
        if (selectedTab == 0) {
            if (downloading.isEmpty()) {
                item { EmptyMessage("No active downloads. Download something to watch offline.") }
            } else {
                items(downloading) { item ->
                    when (item) {
                        is DirectDownloadItem -> ActiveDownloadCard(item)
                        is KinoLibraryItem -> QueueDownloadCard(item)
                    }
                }
            }
        } else {
            if (downloaded.isEmpty()) {
                item { EmptyMessage("No downloads yet. Your offline library will appear here.") }
            } else {
                items(groupDownloads(downloaded)) { group ->
                    DownloadedCard(
                        group = group,
                        onPlay = { group.items.firstOrNull()?.let(onMediaClick) },
                        onDelete = { group.directIds.forEach(DirectDownloadManager::cancelDownload) },
                    )
                }
            }
        }
        item { SmartDownloadsSection() }
        item { DownloadSettingsSection() }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun LibraryHeader(onSearchClick: () -> Unit, onProfileClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("KINO", color = KinoAmber, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = Color.White,
                modifier = Modifier.size(24.dp).clickable(onClick = onSearchClick),
            )
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(Color(0xFF2A2A4A))
                    .border(1.dp, KinoAmber, CircleShape)
                    .clickable(onClick = onProfileClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Person, contentDescription = "Profile", tint = KinoAmber, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(action, color = KinoAmber, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ContinueWatchingCard(media: KinoLibraryItem, onClick: () -> Unit) {
    val progress = if (media.duration > 0L) (media.position.toFloat() / media.duration).coerceIn(0f, 1f) else 0f
    Column(Modifier.width(165.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(224.dp).clip(RoundedCornerShape(14.dp)).background(KinoSurface)) {
            AsyncImage(
                model = media.posterUrl ?: "",
                contentDescription = media.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = .62f))
                    .border(1.dp, Color.White.copy(alpha = .35f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume Play", tint = Color.White, modifier = Modifier.size(16.dp))
                    Text("Resume Play", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(19.dp))
            Text(
                continueWatchingLabel(media),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = .72f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                color = KinoAmberBright,
                trackColor = KinoTrack,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(media.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(if (media.episodeId != null) "Drama / Series" else "Movie", color = KinoMuted, fontSize = 11.sp, maxLines = 1)
    }
}

private fun continueWatchingLabel(media: KinoLibraryItem): String {
    val remaining = (media.duration - media.position).coerceAtLeast(0L)
    val time = "${formatDuration(remaining)} left"
    return if (media.episodeId != null) "E${media.episodeId} • $time" else time
}

@Composable
private fun DownloadsHeader(used: Long, available: Long, smartDownloadsEnabled: Boolean, onSmartDownloadsToggle: (Boolean) -> Unit) {
    val total = (used + available).coerceAtLeast(1L)
    val usage = (used.toFloat() / total).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 25.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Downloads", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Smart Downloads", color = KinoAmber, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Switch(
                    checked = smartDownloadsEnabled,
                    onCheckedChange = onSmartDownloadsToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KinoAmberBright, uncheckedThumbColor = KinoMuted, uncheckedTrackColor = KinoTrack),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text("Using ${formatBytes(used)} • ${formatBytes(available)} free", color = KinoMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { usage }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = KinoAmber, trackColor = KinoTrack)
    }
}

@Composable
private fun DownloadTabs(selectedTab: Int, downloadingCount: Int, downloadedCount: Int, onTabSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        TabItem("Downloading" + if (downloadingCount > 0) " ($downloadingCount)" else "", selectedTab == 0, { onTabSelected(0) }, Modifier.weight(1f))
        TabItem("Downloaded" + if (downloadedCount > 0) " ($downloadedCount)" else "", selectedTab == 1, { onTabSelected(1) }, Modifier.weight(1f))
    }
}

@Composable
private fun TabItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = if (selected) Color.White else KinoMuted, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) KinoAmber else Color.Transparent))
    }
}

@Composable
private fun ActiveDownloadCard(item: DirectDownloadItem) {
    DownloadCardShell {
        AsyncImage(model = item.posterUrl ?: "", contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.size(70.dp, 92.dp).clip(RoundedCornerShape(8.dp)).background(KinoSurface))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(downloadQuality(item), color = KinoMuted, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text("${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}", color = KinoMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { (item.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = KinoAmberBright, trackColor = KinoTrack)
            Spacer(Modifier.height(4.dp))
            Text(if (item.speed.isBlank()) downloadStatusText(item.status) else "${item.speed} · ${downloadEta(item)}", color = KinoMuted, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (item.status == DirectDownloadStatus.DOWNLOADING) "Ⅱ" else "▶", color = Color.White, fontSize = 18.sp, modifier = Modifier.clickable {
                if (item.status == DirectDownloadStatus.DOWNLOADING) DirectDownloadManager.pauseDownload(item.id) else DirectDownloadManager.resumeDownload(item.id)
            }.padding(5.dp))
            Icon(Icons.Default.Delete, contentDescription = "Delete download", tint = KinoMuted, modifier = Modifier.size(18.dp).clickable { DirectDownloadManager.cancelDownload(item.id) }.padding(2.dp))
        }
    }
}

@Composable
private fun QueueDownloadCard(item: KinoLibraryItem) {
    DownloadCardShell {
        AsyncImage(model = item.posterUrl ?: "", contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp, 70.dp).clip(RoundedCornerShape(7.dp)).background(KinoSurface))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (item.downloadStatus == DirectDownloadStatus.PAUSED) "Paused" else "Waiting in queue", color = KinoMuted, fontSize = 12.sp)
        }
        Text("✕", color = KinoMuted, fontSize = 17.sp)
    }
}

@Composable
private fun DownloadedCard(group: DownloadGroup, onPlay: () -> Unit, onDelete: () -> Unit) {
    DownloadCardShell {
        Box {
            AsyncImage(model = group.posterUrl ?: "", contentDescription = group.title, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp, 112.dp).clip(RoundedCornerShape(8.dp)).background(KinoSurface))
            Row(Modifier.align(Alignment.TopStart).padding(4.dp).background(KinoTeal, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Downloaded", tint = Color.White, modifier = Modifier.size(10.dp))
                Text("Downloaded", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable(onClick = onPlay)) {
            Text(group.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (group.items.size > 1) "Series • ${group.items.size} Episodes" else downloadInfo(group.items.first()), color = KinoMuted, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = KinoTealLight, modifier = Modifier.size(14.dp))
                Text("Downloaded", color = KinoTealLight, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Delete, contentDescription = "Delete download", tint = KinoMuted, modifier = Modifier.size(19.dp).clickable(onClick = onDelete).padding(2.dp))
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = KinoMuted, modifier = Modifier.size(20.dp).padding(top = 8.dp))
        }
    }
}

@Composable
private fun DownloadCardShell(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp)).background(KinoSurfaceSoft)
            .border(1.dp, KinoTeal.copy(alpha = .42f), RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SmartDownloadsSection() {
    var nextEpisode by remember { mutableStateOf(true) }
    var personalized by remember { mutableStateOf(false) }
    SectionCard("Smart Downloads") {
        ToggleRow("Download Next Episode", "Auto-download next episode", nextEpisode) { nextEpisode = it }
        HorizontalDivider(color = Color(0xFF303142))
        ToggleRow("Downloads for You", "Personalized on Wi-Fi", personalized) { personalized = it }
    }
}

@Composable
private fun DownloadSettingsSection() {
    var wifiOnly by remember { mutableStateOf(true) }
    var downloadNext by remember { mutableStateOf(true) }
    SectionCard("Download Settings", trailing = "⚙") {
        SettingRow("Download Quality", "Auto  ›")
        SettingRow("Wi-Fi Only", if (wifiOnly) "On" else "Off") { wifiOnly = !wifiOnly }
        SettingRow("Download Next Episode", if (downloadNext) "On" else "Off") { downloadNext = !downloadNext }
        SettingRow("Storage Location", "Internal  ›")
        SettingRow("Delete Watched Downloads", "›")
    }
}

@Composable
private fun SectionCard(title: String, trailing: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(KinoSurface).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (trailing.isNotBlank()) Text(trailing, color = KinoAmber, fontSize = 19.sp)
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
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KinoAmberBright))
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 13.sp)
        Text(value, color = KinoMuted, fontSize = 12.sp)
    }
}

private data class DownloadGroup(val title: String, val posterUrl: String?, val items: List<KinoLibraryItem>, val directIds: List<String> = emptyList())

private fun groupDownloads(items: List<Any>): List<DownloadGroup> = items.mapNotNull { item ->
    when (item) {
        is KinoLibraryItem -> DownloadGroup(item.name, item.posterUrl, listOf(item))
        is DirectDownloadItem -> DownloadGroup(item.title, item.posterUrl, listOf(item.toLibraryItem()), listOf(item.id))
        else -> null
    }
}.groupBy { it.title }.map { (title, groups) ->
    DownloadGroup(title, groups.first().posterUrl, groups.flatMap { it.items }, groups.flatMap { it.directIds })
}

private fun DirectDownloadItem.toLibraryItem() = KinoLibraryItem(name = title, url = url, apiName = apiName, posterUrl = posterUrl, downloadedBytes = downloadedBytes, totalBytes = totalBytes, progress = progress / 100f, localUri = filePath, downloadStatus = status)
private fun downloadQuality(item: DirectDownloadItem): String = if (item.selectedHeight > 0) "${item.selectedHeight}p · Direct" else "Direct download"
private fun downloadInfo(item: KinoLibraryItem): String {
    val availability = if (item.localUri != null) "Offline" else "Downloaded"
    return "${formatBytes(item.totalBytes)} · $availability"
}
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
