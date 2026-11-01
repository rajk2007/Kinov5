package com.lagradost.cloudstream3.utils.downloader

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Enforces the video-first completion contract for HLS downloads.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * THE BUG THIS KILLS
 * ─────────────────────────────────────────────────────────────────────────
 * KDesa streams are demuxed HLS: a video playlist plus a separate audio
 * playlist referenced from the master via `#EXT-X-MEDIA:TYPE=AUDIO`. The
 * downloader ran the audio phase inside `withTimeout { ... }`. When the
 * audio phase blew past the timeout, Kotlin threw `TimeoutCancellationException`
 * — which is a `CancellationException`. The surrounding `try { ... } catch (e: Exception)`
 * caught it and rethrew, which cancelled the parent download job BEFORE
 * `markCompleted(...)` ran. Result: video was on disk, but the DB row stayed
 * RUNNING forever → "stuck at ~100% / Almost done".
 *
 * ─────────────────────────────────────────────────────────────────────────
 * THE CONTRACT
 * ─────────────────────────────────────────────────────────────────────────
 *   1. Video phase failure  → caller marks FAILED and returns. (Video is
 *      required; without it there is nothing to play.)
 *   2. Video phase success  → caller ALWAYS marks COMPLETED, choosing
 *      `master.m3u8` if audio succeeded or `video.m3u8` if it did not.
 *   3. Audio phase          → wrapped in [bestEffort]. A timeout or exception
 *      is logged under `HLS_AUDIO` and converted to `false`. It NEVER
 *      propagates to the caller.
 *   4. Subtitle phase       → same treatment, logged under `HLS_SUBS`.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * WHY withTimeoutOrNull (not withTimeout)
 * ─────────────────────────────────────────────────────────────────────────
 * `withTimeout` THROWS `TimeoutCancellationException` on expiry. `withTimeoutOrNull`
 * RETURNS `null` on expiry. Returning null lets us treat "timed out" and
 * "threw" identically — both become `false` from [bestEffort] — without ever
 * letting a `CancellationException` escape into the parent scope.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * WHY we still rethrow CancellationException inside the block
 * ─────────────────────────────────────────────────────────────────────────
 * If we swallowed it, `withTimeoutOrNull` could not deliver the timeout
 * (the timeout is implemented by throwing `CancellationException` at the next
 * suspension point). So: catch `CancellationException` → rethrow; catch
 * everything else → log + return false. This is the only correct shape.
 */
object HlsCompletionGuard {

    /**
     * Run [block] as best-effort: a timeout or any non-cancel exception is
     * converted to `false` and logged under [tag]. Returns `true` only when
     * [block] completes normally.
     *
     * Use this for the audio and subtitle phases. NEVER use it for the video
     * phase — video failure must abort the download.
     */
    suspend fun bestEffort(
        tag: String,
        timeoutMs: Long,
        block: suspend () -> Unit,
    ): Boolean {
        val outcome: Boolean? = withTimeoutOrNull(timeoutMs) {
            try {
                block()
                true
            } catch (ce: CancellationException) {
                // This is either the timeout firing or a parent cancellation.
                // Rethrow so withTimeoutOrNull can convert it to null (timeout)
                // or let it propagate (genuine parent cancel — we WANT that).
                throw ce
            } catch (e: Exception) {
                Log.i(tag, "best-effort failed: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }
        return when (outcome) {
            null -> {
                Log.i(tag, "best-effort timed out after ${timeoutMs}ms — completing without this phase")
                false
            }
            true -> true
            false -> false
        }
    }

    /**
     * Convenience wrapper for a single network call that should not kill the
     * phase on its own failure. Returns the result or `null` on any error.
     *
     * Use this inside the audio/subs phase for individual segment fetches so
     * one bad segment does not abort the whole rendition.
     */
    suspend fun <T> safeCall(tag: String, label: String, block: suspend () -> T): T? {
        return try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.i(tag, "safeCall($label) failed: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
}
