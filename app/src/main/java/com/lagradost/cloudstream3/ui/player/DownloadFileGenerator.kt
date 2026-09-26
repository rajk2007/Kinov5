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
        val storedFile = meta.id?.let { id ->
            activity?.let { act -> getDownloadFileInfo(act, id)?.path?.toString() }
        }?.let { java.io.File(it) }

        val directFile = DirectDownloadManager.activeDownloads.value.values
            .asSequence()
            .filter { it.status == DirectDownloadStatus.COMPLETED && it.title == meta.name }
            .mapNotNull { it.filePath?.let { path -> java.io.File(path) } }
            .firstOrNull()

        val existingFile = sequenceOf(storedFile, directFile)
            .filterNotNull()
            .firstOrNull { it.isFile && it.length() > 0L }

        if (existingFile != null) {
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
        val relative = meta.relativePath ?: return true
        val display = meta.displayName ?: return true

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

        return true
    }
}
