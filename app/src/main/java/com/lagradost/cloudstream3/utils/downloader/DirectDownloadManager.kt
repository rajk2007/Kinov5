package com.lagradost.cloudstream3.utils.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.parseHlsDuration
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

private data class HlsAudioRendition(
    val groupId: String,
    val language: String,
    val name: String,
    val isDefault: Boolean,
    val uri: String,
)

/** Direct file and unencrypted HLS downloader used by the download-options UI. */
object DirectDownloadManager {
    private const val TAG = "DirectDownload"
    private const val CHANNEL_ID = "kino_downloads"
    private const val DOWNLOAD_PREFS = "kino_downloads"
    private const val SAVED_DOWNLOADS = "saved_downloads"
    private const val MAX_RETRIES = 5
    private const val MAX_RESOLVE_RETRIES = 2
    private const val MIN_VALID_VIDEO_BYTES = 1024L * 1024L // 1 MiB minimum for valid video

    private val _activeDownloads = MutableStateFlow<Map<String, DirectDownloadItem>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DirectDownloadItem>> = _activeDownloads.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()
    private val posterCache = ConcurrentHashMap<String, Bitmap>()
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
        estimatedSizeBytes: Long = 0L,
        dataUrl: String? = null,
        reResolveLink: (suspend () -> ExtractorLink?)? = null,
    ): Boolean {
        Log.e("URL_TRACE", "═════════════════════════════")
        Log.e("URL_TRACE", "DOWNLOAD STARTING:")
        Log.e("URL_TRACE", "  Title: $title")
        Log.e("URL_TRACE", "  FULL URL: ${link.url}")
        Log.e("URL_TRACE", "  URL length: ${link.url.length}")
        Log.e("URL_TRACE", "  Link type: ${link.type}")
        Log.e("URL_TRACE", "  Link type name: ${link.type.name}")
        Log.e("URL_TRACE", "═════════════════════════════")
        Log.e("TRACE_DL", "═══════════════════════════════════")
        Log.e("TRACE_DL", "🚀 startDownload() CALLED")
        Log.e("TRACE_DL", "   Title: $title")
        Log.e("TRACE_DL", "   URL: ${link.url}")
        Log.e("TRACE_DL", "   URL length: ${link.url.length}")
        Log.e("TRACE_DL", "   Link type: ${link.type}")
        Log.e("TRACE_DL", "   Link type name: ${link.type.name}")
        Log.e("TRACE_DL", "   Has .mpd: ${link.url.contains(".mpd", ignoreCase = true)}")
        Log.e("TRACE_DL", "   Has .m3u8: ${link.url.contains(".m3u8", ignoreCase = true)}")
        Log.e("TRACE_DL", "   Has mpd: ${link.url.contains("mpd", ignoreCase = true)}")
        Log.e("TRACE_DL", "   Has m3u8: ${link.url.contains("m3u8", ignoreCase = true)}")
        Log.e("TRACE_DL", "   Has /dash/: ${link.url.contains("/dash/", ignoreCase = true)}")
        Log.e("TRACE_DL", "   Has manifest: ${link.url.lowercase().contains("manifest")}")
        Log.e("TRACE_DL", "   Type == M3U8: ${link.type == ExtractorLinkType.M3U8}")
        Log.e("TRACE_DL", "   Type == DASH: ${link.type == ExtractorLinkType.DASH}")
        Log.e("TRACE_DL", "═══════════════════════════════════")
        initialize(context)
        Log.e(TAG, "URL_DEBUG original link URL: ${link.url}")
        val downloadUrl = cleanDownloadUrl(link.url)
        Log.e(TAG, "URL_DEBUG clean URL for download: $downloadUrl")
        Log.e("DL_DEBUG", "═════════════════════════════")
        Log.e("DL_DEBUG", "startDownload called")
        Log.e("DL_DEBUG", "URL: ${downloadUrl.take(300)}")
        Log.e("DL_DEBUG", "URL length: ${downloadUrl.length}")
        Log.e("DL_DEBUG", "URL lowercase: ${downloadUrl.lowercase().take(150)}")
        Log.e("DL_DEBUG", "Link type: ${link.type}")
        Log.e("DL_DEBUG", "Link type name: ${link.type.name}")
        Log.e("DL_DEBUG", "Contains .mpd: ${downloadUrl.contains(".mpd", ignoreCase = true)}")
        Log.e("DL_DEBUG", "Contains .m3u8: ${downloadUrl.contains(".m3u8", ignoreCase = true)}")
        Log.e("DL_DEBUG", "Contains m3u8: ${downloadUrl.contains("m3u8", ignoreCase = true)}")
        Log.e("DL_DEBUG", "Contains dash: ${downloadUrl.lowercase().contains("dash")}")
        Log.e("DL_DEBUG", "Contains manifest: ${downloadUrl.lowercase().contains("manifest")}")
        Log.e("DL_DEBUG", "Type is M3U8: ${link.type == ExtractorLinkType.M3U8}")
        Log.e("DL_DEBUG", "Type is DASH: ${link.type.name.equals("DASH", ignoreCase = true)}")
        Log.e("DL_DEBUG", "═════════════════════════════")
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
            totalBytes = estimatedSizeBytes,
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
                val isHlsLink = isHls(currentLink) || isHlsContentType(contentType)
                val isDashLink = isDash(currentLink) || isDashContentType(contentType) || isManifestUrl(currentLink.url)
                Log.e("TRACE_ROUTE", "═══════════════════════════════════")
                Log.e("TRACE_ROUTE", "📍 ABOUT TO ROUTE DOWNLOAD")
                Log.e("TRACE_ROUTE", "   isHls calculated: $isHlsLink")
                Log.e("TRACE_ROUTE", "   isDash calculated: $isDashLink")
                Log.e("TRACE_ROUTE", "   URL being checked: ${currentLink.url.lowercase()}")
                Log.e("ROUTE_TRACE", "═════════════════════════════")
                Log.e("ROUTE_TRACE", "ROUTING DECISION:")
                Log.e("ROUTE_TRACE", "  URL: ${currentLink.url.take(150)}")
                Log.e("ROUTE_TRACE", "  Contains .mpd: ${currentLink.url.contains(".mpd")}")
                Log.e("ROUTE_TRACE", "  Contains .m3u8: ${currentLink.url.contains(".m3u8")}")
                Log.e("ROUTE_TRACE", "  Contains mpd: ${currentLink.url.contains("mpd")}")
                Log.e("ROUTE_TRACE", "  Contains m3u8: ${currentLink.url.contains("m3u8")}")
                Log.e("ROUTE_TRACE", "  Contains /dash/: ${currentLink.url.contains("/dash/")}")
                Log.e("ROUTE_TRACE", "  Contains manifest: ${currentLink.url.lowercase().contains("manifest")}")
                Log.e("ROUTE_TRACE", "  Type == M3U8: ${currentLink.type == ExtractorLinkType.M3U8}")
                Log.e("ROUTE_TRACE", "  Type == DASH: ${currentLink.type == ExtractorLinkType.DASH}")
                Log.e("ROUTE_TRACE", "═════════════════════════════")
                Log.e("DL_ROUTE", "About to route URL=${currentLink.url.take(300)}")
                Log.e("DL_ROUTE", "isHls = $isHlsLink")
                Log.e("DL_ROUTE", "isDash = $isDashLink")
                Log.e("DL_ROUTE", "Content-Type: $contentType")
                val result = when {
                    isHlsLink -> {
                        Log.e("TRACE_ROUTE", "✅ TAKING HLS PATH")
                        Log.e("DL_ROUTE", "Taking HLS path")
                        downloadHlsVideo(context.applicationContext, downloadId, currentLink, item)
                    }
                    isDashLink -> {
                        Log.e("TRACE_ROUTE", "✅ TAKING DASH PATH")
                        Log.e("DL_ROUTE", "Taking DASH path")
                        downloadDashVideo(context.applicationContext, downloadId, currentLink, item)
                    }
                    else -> {
                        Log.e("TRACE_ROUTE", "⚠️ TAKING DIRECT/FALLBACK PATH")
                        Log.e("TRACE_ROUTE", "   This might be the problem!")
                        Log.e("DL_ROUTE", "Taking DIRECT path")
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
        if (dataUrl != null) {
            downloadEnglishSubtitle(context.applicationContext, dataUrl, title, fileName, apiName)
        } else {
            Log.e("SUBS", "dataUrl is null; cannot load provider subtitles")
        }
        showDownloadNotification(item)
        Log.d(TAG, "Download started: $title")
        return true
    }

    private fun downloadEnglishSubtitle(
        context: Context,
        dataUrl: String,
        title: String,
        fileName: String,
        apiName: String,
    ) {
        downloadScope.launch {
            try {
                Log.e("SUBS", "Loading provider subtitles title=$title api=$apiName data=${dataUrl.take(160)}")
                val api = APIHolder.getApiFromNameNull(apiName)
                if (api == null) {
                    Log.e("SUBS", "API not found: $apiName")
                    return@launch
                }
                val subtitles = mutableListOf<SubtitleFile>()
                APIRepository(api).loadLinks(
                    data = dataUrl, isCasting = false,
                    subtitleCallback = { subtitle -> subtitles += subtitle },
                    callback = {},
                )
                Log.e("SUBS", "Provider returned ${subtitles.size} subtitle tracks")
                subtitles.forEach { Log.e("SUBS", "track lang=${it.lang} url=${it.url.take(160)}") }
                val subtitle = subtitles.firstOrNull { isEnglishSubtitle(it) }
                if (subtitle == null) {
                    Log.e("SUBS", "No English provider subtitle track found")
                    return@launch
                }
                Log.e("SUBS", "Selected English provider subtitle lang=${subtitle.lang} url=${subtitle.url.take(160)}")
                val content = fetchSubtitleContent(subtitle.url, subtitle.headers.orEmpty())
                if (content.isBlank()) {
                    Log.e("SUBS", "Selected provider subtitle was empty")
                    return@launch
                }
                val base = fileName.substringBeforeLast('.', fileName).ifBlank { fileName }
                val subtitleFile = File(getDownloadDir(context), "$base.srt")
                subtitleFile.writeText(toSrt(content))
                Log.e("SUBS", "saved path=${subtitleFile.absolutePath}")
            } catch (error: Exception) {
                Log.e("SUBS", "Provider subtitle download failed for $title", error)
            }
        }
    }

    private fun isEnglishSubtitle(subtitle: SubtitleFile): Boolean {
        val language = subtitle.lang.trim().lowercase().replace('_', '-')
        return language == "en" || language == "eng" || language.startsWith("en-") ||
            language.contains("english")
    }

    private fun fetchSubtitleContent(url: String, headers: Map<String, String>): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (connection.responseCode !in 200..299) return ""
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun toSrt(content: String): String {
        if (!content.trimStart().startsWith("WEBVTT", ignoreCase = true)) return content
        return content
            .lineSequence()
            .drop(1)
            .joinToString("\n")
            .replace(Regex("(\\d{2}:\\d{2}:\\d{2})\\.(\\d{3})"), "$1,$2")
            .trim() + "\n"
    }

    fun pauseDownload(downloadId: String) {
        Log.d(TAG, "Pausing download without deleting partial data: $downloadId")
        // Disconnect first so a blocking read wakes up promptly; cancellation then preserves
        // the .part file and any completed HLS/DASH segments for resume.
        activeConnections[downloadId]?.disconnect()
        downloadJobs.remove(downloadId)?.cancel()
        updateItem(downloadId) { it.copy(status = DirectDownloadStatus.PAUSED, speed = "", eta = "") }
        persistDownloads(force = true)
        updateDownloadNotification(downloadId)
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
        updateDownloadNotification(downloadId)
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
        Log.e("TRACE_DIRECT", "═══════════════════════════════════")
        Log.e("TRACE_DIRECT", "📄 executeDirectDownload() ENTERED")
        Log.e("TRACE_DIRECT", "   URL: ${link.url}")
        Log.e("TRACE_DIRECT", "   ⚠️ IF THIS IS A MANIFEST URL, THIS IS THE BUG!")
        Log.e("TRACE_DIRECT", "═══════════════════════════════════")
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
        var authoritativeTotalBytes: Long? = null
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
                    activeConnections[downloadId] = connection
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
                    if (responseLength in 1 until MIN_VALID_VIDEO_BYTES) {
                        connection.disconnect()
                        throw IOException(
                            "Server reports only ${formatFileSize(responseLength)}. " +
                                "Refusing to save a non-video response."
                        )
                    }
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
                    val append = when {
                        existingBytes <= 0L -> false
                        responseCode == HttpURLConnection.HTTP_PARTIAL -> true
                        responseCode == HttpURLConnection.HTTP_OK && responseLength > 0L && responseLength == existingBytes -> {
                            // The server ignored Range, but the retained part is already complete.
                            finalizeDownload(downloadId, outputFile, tempFile)
                            return true
                        }
                        else -> {
                            // A 200 response with a different length is a full restart, not an
                            // append. Avoid duplicating the retained prefix in the output.
                            Log.w(TAG, "Server did not return 206 for Range; restarting (existing=$existingBytes, code=$responseCode)")
                            tempFile.delete()
                            false
                        }
                    }
                    val startingBytes = if (append) existingBytes else 0L
                    if (authoritativeTotalBytes == null) {
                        authoritativeTotalBytes = connection.getHeaderField("Content-Range")
                            ?.substringAfterLast('/')
                            ?.toLongOrNull()
                            ?.takeIf { it > 0L }
                            ?: connection.contentLengthLong.takeIf { it > 0L }?.let { startingBytes + it }
                    }
                    val totalBytes = authoritativeTotalBytes ?: item.totalBytes
                    updateItem(downloadId) {
                        it.copy(
                            status = DirectDownloadStatus.DOWNLOADING,
                            downloadedBytes = startingBytes,
                            totalBytes = totalBytes,
                        )
                    }

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
                    activeConnections.remove(downloadId, connection)
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

    /**
     * HLS output rules: MPEG-TS segments may be concatenated, but fMP4 segments must stay in a
     * local HLS playlist (with their init segment); never label a naive fMP4 concat as MP4.
     */
    private suspend fun downloadHlsVideo(context: Context, downloadId: String, link: ExtractorLink, item: DirectDownloadItem): Boolean {
        var outputFile: File? = null
        var tempFile: File? = null
        var segmentDir: File? = null
        var localPlaylistFile: File? = null
        try {
            val headers = requestHeaders(link)
            Log.e("TRACE_HLS", "═══════════════════════════════════")
            Log.e("TRACE_HLS", "📹 downloadHlsVideo() ENTERED")
            Log.e("TRACE_HLS", "   URL: ${link.url}")
            Log.e("TRACE_HLS", "═══════════════════════════════════")
            Log.e("DL_EXEC", "Entered downloadHlsVideo")
            Log.e("DL_EXEC", "Fetching manifest from: ${link.url.take(300)}")
            var playlistResponse = requestHls(link.url, headers)
            var playlistUrl = playlistResponse.url
            var playlist = String(playlistResponse.body, Charsets.UTF_8)
            val masterPlaylistUrl = playlistUrl
            val masterPlaylist = playlist.takeIf { it.contains("#EXT-X-STREAM-INF", ignoreCase = true) }
            var selectedAudio: HlsAudioRendition? = null
            val inputIsMaster = playlist.contains("#EXT-X-STREAM-INF", ignoreCase = true)
            Log.e("HLS_AUDIO", "input_is_master=$inputIsMaster")
            Log.e("TRACE_HLS", "   Manifest fetched: ${playlist.length} chars")
            Log.e("TRACE_HLS", "   First 300 chars: ${playlist.take(300)}")
            Log.e("DL_EXEC", "HLS manifest length: ${playlist.length}")
            Log.e("HLS_DEBUG", "Starting HLS download: ${item.title}")
            Log.e("HLS_DEBUG", "URL: ${link.url.take(150)}")
            Log.e("HLS_DEBUG", "Playlist length: ${playlist.length}")
            Log.e("HLS_DEBUG", "Playlist first 300 chars: ${playlist.take(300)}")
            Log.e("HLS_DEBUG", "Is encrypted: ${playlist.contains("#EXT-X-KEY", ignoreCase = true)}")
            require(playlist.contains("#EXTM3U")) { "The server did not return an HLS playlist." }
            var selectedVariantBandwidth: Long? = null
            if (inputIsMaster) {
                val audioRenditions = parseHlsAudioRenditions(playlist, playlistUrl)
                Log.e("HLS_AUDIO", "renditions=${audioRenditions.size}")
                selectedAudio = audioRenditions.firstOrNull { isEnglishLanguage(it.language) }
                    ?: audioRenditions.firstOrNull { it.isDefault }
                    ?: audioRenditions.firstOrNull()
                if (selectedAudio != null) {
                    Log.e("HLS_AUDIO", "selected lang=${selectedAudio!!.language} uri=${selectedAudio!!.uri.take(180)}")
                } else {
                    Log.e("HLS_AUDIO", "no separate audio; assuming muxed in video")
                }
                val variants = parseHlsVariants(playlist, playlistUrl)
                require(variants.isNotEmpty()) { "No playable HLS variants were found." }
                val selected = if (item.selectedHeight > 0) {
                    variants.filter { (it.height ?: 0) <= item.selectedHeight }.maxByOrNull { it.height ?: 0 }
                        ?: variants.maxByOrNull { it.height ?: it.bandwidth ?: 0 }
                } else variants.maxByOrNull { it.height ?: it.bandwidth ?: 0 }
                selectedVariantBandwidth = selected?.bandwidth?.toLong()
                playlistResponse = requestHls(requireNotNull(selected).url, headers)
                playlistUrl = playlistResponse.url
                playlist = String(playlistResponse.body, Charsets.UTF_8)
            } else if (link.audioTracks.isNotEmpty()) {
                // Some extractors return the selected variant playlist directly and expose
                // separate audio URLs on ExtractorLink instead of returning the master.
                selectedAudio = HlsAudioRendition(
                    groupId = "audio",
                    language = "",
                    name = "Audio",
                    isDefault = true,
                    uri = link.audioTracks.first().url,
                )
                Log.e("HLS_AUDIO", "renditions=${link.audioTracks.size} from ExtractorLink.audioTracks")
                Log.e("HLS_AUDIO", "selected lang=${selectedAudio!!.language.ifBlank { "unknown" }} uri=${selectedAudio!!.uri.take(180)}")
            } else {
                Log.e("HLS_AUDIO", "renditions=0; no separate audio, assuming muxed in video")
            }
            require(!playlist.contains("#EXT-X-KEY", ignoreCase = true)) { "Encrypted HLS downloads are not supported." }
            val segments = playlist.lineSequence().map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { URI(playlistUrl).resolve(it).toString() }.toList()
            require(segments.isNotEmpty()) { "No media segments were found in the HLS playlist." }
            val mediaDurationSeconds = parseHlsDuration(playlist)
            val estimatedMediaBytes = selectedVariantBandwidth?.let { bandwidth ->
                (bandwidth.toDouble() * mediaDurationSeconds / 8.0 * 0.35).toLong()
            }?.takeIf { it > 0L }
            if (item.totalBytes <= 0L && estimatedMediaBytes != null) {
                updateItem(downloadId) { current ->
                    current.copy(totalBytes = estimatedMediaBytes.coerceAtLeast(current.downloadedBytes))
                }
            }
            // URL suffixes are unreliable on signed Kdesa URLs. Probe the first real segment so
            // that a TS stream cannot accidentally be written and advertised as fMP4 (or vice versa).
            val firstSegmentBytes = requestHls(segments.first(), headers).body
            val firstSegmentType = detectHlsSegmentType(firstSegmentBytes)
            Log.e("TRACE_HLS", "   First segment type: $firstSegmentType")
            require(firstSegmentType != HlsSegmentType.HTML) {
                "HLS segment returned an HTML error page; the link may have expired."
            }
            require(firstSegmentType != HlsSegmentType.UNKNOWN) {
                "Unsupported or invalid HLS segment format."
            }
            val isFragmentedMp4 = firstSegmentType == HlsSegmentType.FMP4 ||
                playlist.contains("#EXT-X-MAP", ignoreCase = true)
            val extension = if (isFragmentedMp4) ".mp4" else ".ts"
            val baseName = item.fileName.removeSuffix(".mp4").removeSuffix(".ts")
            outputFile = File(getDownloadDir(context), "$baseName$extension")
            val resolvedOutputFile = requireNotNull(outputFile)
            tempFile = File(resolvedOutputFile.parentFile, "${resolvedOutputFile.name}.part")
            Log.e("HLS_DEBUG", "═════════════════════════════")
            Log.e("HLS_DEBUG", "Starting HLS download: ${item.title}")
            Log.e("HLS_DEBUG", "URL: ${link.url.take(150)}")
            Log.e("HLS_DEBUG", "Output file: ${resolvedOutputFile.absolutePath}")
            Log.e("HLS_DEBUG", "Output extension: ${resolvedOutputFile.extension}")
            Log.e("HLS_DEBUG", "Playlist length: ${playlist.length}")
            Log.e("HLS_DEBUG", "Playlist first 300 chars: ${playlist.take(300)}")
            Log.e("HLS_DEBUG", "Is encrypted: ${playlist.contains("#EXT-X-KEY", ignoreCase = true)}")
            Log.e("TRACE_HLS", "   Segments found: ${segments.size}")
            Log.e("TRACE_HLS", "   Output format: ${if (isFragmentedMp4) "fMP4" else "MPEG-TS"}")
            Log.e("DL_EXEC", "HLS segments found: ${segments.size}")
            Log.e("HLS_DEBUG", "Number of segments: ${segments.size}")
            Log.e("HLS_DEBUG", "First segment URL: ${segments.firstOrNull()?.take(100)}")

            val resolvedTempFile = requireNotNull(tempFile)
            segmentDir = File(resolvedTempFile.parentFile, "${resolvedTempFile.name}.segments")
            val resolvedSegmentDir = requireNotNull(segmentDir)
            val segmentExtension = if (isFragmentedMp4) ".m4s" else ".ts"
            val mapUri = Regex("#EXT-X-MAP:[^\\n]*URI=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                .find(playlist)?.groupValues?.get(1)?.let { URI(playlistUrl).resolve(it).toString() }
            requireWritableDirectory(resolvedSegmentDir, "HLS_STEP=create_segment_dir")
            val initializationBytes = mapUri?.let { uri ->
                val bytes = requestHls(uri, headers).body
                require(bytes.isNotEmpty()) { "The HLS initialization segment was empty." }
                writeHlsFile(File(resolvedSegmentDir, "init.mp4"), bytes, "HLS_STEP=write_segment")
                bytes.size.toLong()
            } ?: 0L
            val totalDownloadedBytes = initializationBytes + downloadSegmentsParallel(
                downloadId,
                segments,
                headers,
                resolvedTempFile,
                logHlsDiagnostics = true,
                keepSegments = true,
                segmentExtension = segmentExtension,
            )
            if (!currentCoroutineContext().isActive) throw CancellationException()
            require(segments.indices.all { index ->
                File(resolvedSegmentDir, "segment_$index$segmentExtension").let { it.isFile && it.length() > 0L }
            }) { "One or more HLS segments failed to download." }

            localPlaylistFile = File(getDownloadDir(context), "$baseName.m3u8")
            val resolvedPlaylistFile = requireNotNull(localPlaylistFile)
            val durations = Regex("#EXTINF:([\\d.]+)", RegexOption.IGNORE_CASE)
                .findAll(playlist)
                .map { it.groupValues[1].toDoubleOrNull() ?: 5.0 }
                .toList()
            val targetDuration = durations.maxOrNull()?.let { kotlin.math.ceil(it).toInt() } ?: 10
            writeHlsText(resolvedPlaylistFile, buildString {
                appendLine("#EXTM3U")
                appendLine("#EXT-X-VERSION:3")
                appendLine("#EXT-X-TARGETDURATION:$targetDuration")
                appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
                appendLine("#EXT-X-MEDIA-SEQUENCE:0")
                if (mapUri != null) appendLine("#EXT-X-MAP:URI=\"${resolvedSegmentDir.name}/init.mp4\"")
                segments.indices.forEach { index ->
                    appendLine("#EXTINF:${durations.getOrNull(index) ?: 5.0},")
                    appendLine("${resolvedSegmentDir.name}/segment_$index$segmentExtension")
                }
                appendLine("#EXT-X-ENDLIST")
            })
            var audioPlaylistFile: File? = null
            var combinedDownloadedBytes = totalDownloadedBytes
            var audioTempFile: File? = null
            selectedAudio?.let { rendition ->
                val audioResponse = requestHls(rendition.uri, headers)
                val audioPlaylist = String(audioResponse.body, Charsets.UTF_8)
                require(audioPlaylist.contains("#EXTM3U", true)) { "Selected HLS audio rendition is not a playlist." }
                val audioSegments = audioPlaylist.lineSequence().map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { URI(audioResponse.url).resolve(it).toString() }.toList()
                require(audioSegments.isNotEmpty()) { "Selected HLS audio rendition has no segments." }
                Log.e("HLS_AUDIO", "audio segments count=${audioSegments.size}")
                val audioFirst = requestHls(audioSegments.first(), headers).body
                val audioFmp4 = detectHlsSegmentType(audioFirst) == HlsSegmentType.FMP4 || audioPlaylist.contains("#EXT-X-MAP", true)
                val audioExtension = if (audioFmp4) ".m4s" else ".ts"
                audioTempFile = File(getDownloadDir(context), "$baseName.audio.part")
                val audioSegmentDir = File(audioTempFile!!.parentFile, "${audioTempFile!!.name}.segments")
                requireWritableDirectory(audioSegmentDir, "HLS_STEP=create_segment_dir")
                val audioMap = Regex("#EXT-X-MAP:[^\\n]*URI=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(audioPlaylist)?.groupValues?.get(1)?.let { URI(audioResponse.url).resolve(it).toString() }
                if (audioMap != null) writeHlsFile(File(audioSegmentDir, "init.mp4"), requestHls(audioMap, headers).body, "HLS_STEP=write_segment")
                val audioBytes = downloadSegmentsParallel(downloadId, audioSegments, headers, audioTempFile!!, progressOffset = 0, progressTotal = audioSegments.size, keepSegments = true, segmentExtension = audioExtension)
                audioPlaylistFile = File(getDownloadDir(context), "$baseName.audio.m3u8")
                val audioDurations = Regex("#EXTINF:([\\d.]+)", RegexOption.IGNORE_CASE).findAll(audioPlaylist).map { it.groupValues[1].toDoubleOrNull() ?: 5.0 }.toList()
                writeHlsText(audioPlaylistFile!!, buildString {
                    appendLine("#EXTM3U"); appendLine("#EXT-X-VERSION:3"); appendLine("#EXT-X-TARGETDURATION:${audioDurations.maxOrNull()?.let { kotlin.math.ceil(it).toInt() } ?: 10}"); appendLine("#EXT-X-PLAYLIST-TYPE:VOD"); appendLine("#EXT-X-MEDIA-SEQUENCE:0")
                    if (audioMap != null) appendLine("#EXT-X-MAP:URI=\"${audioSegmentDir.name}/init.mp4\"")
                    audioSegments.indices.forEach { index -> appendLine("#EXTINF:${audioDurations.getOrNull(index) ?: 5.0},"); appendLine("${audioSegmentDir.name}/segment_$index$audioExtension") }; appendLine("#EXT-X-ENDLIST")
                })
                audioTempFile!!.delete()
                val masterFile = File(getDownloadDir(context), "$baseName.master.m3u8")
                writeHlsText(masterFile, "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",NAME=\"${rendition.name.ifBlank { "English" }}\",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE=\"${rendition.language}\",URI=\"${audioPlaylistFile!!.name}\"\n#EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO=\"audio\"\n${resolvedPlaylistFile.name}\n")
                Log.e("HLS_AUDIO", "audio_segments=${audioSegments.size} bytes=$audioBytes")
                Log.e("HLS_AUDIO", "master_path=${masterFile.absolutePath}")
                Log.e("HLS_AUDIO", "wrote local master with audio group")
                combinedDownloadedBytes += audioBytes
                updateItem(downloadId) { it.copy(filePath = masterFile.absolutePath, downloadedBytes = combinedDownloadedBytes, totalBytes = combinedDownloadedBytes) }
            }
            resolvedTempFile.delete()
            updateItem(downloadId) {
                it.copy(
                    status = DirectDownloadStatus.COMPLETED,
                    progress = 100,
                    downloadedBytes = combinedDownloadedBytes,
                    totalBytes = combinedDownloadedBytes,
                    filePath = audioPlaylistFile?.let { File(getDownloadDir(context), "$baseName.master.m3u8").absolutePath } ?: resolvedPlaylistFile.absolutePath,
                    speed = "",
                    eta = "",
                )
            }
            persistDownloads(force = true)
            Log.e("HLS_AUDIO", "final filePath=${audioPlaylistFile?.let { File(getDownloadDir(context), "$baseName.master.m3u8").absolutePath } ?: resolvedPlaylistFile.absolutePath}")
            masterPlaylist?.let { downloadHlsEnglishSubtitleFallback(context, it, masterPlaylistUrl, headers, item.fileName) }
            Log.e("HLS_DEBUG", "Created local playlist: ${resolvedPlaylistFile.absolutePath}")
            Log.e("HLS_DEBUG", "Total downloaded: $totalDownloadedBytes bytes")
            Log.e("HLS_DEBUG", "═════════════════════════════")
            Log.e("TRACE_HLS", "   Total bytes: $totalDownloadedBytes")
            Log.e("TRACE_HLS", "   Playlist size: ${resolvedPlaylistFile.length()}")
            Log.e("TRACE_HLS", "═══════════════════════════════════")
            showCompletedNotification(downloadId)
            return true
        } catch (_: CancellationException) {
            markPausedIfNoReplacement(downloadId)
            return true
        } catch (error: Exception) {
            tempFile?.delete()
            segmentDir?.deleteRecursively()
            localPlaylistFile?.delete()
            File(getDownloadDir(context), "${sanitizeFileName(item.fileName)}.audio.part").delete()
            File(getDownloadDir(context), "${sanitizeFileName(item.fileName)}.audio.part.segments").deleteRecursively()
            Log.e("HLS_FIX", "HLS download failed: ${error.message}", error)
            if (isRetryableExpiry(error)) {
                Log.e("DL_404", "❌ HLS request failed with an expired/authenticated URL", error)
                return false
            }
            Log.e(TAG, "HLS download failed: ${item.title}", error)
            val readableError = readableDownloadError(error)
            updateItem(downloadId) { it.copy(status = DirectDownloadStatus.FAILED, error = readableError) }
            showFailedNotification(downloadId, readableError)
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
        logHlsDiagnostics: Boolean = false,
        keepSegments: Boolean = false,
        segmentExtension: String = ".bin",
    ): Long {
        val parallelCount = minOf(6, segmentUrls.size)
        val segmentDir = File(outputFile.parentFile, "${outputFile.name}.segments")
        if (!segmentDir.exists()) require(segmentDir.mkdirs()) { "Could not create temporary segment directory: ${segmentDir.absolutePath}" }
        require(segmentDir.isDirectory) { "HLS segment path is not a directory: ${segmentDir.absolutePath}" }
        fun segmentFile(index: Int) = File(segmentDir, "segment_$index$segmentExtension")
        val completed = AtomicInteger(0)
        val totalBytes = AtomicLong(0L)
        segmentUrls.forEachIndexed { index, _ ->
            val existingSegment = segmentFile(index)
            if (existingSegment.exists() && existingSegment.length() > 0L) {
                completed.incrementAndGet()
                totalBytes.addAndGet(existingSegment.length())
            }
        }
        val lastUpdate = AtomicLong(0L)
        val startedAt = System.currentTimeMillis()
        coroutineScope {
                segmentUrls.withIndex()
                    .filterNot { (index, _) ->
                        val segmentFile = segmentFile(index)
                        segmentFile.exists() && segmentFile.length() > 0L
                    }
                    .chunked(parallelCount).forEach { batch ->
                    batch.map { indexed ->
                        async(Dispatchers.IO) {
                            if (!currentCoroutineContext().isActive) throw CancellationException()
                            val bytes = requestHls(indexed.value, headers).body
                            require(bytes.isNotEmpty()) { "Segment ${indexed.index + 1} was empty." }
                            if (logHlsDiagnostics && indexed.index == 0) {
                                val headerString = String(bytes.take(50).toByteArray(), Charsets.ISO_8859_1)
                                Log.e("HLS_DEBUG", "First segment size: ${bytes.size} bytes")
                                Log.e("HLS_DEBUG", "First segment header: ${headerString.take(20)}")
                                when {
                                    bytes.firstOrNull() == 0x47.toByte() -> Log.e("HLS_DEBUG", "Segment appears to be MPEG-TS (0x47 sync byte)")
                                    headerString.contains("ftyp") -> Log.e("HLS_DEBUG", "Segment appears to be fMP4")
                                    headerString.contains("<!DOCTYPE", true) || headerString.contains("<html", true) -> Log.e("HLS_DEBUG", "Segment is HTML (error page)")
                                    headerString.contains("#EXTM3U") -> Log.e("HLS_DEBUG", "Segment is another manifest (nested)")
                                    else -> Log.e("HLS_DEBUG", "Unknown segment type: ${headerString.take(30)}")
                                }
                            }
                            val target = segmentFile(indexed.index)
                            writeHlsFile(target, bytes, "HLS_STEP=write_segment")

                            val finished = completed.incrementAndGet()
                            val downloaded = totalBytes.addAndGet(bytes.size.toLong())
                            val now = System.currentTimeMillis()
                            if (finished == segmentUrls.size || now - lastUpdate.get() >= 500L) {
                                lastUpdate.set(now)
                                val elapsedSeconds = (now - startedAt).coerceAtLeast(1L) / 1_000.0
                                val speedBps = downloaded / elapsedSeconds
                                val downloadedSoFar = downloadedBytesOffset + downloaded
                                val estimatedTotalBytes = downloadedBytesOffset +
                                    (downloaded.toDouble() / finished.coerceAtLeast(1) * (progressTotal - progressOffset)).toLong()
                                val etaTotalBytes = estimatedTotalBytes.takeIf { it > 0L }
                                    ?: (downloadedSoFar + (downloaded.toDouble() / finished.coerceAtLeast(1) * (progressTotal - progressOffset - finished).coerceAtLeast(0)).toLong())
                                updateItem(downloadId) {
                                    val lockedTotal = when {
                                        it.totalBytes > 0L -> it.totalBytes
                                        estimatedTotalBytes > it.totalBytes -> estimatedTotalBytes
                                        else -> it.totalBytes
                                    }.coerceAtLeast(downloadedSoFar)
                                    it.copy(
                                        status = DirectDownloadStatus.DOWNLOADING,
                                        progress = ((progressOffset + finished) * 100 / progressTotal.coerceAtLeast(1)).coerceIn(0, 100),
                                        downloadedBytes = downloadedSoFar,
                                        totalBytes = lockedTotal,
                                        speed = formatSpeed(speedBps),
                                        eta = calculateEta(
                                            downloadedSoFar,
                                            lockedTotal.coerceAtLeast(etaTotalBytes),
                                            speedBps,
                                        ),
                                    )
                                }
                                updateDownloadNotification(downloadId)
                            }
                        }
                    }.awaitAll()
                }
            }

        if (!keepSegments) {
            try {
                requireWritableDirectory(outputFile.parentFile, "HLS_STEP=concat")
                FileOutputStream(outputFile, false).use { output ->
                    segmentUrls.indices.forEach { index ->
                        segmentFile(index).inputStream().use { input -> input.copyTo(output) }
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "HLS_STEP=concat path=${outputFile.absolutePath} ${error::class.java.simpleName}: ${error.message}", error)
                throw IOException("Could not concatenate HLS segments to the download file.", error)
            }
        }
        if (logHlsDiagnostics) {
            Log.e("HLS_DEBUG", "Total downloaded: ${totalBytes.get()} bytes")
            Log.e("HLS_DEBUG", "Output file size: ${if (keepSegments) totalBytes.get() else outputFile.length()} bytes")
        }
        updateItem(downloadId) {
            it.copy(
                progress = ((progressOffset + segmentUrls.size) * 100 / progressTotal.coerceAtLeast(1)).coerceIn(0, 100),
                downloadedBytes = downloadedBytesOffset + totalBytes.get(),
                totalBytes = it.totalBytes.coerceAtLeast(downloadedBytesOffset + totalBytes.get()),
            )
        }
        // Keep the segment directory for local HLS playback; other callers clean up
        // only after every segment has been concatenated successfully.
        if (!keepSegments) segmentDir.deleteRecursively()
        return totalBytes.get()
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
            Log.e("TRACE_DASH", "═══════════════════════════════════")
            Log.e("TRACE_DASH", "📊 downloadDashVideo() ENTERED")
            Log.e("TRACE_DASH", "   URL: ${link.url}")
            Log.e("TRACE_DASH", "═══════════════════════════════════")
            Log.e("DL_EXEC", "Entered downloadDashVideo")
            Log.e("DL_EXEC", "Fetching manifest from: ${link.url.take(300)}")
            Log.e("DASH_PARSE", "Fetching MPD: ${link.url.take(300)}")
            val response = requestHls(link.url, headers)
            val manifest = String(response.body, Charsets.UTF_8)
            Log.e("TRACE_DASH", "   Manifest fetched: ${manifest.length} chars")
            Log.e("TRACE_DASH", "   First 300 chars: ${manifest.take(300)}")
            Log.e("DASH_PARSE", "Manifest length: ${manifest.length}")
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
            Log.e("TRACE_DASH", "   Segments found: ${videoSegments.size + audioSegments.size}")
            Log.e("DASH_PARSE", "Found ${videoSegments.size} video and ${audioSegments.size} audio segments")
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
            Log.e("TRACE_DASH", "   Total bytes: ${outputFile.length()}")
            Log.e("TRACE_DASH", "   File size: ${outputFile.length()}")
            Log.e("TRACE_DASH", "═══════════════════════════════════")
            Log.e("DL_EXEC", "DASH output file size: ${outputFile.length()}")
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
                val parentTemplate = Regex("<SegmentTemplate\\b([^>]*?)(?:/\\s*>|>(.*?)</SegmentTemplate>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(adaptationBody)
            representationRegex.findAll(adaptationBody).forEach { representation ->
                val attrs = parentAttrs + parseXmlAttributes(representation.groupValues[1])
                val body = representation.groupValues.getOrNull(2).orEmpty()
                val base = Regex("<BaseURL\\s*>(.*?)</BaseURL>", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.trim() ?: parentBase ?: manifestUrl
                val template = Regex("<SegmentTemplate\\b([^>]*?)(?:/\\s*>|>(.*?)</SegmentTemplate>)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(body) ?: parentTemplate
                val segments = template?.let { expandDashTemplate(it.groupValues[1], it.groupValues[2], attrs, base, manifestUrl) }.orEmpty()
                if (segments.isEmpty()) Log.w("DASH_PARSE", "No segments for ${attrs["id"].orEmpty()} ${attrs["mimeType"].orEmpty()} ${attrs["height"].orEmpty()}p")
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
        logDownloadedSignature(tempFile)
        require(tempFile.length() > 0L) { "Downloaded file is empty." }
        require(tempFile.length() >= MIN_VALID_VIDEO_BYTES) {
            "Downloaded file is only ${formatFileSize(tempFile.length())}; " +
                "it may be a manifest, not a video."
        }
        require(!isHtmlResponse(tempFile)) {
            "Downloaded HTML page instead of video; the link may have expired."
        }
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
        requireWritableDirectory(outputFile.parentFile, "HLS_STEP=finalize")
        if (outputFile.exists() && !outputFile.delete()) {
            throw IOException("Could not replace existing download file: ${outputFile.absolutePath}")
        }
        if (!tempFile.renameTo(outputFile)) {
            Log.w(TAG, "HLS_STEP=finalize rename failed; falling back to copy path=${outputFile.absolutePath}")
            try {
                tempFile.inputStream().use { input -> outputFile.outputStream().use { output -> input.copyTo(output) } }
                require(tempFile.delete()) { "Could not remove temporary download after finalize copy." }
            } catch (error: Exception) {
                Log.e(TAG, "HLS_STEP=finalize path=${outputFile.absolutePath} ${error::class.java.simpleName}: ${error.message}", error)
                throw IOException("Could not finalize download file (filesystem error).", error)
            }
        }
        val actualSize = outputFile.length()
        Log.e("TRACE_COMPLETE", "═══════════════════════════════════")
        Log.e("TRACE_COMPLETE", "✅ markCompleted() equivalent: finalizeDownload() CALLED")
        Log.e("TRACE_COMPLETE", "   File: ${outputFile.absolutePath}")
        Log.e("TRACE_COMPLETE", "   File size: $actualSize bytes")
        Log.e("TRACE_COMPLETE", "   File size formatted: ${formatFileSize(actualSize)}")
        if (actualSize < 1024 * 1024) {
            Log.e("TRACE_COMPLETE", "   ⚠️ SUSPICIOUSLY SMALL FILE!")
            Log.e("TRACE_COMPLETE", "   This is likely a manifest, not a video!")
            runCatching {
                val firstBytes = ByteArray(100)
                outputFile.inputStream().use { it.read(firstBytes) }
                val content = String(firstBytes).trimEnd('\u0000')
                Log.e("TRACE_COMPLETE", "   First 100 chars: $content")
                if (content.contains("<?xml", ignoreCase = true) || content.contains("<MPD", ignoreCase = true) || content.contains("#EXTM3U", ignoreCase = true)) {
                    Log.e("TRACE_COMPLETE", "   ❌ CONFIRMED: This IS a manifest file!")
                    Log.e("TRACE_COMPLETE", "   ❌ The download should have been redirected to DASH/HLS!")
                }
            }.onFailure { Log.e("TRACE_COMPLETE", "   Could not inspect completed file", it) }
        }
        Log.e("TRACE_COMPLETE", "═══════════════════════════════════")
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

    private fun downloadHlsEnglishSubtitleFallback(
        context: Context, masterPlaylist: String, masterUrl: String, headers: Map<String, String>, fileName: String,
    ) {
        try {
            val candidates = masterPlaylist.lineSequence()
                .filter { it.startsWith("#EXT-X-MEDIA:", true) && it.contains("TYPE=SUBTITLES", true) }
                .mapNotNull { line ->
                    val attrs = Regex("([A-Z-]+)=\"([^\"]*)\"|([A-Z-]+)=([^,]*)", RegexOption.IGNORE_CASE).findAll(line.substringAfter(":")).associate { it.groupValues[1].ifBlank { it.groupValues[3] } to it.groupValues[2].ifBlank { it.groupValues[4] } }
                    val uri = attrs["URI"] ?: return@mapNotNull null
                    Triple(attrs, attrs["LANGUAGE"].orEmpty(), URI(masterUrl).resolve(uri).toString())
                }.toList()
            val selected = candidates.firstOrNull { it.first["DEFAULT"].equals("YES", true) }
                ?: candidates.firstOrNull { isEnglishLanguage(it.second) }
                ?: candidates.firstOrNull()
            if (selected == null) { Log.e("SUBS", "HLS has no subtitle renditions"); return }
            val response = requestHls(selected.third, headers)
            val body = String(response.body, Charsets.UTF_8)
            val subtitleContent = if (body.contains("#EXTM3U", true)) {
                body.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.map { URI(response.url).resolve(it).toString() }.joinToString("\n") { String(requestHls(it, headers).body, Charsets.UTF_8) }
            } else body
            if (subtitleContent.isBlank()) { Log.e("SUBS", "HLS subtitle rendition was empty"); return }
            val base = fileName.substringBeforeLast('.', fileName).ifBlank { fileName }
            val subtitleFile = File(getDownloadDir(context), "$base.srt")
            if (!subtitleFile.exists()) { subtitleFile.writeText(toSrt(subtitleContent)); Log.e("SUBS", "saved path=${subtitleFile.absolutePath}") }
        } catch (error: Exception) { Log.e("SUBS", "HLS subtitle fallback failed", error) }
    }

    private fun parseHlsAudioRenditions(playlist: String, baseUrl: String): List<HlsAudioRendition> =
        playlist.lineSequence().filter { it.startsWith("#EXT-X-MEDIA:", true) && it.contains("TYPE=AUDIO", true) }.mapNotNull { line ->
            val attrs = Regex("([A-Z-]+)=\"([^\"]*)\"|([A-Z-]+)=([^,]*)", RegexOption.IGNORE_CASE).findAll(line.substringAfter(":")).associate { it.groupValues[1].ifBlank { it.groupValues[3] } to it.groupValues[2].ifBlank { it.groupValues[4] } }
            val uri = attrs["URI"] ?: return@mapNotNull null
            HlsAudioRendition(attrs["GROUP-ID"].orEmpty(), attrs["LANGUAGE"].orEmpty(), attrs["NAME"].orEmpty(), attrs["DEFAULT"].equals("YES", true), URI(baseUrl).resolve(uri).toString())
        }.toList()

    private fun isEnglishLanguage(language: String): Boolean = language.lowercase().replace('_', '-').let { it == "en" || it == "eng" || it.startsWith("en-") || it.contains("english") }

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

    private fun isHtmlResponse(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val bytes = ByteArray(512)
            val count = input.read(bytes).coerceAtLeast(0)
            String(bytes, 0, count, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                .let { sample ->
                    sample.startsWith("<!doctype html", ignoreCase = true) ||
                        sample.startsWith("<html", ignoreCase = true)
                }
        }
    }.getOrDefault(false)

    private fun logDownloadedSignature(file: File) {
        runCatching {
            val bytes = ByteArray(200)
            val count = file.inputStream().use { it.read(bytes).coerceAtLeast(0) }
            val sample = String(bytes, 0, count, Charsets.UTF_8)
            val kind = when {
                sample.contains("<!doctype html", ignoreCase = true) || sample.contains("<html", ignoreCase = true) -> "HTML error page"
                sample.contains("#EXTM3U", ignoreCase = true) -> "HLS manifest"
                sample.contains("<?xml", ignoreCase = true) || sample.contains("<MPD", ignoreCase = true) -> "DASH manifest"
                count >= 8 && bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() && bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte() -> "MP4 video"
                count >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() -> "MKV/WebM video"
                else -> "unknown"
            }
            Log.e("DL_DEBUG", "Downloaded file: ${file.length()} bytes; detected=$kind; firstBytes=${sample.take(200)}")
        }.onFailure { error ->
            Log.e("DL_DEBUG", "Could not inspect downloaded file signature", error)
        }
    }

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
                put("speed", item.speed)
                put("eta", item.eta)
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
                        speed = json.optString("speed", ""),
                        eta = json.optString("eta", ""),
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
        val customView = createProgressView(context, item)
        notify(item.id, buildCustomNotification(context, customView, android.R.drawable.stat_sys_download, ongoing = true, item = item))
        loadPosterForNotification(context, item, customView)
    }

    private fun updateDownloadNotification(id: String) {
        val item = _activeDownloads.value[id] ?: return
        val context = appContext ?: return
        val customView = createProgressView(context, item)
        notify(id, buildCustomNotification(context, customView, android.R.drawable.stat_sys_download, ongoing = true, item = item))
        loadPosterForNotification(context, item, customView)
    }

    private fun showCompletedNotification(id: String) {
        val item = _activeDownloads.value[id] ?: return
        val context = appContext ?: return
        // Replace the ongoing progress notification with a completed, actionable one.
        cancelNotification(id, context)
        val customView = RemoteViews(context.packageName, R.layout.notification_download_complete).apply {
            setTextViewText(R.id.notif_complete_title, item.title)
        }
        notify(id, buildCustomNotification(context, customView, android.R.drawable.stat_sys_download_done, ongoing = false, autoCancel = true, item = item, completed = true))
        loadPosterForNotification(context, item, customView, completed = true)
    }

    private fun showFailedNotification(id: String, error: String) {
        val context = appContext ?: return
        val readableError = readableDownloadError(IOException(error))
        notify(id, NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Failed")
            .setContentText(readableError)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build())
    }

    private fun createProgressView(context: Context, item: DirectDownloadItem): RemoteViews =
        RemoteViews(context.packageName, R.layout.notification_download_progress).apply {
            setTextViewText(R.id.notif_app_name, "KINO")
            setTextViewText(
                R.id.notif_status,
                if (item.status == DirectDownloadStatus.PAUSED) "Download paused" else "Download in progress",
            )
            setTextViewText(R.id.notif_title, item.title)
            setTextViewText(R.id.notif_subtitle, buildString {
                append(qualityLabel(item.selectedHeight))
                if (item.apiName.isNotBlank()) append(" · ${item.apiName}")
                if (item.speed.isNotBlank()) append(" · ${item.speed}")
            })
            setProgressBar(R.id.notif_progress, 100, item.progress, item.totalBytes <= 0L)
            setTextViewText(R.id.notif_progress_text, "${formatFileSize(item.downloadedBytes)} / ${formatFileSize(item.totalBytes)} (${item.progress}%)")
            setTextViewText(R.id.notif_eta, item.eta.ifBlank { "Calculating..." })
        }

    private fun actionPendingIntent(context: Context, item: DirectDownloadItem, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            (item.id + action).hashCode(),
            Intent(context, DownloadActionReceiver::class.java).apply {
                putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, item.id)
                putExtra(DownloadActionReceiver.EXTRA_ACTION, action)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun libraryPendingIntent(context: Context, item: DirectDownloadItem, tab: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            (item.id + tab).hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "library")
                putExtra("library_tab", tab)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun playPendingIntent(context: Context, item: DirectDownloadItem): PendingIntent =
        PendingIntent.getActivity(
            context,
            (item.id + "play").hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "play_download")
                putExtra("file_path", item.filePath)
                putExtra("title", item.title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun qualityLabel(height: Int): String = when {
        height >= 2160 -> "4K"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        else -> "Auto"
    }

    private fun buildCustomNotification(
        context: Context,
        customView: RemoteViews,
        smallIcon: Int,
        ongoing: Boolean,
        autoCancel: Boolean = false,
        item: DirectDownloadItem? = null,
        completed: Boolean = false,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
            .setOnlyAlertOnce(true)
        item?.let {
            builder.setContentIntent(libraryPendingIntent(context, it, if (completed) "downloaded" else "downloading"))
            if (completed) {
                if (!it.filePath.isNullOrBlank()) {
                    builder.addAction(R.drawable.ic_notification_play, "Watch", playPendingIntent(context, it))
                }
            } else {
                val paused = it.status == DirectDownloadStatus.PAUSED
                builder.addAction(
                    if (paused) R.drawable.ic_notification_play else R.drawable.ic_notification_pause,
                    if (paused) "Resume" else "Pause",
                    actionPendingIntent(context, it, if (paused) DownloadActionReceiver.ACTION_RESUME else DownloadActionReceiver.ACTION_PAUSE),
                )
                builder.addAction(R.drawable.ic_notification_cancel, "Cancel", actionPendingIntent(context, it, DownloadActionReceiver.ACTION_CANCEL))
            }
        }
        return builder.build()
    }

    private fun loadPosterForNotification(
        context: Context,
        item: DirectDownloadItem,
        customView: RemoteViews,
        completed: Boolean = false,
    ) {
        val posterUrl = item.posterUrl ?: return
        posterCache[posterUrl]?.let { bitmap ->
            customView.setImageViewBitmap(if (completed) R.id.notif_complete_poster else R.id.notif_poster, bitmap)
            val current = _activeDownloads.value[item.id] ?: item
            notify(current.id, buildCustomNotification(context, customView,
                if (completed) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download,
                ongoing = !completed, autoCancel = completed, item = current, completed = completed))
            return
        }
        downloadScope.launch(Dispatchers.IO) {
            try {
                val connection = URL(posterUrl).openConnection().apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                }
                connection.getInputStream().use { input ->
                    BitmapFactory.decodeStream(input)?.let { bitmap ->
                        posterCache[posterUrl] = bitmap
                        val imageId = if (completed) R.id.notif_complete_poster else R.id.notif_poster
                        customView.setImageViewBitmap(imageId, bitmap)
                        val current = _activeDownloads.value[item.id] ?: item
                        notify(current.id, buildCustomNotification(context, customView,
                            if (completed) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download,
                            ongoing = !completed, autoCancel = completed, item = current, completed = completed))
                    }
                }
            } catch (_: Exception) {
                // A poster is optional; the notification remains usable without it.
            }
        }
    }
    private fun notify(id: String, notification: Notification) { appContext?.let { runCatching { NotificationManagerCompat.from(it).notify(id.hashCode(), notification) } } }
    private fun cancelNotification(id: String, context: Context) { runCatching { NotificationManagerCompat.from(context).cancel(id.hashCode()) } }
    private fun httpException(code: Int, message: String?): IOException = when (code) { 401 -> IOException("Authentication required (401)."); 403 -> IOException("Access denied (403)."); 404 -> IOException("File not found (404). The link may have expired."); in 500..599 -> IOException("Server error ($code). Try again later."); else -> IOException("HTTP $code: ${message ?: "Request failed"}") }
    private fun calculateEta(downloadedBytes: Long, totalBytes: Long, speedBps: Double): String {
        Log.e("ETA_DEBUG", "calculateEta: downloaded=$downloadedBytes total=$totalBytes speed=$speedBps")
        if (totalBytes <= 0L) {
            Log.e("ETA_DEBUG", "ETA unavailable: totalBytes is zero")
            return "Calculating..."
        }
        if (downloadedBytes >= totalBytes) {
            Log.e("ETA_DEBUG", "Download reached estimated total; returning almost done")
            return "Almost done..."
        }
        if (speedBps <= 0.0) {
            Log.e("ETA_DEBUG", "ETA unavailable: speed is zero")
            return "Calculating..."
        }
        val remainingSeconds = ((totalBytes - downloadedBytes) / speedBps).toLong().coerceAtLeast(1L)
        return when {
            remainingSeconds >= 3600L -> "${remainingSeconds / 3600L}h ${(remainingSeconds % 3600L) / 60L}m left"
            remainingSeconds >= 60L -> "${remainingSeconds / 60L}m left"
            else -> "${remainingSeconds}s left"
        }
    }
    private fun formatFileSize(bytes: Long): String = when { bytes >= 1_000_000_000L -> String.format("%.1f GB", bytes / 1_000_000_000.0); bytes >= 1_000_000L -> String.format("%.1f MB", bytes / 1_000_000.0); bytes >= 1_000L -> String.format("%.1f KB", bytes / 1_000.0); else -> "$bytes B" }
    private fun formatSpeed(bytesPerSecond: Double): String = when { bytesPerSecond >= 1_000_000 -> String.format("%.1f MB/s", bytesPerSecond / 1_000_000.0); bytesPerSecond >= 1_000 -> String.format("%.1f KB/s", bytesPerSecond / 1_000.0); else -> String.format("%.0f B/s", bytesPerSecond) }
    private fun sanitizeFileName(name: String): String = name
        .replace(Regex("[\\x00-\\x1F\\x7F/\\\\:*?\"<>|]"), "_")
        .replace(Regex("[^\\w\\s.-]"), "_")
        .replace(Regex("\\s+"), "_")
        .replace(Regex("_+"), "_")
        .trim(' ', '_', '.')
        .take(80)

    private enum class HlsSegmentType { TS, FMP4, HTML, UNKNOWN }

    private fun detectHlsSegmentType(bytes: ByteArray): HlsSegmentType {
        if (bytes.isEmpty()) return HlsSegmentType.UNKNOWN
        val header = String(bytes.take(256).toByteArray(), Charsets.ISO_8859_1)
        if (header.contains("<html", true) || header.contains("<!doctype", true)) return HlsSegmentType.HTML
        if (bytes.first() == 0x47.toByte() || bytes.getOrNull(188) == 0x47.toByte() || bytes.getOrNull(376) == 0x47.toByte()) return HlsSegmentType.TS
        if (header.contains("ftyp") || header.contains("styp") || header.contains("moof") || header.contains("mdat")) return HlsSegmentType.FMP4
        return HlsSegmentType.UNKNOWN
    }

    private fun requireWritableDirectory(directory: File?, step: String) {
        require(directory != null) { "$step: download parent directory is unavailable." }
        if (!directory.exists()) require(directory.mkdirs()) { "$step: could not create directory ${directory.absolutePath}." }
        require(directory.isDirectory && directory.canWrite()) { "$step: directory is not writable: ${directory.absolutePath}." }
        require(directory.usableSpace > 0L) { "$step: storage is full: ${directory.absolutePath}." }
    }

    private fun writeHlsFile(file: File, bytes: ByteArray, step: String) {
        requireWritableDirectory(file.parentFile, step)
        val part = File(file.parentFile, "${file.name}.part")
        try {
            part.outputStream().use { it.write(bytes) }
            require(part.length() == bytes.size.toLong()) { "$step: incomplete write: ${file.absolutePath}." }
            if (file.exists() && !file.delete()) throw IOException("Could not replace ${file.absolutePath}")
            require(part.renameTo(file)) { "Could not finalize ${file.absolutePath}." }
        } catch (error: Exception) {
            part.delete()
            Log.e(TAG, "$step path=${file.absolutePath} ${error::class.java.simpleName}: ${error.message}", error)
            throw IOException("Could not write download file (filesystem error).", error)
        }
    }

    private fun writeHlsText(file: File, content: String) = writeHlsFile(file, content.toByteArray(Charsets.UTF_8), "HLS_STEP=finalize")

    private fun readableDownloadError(error: Throwable): String {
        val message = error.message.orEmpty().trim()
        val pathOnly = message.startsWith("/storage/") || message.startsWith("/data/") ||
            message == error.cause?.message
        return when {
            error is java.io.FileNotFoundException || pathOnly -> "Storage error while saving download. Check free space and try again."
            message.isBlank() -> "Download failed due to an unknown filesystem or network error."
            else -> message
        }
    }
}
