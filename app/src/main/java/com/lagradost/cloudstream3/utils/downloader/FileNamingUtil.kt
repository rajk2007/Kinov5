package com.lagradost.cloudstream3.utils.downloader

import java.text.Normalizer

/**
 * Produces file-system-safe base names from video titles.
 *
 * WHY THIS IS HERE
 * ─────────────────
 * KDesa titles frequently contain characters that emulated FAT/exFAT storage
 * (which Android still uses under /Android/data on some devices) refuses:
 *   ":"  → "Tonari no Seki-kun: The Master of Killing Time" — illegal on FAT
 *   "/" "\" "*" "?" "\"" "<" ">" "|" → reserved across Windows-derived FS
 *   trailing space or "." → silently stripped by Android, breaking lookup
 *
 * If the downloader writes `Tonari no Seki-kun:.m3u8` and the resolver looks
 * for `Tonari no Seki-kun.m3u8`, the play call fails and the failure surfaces
 * as a confusing container error. So both the downloader and the resolver
 * MUST go through the same normaliser.
 *
 * CJK characters (Japanese, Chinese, Korean) are KEPT. They are valid on the
 * ext4/f2fs filesystems Android uses for app-specific storage; stripping them
 * would mangle titles like "となりの関くん" beyond recognition.
 */
object FileNamingUtil {

    /** Characters that cause failures on at least one Android storage backend. */
    private val ILLEGAL = charArrayOf(':', '/', '\\', '*', '?', '"', '<', '>', '|')

    /**
     * Returns a base name (no extension, no path) safe for use as a filename
     * stem on every Android storage backend. Length-capped at 80 chars so the
     * full filename (stem + ".master.m3u8" etc.) stays well under the 255-byte
     * limit even after UTF-8 encoding.
     */
    fun sanitize(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFC)
        .replace(Regex("[\\x00-\\x1F\\x7F/\\\\:*?\"<>|]"), "_")
        .replace(Regex("[^\\w\\s.-]"), "_")
        .replace(Regex("\\s+"), "_")
        .replace(Regex("_+"), "_")
        .trim(' ', '_', '.')
        .take(80)
        .ifBlank { "untitled" }
}
