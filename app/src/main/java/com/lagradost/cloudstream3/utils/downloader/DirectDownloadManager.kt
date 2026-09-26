package com.lagradost.cloudstream3.utils.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
import java.util.concurrent.ConcurrentHashMap

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
    val error: String? = null,
    val selectedHeight: Int = 0,
)

enum class DirectDownloadStatus { PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED }

private const val MIN_VALID_VIDEO_BYTES = 1024L * 1024L

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
    private const val MAX_RETRIES = 3
    private const val MAX_RESOLVE_RETRIES = 2

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
            Toast.makeText(context, "Invalid download URL. The link may have expired.", Toast.LENGTH_LONG).show()
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
        )
        _activeDownloads.value = _activeDownloads.value + (downloadId to item)
        downloadJobs[downloadId] = downloadScope.launch {
            var currentLink = downloadLink
            var resolveAttempt = 0
            while (currentCoroutineContext().isActive) {
                val result = if (isHls(currentLink)) {
                    downloadHlsVideo(context.applicationContext, downloadId, currentLink, item)
                } else if (isDash(currentLink)) {
                    downloadDashVideo(context.applicationContext, downloadId, currentLink, item)
                } else {
                    executeDownload(context.applicationContext, downloadId, currentLink)
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
                updateItem(downloadId) { it.copy(url = cleanDownloadUrl(freshLink.url), status = DirectDownloadStatus.PENDING, error = null) }
                delay(1_000L)
            }
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
            type = if (isHlsUrl(item.url)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        )
        updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PENDING, error = null) }
        downloadJobs[downloadId] = downloadScope.launch {
            if (isHls(link)) downloadHlsVideo(context, downloadId, link, item)
            else if (isDash(link)) downloadDashVideo(context, downloadId, link, item)
            else executeDownload(context, downloadId, link)
        }
    }

    fun cancelDownload(downloadId: String) {
        val item = _activeDownloads.value[downloadId]
        downloadJobs.remove(downloadId)?.cancel()
        item?.filePath?.let { File(it).delete() }
        item?.let { File(getDownloadDir(appContext ?: return), outputName(it.fileName) + ".part").delete() }
        _activeDownloads.value = _activeDownloads.value - downloadId
        appContext?.let { cancelNotification(downloadId, it) }
    }

    private suspend fun executeDownload(context: Context, downloadId: String, link: ExtractorLink): Boolean {
        val item = _activeDownloads.value[downloadId] ?: return true
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
                        if (existingBytes > 0L && !isSignedDirectUrl(link.url)) setRequestProperty("Range", "bytes=$existingBytes-")
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
                        throw IOException("Server returned '${contentType.take(60)}', not a video. The link is likely expired or requires different headers.")
                    }
                    if (responseLength in 1 until MIN_VALID_VIDEO_BYTES) {
                        connection.disconnect()
                        throw IOException("Server reports only ${formatFileSize(responseLength)}. Refusing to save a non-video response.")
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
                                    updateItem(downloadId) { it.copy(progress = progress, downloadedBytes = downloadedBytes, speed = formatSpeed((downloadedBytes - startingBytes) / elapsed)) }
                                    updateDownloadNotification(downloadId)
                                    lastUpdate = now
                                }
                            }
                        }
                    }
                    if (!currentCoroutineContext().isActive) throw CancellationException()
                    if (totalBytes > 0L && tempFile.length() < totalBytes) throw IOException("Connection ended before the file completed.")
                    finalizeDownload(downloadId, outputFile, tempFile)
                    showCompletedNotification(downloadId, outputFile)
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
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PAUSED) }
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

            var downloaded = 0L
            FileOutputStream(tempFile, false).use { output ->
                segments.forEachIndexed { index, segmentUrl ->
                    if (!currentCoroutineContext().isActive) throw CancellationException()
                    val bytes = requestHls(segmentUrl, headers).body
                    output.write(bytes)
                    downloaded += bytes.size
                    val progress = ((index + 1) * 100L / segments.size).toInt()
                    updateItem(downloadId) { it.copy(status = DirectDownloadStatus.DOWNLOADING, progress = progress, downloadedBytes = downloaded, totalBytes = downloaded * segments.size / (index + 1L)) }
                    updateDownloadNotification(downloadId)
                }
            }
            if (!currentCoroutineContext().isActive) throw CancellationException()
            finalizeDownload(downloadId, outputFile, tempFile, validateContainer = false)
            showCompletedNotification(downloadId, outputFile)
            return true
        } catch (_: CancellationException) {
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PAUSED) }
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

    private data class DashRepresentation(val width: Int, val height: Int, val bandwidth: Long, val segments: List<String>)

    /** Downloads unencrypted ISO-BMFF DASH video by expanding the MPD segment addressing. */
    private suspend fun downloadDashVideo(context: Context, downloadId: String, link: ExtractorLink, item: DirectDownloadItem): Boolean {
        val outputFile = File(getDownloadDir(context), outputName(item.fileName))
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.part")
        try {
            val headers = requestHeaders(link)
            Log.e("DASH_DEBUG", "Fetching MPD: ${link.url.take(300)}")
            val response = requestHls(link.url, headers)
            val manifest = String(response.body, Charsets.UTF_8)
            require(manifest.contains("<MPD", ignoreCase = true)) { "The server did not return a DASH MPD manifest." }
            val representations = parseDashRepresentations(manifest, response.url)
            require(representations.isNotEmpty()) { "No playable video representations were found in the DASH manifest." }
            val selected = if (item.selectedHeight > 0) representations.filter { it.height in 1..item.selectedHeight }.maxByOrNull { it.height } ?: representations.maxByOrNull { it.height } else representations.maxByOrNull { it.height * 1_000_000L + it.bandwidth }
            val segments = requireNotNull(selected).segments
            require(segments.isNotEmpty()) { "No DASH media segments were found in the manifest." }
            var downloaded = 0L
            FileOutputStream(tempFile, false).use { output ->
                segments.forEachIndexed { index, segmentUrl ->
                    if (!currentCoroutineContext().isActive) throw CancellationException()
                    val bytes = requestHls(segmentUrl, headers).body
                    require(bytes.isNotEmpty()) { "DASH segment ${index + 1} was empty." }
                    output.write(bytes)
                    downloaded += bytes.size
                    val progress = ((index + 1) * 100L / segments.size).toInt()
                    updateItem(downloadId) { it.copy(status = DirectDownloadStatus.DOWNLOADING, progress = progress, downloadedBytes = downloaded, totalBytes = downloaded * segments.size / (index + 1L)) }
                    updateDownloadNotification(downloadId)
                }
            }
            if (!currentCoroutineContext().isActive) throw CancellationException()
            finalizeDownload(downloadId, outputFile, tempFile, validateContainer = false)
            showCompletedNotification(downloadId, outputFile)
            return true
        } catch (_: CancellationException) {
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PAUSED) }
            return true
        } catch (error: Exception) {
            tempFile.delete()
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

    private fun parseDashRepresentations(manifest: String, manifestUrl: String): List<DashRepresentation> {
        val result = mutableListOf<DashRepresentation>()
        val adaptationRegex = Regex("<AdaptationSet\\b([^>]*)>(.*?)</AdaptationSet>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val representationRegex = Regex("<Representation\\b([^>]*?)(?:/>|>(.*?)</Representation>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        adaptationRegex.findAll(manifest).forEach { adaptation ->
            val parentAttrs = parseXmlAttributes(adaptation.groupValues[1])
            val adaptationBody = adaptation.groupValues[2]
            if (parentAttrs["contentType"]?.equals("video", true) != true && !adaptationBody.contains("mimeType=\"video/", true)) return@forEach
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

    private fun expandDashTemplate(templateAttrs: String, templateBody: String, attrs: Map<String, String>, base: String, manifestUrl: String): List<String> {
        val template = parseXmlAttributes(templateAttrs)
        val initialization = template["initialization"]?.let { resolveDashUrl(substituteDash(it, attrs["id"].orEmpty(), 0L, 0L), base, manifestUrl) }
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
                segments += resolveDashUrl(substituteDash(media, attrs["id"].orEmpty(), number, currentTime), base, manifestUrl)
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

    private fun substituteDash(template: String, id: String, number: Long, time: Long): String =
        template.replace("\$RepresentationID\$", id)
            .replace(Regex("\${'$'}Number%0(\\d+)d\${'$'}")) { it.groupValues[1].toInt().let { width -> number.toString().padStart(width, '0') } }
            .replace("\$Number\$", number.toString())
            .replace("\$Time\$", time.toString())
    private fun resolveDashUrl(path: String, base: String, manifestUrl: String): String = runCatching { URI(if (base.startsWith("http", true)) base else URI(manifestUrl).resolve(base).toString()).resolve(path).toString() }.getOrDefault(path)
    private fun parseXmlAttributes(raw: String): Map<String, String> = Regex("([A-Za-z_:][\\w:.-]*)\\s*=\\s*\"([^\"]*)\"").findAll(raw).associate { it.groupValues[1] to it.groupValues[2] }

    private fun finalizeDownload(downloadId: String, outputFile: File, tempFile: File, validateContainer: Boolean = true) {
        require(tempFile.length() >= MIN_VALID_VIDEO_BYTES) {
            "Downloaded file is only ${formatFileSize(tempFile.length())}; it may be a manifest, not a video."
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
        val size = outputFile.length()
        updateItem(downloadId) { it.copy(status = DirectDownloadStatus.COMPLETED, progress = 100, downloadedBytes = size, totalBytes = size, filePath = outputFile.absolutePath, speed = "") }
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

    private fun isDash(link: ExtractorLink): Boolean = link.url.contains(".mpd", ignoreCase = true) || link.type.name.equals("DASH", ignoreCase = true)
    private fun isHls(link: ExtractorLink): Boolean = link.type == ExtractorLinkType.M3U8 || isHlsUrl(link.url)
    private fun isHlsUrl(url: String): Boolean = url.contains(".m3u8", ignoreCase = true)
    private fun validateUrl(url: String): Boolean = runCatching { URI(url).let { it.scheme in setOf("http", "https") && !it.host.isNullOrBlank() } }.getOrDefault(false)
    private fun updateItem(id: String, update: (DirectDownloadItem) -> DirectDownloadItem) { _activeDownloads.value = _activeDownloads.value.toMutableMap().apply { get(id)?.let { put(id, update(it)) } } }
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
    private fun showCompletedNotification(id: String, file: File) { val item = _activeDownloads.value[id] ?: return; notify(id, NotificationCompat.Builder(appContext ?: return, CHANNEL_ID).setContentTitle("Download Complete: ${item.title}").setContentText(formatFileSize(file.length())).setSmallIcon(android.R.drawable.stat_sys_download_done).setAutoCancel(true).build()) }
    private fun showFailedNotification(id: String, error: String) {
        val context = appContext ?: return
        notify(id, NotificationCompat.Builder(context, CHANNEL_ID).setContentTitle("Download Failed").setContentText(error).setSmallIcon(android.R.drawable.stat_notify_error).setAutoCancel(true).build())
    }
    private fun notify(id: String, notification: Notification) { appContext?.let { runCatching { NotificationManagerCompat.from(it).notify(id.hashCode(), notification) } } }
    private fun cancelNotification(id: String, context: Context) { runCatching { NotificationManagerCompat.from(context).cancel(id.hashCode()) } }
    private fun httpException(code: Int, message: String?): IOException = when (code) { 401 -> IOException("Authentication required (401)."); 403 -> IOException("Access denied (403)."); 404 -> IOException("File not found (404). The link may have expired."); in 500..599 -> IOException("Server error ($code). Try again later."); else -> IOException("HTTP $code: ${message ?: "Request failed"}") }
    private fun formatFileSize(bytes: Long): String = when { bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0); bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0); bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0); else -> "$bytes B" }
    private fun formatSpeed(bytesPerSecond: Double): String = when { bytesPerSecond >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSecond / 1_000_000.0); bytesPerSecond >= 1_000 -> String.format("%.1f KB/s", bytesPerSecond / 1_000.0); else -> String.format("%.0f B/s", bytesPerSecond) }
    private fun sanitizeFileName(name: String): String = name.replace(Regex("[^\\w\\s.-]"), "").trim().take(100)
}
