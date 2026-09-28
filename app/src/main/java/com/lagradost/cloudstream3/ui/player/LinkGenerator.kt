package com.lagradost.cloudstream3.ui.player

import android.net.Uri
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.actions.temp.CloudStreamPackage
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.unshortenLinkSafe

data class ExtractorUri(
    val uri: Uri,
    val name: String,

    val basePath: String? = null,
    val relativePath: String? = null,
    val displayName: String? = null,

    val id: Int? = null,
    val parentId: Int? = null,
    val episode: Int? = null,
    val season: Int? = null,
    val headerName: String? = null,
    val tvType: TvType? = null,
)

/**
 * Used to open the player more easily with the LinkGenerator
 **/
data class BasicLink(
    val url: String,
    val name: String? = null,
)

class LinkGenerator(
    private val links: List<BasicLink>,
    private val extract: Boolean = true,
    private val refererUrl: String? = null,
    id: Int?
) : NoVideoGenerator(id) {
    override val canSkipLoading = true

    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean {
        links.amap { link ->
            val uri = link.url.toUriOrNull()
            if (uri?.scheme == "file" || uri?.scheme == "content") {
                // Route local media to CS3IPlayer.loadOfflinePlayer(). An
                // ExtractorLink would send file:// through the network player.
                callback(
                    null to ExtractorUri(
                        uri = uri,
                        name = link.name ?: uri.lastPathSegment ?: "Downloaded Video",
                    )
                )
                return@amap
            }

            if (!extract || !loadExtractor(link.url, refererUrl, {
                    subtitleCallback(PlayerSubtitleHelper.getSubtitleData(it))
                }) {
                    callback(it to null)
                }) {

                // if don't extract or if no extractor found simply return the link
                callback(
                    newExtractorLink(
                        "",
                        link.name ?: link.url,
                        unshortenLinkSafe(link.url), // unshorten because it might be a raw link
                        type = INFER_TYPE,
                    ) {
                        this.referer = refererUrl ?: ""
                        this.quality = Qualities.Unknown.value
                    } to null
                )
            }
        }

        return true
    }

    private fun String.toUriOrNull(): Uri? = runCatching { Uri.parse(this) }.getOrNull()
}

class MinimalLinkGenerator(
    private val links: List<CloudStreamPackage.MinimalVideoLink>,
    private val subs: List<CloudStreamPackage.MinimalSubtitleLink>,
    id: Int?
) : NoVideoGenerator(id) {
    override suspend fun generateLinks(
        clearCache: Boolean,
        sourceTypes: Set<ExtractorLinkType>,
        callback: (Pair<ExtractorLink?, ExtractorUri?>) -> Unit,
        subtitleCallback: (SubtitleData) -> Unit,
        offset: Int,
        isCasting: Boolean
    ): Boolean {
        for (link in links) {
            callback(link.toExtractorLink())
        }
        for (link in subs) {
            subtitleCallback(link.toSubtitleData())
        }

        return true
    }
}
