package com.lagradost.cloudstream3.ui.result

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
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLinkSelected(link) }
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
    link: ExtractorLink,
    loadResponse: LoadResponse,
    dataString: String,
    resultId: Int,
    apiName: String,
    isMovie: Boolean,
) {
    val resultType = loadResponse.type
    val episode = ResultEpisode(
        headerName = loadResponse.name,
        name = loadResponse.name,
        poster = loadResponse.posterUrl,
        episode = 0,
        seasonIndex = null,
        season = null,
        data = dataString,
        apiName = apiName,
        id = resultId,
        index = 0,
        position = 0L,
        duration = 0L,
        score = loadResponse.score,
        description = loadResponse.plot,
        isFiller = null,
        tvType = resultType,
        parentId = resultId,
        videoWatchState = VideoWatchState.None,
    )
    val downloadItem = DownloadObjects.DownloadQueueItem(
        episode = episode,
        isMovie = isMovie,
        resultName = loadResponse.name,
        resultType = resultType ?: TvType.Movie,
        resultPoster = loadResponse.posterUrl,
        apiName = apiName,
        resultId = resultId,
        resultUrl = loadResponse.url,
        links = listOf(link),
    )
    DownloadQueueManager.addToQueue(downloadItem.toWrapper())
}
