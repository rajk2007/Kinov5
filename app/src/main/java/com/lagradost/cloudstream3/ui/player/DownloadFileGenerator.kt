package com.lagradost.cloudstream3.ui.player

import android.net.Uri
import android.util.Log
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTagIETF
import com.lagradost.cloudstream3.utils.SubtitleUtils.cleanDisplayName
import com.lagradost.cloudstream3.utils.SubtitleUtils.isMatchingSubtitle
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getFolder
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadItem
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadManager
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadStatus
import com.lagradost.cloudstream3.utils.downloader.VideoDownloadManager.getDownloadFileInfo

class DownloadFileGenerator(
    episodes: List<ExtractorUri>
) : VideoGenerator<ExtractorUri>(episodes) {
    override val hasCache = false
    override val canSkipLoading = false

    override fun getId(index: Int): Int? = this.videos.getOrNull(index)?.id

    private fun resolveLocalUri(meta: ExtractorUri): Uri? {
        fun normalized(value: String): String = value
            .replace(Regex("[^\\w\\s.-]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim(' ', '_', '.')
            .lowercase()

        val storedFile = meta.id?.let { id ->
            activity?.let { act -> getDownloadFileInfo(act, id)?.path?.toString() }
        }?.let { java.io.File(it) }

        val expectedBases = listOfNotNull(meta.name, meta.displayName)
            .map(::normalized)
            .filter { it.isNotBlank() }
            .toSet()
        val directCandidates = DirectDownloadManager.activeDownloads.value.values
            .asSequence()
            .filter { it.status == DirectDownloadStatus.COMPLETED }
            .mapNotNull { item -> item.filePath?.let { path -> item to java.io.File(path) } }
            .filter { (item, file) ->
                val pathBase = normalized(file.name.substringBeforeLast('.'))
                    .removeSuffix(".master")
                    .removeSuffix(".audio")
                val titleMatch = item.title == meta.name || normalized(item.title) in expectedBases
                val pathMatch = pathBase in expectedBases || expectedBases.any { pathBase.startsWith("${it}_") }
                titleMatch || pathMatch
            }
            .sortedWith(
                compareByDescending<Pair<DirectDownloadItem, java.io.File>> { (_, file) -> file.name.endsWith(".master.m3u8", true) }
                    .thenByDescending { (_, file) -> file.name.endsWith(".m3u8", true) }
            )
            .firstOrNull()
        val directFile = directCandidates?.second
        directCandidates?.let { (item, file) ->
            Log.e("PLAY_DEBUG", "resolve matched path=${file.absolutePath} titleMatch=${item.title == meta.name}")
        }

        val existingFile = sequenceOf(storedFile, directFile)
            .filterNotNull()
            .filter { it.isFile && it.length() > 0L }
            .sortedWith(
                compareByDescending<java.io.File> { it.name.endsWith(".master.m3u8", true) }
                    .thenByDescending { it.name.endsWith(".m3u8", true) }
            )
            .firstOrNull()

        if (existingFile != null) {
            Log.e("PLAY_DEBUG", "resolve matched path=${existingFile.absolutePath} titleMatch=${existingFile.name.substringBeforeLast('.').removeSuffix(".master").removeSuffix(".audio").let(::normalized) in expectedBases}")
            Log.e("PLAY_DEBUG", "Loading downloaded file:")
            Log.e("PLAY_DEBUG", "  File path: ${existingFile.absolutePath}")
            Log.e("PLAY_DEBUG", "  File exists: ${existingFile.exists()}")
            Log.e("PLAY_DEBUG", "  File size: ${existingFile.length()}")
            Log.e("PLAY_DEBUG", "  File extension: ${existingFile.extension}")
            runCatching {
                val header = ByteArray(20)
                existingFile.inputStream().use { it.read(header) }
                Log.e("PLAY_DEBUG", "  File header: ${String(header, Charsets.ISO_8859_1)}")
            }.onFailure { Log.e("PLAY_DEBUG", "  Could not read file header", it) }
            Log.d("DownloadFileGenerator", "Using local download: ${existingFile.absolutePath}")
            return Uri.fromFile(existingFile)
        }

        val existingFileUri = meta.uri.takeIf { it.scheme == "file" }
        return existingFileUri?.takeIf { uri ->
            uri.path?.let { java.io.File(it) }?.let { it.isFile && it.length() > 0L } == true
        }
    }

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean {
        val meta = videos.getOrNull(offset) ?: return false
        val localUri = resolveLocalUri(meta)
        if (localUri == null) {
            Log.w("DownloadFileGenerator", "No valid local file found for: ${meta.name}; aborting playback")
            return false
        }
        callback(null to meta.copy(uri = localUri))

        val ctx = context ?: return true
        val localFile = localUri.path?.let { java.io.File(it) }
        localFile?.parentFile?.let { folder ->
            val directSubtitles = folder.listFiles()
                ?.filter { file ->
                    val name = file.name
                    val lower = name.lowercase()
                    lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ass")
                }
                .orEmpty()
            Log.e("SUBS", "offline scan found=${directSubtitles.size} folder=${folder.absolutePath}")
            directSubtitles.forEach { file ->
                Log.e("SUBS", "Registered sidecar ${file.absolutePath}")
                subtitleCallback(
                    SubtitleData(
                        "English",
                        "",
                        Uri.fromFile(file).toString(),
                        SubtitleOrigin.DOWNLOADED_FILE,
                        file.name.toSubtitleMimeType(),
                        emptyMap(),
                        "en",
                    )
                )
            }
        }
        val relative = meta.relativePath
        val display = meta.displayName
        if (relative != null && display != null) {
            val cleanDisplay = cleanDisplayName(display)
            getFolder(ctx, relative, meta.basePath)?.forEach { (name, uri) ->
                if (isMatchingSubtitle(name, display, cleanDisplay)) {
                    val cleanName = cleanDisplayName(name)
                    val lastNum = Regex(" ([0-9]+)$")
                    val nameSuffix = lastNum.find(cleanName)?.groupValues?.get(1) ?: ""
                    val originalName = cleanName.removePrefix(cleanDisplay).replace(lastNum, "").trim()
                    subtitleCallback(
                        SubtitleData(
                            originalName.ifBlank { ctx.getString(R.string.default_subtitles) },
                            nameSuffix,
                            uri.toString(),
                            SubtitleOrigin.DOWNLOADED_FILE,
                            name.toSubtitleMimeType(),
                            emptyMap(),
                            fromLanguageToTagIETF(originalName, true)
                        )
                    )
                }
            }
        }

        return true
    }
}
