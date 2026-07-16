package com.universaldownloader.engine

import com.universaldownloader.data.model.DownloadProgress
import com.universaldownloader.data.model.DownloadResult
import com.universaldownloader.data.model.DownloadSessionState
import com.universaldownloader.data.model.DownloadStatus
import com.universaldownloader.data.model.LinkEntry
import com.universaldownloader.data.model.LogEntry
import com.universaldownloader.data.model.LogTag
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.util.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Orchestrates the download pipeline.
 * Port of Python's core/engine.py DownloadEngine.
 *
 * Uses Kotlin coroutines instead of ThreadPoolExecutor.
 * Uses Semaphore for concurrency control.
 * Uses StateFlow for reactive state updates to the UI.
 */
class DownloadEngine(private val pythonBridge: PythonBridge) {

    private val _sessionState = MutableStateFlow(DownloadSessionState())
    val sessionState: StateFlow<DownloadSessionState> = _sessionState

    private var downloadJob: Job? = null

    fun isRunning(): Boolean = downloadJob?.isActive == true

    /**
     * Run the download pipeline for all links.
     * Port of DownloadEngine.run() from engine.py.
     */
    suspend fun run(
        links: List<LinkEntry>,
        settings: DownloadSettings,
        linksFilePath: String?,
        scope: CoroutineScope
    ) {
        if (links.isEmpty()) {
            addLog("No links to process.", LogTag.WARNING)
            return
        }

        _sessionState.update {
            DownloadSessionState(
                isRunning = true,
                links = links,
                totalLinks = links.size
            )
        }

        addLog("═".repeat(50), LogTag.INFO)
        addLog("  Universal Downloader — Android", LogTag.INFO)
        addLog("═".repeat(50), LogTag.INFO)
        addLog("Starting download of ${links.size} link(s)", LogTag.INFO)
        addLog("Download directory: ${settings.downloadDir}", LogTag.INFO)
        addLog("Resolution: ${if (settings.highestRes) "HIGHEST" else "1080p cap"}", LogTag.INFO)
        if (settings.audioOnly) {
            addLog("Mode: Audio-only (MP3 320kbps)", LogTag.INFO)
        }

        val startTime = System.currentTimeMillis()

        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                if (settings.maxConcurrent > 1) {
                    addLog("Using ${settings.maxConcurrent} parallel download workers", LogTag.INFO)
                    runParallel(links, settings, linksFilePath)
                } else {
                    runSequential(links, settings, linksFilePath)
                }
            } catch (e: CancellationException) {
                addLog("⚠️  Download cancelled", LogTag.WARNING)
                throw e
            } catch (e: Exception) {
                addLog("❌ Engine error: ${e.message}", LogTag.ERROR)
            } finally {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                printSummary(elapsed)
                _sessionState.update { it.copy(isRunning = false) }
            }
        }

        downloadJob?.join()
    }

    /** Cancel all active downloads. */
    fun cancel() {
        downloadJob?.cancel()
        _sessionState.update { it.copy(isRunning = false) }
        addLog("⏹  Download stopped by user.", LogTag.WARNING)
    }

    /** Clear log entries. */
    fun clearLogs() {
        _sessionState.update { it.copy(logEntries = emptyList()) }
    }

    // ── Sequential execution (port of _run_sequential) ──────────────────────

    private suspend fun runSequential(
        links: List<LinkEntry>,
        settings: DownloadSettings,
        linksFilePath: String?
    ) {
        for ((index, link) in links.withIndex()) {
            coroutineContext.ensureActive()
            addLog("━━━ [${index + 1}/${links.size}] ━━━", LogTag.INFO)

            val result = downloadSingle(link, settings)
            addResult(result)

            if ((result.success || result.skipped) && linksFilePath != null) {
                LinkParser.markDoneInFile(File(linksFilePath), link.lineNumber)
            }

            _sessionState.update { it.copy(totalProcessed = index + 1) }
        }
    }

    // ── Parallel execution (port of _run_parallel) ──────────────────────────

    private suspend fun runParallel(
        links: List<LinkEntry>,
        settings: DownloadSettings,
        linksFilePath: String?
    ) = coroutineScope {
        val semaphore = Semaphore(settings.maxConcurrent)
        var processed = 0

        val deferreds = links.mapIndexed { index, link ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    ensureActive()
                    addLog("━━━ [${index + 1}/${links.size}] ━━━", LogTag.INFO)

                    val result = downloadSingle(link, settings)
                    addResult(result)

                    if (result.success && linksFilePath != null) {
                        LinkParser.markDoneInFile(File(linksFilePath), link.lineNumber)
                    }

                    synchronized(this@DownloadEngine) {
                        processed++
                        _sessionState.update { it.copy(totalProcessed = processed) }
                    }
                }
            }
        }
        deferreds.awaitAll()
    }

    // ── Single download ─────────────────────────────────────────────────────

    private suspend fun downloadSingle(
        link: LinkEntry,
        settings: DownloadSettings
    ): DownloadResult {
        addLog("⬇️  Downloading: ${link.url}", LogTag.DOWNLOAD)

        return try {
            pythonBridge.downloadUrl(
                url = link.url,
                outputDir = settings.downloadDir,
                highestRes = settings.highestRes,
                audioOnly = settings.audioOnly,
                cookiesFile = settings.cookiesFile.ifBlank { null },
                maxRetries = settings.maxRetries,
                customName = link.customName,
                onProgress = { progressJson ->
                    handleProgress(progressJson)
                }
            ).copy(lineNumber = link.lineNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DownloadResult(
                url = link.url,
                success = false,
                error = e.message ?: "Unknown error",
                lineNumber = link.lineNumber
            )
        }.also { result ->
            if (result.success && !result.skipped) {
                addLog("✅ Saved: ${result.title ?: result.url}", LogTag.SUCCESS)
            } else if (result.skipped) {
                addLog("⏭️  Already downloaded: ${result.title ?: result.url}", LogTag.INFO)
            } else {
                addLog("❌ Failed: ${result.title ?: result.url}", LogTag.ERROR)
                result.error?.let { addLog("   Error: $it", LogTag.ERROR) }
            }
        }
    }

    // ── Progress handling ────────────────────────────────────────────────────

    private fun handleProgress(progressJson: String) {
        try {
            val obj = JSONObject(progressJson)
            val filename = obj.optString("filename", "")
            val status = obj.optString("status", "")

            when (status) {
                "downloading" -> {
                    val progress = DownloadProgress(
                        filename = filename,
                        downloadedBytes = obj.optLong("downloaded", 0),
                        totalBytes = obj.optLong("total", 0),
                        speed = if (obj.has("speed") && !obj.isNull("speed"))
                            obj.optDouble("speed") else null,
                        eta = if (obj.has("eta") && !obj.isNull("eta"))
                            obj.optInt("eta") else null,
                        status = DownloadStatus.DOWNLOADING
                    )
                    _sessionState.update { state ->
                        state.copy(
                            currentProgress = state.currentProgress + (filename to progress)
                        )
                    }
                }
                "finished", "error" -> {
                    _sessionState.update { state ->
                        state.copy(
                            currentProgress = state.currentProgress - filename
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Don't let progress parsing errors break the download
        }
    }

    // ── Summary (port of _print_summary) ────────────────────────────────────

    private fun printSummary(elapsedSeconds: Double) {
        val results = _sessionState.value.results
        val succeeded = results.count { it.success && !it.skipped }
        val failed = results.count { !it.success }
        val skipped = results.count { it.skipped }
        val totalSize = results.sumOf { it.fileSizeBytes }

        addLog("", LogTag.INFO)
        addLog("═".repeat(50), LogTag.INFO)
        addLog("  DOWNLOAD SUMMARY", LogTag.INFO)
        addLog("═".repeat(50), LogTag.INFO)
        addLog("  ✅ $succeeded succeeded", LogTag.SUCCESS)
        addLog("  ❌ $failed failed", LogTag.ERROR)
        addLog("  ⏭️  $skipped skipped (already downloaded)", LogTag.INFO)
        addLog("  📦 Total size: ${Formatters.formatFileSize(totalSize)}", LogTag.INFO)
        addLog("  ⏱️  Total time: ${Formatters.formatDuration(elapsedSeconds)}", LogTag.INFO)

        if (failed > 0) {
            addLog("─".repeat(50), LogTag.INFO)
            addLog("  Failed downloads:", LogTag.ERROR)
            results.filter { !it.success }.forEach { r ->
                val display = (r.title ?: r.url).let {
                    if (it.length > 45) it.take(42) + "..." else it
                }
                addLog("    Line ${r.lineNumber}: $display", LogTag.ERROR)
                r.error?.let { addLog("      → $it", LogTag.ERROR) }
            }
        }

        addLog("═".repeat(50), LogTag.INFO)
    }

    // ── State helpers ───────────────────────────────────────────────────────

    private fun addLog(message: String, tag: LogTag) {
        val entry = LogEntry(message = message, tag = tag)
        _sessionState.update { it.copy(logEntries = it.logEntries + entry) }
    }

    private fun addResult(result: DownloadResult) {
        _sessionState.update { it.copy(results = it.results + result) }
    }
}
