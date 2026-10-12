package com.lagradost.cloudstream3.utils.downloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles actions emitted by direct-download notifications. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        DirectDownloadManager.initialize(context)
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_PAUSE -> DirectDownloadManager.pauseDownload(downloadId)
            ACTION_RESUME -> DirectDownloadManager.resumeDownload(downloadId)
            ACTION_CANCEL -> DirectDownloadManager.cancelDownload(downloadId)
        }
    }

    companion object {
        const val EXTRA_DOWNLOAD_ID = "download_id"
        const val EXTRA_ACTION = "action"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val ACTION_CANCEL = "CANCEL"
    }
}
