package com.lagradost.cloudstream3.ui.library

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.player.BasicLink
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LinkGenerator
import com.lagradost.cloudstream3.ui.result.ResultFragment
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadManager
import com.lagradost.cloudstream3.utils.downloader.DirectDownloadStatus
import java.io.File

class KinoLibraryFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                KinoLibraryScreen(
                    onMediaClick = { media ->
                        val isDownload = media.downloadStatus != null ||
                            media.localUri != null ||
                            media.downloadedBytes > 0L ||
                            media.totalBytes > 0L
                        if (isDownload) {
                            // A download must never fall through to ResultFragment: that
                            // fragment receives media.url and tries to stream the remote URL.
                            playDownloadLocally(media)
                        } else {
                            val bundle = ResultFragment.newInstance(
                                url = media.url,
                                apiName = media.apiName,
                                name = media.name,
                                startAction = 2,
                                startValue = media.episodeId ?: 0,
                            )
                            findNavController().navigate(R.id.navigation_results_phone, bundle)
                        }
                    }
                )
            }
        }
    }

    private fun playDownloadLocally(media: KinoLibraryItem) {
        val fallback = DirectDownloadManager.activeDownloads.value.values.firstOrNull {
            it.title == media.name && it.status == DirectDownloadStatus.COMPLETED && it.filePath != null
        }?.filePath
        val rawPath = media.localUri ?: fallback
        val uri = rawPath?.let { raw ->
            val parsed = Uri.parse(raw)
            if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(raw)) else parsed
        }

        if (uri == null || !isUsableLocalUri(uri)) {
            Toast.makeText(
                requireContext(),
                "Downloaded file not found or incomplete. Please re-download.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Use a non-extracting generator so the file URI is handed to the player
        // exactly as-is; the original remote URL is intentionally never used here.
        val linkGenerator = LinkGenerator(
            listOf(BasicLink(uri.toString(), media.name)),
            extract = false,
            id = media.id ?: media.episodeId ?: media.name.hashCode(),
        )
        findNavController().navigate(
            R.id.global_to_navigation_player,
            GeneratorPlayer.newInstance(linkGenerator, 0)
        )
    }

    private fun isUsableLocalUri(uri: Uri): Boolean {
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            val file = File(path)
            return file.isFile && file.length() > 1024L
        }
        // Persisted SAF downloads may be content:// URIs. They are local by
        // construction; LinkGenerator still bypasses extraction for them.
        return uri.scheme == "content"
    }
}
