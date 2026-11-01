package com.lagradost.cloudstream3.utils.downloader

import android.util.Log

/**
 * Single source of truth for download log tags.
 *
 * WHY: the Grok diagnostic recipe greps logcat for exact tag strings. If those
 * strings are scattered as inline literals across `DirectDownloadManager`,
 * `DownloadFileGenerator`, `CS3IPlayer`, etc., they drift between builds and
 * the recipe stops matching. Centralising them here keeps the contract stable.
 *
 * HOW TO USE:
 *   DownloadLog.phase("video-done: $count segments, ${bytes}B -> $path")
 *   DownloadLog.audio("master parse: renditions=$n typeAudio=$hasAudio")
 *   DownloadLog.play("resolve: opening=$name size=$size exists=$exists")
 *
 * The five tags below are the ONLY ones the diagnostic decision tree keys on.
 * Do not rename without updating DIAGNOSTIC.md.
 */
object DownloadLog {

    /** Lifecycle of a download: video-start, video-done, audio-start, audio-done, subs, completed, failed. */
    const val HLS_PHASE = "HLS_PHASE"

    /** Audio branch: master playlist parse, segment fetches, failures, timeouts. */
    const val HLS_AUDIO = "HLS_AUDIO"

    /** Subtitle branch: provider tracks, HLS TYPE=SUBTITLES, .srt writes. */
    const val HLS_SUBS = "HLS_SUBS"

    /** Offline player file resolution: what was requested vs what was opened. */
    const val PLAY_DEBUG = "PLAY_DEBUG"

    /** General download chatter that does not belong to a phase above. */
    const val DL_GENERAL = "KINO_DL"

    fun phase(msg: String) = Log.i(HLS_PHASE, msg)
    fun audio(msg: String) = Log.i(HLS_AUDIO, msg)
    fun subs(msg: String) = Log.i(HLS_SUBS, msg)
    fun play(msg: String) = Log.i(PLAY_DEBUG, msg)
    fun general(msg: String) = Log.i(DL_GENERAL, msg)

    /** For stack traces on best-effort failures — kept off the hot path. */
    fun audio(throwable: Throwable) =
        Log.w(HLS_AUDIO, "${throwable.javaClass.simpleName}: ${throwable.message}", throwable)
}
