package com.lagradost.cloudstream3.utils.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject

/** State exposed to the direct-download UI. */
data class DirectDownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val fileName: String,
    val posterUrl: String?,
    val apiName: String,
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: DirectDownloadStatus = DirectDownloadStatus.PENDING,
    val filePath: String? = null,
    val speed: String = "",
    val eta: String = "",
    val error: String? = null,
    val selectedHeight: Int = 0,
    val referer: String = "",
    val headers: Map<String, String> = emptyMap(),
)

enum class DirectDownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

/** Kept for callers that used the old capability check; all validated links are now attempted. */
fun isUnsupportedDirectDownload(link: ExtractorLink, url: String = link.url): Boolean = false

/** Removes probe-only URL fragments before a URL is used for an HTTP request. */
fun cleanDownloadUrl(url: String): String = url.substringBefore("#")

private fun isDirectFileUrl(url: String): Boolean {
    val normalized = url.lowercase()
    // A manifest is never a direct file, even when hosted on a signed R2 URL.
    if (normalized.contains(".mpd")) return false
    if (normalized.contains(".mkv") || normalized.contains(".mp4") || normalized.contains(".webm")) return true
    if (normalized.contains("cloudflarestorage.com") ||
        normalized.contains("x-amz-signature") ||
        normalized.contains("x-amz-credential")) {
        return true
    }
    return false
}

private data class HlsVariant(
    val bandwidth: Int?,
    val width: Int?,
    val height: Int?,
    val url: String,
)

private data class HlsResponse(val url: String, val body: ByteArray)

/** Direct file and unencrypted HLS downloader used by the download-options UI. */
object DirectDownloadManager {
    private const val TAG = "DirectDownload"
    private const val CHANNEL_ID = "kino_downloads"
    private const val DOWNLOAD_PREFS = "kino_downloads"
    private const val SAVED_DOWNLOADS = "saved_downloads"
    private const val MAX_RETRIES = 5
    private const val MAX_RESOLVE_RETRIES = 2

    private val _activeDownloads = MutableStateFlow<Map<String, DirectDownloadItem>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DirectDownloadItem>> = _activeDownloads.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var hasLoadedPersistedDownloads = false
    private var lastPersistAt = 0L

    /** Toasts may be requested by the IO download scope, so always post them to Main. */
    private fun showToastSafe(context: Context, message: String, duration: Int = Toast.LENGTH_LONG) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, duration).show()
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (!hasLoadedPersistedDownloads) {
            loadPersistedDownloads()
            hasLoadedPersistedDownloads = true
        }
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
        reResolveLink: (suspend () -> ExtractorLink?)? = null,
    ): Boolean {
        initialize(context)
        Log.e(TAG, "URL_DEBUG original link URL: ${link.url}")
        val downloadUrl = cleanDownloadUrl(link.url)
        Log.e(TAG, "URL_DEBUG clean URL for download: $downloadUrl")
        val downloadLink = if (downloadUrl == link.url) link else ExtractorLink(
            source = link.source,
            name = link.name,
            url = downloadUrl,
            referer = link.referer,
            quality = link.quality,
            headers = link.headers,
            extractorData = link.extractorData,
            type = link.type,
            audioTracks = link.audioTracks,
        )
        if (!validateUrl(downloadUrl)) {
            showToastSafe(context, "Invalid download URL. The link may have expired.")
            return false
        }
        val downloadId = "${title}_${System.currentTimeMillis()}"
        val item = DirectDownloadItem(
            id = downloadId,
            title = title,
            url = downloadUrl,
            fileName = sanitizeFileName(fileName).ifBlank { "download_$downloadId" },
            posterUrl = posterUrl,
            apiName = apiName,
            selectedHeight = selectedHeight,
            referer = downloadLink.referer,
            headers = downloadLink.headers,
        )
        _activeDownloads.value = _activeDownloads.value + (downloadId to item)
        persistDownloads(force = true)
        downloadJobs[downloadId] = downloadScope.launch {
            var currentLink = downloadLink
            var resolveAttempt = 0
            while (currentCoroutineContext().isActive) {
                val contentType = if (!isHls(currentLink) && !isDash(currentLink)) {
                    checkContentType(currentLink)
                } else {
                    ""
                }
                Log.e("DL_TYPE", "URL=${currentLink.url.take(300)} type=${currentLink.type} contentType=$contentType")
                val result = when {
                    isHls(currentLink) || isHlsContentType(contentType) -> {
                        Log.e("DL_TYPE", "HLS download detected")
                        downloadHlsVideo(context.applicationContext, downloadId, currentLink, item)
                    }
                    isDash(currentLink) || isDashContentType(contentType) || isManifestUrl(currentLink.url) -> {
                        Log.e("DL_TYPE", "DASH download detected")
                        downloadDashVideo(context.applicationContext, downloadId, currentLink, item)
                    }
                    else -> {
                        Log.e("DL_TYPE", "Direct download detected")
                        executeDownload(context.applicationContext, downloadId, currentLink)
                    }
                }
                if (result) return@launch
                if (result || reResolveLink == null || resolveAttempt >= MAX_RESOLVE_RETRIES) return@launch
                resolveAttempt++
                Log.e("DL_404", "🔄 Attempting re-resolve (attempt $resolveAttempt/$MAX_RESOLVE_RETRIES)")
                val freshLink = reResolveLink() ?: run {
                    Log.e("DL_404", "❌ Re-resolve returned null")
                    updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = "Could not refresh download link. Please try again.") }
                    showFailedNotification(downloadId, "Could not refresh download link. Please try again.")
                    return@launch
                }
                Log.e("DL_404", "✅ Got fresh link: ${freshLink.url.take(150)}")
                currentLink = freshLink
                updateItem(downloadId) {
                    it.copy(
                        url = cleanDownloadUrl(freshLink.url),
                        referer = freshLink.referer,
                        headers = freshLink.headers,
                        status = DirectDownloadStatus.PENDING,
                        error = null,
                    )
                }
                delay(1_000L)
            }
        }
        showDownloadNotification(item)
        Log.d(TAG, "Download started: $title")
        return true
    }

    fun pauseDownload(downloadId: String) {
        Log.d(TAG, "Pausing download without deleting partial data: $downloadId")
        // Cancellation exits executeDownload through its CancellationException path. The
        // .part file and the item in activeDownloads are intentionally retained for resume.
        downloadJobs.remove(downloadId)?.cancel()
        updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PAUSED, speed = "", eta = "") }
        persistDownloads(force = true)
    }

    fun resumeDownload(downloadId: String) {
        Log.e(TAG, "========== resumeDownload($downloadId) ==========")
        val item = _activeDownloads.value[downloadId]
        if (item == null) {
            Log.e(TAG, "Resume aborted: item not found. Active IDs=${_activeDownloads.value.keys}")
            return
        }
        Log.e(TAG, "Resume item='${item.title}', status=${item.status}, url=${item.url.take(120)}, downloaded=${item.downloadedBytes}, total=${item.totalBytes}")
        if (item.status != DirectDownloadStatus.PAUSED) {
            Log.e(TAG, "Resume aborted: expected PAUSED, got ${item.status}")
            return
        }
        if (item.url.isBlank()) {
            Log.e(TAG, "Resume aborted: URL is blank")
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = "No URL available for resume") }
            persistDownloads(force = true)
            return
        }
        val context = appContext
        if (context == null) {
            Log.e(TAG, "Resume aborted: appContext is null")
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = "No context available") }
            persistDownloads(force = true)
            return
        }
        val link = ExtractorLink(
            source = item.apiName,
            name = item.title,
            url = item.url,
            referer = item.referer,
            quality = 0,
            headers = item.headers,
            type = if (isHlsUrl(item.url)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        )
        Log.e(TAG, "Resume link created: referer=${link.referer.isNotBlank()}, headers=${link.headers.keys}, type=${link.type}")
        updateItem(downloadId) { it.copy(status = DirectDownloadStatus.DOWNLOADING, error = null, speed = "", eta = "") }
        persistDownloads(force = true)
        val job = downloadScope.launch {
            Log.e(TAG, "Resume coroutine started for $downloadId")
            try {
                val result = when {
                    isHls(link) -> downloadHlsVideo(context, downloadId, link, item)
                    isDash(link) -> downloadDashVideo(context, downloadId, link, item)
                    else -> executeDownload(context, downloadId, link)
                }
                Log.e(TAG, "Resume coroutine finished for $downloadId, result=$result")
            } catch (error: CancellationException) {
                Log.e(TAG, "Resume coroutine cancelled for $downloadId")
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Resume failed for $downloadId", error)
                updateItem(downloadId) { current -> current.copy(status = DirectDownloadStatus.FAILED, error = error.message) }
                persistDownloads(force = true)
            }
        }
        downloadJobs[downloadId] = job
        Log.e(TAG, "Resume job stored for $downloadId: $job")
    }

    fun cancelDownload(downloadId: String) {
        val item = _activeDownloads.value[downloadId]
        downloadJobs.remove(downloadId)?.cancel()
        item?.filePath?.let { File(it).delete() }
        item?.let { File(getDownloadDir(appContext ?: return), outputName(it.fileName) + ".part").delete() }
        _activeDownloads.value = _activeDownloads.value - downloadId
        persistDownloads(force = true)
        appContext?.let { cancelNotification(downloadId, it) }
    }

    private suspend fun executeDownload(context: Context, downloadId: String, link: ExtractorLink): Boolean {
        val item = _activeDownloads.value[downloadId] ?: return true
        if (isManifestUrl(link.url)) {
            Log.e(TAG, "Manifest URL reached direct path; redirecting to a manifest downloader")
            return if (isHls(link)) {
                downloadHlsVideo(context, downloadId, link, item)
            } else {
                downloadDashVideo(context, downloadId, link, item)
            }
        }
        val outputFile = File(getDownloadDir(context), outputName(item.fileName))
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.part")
        var attempt = 0
        try {
            while (attempt < MAX_RETRIES) {
                var connection: HttpURLConnection? = null
                try {
                    val existingBytes = tempFile.length()
                    connection = (URL(link.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15_000
                        readTimeout = 300_000
                        instanceFollowRedirects = true
                        requestHeaders(link).forEach { (key, value) -> setRequestProperty(key, value) }
                        if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                    val responseCode = connection.responseCode
                    Log.e("DL_404", "═════════════════════════════")
                    Log.e("DL_404", "Attempting to download:")
                    Log.e("DL_404", "  URL: ${link.url.take(300)}")
                    Log.e("DL_404", "  Type: ${link.type}")
                    Log.e("DL_404", "  Headers: ${requestHeaders(link)}")
                    Log.e("DL_404", "Response:")
                    Log.e("DL_404", "  Code: $responseCode")
                    Log.e("DL_404", "  Content-Type: ${connection.contentType}")
                    Log.e("DL_404", "  Content-Length: ${connection.contentLengthLong}")
                    Log.e("DL_404", "  Final URL: ${connection.url}")
                    if (responseCode == 429) {
                        attempt++
                        if (attempt >= MAX_RETRIES) throw httpException(responseCode, connection.responseMessage)
                        val retryAfterSeconds = connection.getHeaderField("Retry-After")?.toLongOrNull()
                        val backoff = retryAfterSeconds?.times(1_000L)?.coerceIn(5_000L, 60_000L)
                            ?: when (attempt) {
                                1 -> 5_000L
                                2 -> 15_000L
                                else -> 30_000L
                            }
                        Log.w(TAG, "HTTP 429 for ${item.title}; retrying in ${backoff / 1_000L}s (attempt $attempt/$MAX_RETRIES)")
                        delay(backoff)
                        continue
                    }
                    if (responseCode !in 200..299 && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                            Log.e("DL_404", "❌ $responseCode ERROR - URL may be expired")
                            runCatching { connection.errorStream?.bufferedReader()?.use { it.readText().take(500) } }
                                .onSuccess { Log.e("DL_404", "  Error body: $it") }
                        }
                        throw httpException(responseCode, connection.responseMessage)
                    }
                    val contentType = connection.contentType.orEmpty()
                    val responseLength = connection.contentLengthLong
                    if (!isVideoResponse(contentType, connection.url.toString())) {
                        connection.disconnect()
                        if (isHlsContentType(contentType)) {
                            Log.e(TAG, "Direct response is an HLS manifest; retrying through the HLS downloader")
                            return downloadHlsVideo(context, downloadId, link, item)
                        }
                        if (isDashContentType(contentType)) {
                            Log.e(TAG, "Direct response is a DASH/XML manifest; retrying through the DASH downloader")
                            return downloadDashVideo(context, downloadId, link, item)
                        }
                        throw IOException("Server returned '${contentType.take(60)}', not a video. The link is likely expired or requires different headers.")
                    }
                    val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
                    if (!append) tempFile.delete()
                    val startingBytes = if (append) existingBytes else 0L
                    val contentLength = connection.contentLengthLong
                    val totalBytes = if (contentLength > 0L) startingBytes + contentLength else 0L
                    updateItem(downloadId) { it.copy(status = DirectDownloadStatus.DOWNLOADING, downloadedBytes = startingBytes, totalBytes = totalBytes) }

                    var downloadedBytes = startingBytes
                    var lastUpdate = System.currentTimeMillis()
                    val startTime = lastUpdate
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile, append).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (currentCoroutineContext().isActive) {
                                val count = input.read(buffer)
                                if (count == -1) break
                                output.write(buffer, 0, count)
                                downloadedBytes += count
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate >= 500L) {
                                    val elapsed = (now - startTime).coerceAtLeast(1L) / 1000.0
                                    val progress = if (totalBytes > 0L) (downloadedBytes * 100L / totalBytes).toInt().coerceIn(0, 100) else 0
                                    val speedBps = (downloadedBytes - startingBytes) / elapsed
                                    updateItem(downloadId) {
                                        it.copy(
                                            progress = progress,
                                            downloadedBytes = downloadedBytes,
                                            speed = formatSpeed(speedBps),
                                            eta = calculateEta(downloadedBytes, totalBytes, speedBps),
                                        )
                                    }
                                    updateDownloadNotification(downloadId)
                                    lastUpdate = now
                                }
                            }
                        }
                    }
                    if (!currentCoroutineContext().isActive) throw CancellationException()
                    if (totalBytes > 0L && tempFile.length() < totalBytes) throw IOException("Connection ended before the file completed.")
                    detectManifestType(tempFile)?.let { manifestType ->
                        tempFile.delete()
                        Log.e(TAG, "Direct response was a ${manifestType.name} manifest; redirecting to its segment downloader")
                        return if (manifestType == ManifestType.HLS) {
                            downloadHlsVideo(context, downloadId, link, item)
                        } else {
                            downloadDashVideo(context, downloadId, link, item)
                        }
                    }
                    finalizeDownload(downloadId, outputFile, tempFile)
                    showCompletedNotification(downloadId)
                    return true
                } catch (error: SocketTimeoutException) {
                    attempt++
                    if (attempt >= MAX_RETRIES) throw IOException("Download timed out after $MAX_RETRIES attempts.", error)
                    delay(2_000L * attempt)
                } catch (error: IOException) {
                    attempt++
                    if (attempt >= MAX_RETRIES) throw error
                    delay(2_000L * attempt)
                } finally {
                    connection?.disconnect()
                }
            }
throw IOException("Download failed after $MAX_RETRIES attempts.")
        } catch (_: CancellationException) {
            markPausedIfNoReplacement(downloadId)
            return true
        } catch (error: Exception) {
            // Return retryable HTTP expiry/auth failures to startDownload so it can
            // obtain a fresh signed URL before marking the item failed.
            val retryable = error.message?.contains(Regex("\\b(401|403|404)\\b")) == true
            if (!retryable) {
                Log.e(TAG, "Download failed: ${item.title}", error)
                updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = error.message) }
                showFailedNotification(downloadId, error.message ?: "Unknown error")
            }
            return !retryable
        }
    }

    private suspend fun downloadHlsVideo(context: Context, downloadId: String, link: ExtractorLink, item: DirectDownloadItem): Boolean {
        val outputFile = File(getDownloadDir(context), outputName(item.fileName))
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.part")
        try {
            val headers = requestHeaders(link)
            var playlistResponse = requestHls(link.url, headers)
            var playlistUrl = playlistResponse.url
            var playlist = String(playlistResponse.body, Charsets.UTF_8)
            require(playlist.contains("#EXTM3U")) { "The server did not return an HLS playlist." }
            if (playlist.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
                val variants = parseHlsVariants(playlist, playlistUrl)
                require(variants.isNotEmpty()) { "No playable HLS variants were found." }
                val selected = if (item.selectedHeight > 0) {
                    variants.filter { (it.height ?: 0) <= item.selectedHeight }.maxByOrNull { it.height ?: 0 }
                        ?: variants.maxByOrNull { it.height ?: it.bandwidth ?: 0 }
                } else variants.maxByOrNull { it.height ?: it.bandwidth ?: 0 }
                playlistResponse = requestHls(requireNotNull(selected).url, headers)
                playlistUrl = playlistResponse.url
                playlist = String(playlistResponse.body, Charsets.UTF_8)
            }
            require(!playlist.contains("#EXT-X-KEY", ignoreCase = true)) { "Encrypted HLS downloads are not supported." }
            val segments = playlist.lineSequence().map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { URI(playlistUrl).resolve(it).toString() }.toList()
            require(segments.isNotEmpty()) { "No media segments were found in the HLS playlist." }

            downloadSegmentsParallel(downloadId, segments, headers, tempFile)
            if (!currentCoroutineContext().isActive) throw CancellationException()
            finalizeDownload(downloadId, outputFile, tempFile, validateContainer = false)
            showCompletedNotification(downloadId)
            return true
        } catch (_: CancellationException) {
            markPausedIfNoReplacement(downloadId)
            return true
        } catch (error: Exception) {
            tempFile.delete()
            if (isRetryableExpiry(error)) {
                Log.e("DL_404", "❌ HLS request failed with an expired/authenticated URL", error)
                return false
            }
            Log.e(TAG, "HLS download failed: ${item.title}", error)
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = error.message) }
            showFailedNotification(downloadId, error.message ?: "Unknown HLS download error")
            return true
        }
    }

    /** Download media segments concurrently, then concatenate them in manifest order. */
    private suspend fun downloadSegmentsParallel(
        downloadId: String,
        segmentUrls: List<String>,
        headers: Map<String, String>,
        outputFile: File,
        progressOffset: Int = 0,
        progressTotal: Int = segmentUrls.size,
        downloadedBytesOffset: Long = 0L,
    ) {
        val parallelCount = minOf(6, segmentUrls.size)
        val segmentDir = File(outputFile.parentFile, "${outputFile.name}.segments")
        segmentDir.deleteRecursively()
        require(segmentDir.mkdirs()) { "Could not create temporary segment directory." }

        val completed = AtomicInteger(0)
        val totalBytes = AtomicLong(0L)
        val lastUpdate = AtomicLong(0L)
        try {
            coroutineScope {
                segmentUrls.withIndex().chunked(parallelCount).forEach { batch ->
                    batch.map { indexed ->
                        async(Dispatchers.IO) {
                            if (!currentCoroutineContext().isActive) throw CancellationException()
                            val bytes = requestHls(indexed.value, headers).body
                            require(bytes.isNotEmpty()) { "Segment ${indexed.index + 1} was empty." }
                            File(segmentDir, "segment_${indexed.index}.bin").writeBytes(bytes)

                            val finished = completed.incrementAndGet()
                            val downloaded = totalBytes.addAndGet(bytes.size.toLong())
                            val now = System.currentTimeMillis()
                            if (finished == segmentUrls.size || now - lastUpdate.get() >= 500L) {
                                lastUpdate.set(now)
                                updateItem(downloadId) {
                                    it.copy(
                                        status = DirectDownloadStatus.DOWNLOADING,
                                        progress = ((progressOffset + finished) * 100 / progressTotal.coerceAtLeast(1)).coerceIn(0, 100),
                                        downloadedBytes = downloadedBytesOffset + downloaded,
                                    )
                                }
                                updateDownloadNotification(downloadId)
                            }
                        }
                    }.awaitAll()
                }
            }

            FileOutputStream(outputFile, false).use { output ->
                segmentUrls.indices.forEach { index ->
                    File(segmentDir, "segment_$index.bin").inputStream().use { input -> input.copyTo(output) }
                }
            }
            updateItem(downloadId) {
                it.copy(
                    progress = ((progressOffset + segmentUrls.size) * 100 / progressTotal.coerceAtLeast(1)).coerceIn(0, 100),
                    downloadedBytes = downloadedBytesOffset + totalBytes.get(),
                )
            }
        } finally {
            segmentDir.deleteRecursively()
        }
    }

    private data class DashRepresentation(val width: Int, val height: Int, val bandwidth: Long, val segments: List<String>)

    /** Downloads unencrypted ISO-BMFF DASH video and audio, then muxes both tracks. */
    private suspend fun downloadDashVideo(context: Context, downloadId: String, link: ExtractorLink, item: DirectDownloadItem): Boolean {
        val outputFile = File(getDownloadDir(context), outputName(item.fileName))
        val videoFile = File(outputFile.parentFile, "${outputFile.name}.video.part")
        val audioFile = File(outputFile.parentFile, "${outputFile.name}.audio.part")
        val muxedFile = File(outputFile.parentFile, "${outputFile.name}.muxed.part")
        try {
            val headers = requestHeaders(link)
            Log.e("DASH_DEBUG", "Fetching MPD: ${link.url.take(300)}")
            val response = requestHls(link.url, headers)
            val manifest = String(response.body, Charsets.UTF_8)
            require(manifest.contains("<MPD", ignoreCase = true)) { "The server did not return a DASH MPD manifest." }
            val videoRepresentations = parseDashRepresentations(manifest, response.url, contentType = "video")
            val audioRepresentations = parseDashRepresentations(manifest, response.url, contentType = "audio")
            require(videoRepresentations.isNotEmpty()) { "No playable video representations were found in the DASH manifest." }
            val selectedVideo = if (item.selectedHeight > 0) {
                videoRepresentations.filter { it.height in 1..item.selectedHeight }.maxByOrNull { it.height }
                    ?: videoRepresentations.maxByOrNull { it.height }
            } else {
                videoRepresentations.maxByOrNull { it.height * 1_000_000L + it.bandwidth }
            }
            val videoSegments = requireNotNull(selectedVideo).segments
            require(videoSegments.isNotEmpty()) { "No DASH video segments were found in the manifest." }
            val selectedAudio = audioRepresentations.maxByOrNull { it.bandwidth }
            val audioSegments = selectedAudio?.segments.orEmpty()
            Log.e("DASH_AUDIO", "Found ${videoRepresentations.size} video and ${audioRepresentations.size} audio representations")
            downloadSegmentsParallel(downloadId, videoSegments, headers, videoFile, progressOffset = 0, progressTotal = videoSegments.size + audioSegments.size)
            if (audioSegments.isNotEmpty()) {
                downloadSegmentsParallel(downloadId, audioSegments, headers, audioFile, progressOffset = videoSegments.size, progressTotal = videoSegments.size + audioSegments.size, downloadedBytesOffset = videoFile.length())
                muxDashTracks(videoFile, audioFile, muxedFile)
            } else {
                Log.w("DASH_AUDIO", "No audio representation found; keeping the video-only DASH download")
                require(videoFile.renameTo(muxedFile)) { "Unable to prepare the DASH video for finalization." }
            }
            if (!currentCoroutineContext().isActive) throw CancellationException()
            finalizeDownload(downloadId, outputFile, muxedFile, validateContainer = true)
            showCompletedNotification(downloadId)
            return true
        } catch (_: CancellationException) {
            markPausedIfNoReplacement(downloadId)
            return true
        } catch (error: Exception) {
            videoFile.delete()
            audioFile.delete()
            muxedFile.delete()
            if (isRetryableExpiry(error)) {
                Log.e("DL_404", "❌ DASH manifest/segment request failed with an expired/authenticated URL", error)
                return false
            }
            Log.e(TAG, "DASH download failed: ${item.title}", error)
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = error.message) }
            showFailedNotification(downloadId, error.message ?: "Unknown DASH download error")
            return true
        }
    }

    private fun parseDashRepresentations(manifest: String, manifestUrl: String, contentType: String = "video"): List<DashRepresentation> {
        val result = mutableListOf<DashRepresentation>()
        val adaptationRegex = Regex("<AdaptationSet\\b([^>]*)>(.*?)</AdaptationSet>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val representationRegex = Regex("<Representation\\b([^>]*?)(?:/>|>(.*?)</Representation>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        adaptationRegex.findAll(manifest).forEach { adaptation ->
            val parentAttrs = parseXmlAttributes(adaptation.groupValues[1])
            val adaptationBody = adaptation.groupValues[2]
            val isRequestedType = parentAttrs["contentType"]?.equals(contentType, true) == true ||
                adaptationBody.contains("mimeType=\"$contentType/", true) ||
                adaptationBody.contains("contentType=\"$contentType\"", true)
            if (!isRequestedType) return@forEach
            val parentBase = Regex("<BaseURL\\s*>(.*?)</BaseURL>", RegexOption.IGNORE_CASE).find(adaptationBody)?.groupValues?.get(1)?.trim()
            val parentTemplate = Regex("<SegmentTemplate\\b([^>]*)>(.*?)</SegmentTemplate>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(adaptationBody)
            representationRegex.findAll(adaptationBody).forEach { representation ->
                val attrs = parentAttrs + parseXmlAttributes(representation.groupValues[1])
                val body = representation.groupValues.getOrNull(2).orEmpty()
                val base = Regex("<BaseURL\\s*>(.*?)</BaseURL>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim() ?: parentBase ?: manifestUrl
                val template = Regex("<SegmentTemplate\\b([^>]*)>(.*?)</SegmentTemplate>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(body) ?: parentTemplate
                val segments = template?.let { expandDashTemplate(it.groupValues[1], it.groupValues[2], attrs, base, manifestUrl) }.orEmpty()
                if (segments.isNotEmpty()) result += DashRepresentation(attrs["width"]?.toIntOrNull() ?: 0, attrs["height"]?.toIntOrNull() ?: 0, attrs["bandwidth"]?.toLongOrNull() ?: 0L, segments)
            }
        }
        return result.distinctBy { Triple(it.width, it.height, it.segments) }
    }

    /** Mux the independently downloaded ISO-BMFF tracks into the MP4 played by the app. */
    private fun muxDashTracks(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            val videoTrack = (0 until videoExtractor.trackCount).firstOrNull { index ->
                videoExtractor.getTrackFormat(index).getString("mime")?.startsWith("video/") == true
            } ?: throw IOException("The DASH video track could not be read after download.")
            val audioTrack = (0 until audioExtractor.trackCount).firstOrNull { index ->
                audioExtractor.getTrackFormat(index).getString("mime")?.startsWith("audio/") == true
            } ?: throw IOException("The DASH audio track could not be read after download.")

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxedVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val muxedAudioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            muxer.start()
            started = true

            fun copyTrack(extractor: MediaExtractor, outputTrack: Int) {
                extractor.selectTrack(if (extractor === videoExtractor) videoTrack else audioTrack)
                val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                while (true) {
                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(outputTrack, buffer, bufferInfo)
                    extractor.advance()
                }
                extractor.unselectTrack(if (extractor === videoExtractor) videoTrack else audioTrack)
            }

            copyTrack(videoExtractor, muxedVideoTrack)
            copyTrack(audioExtractor, muxedAudioTrack)
        } finally {
            if (started) runCatching { muxer?.stop() }
            muxer?.release()
            videoExtractor.release()
            audioExtractor.release()
        }
    }

    private fun expandDashTemplate(templateAttrs: String, templateBody: String, attrs: Map<String, String>, base: String, manifestUrl: String): List<String> {
        val template = parseXmlAttributes(templateAttrs)
        val bandwidth = attrs["bandwidth"]?.toLongOrNull() ?: 0L
        val initialization = template["initialization"]?.let { resolveDashUrl(substituteDash(it, attrs["id"].orEmpty(), 0L, 0L, bandwidth), base, manifestUrl) }
        val media = template["media"] ?: return emptyList()
        val segments = mutableListOf<String>()
        initialization?.let { segments += it }
        val timeline = Regex("<SegmentTimeline\\b[^>]*>(.*?)</SegmentTimeline>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(templateBody)?.groupValues?.get(1)
        if (timeline == null) return segments
        var currentTime = 0L
        var number = template["startNumber"]?.toLongOrNull() ?: 1L
        Regex("<S\\b([^>]*)/?>", RegexOption.IGNORE_CASE).findAll(timeline).forEach { match ->
            val item = parseXmlAttributes(match.groupValues[1])
            val duration = item["d"]?.toLongOrNull() ?: return@forEach
            item["t"]?.toLongOrNull()?.let { currentTime = it }
            val repeat = (item["r"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            repeat(repeat + 1) {
                segments += resolveDashUrl(substituteDash(media, attrs["id"].orEmpty(), number, currentTime, bandwidth), base, manifestUrl)
                currentTime += duration
                number++
            }
        }
        if (segments.size == if (initialization == null) 0 else 1) {
            Regex("<SegmentURL\\b[^>]*?media=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .findAll(templateBody)
                .forEach { segments += resolveDashUrl(it.groupValues[1], base, manifestUrl) }
        }
        return segments
    }

    /** Resolves DASH template variables using literal string operations, never Regex on the template. */
    private fun substituteDash(template: String, id: String, number: Long, time: Long, bandwidth: Long): String {
        var result = template.replace("\$RepresentationID\$", id)

        // DASH supports zero-padded forms such as $Number%05d$. Find those literal
        // delimiters directly so braces/dollar signs in a template cannot become regex syntax.
        val paddedPrefix = "\$Number%0"
        var searchFrom = 0
        while (true) {
            val start = result.indexOf(paddedPrefix, searchFrom)
            if (start < 0) break
            val suffixStart = start + paddedPrefix.length
            val end = result.indexOf("d\$", suffixStart)
            if (end < 0) break
            val widthText = result.substring(suffixStart, end)
            if (widthText.isNotEmpty() && widthText.all(Char::isDigit)) {
                val width = widthText.toIntOrNull()
                if (width != null) {
                    val replacement = number.toString().padStart(width, '0')
                    result = result.replaceRange(start, end + 2, replacement)
                    searchFrom = start + replacement.length
                    continue
                }
            }
            searchFrom = suffixStart
        }

        return result
            .replace("\$Number\$", number.toString())
            .replace("\$Time\$", time.toString())
            .replace("\$Bandwidth\$", bandwidth.toString())
    }
    private fun resolveDashUrl(path: String, base: String, manifestUrl: String): String = runCatching { URI(if (base.startsWith("http", true)) base else URI(manifestUrl).resolve(base).toString()).resolve(path).toString() }.getOrDefault(path)
    private fun parseXmlAttributes(raw: String): Map<String, String> = Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*\"([^\"]*)\"").findAll(raw).associate { it.groupValues[1] to it.groupValues[2] }

    private fun finalizeDownload(downloadId: String, outputFile: File, tempFile: File, validateContainer: Boolean = true) {
        require(tempFile.length() > 0L) { "Downloaded file is empty." }
        require(detectManifestType(tempFile) == null) {
            "Downloaded response is a manifest, not a video."
        }
        if (validateContainer) {
            val isVideoContainer = runCatching {
                tempFile.inputStream().use { input ->
                    val head = ByteArray(16)
                    val read = input.read(head)
                    val isMkv = read >= 4 && head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() && head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()
                    val isMp4 = read >= 8 && head[4] == 'f'.code.toByte() && head[5] == 't'.code.toByte() && head[6] == 'y'.code.toByte() && head[7] == 'p'.code.toByte()
                    isMkv || isMp4
                }
            }.getOrDefault(false)
            if (!isVideoContainer) {
                tempFile.delete()
                throw IOException("Downloaded file is not a valid video container. The URL returned an error page instead of the video file.")
            }
        }
        if (outputFile.exists()) outputFile.delete()
        require(tempFile.renameTo(outputFile)) { "Unable to finalize download." }
        val actualSize = outputFile.length()
        Log.d(TAG, "Download completed: ${outputFile.name} (${formatFileSize(actualSize)})")
        updateItem(downloadId) {
            it.copy(
                status = DirectDownloadStatus.COMPLETED,
                progress = 100,
                downloadedBytes = actualSize,
                totalBytes = actualSize,
                filePath = outputFile.absolutePath,
                speed = "",
                eta = "",
            )
        }
        persistDownloads(force = true)
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

    private fun requestHls(url: String, headers: Map<String, String>): HlsResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            val code = connection.responseCode
            Log.e("DL_404", "Manifest/segment response: code=$code contentType=${connection.contentType} contentLength=${connection.contentLengthLong} finalUrl=${connection.url}")
            if (code !in 200..299) {
                if (code == HttpURLConnection.HTTP_NOT_FOUND || code == HttpURLConnection.HTTP_FORBIDDEN) {
                    Log.e("DL_404", "❌ $code ERROR for ${url.take(300)}")
                    runCatching { connection.errorStream?.bufferedReader()?.use { it.readText().take(500) } }
                        .onSuccess { Log.e("DL_404", "  Error body: $it") }
                }
                throw httpException(code, connection.responseMessage)
            }
            HlsResponse(connection.url.toString(), connection.inputStream.use { it.readBytes() })
        } finally { connection.disconnect() }
    }

    private fun requestHeaders(link: ExtractorLink): Map<String, String> {
        val userAgent = link.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: USER_AGENT
        if (isSignedDirectUrl(link.url)) {
            return mapOf("User-Agent" to userAgent, "Accept" to "*/*", "Accept-Encoding" to "identity")
        }
        val banned = setOf("range", "accept-encoding", "connection", "host", "content-length", "transfer-encoding", "expect")
        return buildMap {
            link.headers.filterKeys { it.lowercase() !in banned }.forEach { (key, value) -> put(key, value) }
            put("User-Agent", userAgent)
            put("Accept", "*/*")
            put("Accept-Encoding", "identity")
            if (link.referer.isNotBlank() && keys.none { it.equals("Referer", true) }) put("Referer", link.referer)
        }
    }

    private fun isSignedDirectUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains("cloudflarestorage.com") || normalized.contains("x-amz-signature") ||
            normalized.contains("x-amz-credential") || normalized.contains("x-amz-algorithm")
    }

    private fun isVideoResponse(contentType: String, url: String): Boolean {
        if (contentType.startsWith("video/", ignoreCase = true)) return true
        if (contentType.isBlank() || contentType.startsWith("application/octet-stream", true) || contentType.startsWith("binary", true)) {
            val normalized = url.lowercase()
            return normalized.contains(".mkv") || normalized.contains(".mp4") || normalized.contains(".webm") ||
                normalized.contains("cloudflarestorage.com") || normalized.contains("x-amz-signature")
        }
        return false
    }

    private fun isRetryableExpiry(error: Throwable): Boolean =
        error.message?.contains(Regex("\\b(401|403|404)\\b")) == true

    private enum class ManifestType { HLS, DASH }

    private fun detectManifestType(file: File): ManifestType? = runCatching {
        val sample = file.inputStream().use { input ->
            val bytes = ByteArray(8 * 1024)
            val count = input.read(bytes).coerceAtLeast(0)
            String(bytes, 0, count, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        }
        when {
            sample.startsWith("#EXTM3U", ignoreCase = true) -> ManifestType.HLS
            sample.contains("<MPD", ignoreCase = true) -> ManifestType.DASH
            else -> null
        }
    }.getOrNull()

    private fun isDash(link: ExtractorLink): Boolean =
        link.url.contains(".mpd", ignoreCase = true) ||
            link.url.contains("mpd", ignoreCase = true) ||
            link.url.contains("/dash/", ignoreCase = true) ||
            link.url.contains("manifest", ignoreCase = true) ||
            link.type.name.equals("DASH", ignoreCase = true)
    private fun isHls(link: ExtractorLink): Boolean =
        link.type == ExtractorLinkType.M3U8 || isHlsUrl(link.url)
    private fun isHlsUrl(url: String): Boolean =
        url.contains(".m3u8", ignoreCase = true) || url.contains("m3u8", ignoreCase = true)
    private fun isManifestUrl(url: String): Boolean {
        val normalized = url.lowercase()
        return normalized.contains(".mpd") ||
            normalized.contains(".m3u8") ||
            normalized.contains("manifest") ||
            normalized.contains("/dash/") ||
            normalized.endsWith("index.mpd") ||
            normalized.endsWith("playlist.m3u8")
    }

    private fun isHlsContentType(contentType: String): Boolean {
        val normalized = contentType.lowercase()
        return normalized.contains("mpegurl") || normalized.contains("m3u8")
    }

    private fun isDashContentType(contentType: String): Boolean {
        val normalized = contentType.lowercase()
        return normalized.contains("dash") || normalized.contains("mpd") || normalized.contains("xml")
    }

    private fun checkContentType(link: ExtractorLink): String {
        val connection = runCatching {
            (URL(link.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 5_000
                readTimeout = 5_000
                instanceFollowRedirects = true
                requestHeaders(link).forEach { (key, value) -> setRequestProperty(key, value) }
            }
        }.getOrNull() ?: return ""
        return try {
            connection.responseCode
            connection.contentType.orEmpty()
        } catch (error: Exception) {
            Log.d(TAG, "Content-type probe failed for ${link.url.take(120)}", error)
            ""
        } finally {
            connection.disconnect()
        }
    }
    private fun validateUrl(url: String): Boolean = runCatching { URI(url).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)

    private fun persistDownloads(force: Boolean = false) {
        val context = appContext ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastPersistAt < 2_000L) return
        val downloads = JSONArray()
        _activeDownloads.value.values.forEach { item ->
            downloads.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("url", item.url)
                put("fileName", item.fileName)
                put("posterUrl", item.posterUrl ?: JSONObject.NULL)
                put("apiName", item.apiName)
                put("progress", item.progress)
                put("downloadedBytes", item.downloadedBytes)
                put("totalBytes", item.totalBytes)
                put("status", item.status.name)
                put("filePath", item.filePath ?: JSONObject.NULL)
                put("selectedHeight", item.selectedHeight)
                put("referer", item.referer)
                put("headers", JSONObject().apply { item.headers.forEach { (key, value) -> put(key, value) } })
                put("error", item.error ?: JSONObject.NULL)
            })
        }
        context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SAVED_DOWNLOADS, downloads.toString())
            .apply()
        lastPersistAt = now
    }

    private fun loadPersistedDownloads() {
        val context = appContext ?: return
        val saved = context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
            .getString(SAVED_DOWNLOADS, null) ?: return
        runCatching {
            val restored = mutableMapOf<String, DirectDownloadItem>()
            JSONArray(saved).let { array ->
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    val savedStatus = runCatching { DirectDownloadStatus.valueOf(json.optString("status")) }
                        .getOrDefault(DirectDownloadStatus.PAUSED)
                    val status = when (savedStatus) {
                        DirectDownloadStatus.DOWNLOADING, DirectDownloadStatus.PENDING -> DirectDownloadStatus.PAUSED
                        else -> savedStatus
                    }
                    restored[json.getString("id")] = DirectDownloadItem(
                        id = json.getString("id"),
                        title = json.getString("title"),
                        url = json.getString("url"),
                        fileName = json.getString("fileName"),
                        posterUrl = json.optString("posterUrl").takeUnless { it.isBlank() || it == "null" },
                        apiName = json.optString("apiName", "Unknown"),
                        progress = json.optInt("progress", 0),
                        downloadedBytes = json.optLong("downloadedBytes", 0L),
                        totalBytes = json.optLong("totalBytes", 0L),
                        status = status,
                        filePath = json.optString("filePath").takeUnless { it.isBlank() || it == "null" },
                        error = json.optString("error").takeUnless { it.isBlank() || it == "null" },
                        selectedHeight = json.optInt("selectedHeight", 0),
                        referer = json.optString("referer", ""),
                        headers = json.optJSONObject("headers")?.let { headersJson ->
                            headersJson.keys().asSequence().associateWith { key -> headersJson.optString(key) }
                        } ?: emptyMap(),
                    )
                }
            }
            _activeDownloads.value = restored
            lastPersistAt = System.currentTimeMillis()
            Log.d(TAG, "Restored ${restored.size} persisted downloads")
        }.onFailure { error ->
            Log.e(TAG, "Failed to restore persisted downloads", error)
        }
    }

    private fun markPausedIfNoReplacement(id: String) {
        if (downloadJobs[id]?.isActive != true) {
            updateItem(id) { it.copy(status = DirectDownloadStatus.PAUSED, speed = "", eta = "") }
        } else {
            Log.d(TAG, "Ignoring stale cancellation for $id because a replacement job is active")
        }
    }

    private fun updateItem(id: String, update: (DirectDownloadItem) -> DirectDownloadItem) {
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            get(id)?.let { put(id, update(it)) }
        }
        persistDownloads()
    }
    private fun getDownloadDir(context: Context): File = (context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "downloads")).apply { mkdirs() }
    private fun outputName(fileName: String): String = if (fileName.substringAfterLast('.', "").isNotBlank()) fileName else "$fileName.mp4"

    private fun createNotificationChannel() {
        val context = appContext ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply { description = "Download progress notifications" })
        }
    }

    private fun showDownloadNotification(item: DirectDownloadItem) {
        val context = appContext ?: return
        notify(item.id, NotificationCompat.Builder(context, CHANNEL_ID).setContentTitle("Downloading: ${item.title}").setContentText("0%").setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setProgress(100, 0, true).build())
    }
    private fun updateDownloadNotification(id: String) { val item = _activeDownloads.value[id] ?: return; notify(id, NotificationCompat.Builder(appContext ?: return, CHANNEL_ID).setContentTitle("Downloading: ${item.title}").setContentText("${item.progress}% ${item.speed}").setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(true).setProgress(100, item.progress, item.totalBytes <= 0L).build()) }
    private fun showCompletedNotification(id: String) {
        val item = _activeDownloads.value[id] ?: return
        val actualSize = item.totalBytes
        notify(
            id,
            NotificationCompat.Builder(appContext ?: return, CHANNEL_ID)
                .setContentTitle("Download Complete: ${item.title}")
                .setContentText(formatFileSize(actualSize))
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .build()
        )
    }
    private fun showFailedNotification(id: String, error: String) {
        val context = appContext ?: return
        notify(id, NotificationCompat.Builder(context, CHANNEL_ID).setContentTitle("Download Failed").setContentText(error).setSmallIcon(android.R.drawable.stat_notify_error).setAutoCancel(true).build())
    }
    private fun notify(id: String, notification: Notification) { appContext?.let { runCatching { NotificationManagerCompat.from(it).notify(id.hashCode(), notification) } } }
    private fun cancelNotification(id: String, context: Context) { runCatching { NotificationManagerCompat.from(context).cancel(id.hashCode()) } }
    private fun httpException(code: Int, message: String?): IOException = when (code) { 401 -> IOException("Authentication required (401)."); 403 -> IOException("Access denied (403)."); 404 -> IOException("File not found (404). The link may have expired."); in 500..599 -> IOException("Server error ($code). Try again later."); else -> IOException("HTTP $code: ${message ?: "Request failed"}") }
    private fun calculateEta(downloadedBytes: Long, totalBytes: Long, speedBps: Double): String {
        if (totalBytes <= 0L || downloadedBytes >= totalBytes || speedBps <= 0.0) return "Calculating..."
        val remainingSeconds = ((totalBytes - downloadedBytes) / speedBps).toLong().coerceAtLeast(1L)
        return when {
            remainingSeconds >= 3600L -> "${remainingSeconds / 3600L}h ${(remainingSeconds % 3600L) / 60L}m left"
            remainingSeconds >= 60L -> "${remainingSeconds / 60L}m left"
            else -> "${remainingSeconds}s left"
        }
    }
    private fun formatFileSize(bytes: Long): String = when { bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0); bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0); bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0); else -> "$bytes B" }
    private fun formatSpeed(bytesPerSecond: Double): String = when { bytesPerSecond >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSecond / 1_000_000.0); bytesPerSecond >= 1_000 -> String.format("%.1f KB/s", bytesPerSecond / 1_000.0); else -> String.format("%.0f B/s", bytesPerSecond) }
    private fun sanitizeFileName(name: String): String = name.replace(Regex("[^\\w\\s.-]"), "").trim().take(100)
}
