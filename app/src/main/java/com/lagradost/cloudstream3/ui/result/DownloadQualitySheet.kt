package com.lagradost.cloudstream3.ui.result

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.ExtractorLink

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualitySheet(
    links: List<ExtractorLink>,
    onLinkSelected: (ExtractorLink) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Choose download quality", modifier = Modifier.padding(20.dp))
            links.forEach { link ->
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onLinkSelected(link) }
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(link.name)
                    Text(link.source, modifier = Modifier.padding(top = 4.dp))
                }
                HorizontalDivider()
            }
            if (links.isEmpty()) {
                Text("No download links found.", modifier = Modifier.padding(20.dp))
                Button(onClick = onDismiss, modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text("Close")
                }
            }
        }
    }
}

fun startDownload(
    context: Context,
    link: ExtractorLink,
    name: String,
    apiName: String,
    url: String,
    type: com.lagradost.cloudstream3.TvType?,
    posterUrl: String?,
) {
    val episode = ResultEpisode(
        headerName = name,
        name = name,
        poster = posterUrl,
        episode = 1,
        seasonIndex = null,
        season = null,
        data = url,
        apiName = apiName,
        id = url.hashCode(),
        index = 0,
        position = 0L,
        duration = 0L,
        score = null,
        description = null,
        isFiller = null,
        tvType = type ?: com.lagradost.cloudstream3.TvType.Movie,
        parentId = url.hashCode(),
        videoWatchState = VideoWatchState.None,
    )
    com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager.addToQueue(
        com.lagradost.cloudstream3.utils.downloader.DownloadObjects.DownloadQueueItem(
            episode = episode,
            isMovie = episode.tvType == com.lagradost.cloudstream3.TvType.Movie,
            resultName = name,
            resultType = episode.tvType,
            resultPoster = posterUrl,
            apiName = apiName,
            resultId = url.hashCode(),
            resultUrl = url,
            links = listOf(link),
        ).toWrapper()
    )
}
