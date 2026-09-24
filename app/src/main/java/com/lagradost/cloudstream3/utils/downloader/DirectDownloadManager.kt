package com.lagradost.cloudstream3.utils.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class DirectDownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val fileName: String,
    val posterUrl: String?,
    val apiName: String,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val status: DirectDownloadStatus = DirectDownloadStatus.PENDING,
    val filePath: String? = null,
    val speed: String = "",
    val error: String? = null,
    val selectedHeight: Int = 0,
)

enum class DirectDownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

private const val MIN_VALID_VIDEO_BYTES = 1024L * 1024L

/** DASH is not currently supported; HLS is downloaded segment-by-segment. */
fun isUnsupportedDirectDownload(link: ExtractorLink, url: String = link.url): Boolean =
    link.type == ExtractorLinkType.DASH || url.contains(".mpd", ignoreCase = true)

/** Direct file downloader that intentionally bypasses the queue service. */
object DirectDownloadManager {
    private const val TAG = "DirectDownload"
    private const val CHANNEL_ID = "kino_downloads"

    private val _activeDownloads = MutableStateFlow<Map<String, DirectDownloadItem>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DirectDownloadItem>> = _activeDownloads.asStateFlow()
    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel()
    }

    fun startDownload(
        context: Context,
        link: ExtractorLink,
        title: String,
        fileName: String,
        posterUrl: String? = null,
        apiName: String = "Unknown",
        selectedHeight: Int = 0,
    ): Boolean {
        initialize(context)
        if (!validateUrl(link.url)) {
            val message = "Invalid download URL. The link may have expired; please try again."
            Log.w(TAG, "Invalid download URL: ${link.url.take(120)}")
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            return false
        }
        if (isUnsupportedDirectDownload(link)) {
            val message = "DASH links aren't supported for direct download. Please try a different source."
            Log.w(TAG, "Blocked DASH manifest download: ${link.url}")
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            return false
        }
        val downloadId = "${title}_${System.currentTimeMillis()}"
        val item = DirectDownloadItem(
            id = downloadId,
            title = title,
            url = link.url,
            fileName = sanitizeFileName(fileName).ifBlank { "download_$downloadId" },
            posterUrl = posterUrl,
            apiName = apiName,
            selectedHeight = selectedHeight,
        )
        _activeDownloads.value = _activeDownloads.value + (downloadId to item)
        downloadJobs[downloadId] = downloadScope.launch {
            if (isHls(link)) downloadHlsVideo(context.applicationContext, downloadId, link, item)
            else executeDownload(context.applicationContext, downloadId, link)
        }
        showDownloadNotification(item)
        Log.d(TAG, "Download started: $title")
        return true
    }

    fun pauseDownload(downloadId: String) {
        downloadJobs.remove(downloadId)?.cancel()
        updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PAUSED) }
    }

    fun resumeDownload(downloadId: String) {
        val item = _activeDownloads.value[downloadId] ?: return
        if (item.status != DirectDownloadStatus.PAUSED) return
        val context = appContext ?: return
        val link = ExtractorLink(
            source = "Direct download",
            name = item.title,
            url = item.url,
            referer = "",
            quality = 0,
            headers = emptyMap(),
            type = ExtractorLinkType.VIDEO,
        )
        updateItem(downloadId) { it.copy(status = DirectDownloadStatus.DOWNLOADING, error = null) }
        downloadJobs[downloadId] = downloadScope.launch {
            executeDownload(context, downloadId, link)
        }
    }

    fun cancelDownload(downloadId: String) {
        val item = _activeDownloads.value[downloadId]
        downloadJobs.remove(downloadId)?.cancel()
        item?.filePath?.let { File(it).delete() }
        _activeDownloads.value = _activeDownloads.value - downloadId
        appContext?.let { cancelNotification(downloadId, it) }
    }

    private suspend fun executeDownload(context: Context, downloadId: String, link: ExtractorLink) {
        var connection: HttpURLConnection? = null
        val item = _activeDownloads.value[downloadId] ?: return
        val outputFile = File(getDownloadDir(context), outputName(item.fileName))
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.part")
        try {
            connection = (URL(link.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", link.headers["User-Agent"] ?: USER_AGENT)
                link.headers.forEach { (key, value) ->
                    if (!key.equals("User-Agent", ignoreCase = true)) setRequestProperty(key, value)
                }
                if (link.referer.isNotBlank()) setRequestProperty("Referer", link.referer)
            }
            if (connection.responseCode !in 200..299) {
                throw httpException(connection.responseCode, connection.responseMessage)
            }

            val totalBytes = connection.contentLengthLong
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.DOWNLOADING, totalBytes = totalBytes) }
            var downloadedBytes = 0L
            var lastUpdateTime = System.currentTimeMillis()
            var lastUpdateBytes = 0L
            val startTime = lastUpdateTime

            connection.inputStream.use { input ->
                FileOutputStream(tempFile, false).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (currentCoroutineContext().isActive) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 500L || downloadedBytes - lastUpdateBytes >= 1024L * 1024L) {
                            val elapsed = (now - startTime).coerceAtLeast(1L) / 1000.0
                            val progress = if (totalBytes > 0L) {
                                (downloadedBytes * 100L / totalBytes).toInt().coerceIn(0, 100)
                            } else 0
                            updateItem(downloadId) {
                                it.copy(
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    speed = formatSpeed(downloadedBytes / elapsed),
                                )
                            }
                            updateDownloadNotification(downloadId)
                            lastUpdateTime = now
                            lastUpdateBytes = downloadedBytes
                        }
                    }
                }
            }
            if (!currentCoroutineContext().isActive) throw CancellationException()
            if (outputFile.exists()) outputFile.delete()
            if (!tempFile.renameTo(outputFile)) throw IllegalStateException("Unable to finalize download")

            val fileSize = outputFile.length()
            if (fileSize < MIN_VALID_VIDEO_BYTES) {
                outputFile.delete()
                throw IllegalStateException(
                    "Downloaded file is only ${formatFileSize(fileSize)}; it may be a streaming manifest, not a video."
                )
            }
            updateItem(downloadId) {
                it.copy(
                    status = DirectDownloadStatus.COMPLETED,
                    progress = 100,
                    downloadedBytes = fileSize,
                    totalBytes = fileSize,
                    filePath = outputFile.absolutePath,
                    speed = "",
                )
            }
            showCompletedNotification(downloadId, outputFile)
        } catch (_: CancellationException) {
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PAUSED) }
        } catch (error: Exception) {
            Log.e(TAG, "Download failed: ${item.title}", error)
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = error.message) }
            showFailedNotification(downloadId, error.message ?: "Unknown error")
        } finally {
            connection?.disconnect()
        }
    }

    private data class HlsVariant(val bandwidth: Int?, val width: Int?, val height: Int?, val url: String)
    private data class HlsResponse(val url: String, val body: ByteArray)

    /** Download an HLS media playlist as a concatenation of its media segments. */
    private suspend fun downloadHlsVideo(context: Context, downloadId: String, link: ExtractorLink, item: DirectDownloadItem) {
        val outputFile = File(getDownloadDir(context), outputName(item.fileName))
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.part")
        try {
            val headers = requestHeaders(link)
            var playlistUrl = link.url
            var response = requestHls(playlistUrl, headers)
            playlistUrl = response.url
            var playlist = String(response.body, Charsets.UTF_8)
            require(playlist.contains("#EXTM3U")) { "The server did not return an HLS playlist." }
            if (playlist.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
                val variants = parseHlsVariants(playlist, playlistUrl)
                require(variants.isNotEmpty()) { "No playable HLS variants were found." }
                val selected = if (item.selectedHeight > 0) {
                    variants.filter { (it.height ?: 0) <= item.selectedHeight }.maxByOrNull { it.height ?: 0 }
                        ?: variants.maxByOrNull { it.height ?: it.bandwidth ?: 0 }
                } else variants.maxByOrNull { it.height ?: it.bandwidth ?: 0 } ?: variants.first()
                playlistUrl = selected!!.url
                response = requestHls(playlistUrl, headers)
                playlistUrl = response.url
                playlist = String(response.body, Charsets.UTF_8)
            }
            require(!playlist.contains("#EXT-X-KEY", ignoreCase = true)) {
                "Encrypted HLS downloads are not supported by this downloader."
            }
            val segments = playlist.lineSequence().map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { URI(playlistUrl).resolve(it).toString() }.toList()
            require(segments.isNotEmpty()) { "No media segments were found in the HLS playlist." }
            var downloaded = 0L
            FileOutputStream(tempFile, false).use { output ->
                segments.forEachIndexed { index, segmentUrl ->
                    if (!currentCoroutineContext().isActive) throw CancellationException()
                    val bytes = requestHls(segmentUrl, headers).body
                    output.write(bytes)
                    downloaded += bytes.size
                    val progress = ((index + 1) * 100L / segments.size).toInt()
                    updateItem(downloadId) { it.copy(progress = progress, downloadedBytes = downloaded, totalBytes = downloaded * segments.size / (index + 1L)) }
                    updateDownloadNotification(downloadId)
                }
            }
            if (!currentCoroutineContext().isActive) throw CancellationException()
            if (outputFile.exists()) outputFile.delete()
            require(tempFile.renameTo(outputFile)) { "Unable to finalize HLS download." }
            require(outputFile.length() >= MIN_VALID_VIDEO_BYTES) { "Downloaded HLS file is only ${formatFileSize(outputFile.length())}." }
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.COMPLETED, progress = 100, downloadedBytes = outputFile.length(), totalBytes = outputFile.length(), filePath = outputFile.absolutePath, speed = "") }
            showCompletedNotification(downloadId, outputFile)
        } catch (_: CancellationException) {
            tempFile.delete()
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PAUSED) }
        } catch (error: Exception) {
            tempFile.delete()
            Log.e(TAG, "HLS download failed: ${item.title}", error)
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = error.message) }
            showFailedNotification(downloadId, error.message ?: "Unknown HLS download error")
        }
    }

    private fun parseHlsVariants(playlist: String, baseUrl: String): List<HlsVariant> {
        val lines = playlist.lines()
        return lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:", ignoreCase = true)) return@mapIndexedNotNull null
            val path = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }?.trim() ?: return@mapIndexedNotNull null
            val bandwidth = Regex("(?:AVERAGE-BANDWIDTH|BANDWIDTH)=(\\d+)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.toIntOrNull()
            val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)", RegexOption.IGNORE_CASE).find(line)
            HlsVariant(bandwidth, resolution?.groupValues?.get(1)?.toIntOrNull(), resolution?.groupValues?.get(2)?.toIntOrNull(), URI(baseUrl).resolve(path).toString())
        }
    }

    private fun requestHeaders(link: ExtractorLink): Map<String, String> = buildMap {
        put("User-Agent", link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: USER_AGENT)
        link.headers.filterKeys { !it.equals("User-Agent", true) }.forEach { (key, value) -> put(key, value) }
        if (link.referer.isNotBlank() && keys.none { it.equals("Referer", true) }) put("Referer", link.referer)
    }

    private fun requestHls(url: String, headers: Map<String, String>): HlsResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw httpException(code, connection.responseMessage)
            HlsResponse(connection.url.toString(), connection.inputStream.use { it.readBytes() })
        } finally { connection.disconnect() }
    }

    private fun isHls(link: ExtractorLink): Boolean = link.type == ExtractorLinkType.M3U8 || link.url.contains(".m3u8", ignoreCase = true)

    private fun validateUrl(url: String): Boolean = runCatching { URI(url).let { (it.scheme == "http" || it.scheme == "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)

    private fun httpException(code: Int, message: String?): IOException = when (code) {
        401 -> IOException("Authentication required (401).")
        403 -> IOException("Access denied (403). The server blocked the request.")
        404 -> IOException("File not found (404). The link may have expired or is incorrect.")
        in 500..599 -> IOException("Server error ($code). Try again later.")
        else -> IOException("HTTP $code: ${message ?: "Request failed"}")
    }

    private fun updateItem(downloadId: String, update: (DirectDownloadItem) -> DirectDownloadItem) {
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            get(downloadId)?.let { put(downloadId, update(it)) }
        }
    }

    private fun getDownloadDir(context: Context): File =
        (context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "downloads")).apply { mkdirs() }

    private fun outputName(fileName: String): String =
        if (fileName.substringAfterLast('.', "").isNotBlank()) fileName else "$fileName.mp4"

    private fun createNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Download progress notifications"
                }
            )
        }
    }

    private fun showDownloadNotification(item: DirectDownloadItem) {
        notify(item.id, NotificationCompat.Builder(appContext ?: return, CHANNEL_ID)
            .setContentTitle("Downloading: ${item.title}")
            .setContentText("0%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build())
    }

    private fun updateDownloadNotification(downloadId: String) {
        val item = _activeDownloads.value[downloadId] ?: return
        notify(downloadId, NotificationCompat.Builder(appContext ?: return, CHANNEL_ID)
            .setContentTitle("Downloading: ${item.title}")
            .setContentText("${item.progress}% - ${item.speed}")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, item.progress, item.totalBytes <= 0L)
            .build())
    }

    private fun showCompletedNotification(downloadId: String, file: File) {
        val item = _activeDownloads.value[downloadId] ?: return
        notify(downloadId, NotificationCompat.Builder(appContext ?: return, CHANNEL_ID)
            .setContentTitle("Download Complete: ${item.title}")
            .setContentText(formatFileSize(file.length()))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build())
    }

    private fun showFailedNotification(downloadId: String, error: String) {
        notify(downloadId, NotificationCompat.Builder(appContext ?: return, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build())
    }

    private fun notify(downloadId: String, notification: Notification) {
        val context = appContext ?: return
        runCatching { NotificationManagerCompat.from(context).notify(downloadId.hashCode(), notification) }
    }

    private fun cancelNotification(downloadId: String, context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(downloadId.hashCode()) }
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    private fun formatSpeed(bytesPerSecond: Double): String = when {
        bytesPerSecond >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSecond / 1_000_000.0)
        bytesPerSecond >= 1_000 -> String.format("%.1f KB/s", bytesPerSecond / 1_000.0)
        else -> String.format("%.0f B/s", bytesPerSecond)
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^\\w\\s.-]"), "").trim().take(100)
}
