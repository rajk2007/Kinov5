package com.lagradost.cloudstream3.ui.result

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.utils.downloader.DownloadObjects
import com.lagradost.cloudstream3.utils.downloader.DownloadQueueManager

@Composable
fun DownloadQualitySheet(
    links: List<ExtractorLink>,
    onLinkSelected: (ExtractorLink) -> Unit,
    onDismiss: () -> Unit,
) {
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
                Text(
                    "${link.source} · ${link.quality}",
                    modifier = Modifier.padding(top = 4.dp),
                )
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

fun startDownload(
    link: ExtractorLink,
    name: String,
    apiName: String,
    pageUrl: String,
    dataString: String,
    resultId: Int,
    type: TvType?,
    posterUrl: String?,
) {
    val tvType = type ?: TvType.Movie
    val episode = ResultEpisode(
        headerName = name,
        name = name,
        poster = posterUrl,
        episode = 1,
        seasonIndex = null,
        season = null,
        data = dataString,
        apiName = apiName,
        id = resultId,
        index = 0,
        position = 0L,
        duration = 0L,
        score = null,
        description = null,
        isFiller = null,
        tvType = tvType,
        parentId = resultId,
        videoWatchState = VideoWatchState.None,
    )

    DownloadQueueManager.addToQueue(
        DownloadObjects.DownloadQueueItem(
            episode = episode,
            isMovie = tvType == TvType.Movie,
            resultName = name,
            resultType = tvType,
            resultPoster = posterUrl,
            apiName = apiName,
            resultId = resultId,
            resultUrl = pageUrl,
            links = listOf(link),
        ).toWrapper()
    )
    android.util.Log.d(
        "KinoDL",
        "queued id=$resultId ctx=${com.lagradost.cloudstream3.CloudStreamApp.context != null} " +
            "queue=${DownloadQueueManager.queue.value.size}"
    )
}
