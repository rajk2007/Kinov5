package com.lagradost.cloudstream3.ui.player

// V38: ExtractorUri gains an optional explicit file path. When set, playback uses
// exactly this file instead of re-resolving by title (which matched the wrong
// quality when several qualities of one title were downloaded).

import android.net.Uri
import android.util.Log
import java.io.File
import com.lagradost.cloudstream3.CloudStreamApp.Companion.context
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.PlayerSubtitleHelper.Companion.toSubtitleMimeType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromTagToEnglishLanguageName
import com.lagradost.cloudstream3.utils.SubtitleHelper.fromLanguageToTagIETF
import com.lagradost.cloudstream3.utils.SubtitleUtils.cleanDisplayName
import com.lagradost.cloudstream3.utils.SubtitleUtils.isMatchingSubtitle
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getFolder
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadItem
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadManager
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadStatus
import com.lagradost.cloudstream3.utils.downloader.DownloadLog
import com.lagradost.cloudstream3.utils.downloader.FileNamingUtil
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

        // V38: an explicit path from the tapped row always wins. The title-based
        // search below cannot distinguish 480p from 1080p of the same title.
        val explicitFile = (meta as? ExtractorUri)?.filePath
            ?.let { java.io.File(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
        if (explicitFile != null) {
            DownloadLog.play("resolve: explicit tapped path=${explicitFile.absolutePath}")
            return Uri.fromFile(explicitFile)
        }

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

        val requestedBase = FileNamingUtil.sanitize(meta.name)
        val candidateDir = listOfNotNull(storedFile?.parentFile, directFile?.parentFile).firstOrNull()
        val masterCandidate = candidateDir?.let { File(it, "$requestedBase.master.m3u8") }
        val videoCandidate = candidateDir?.let { File(it, "$requestedBase.m3u8") }
        val existingFile = sequenceOf(masterCandidate, videoCandidate, storedFile, directFile)
            .filterNotNull()
            .filter { it.isFile && it.length() > 0L }
            .sortedWith(
                compareByDescending<java.io.File> { it.name.endsWith(".master.m3u8", true) }
                    .thenByDescending { it.name.endsWith(".m3u8", true) }
            )
            .firstOrNull()

        DownloadLog.play(
            "resolve: requested='${meta.name}' baseName='$requestedBase' " +
                "dir=${candidateDir?.absolutePath ?: "NONE"} " +
                "video_exists=${videoCandidate?.isFile == true} video_size=${videoCandidate?.takeIf { it.isFile }?.length() ?: -1} " +
                "master_exists=${masterCandidate?.isFile == true} master_size=${masterCandidate?.takeIf { it.isFile }?.length() ?: -1} " +
                "opening='${existingFile?.name ?: "NONE"}'"
        )
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
            Log.e("PLAY_DEBUG", "final path opened=${existingFile.absolutePath}")
            return Uri.fromFile(existingFile)
        }

        val existingFileUri = meta.uri.takeIf { it.scheme == "file" }
        return existingFileUri?.takeIf { uri ->
            uri.path?.let { java.io.File(it) }?.let { it.isFile && it.length() > 0L } == true
        }
    }

    private fun extractLangFromSubtitleName(fileName: String, videoBase: String): Pair<String?, String> {
        val subtitleBase = fileName.substringBeforeLast('.')
        val suffix = subtitleBase.removePrefix(videoBase).trim('.', '_', '-')
        val languageCode = suffix.split(Regex("[._-]+"))
            .asSequence()
            .mapNotNull { token -> fromLanguageToTagIETF(token) }
            .firstOrNull()
        val originalName = languageCode?.let { fromTagToEnglishLanguageName(it) }
            ?: subtitleBase
        Log.i("SUBS", "derived language file=$fileName suffix=$suffix language=$languageCode original=$originalName")
        return languageCode to originalName
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
        val localFile = localUri.path?.let { java.io.File(it) }
        val registeredSubtitlePaths = mutableSetOf<String>()
        localFile?.parentFile?.let { folder ->
            val videoBase = localFile.name.substringBeforeLast('.')
                .removeSuffix(".master").removeSuffix(".audio")
            val directSubtitles = folder.listFiles()
                ?.filter { file ->
                    val lower = file.name.lowercase()
                    val subtitleBase = file.name.substringBeforeLast('.')
                    val suffix = subtitleBase.drop(videoBase.length)
                    val hasVideoBase = subtitleBase.equals(videoBase, ignoreCase = true) ||
                        (subtitleBase.startsWith(videoBase, ignoreCase = true) &&
                            suffix.firstOrNull()?.let { it == '.' || it == '-' || it == '_' || it.isWhitespace() } == true)
                    (lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ass")) &&
                        hasVideoBase
                }
                .orEmpty()
            Log.e("SUBS", "offline scan found=${directSubtitles.size} folder=${folder.absolutePath} videoBase=$videoBase")
            directSubtitles.forEach { file ->
                val (languageCode, originalName) = extractLangFromSubtitleName(file.name, videoBase)
                Log.e("SUBS", "Registered sidecar ${file.absolutePath}")
                registeredSubtitlePaths += file.absolutePath
                subtitleCallback(
                    SubtitleData(
                        originalName,
                        "",
                        Uri.fromFile(file).toString(),
                        SubtitleOrigin.DOWNLOADED_FILE,
                        file.name.toSubtitleMimeType(),
                        emptyMap(),
                        languageCode
                    )
                )
            }
        }
        val relative = meta.relativePath
        val display = meta.displayName
        val ctx = context
        if (ctx != null && relative != null && display != null) {
            val cleanDisplay = cleanDisplayName(display)
            getFolder(ctx, relative, meta.basePath)?.forEach { (name, uri) ->
                val alreadyRegistered = runCatching { Uri.parse(uri.toString()).path }
                    .getOrNull()?.let { it in registeredSubtitlePaths } == true
                if (isMatchingSubtitle(name, display, cleanDisplay) && !alreadyRegistered) {
                    val cleanName = cleanDisplayName(name)
                    val lastNum = Regex(" ([0-9]+)$")
                    val nameSuffix = lastNum.find(cleanName)?.groupValues?.get(1) ?: ""
                    val originalName = cleanName.removePrefix(cleanDisplay).replace(lastNum, "").trim()
                    val (derivedLanguage, derivedOriginalName) = extractLangFromSubtitleName(name, cleanDisplay)
                    val languageCode = derivedLanguage ?: fromLanguageToTagIETF(originalName, true)
                    subtitleCallback(
                        SubtitleData(
                            derivedOriginalName.ifBlank { ctx.getString(R.string.default_subtitles) },
                            nameSuffix,
                            uri.toString(),
                            SubtitleOrigin.DOWNLOADED_FILE,
                            name.toSubtitleMimeType(),
                            emptyMap(),
                            languageCode
                        )
                    )
                    Log.i("SUBS", "Registered downloaded subtitle name=$name language=$languageCode")
                }
            }
        }

        Log.i("SUBS", "offline subtitles registered=${registeredSubtitlePaths.size}; emitting video callback")
        DownloadLog.play("hand-off: opening=${localUri.path ?: localUri} player=GeneratorPlayer")
        callback(null to meta.copy(uri = localUri))

        return true
    }
}
