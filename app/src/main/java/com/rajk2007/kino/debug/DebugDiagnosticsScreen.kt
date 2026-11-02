package com.rajk2007.kino.debug

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app diagnostic screen — replaces `adb logcat` + `adb shell cat` for
 * phone-only development.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * WHY THIS EXISTS
 * ─────────────────────────────────────────────────────────────────────────
 * On Android 13+ you cannot read another app's `Android/data` folder from a
 * file manager, and `logcat` is restricted to your own process without
 * `READ_LOGS` (which needs ADB or root to grant). So a developer with only a
 * phone has no way to inspect what the downloader wrote or what it logged.
 *
 * This screen solves it by running inside the app itself. An app can ALWAYS:
 *   • read its own files (its `getExternalFilesDir` is fully accessible to it)
 *   • read its own logcat (`Runtime.exec("logcat")` shows the calling app's
 *     logs without any permission, on every Android version since 4.1)
 *
 * ─────────────────────────────────────────────────────────────────────────
 * WHAT IT SHOWS
 * ─────────────────────────────────────────────────────────────────────────
 * 1. Every file in the most recent download's folder (name + size) — so you
 *    can see whether segments were actually saved or only the playlist.
 * 2. The content of the most recent `.master.m3u8` (or any `.m3u8` if no
 *    master exists) — the critical check: do the URI lines say `https://...`
 *    (broken offline) or local filenames (correct)?
 * 3. The sibling `.audio.m3u8` if one exists — same URI check for the audio
 *    playlist's segment lines.
 * 4. The app's own recent logcat, filtered to the download/player tags.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * HOW TO USE
 * ─────────────────────────────────────────────────────────────────────────
 * 1. Wire this Composable into your navigation (see file bottom for a
 *    minimal integration snippet).
 * 2. Do a download (or play one that already exists).
 * 3. Open this screen, tap Refresh.
 * 4. Tap Share → send the text dump to yourself (Telegram, email, notes).
 *
 * The Share dump contains everything needed to diagnose the download in one
 * text blob — no adb, no laptop, no root.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDiagnosticsScreen(
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Tap Refresh to load.") }
    var fileList by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlistPath by remember { mutableStateOf("(none)") }
    var playlistContent by remember { mutableStateOf("(no file loaded)") }
    var audioPath by remember { mutableStateOf("(none)") }
    var audioContent by remember { mutableStateOf("") }
    var logcatText by remember { mutableStateOf("(tap Refresh)") }
    var dump by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            status = "Reading…"
            withContext(Dispatchers.IO) {
                // ── Logcat: read our own process logs, filter to relevant tags ──
                logcatText = runCatching {
                    val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
                    proc.inputStream.bufferedReader().use { reader ->
                        reader.readText()
                            .lineSequence()
                            .filter { line -> FILTER_TAGS.any { line.contains("/$it") || line.contains(" $it:") } }
                            .toList()
                            .takeLast(800)
                            .joinToString("\n")
                    }
                }.getOrElse { "logcat read failed: ${it.javaClass.simpleName}: ${it.message}" }

                // ── Files: find the most recent .m3u8 in Movies/ ──
                val moviesDir = File(context.getExternalFilesDir(null), "Movies")
                if (!moviesDir.exists()) {
                    fileList = listOf("Movies dir does not exist: ${moviesDir.absolutePath}")
                    playlistPath = "(no Movies dir)"
                    playlistContent = "Path checked: ${moviesDir.absolutePath}\n" +
                        "This means no download has ever been started, or storage permission is missing."
                    audioPath = "(none)"
                    audioContent = ""
                } else {
                    // Prefer .master.m3u8, fall back to any .m3u8.
                    val allM3u8 = moviesDir.walkTopDown()
                        .filter { it.isFile && it.name.endsWith(".m3u8") }
                        .sortedByDescending { it.lastModified() }
                        .toList()
                    val masters = allM3u8.filter { it.name.endsWith(".master.m3u8") }
                    val target = masters.firstOrNull() ?: allM3u8.firstOrNull()

                    if (target != null) {
                        playlistPath = target.absolutePath
                        playlistContent = runCatching { target.readText() }
                            .getOrElse { "read failed: ${it.message}" }

                        // Look for sibling .audio.m3u8 (same base name)
                        val base = target.nameWithoutExtension.removeSuffix(".master")
                        val audioFile = File(target.parentFile, "$base.audio.m3u8")
                        if (audioFile.exists()) {
                            audioPath = audioFile.absolutePath
                            audioContent = runCatching { audioFile.readText() }
                                .getOrElse { "read failed: ${it.message}" }
                        } else {
                            audioPath = "(no sibling .audio.m3u8 — stream may be muxed or audio was never downloaded)"
                            audioContent = ""
                        }

                        // List every file in the same folder so we can see if segments exist
                        fileList = target.parentFile
                            ?.listFiles()
                            ?.sortedBy { it.name.lowercase() }
                            ?.map { fmt -> "${fmt.name}  (${fmt.length()} B)" }
                            ?: emptyList()
                    } else {
                        playlistPath = "(no .m3u8 found)"
                        playlistContent = "Movies dir exists but contains no .m3u8 files.\n" +
                            "Top-level contents:\n" +
                            (moviesDir.listFiles()?.joinToString("\n") { "  ${it.name}" } ?: "  (empty)")
                        audioPath = "(none)"
                        audioContent = ""
                        fileList = moviesDir.walkTopDown().take(30).map { it.absolutePath }.toList()
                    }
                }
            }
            status = "Loaded ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
        }
    }

    fun share() {
        val text = buildString {
            appendLine("=== KINO DIAGNOSTIC DUMP ===")
            appendLine("Time: ${Date()}")
            appendLine("Package: ${context.packageName}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("=== STATUS ===")
            appendLine(status)
            appendLine()
            appendLine("=== FILES IN DOWNLOAD FOLDER ===")
            if (fileList.isEmpty()) appendLine("(none listed)") else fileList.forEach { appendLine(it) }
            appendLine()
            appendLine("=== PLAYLIST CONTENT ===")
            appendLine("Path: $playlistPath")
            appendLine()
            appendLine(playlistContent)
            appendLine()
            appendLine("=== AUDIO PLAYLIST ===")
            appendLine("Path: $audioPath")
            if (audioContent.isNotEmpty()) {
                appendLine()
                appendLine(audioContent)
            }
            appendLine()
            appendLine("=== SUBTITLE FILES ===")
            val moviesDir = File(context.getExternalFilesDir(null), "Movies")
            moviesDir.listFiles()?.filter { it.name.endsWith(".srt") || it.name.endsWith(".raw.sub") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(5)
                ?.forEach { file ->
                    appendLine("--- ${file.name} (${file.length()} bytes) ---")
                    file.useLines { it.take(15).forEach { line -> appendLine(line.take(200)) } }
                    appendLine()
                }
            appendLine()
            appendLine("=== LOGCAT (filtered) ===")
            appendLine(logcatText)
            appendLine()
            appendLine("=== END DUMP ===")
        }
        dump = text
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "KINO diagnostic dump")
        }
        context.startActivity(Intent.createChooser(intent, "Share diagnostic dump"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("KINO Diagnostics") },
                navigationIcon = { TextButton(onClick = onClose) { Text("Close") } },
                actions = { TextButton(onClick = { share() }) { Text("Share") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { refresh() }) { Text("Refresh") }
                Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }

            Section("Files in download folder") {
                if (fileList.isEmpty()) Muted("(none — tap Refresh)")
                else fileList.forEach { Mono(it) }
            }

            Section("Playlist content") {
                Muted("Path: $playlistPath")
                Spacer(Modifier.height(4.dp))
                SelectionContainer { Mono(playlistContent) }
            }

            if (audioContent.isNotEmpty()) {
                Section("Audio playlist") {
                    Muted("Path: $audioPath")
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer { Mono(audioContent) }
                }
            }

            Section("Logcat (filtered)") {
                SelectionContainer { Mono(logcatText) }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Mono(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

/** Tags whose log lines are kept in the Logcat section. */
private val FILTER_TAGS = listOf(
    "HLS_PHASE", "HLS_AUDIO", "HLS_SUBS", "PLAY_DEBUG", "KINO_DL",
    "ExoPlayer", "AudioTrack", "MediaCodec", "okhttp", "OkHttp",
    "Download", "DirectDownload",
    "SIZE", "SIZE_FINAL", "BW_SOURCE", "SUBS", "DL_404", "TRACE_HLS", "QualityProbe",
)

/* ═══════════════════════════════════════════════════════════════════════════
 *  MINIMAL INTEGRATION SNIPPET
 *  ═══════════════════════════════════════════════════════════════════════════
 *  Add a route to your NavHost and a button somewhere reachable (Settings is
 *  the natural home). Example with Jetpack Navigation Compose:
 *
 *  // In your NavHost builder:
 *  composable("debug_diagnostics") {
 *      DebugDiagnosticsScreen(onClose = { navController.popBackStack() })
 *  }
 *
 *  // In your Settings composable:
 *  ListItem(
 *      headlineContent = { Text("Diagnostics") },
 *      modifier = Modifier.clickable { navController.navigate("debug_diagnostics") },
 *  )
 *
 *  If you do not use Navigation Compose, wrap it in an Activity instead:
 *
 *  class DebugDiagnosticsActivity : ComponentActivity() {
 *      override fun onCreate(b: Bundle?) {
 *          super.onCreate(b)
 *          setContent { DebugDiagnosticsScreen(onClose = { finish() }) }
 *      }
 *  }
 *  // and add <activity android:name=".debug.DebugDiagnosticsActivity"/> to manifest.
 * ═══════════════════════════════════════════════════════════════════════════ */
