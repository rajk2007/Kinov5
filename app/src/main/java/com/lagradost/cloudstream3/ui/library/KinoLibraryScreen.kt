package com.lagradost.cloudstream3.ui.library

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java.io.File
import java.io.FileInputStream

import com.lagradost.cloudstream3.utils.downloader.DirectDownloadItem
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadManager
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadStatus

private val KinoBackgroundTop = Color(0xFF0D0D0F)
private val KinoBackgroundBottom = Color(0xFF1A1A2E)
private val KinoSurface = Color(0xCC171827)
private val KinoSurfaceSoft = Color(0x991F2032)
private val KinoRed = Color(0xFFE50914)
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
            LibraryHeader()
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
            DownloadsHeader(used = usedBytes, available = availableBytes)
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
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun LibraryHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 15.dp),
    ) {
        Text("KINO", color = KinoRed, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
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
        Text(action, color = KinoRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                color = KinoRed,
                trackColor = KinoTrack,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(cleanLibraryTitle(media.name), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun continueWatchingLabel(media: KinoLibraryItem): String {
    val remaining = (media.duration - media.position).coerceAtLeast(0L)
    val time = "${formatDuration(remaining)} left"
    return time
}

@Composable
private fun DownloadsHeader(used: Long, available: Long) {
    val total = (used + available).coerceAtLeast(1L)
    val usage = (used.toFloat() / total).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 25.dp)) {
        Text("Downloads", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(5.dp))
        Text("Using ${formatBytes(used)} • ${formatBytes(available)} free", color = KinoMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { usage }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = KinoRed, trackColor = KinoTrack)
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
        Box(Modifier.fillMaxWidth().height(2.dp).background(if (selected) KinoRed else Color.Transparent))
    }
}

@Composable
private fun ActiveDownloadCard(item: DirectDownloadItem) {
    DownloadCardShell(onClick = {
        when (item.status) {
            DirectDownloadStatus.DOWNLOADING -> DirectDownloadManager.pauseDownload(item.id)
            DirectDownloadStatus.PAUSED -> DirectDownloadManager.resumeDownload(item.id)
            else -> Unit
        }
    }) {
        AsyncImage(model = item.posterUrl ?: "", contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.size(70.dp, 92.dp).clip(RoundedCornerShape(8.dp)).background(KinoSurface))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(cleanLibraryTitle(item.title), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(downloadQuality(item), color = KinoMuted, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(5.dp))
            Text("${formatBytes(item.downloadedBytes)} / ${formatBytes(item.totalBytes)}", color = KinoMuted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { (item.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)), color = KinoRed, trackColor = KinoTrack)
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    item.status == DirectDownloadStatus.PAUSED -> "Paused · ${formatBytes(item.downloadedBytes)} saved"
                    item.speed.isBlank() -> downloadStatusText(item.status)
                    else -> "${item.speed} · ${item.eta.ifBlank { "Calculating..." }}"
                },
                color = KinoMuted,
                fontSize = 11.sp,
            )
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
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    DownloadCardShell(onClick = onPlay) {
        Box {
            AsyncImage(model = group.posterUrl ?: "", contentDescription = group.title, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp, 112.dp).clip(RoundedCornerShape(8.dp)).background(KinoSurface))
            Row(Modifier.align(Alignment.TopStart).padding(4.dp).background(KinoRed, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Downloaded", tint = Color.White, modifier = Modifier.size(10.dp))
                Text("Downloaded", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(group.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (group.items.size > 1) "Series • ${group.items.size} Episodes" else downloadInfo(group.items.first()), color = KinoMuted, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Downloaded", tint = KinoRed, modifier = Modifier.size(14.dp))
                Text("Downloaded", color = KinoRed, fontSize = 12.sp)
            }
        }
        Box {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Options",
                tint = Color.White,
                modifier = Modifier.size(30.dp).clickable { showMenu = true }.padding(3.dp),
            )
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                DropdownMenuItem(text = { Text("Send to Other Device") }, onClick = {
                    showMenu = false
                    group.items.firstOrNull()?.localUri?.let { sendDownload(context, it, group.title) }
                })
                DropdownMenuItem(text = { Text("Save to Gallery") }, onClick = {
                    showMenu = false
                    group.items.firstOrNull()?.localUri?.let { saveDownloadToGallery(context, it, group.title) }
                })
            }
        }
    }
}

@Composable
private fun DownloadCardShell(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .08f), KinoSurfaceSoft)))
            .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(14.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SmartDownloadsSection() {
    var nextEpisode by remember { mutableStateOf(true) }
    var personalized by remember { mutableStateOf(false) }
    SectionCard("Smart Downloads") {
        ToggleRow("Download Next Episode", nextEpisode) { nextEpisode = it }
        HorizontalDivider(color = Color(0xFF303142))
        ToggleRow("Downloads for You", personalized) { personalized = it }
    }
}

@Composable
private fun SectionCard(title: String, trailing: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(KinoSurface).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (trailing.isNotBlank()) Text(trailing, color = KinoRed, fontSize = 19.sp)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = KinoRed))
    }
}

@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 13.sp)
        Text(value, color = KinoMuted, fontSize = 12.sp)
    }
}

private fun cleanLibraryTitle(title: String): String = title
    .replace(Regex("\\s*-\\s*Episode\\s+0\\b", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s+Episode\\s+0\\b", RegexOption.IGNORE_CASE), "")
    .replace("S0E", "E", ignoreCase = false)
    .trim()

private fun localFile(path: String): File? {
    val uri = Uri.parse(path)
    return if (uri.scheme.isNullOrBlank()) File(path) else if (uri.scheme == "file") uri.path?.let(::File) else null
}

private fun sendDownload(context: Context, path: String, title: String) {
    val file = localFile(path) ?: return
    if (!file.isFile) return
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

private fun saveDownloadToGallery(context: Context, path: String, title: String) {
    val file = localFile(path) ?: return
    if (!file.isFile) return
    val safeName = "${cleanLibraryTitle(title).ifBlank { "download" }}.mp4"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        runCatching {
            resolver.openOutputStream(uri)?.use { output -> FileInputStream(file).use { it.copyTo(output) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure { resolver.delete(uri, null, null) }
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
