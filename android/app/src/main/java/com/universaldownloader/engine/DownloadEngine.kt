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
        scope: CoroutineScope,
        formatsMap: Map<String, String> = emptyMap()
    ) {
        if (links.isEmpty()) {
            addLog("No links to process.", LogTag.WARNING)
            return
        }

        _sessionState.update { current ->
            current.copy(
                isRunning = true,
                links = current.links + links, // Append links
                totalLinks = current.totalLinks + links.size
            )
        }

        addLog("═".repeat(50), LogTag.INFO)
        addLog("  Universal Downloader — Android", LogTag.INFO)
        addLog("═".repeat(50), LogTag.INFO)
        addLog("Adding ${links.size} link(s) to queue", LogTag.INFO)
        addLog("Download directory: ${settings.downloadDir}", LogTag.INFO)
        addLog("Resolution: ${if (settings.highestRes) "HIGHEST" else "1080p cap"}", LogTag.INFO)
        if (settings.audioOnly) {
            addLog("Mode: Audio-only (${settings.audioFormat} ${settings.audioQuality}kbps)", LogTag.INFO)
        }

        val startTime = System.currentTimeMillis()

        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                if (settings.maxConcurrent > 1) {
                    addLog("Using ${settings.maxConcurrent} parallel download workers", LogTag.INFO)
                    runParallel(links, settings, linksFilePath, formatsMap)
                } else {
                    runSequential(links, settings, linksFilePath, formatsMap)
                }
            } catch (e: CancellationException) {
                addLog("⚠️  Download batch cancelled", LogTag.WARNING)
                throw e
            } catch (e: Exception) {
                val friendlyError = toFriendlyError(e.message ?: "Unknown engine error")
                addLog("❌ $friendlyError", LogTag.ERROR)
            } finally {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                printSummary(elapsed)
                // Only mark as not running if this was the last active job
                // Since we only have one downloadJob at a time currently, this is fine.
                _sessionState.update { it.copy(isRunning = false) }
            }
        }

        downloadJob?.join()
    }

    fun cancel() {
        PythonBridge.setCancel(true)
        downloadJob?.cancel()
        _sessionState.update { it.copy(isRunning = false) }
        addLog("⏹  Download stopped by user.", LogTag.WARNING)
    }

    /** Clear log entries. */
    fun clearLogs() {
        _sessionState.update { it.copy(logEntries = emptyList()) }
    }

    private suspend fun runSequential(
        links: List<LinkEntry>,
        settings: DownloadSettings,
        linksFilePath: String?,
        formatsMap: Map<String, String>
    ) {
        for ((index, link) in links.withIndex()) {
            coroutineContext.ensureActive()
            addLog("━━━ [${index + 1}/${links.size}] ━━━", LogTag.INFO)

            val result = downloadSingle(link, settings, formatsMap[link.url])
            addResult(result)

            if ((result.success || result.skipped) && linksFilePath != null) {
                LinkParser.markDoneInFile(File(linksFilePath), link.lineNumber)
            }

            _sessionState.update { it.copy(totalProcessed = index + 1) }
        }
    }

    private suspend fun runParallel(
        links: List<LinkEntry>,
        settings: DownloadSettings,
        linksFilePath: String?,
        formatsMap: Map<String, String>
    ) = coroutineScope {
        val semaphore = Semaphore(settings.maxConcurrent)
        var processed = 0

        val deferreds = links.mapIndexed { index, link ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    ensureActive()
                    addLog("━━━ [${index + 1}/${links.size}] ━━━", LogTag.INFO)

                    val result = downloadSingle(link, settings, formatsMap[link.url])
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
        settings: DownloadSettings,
        formatId: String? = null
    ): DownloadResult {
        addLog("⬇️  Downloading: ${link.url}", LogTag.DOWNLOAD)

        // Job-Aware Decoupling: Calculate effective flags based on the specific selection
        val effectiveAudioOnly = when (formatId) {
            "bestaudio" -> true
            null, "auto" -> settings.audioOnly
            else -> false // Any other specific ID means we chose a Video format
        }

        return try {
            pythonBridge.downloadUrl(
                url = link.url,
                outputDir = settings.downloadDir,
                highestRes = settings.highestRes,
                audioOnly = effectiveAudioOnly,
                cookiesFile = settings.cookiesFile.ifBlank { null },
                maxRetries = settings.maxRetries,
                customName = link.customName,
                formatId = formatId,
                audioFormat = settings.audioFormat,
                audioQuality = settings.audioQuality,
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
                if (result.quality != null) {
                    addLog("   Quality: ${result.quality}", LogTag.INFO)
                }
            } else if (result.skipped) {
                addLog("⏭️  Already downloaded: ${result.title ?: result.url}", LogTag.INFO)
            } else {
                addLog("❌ Failed: ${result.title ?: result.url}", LogTag.ERROR)
                val friendlyError = toFriendlyError(result.error ?: "Unknown error")
                addLog("   $friendlyError", LogTag.ERROR)
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
                val friendlyError = toFriendlyError(r.error ?: "Unknown error")
                addLog("      → $friendlyError", LogTag.ERROR)
            }
        }

        addLog("═".repeat(50), LogTag.INFO)
    }

    private fun toFriendlyError(rawError: String): String {
        return when {
            rawError.contains("Sign in to confirm you're not a bot", ignoreCase = true) ||
            rawError.contains("confirm your age", ignoreCase = true) -> 
                "Verification Required: Please go to Settings and 'Login to YouTube' to continue."
            
            rawError.contains("This video is private", ignoreCase = true) -> 
                "Private Content: Please login in Settings to download your private videos."
            
            rawError.contains("403", ignoreCase = true) || 
            rawError.contains("Forbidden", ignoreCase = true) -> 
                "Access Denied: Your login session may have expired. Please re-extract cookies in Settings."
            
            rawError.contains("Unsupported URL", ignoreCase = true) ||
            rawError.contains("no extractor", ignoreCase = true) ->
                "Unsupported Site: The app doesn't know how to download from this website yet."
                
            rawError.contains("No space left on device", ignoreCase = true) ->
                "Storage Full: Please clear some space on your phone to save the video."
                
            rawError.contains("ffmpeg is not installed", ignoreCase = true) ||
            rawError.contains("ffprobe is not installed", ignoreCase = true) ->
                "Quality Note: FFmpeg is missing. Downloading the best single-file version (usually 720p) instead."

            rawError.contains("Encoder not found", ignoreCase = true) ->
                "Audio Error: Your FFmpeg build doesn't support this format. Please go to Settings and change 'Audio Format' to M4A or Original."

            rawError.contains("file name too long", ignoreCase = true) ||
            rawError.contains("path too long", ignoreCase = true) ->
                "Folder Error: The video title is too long. I've automatically shortened it to save it successfully."

            rawError.contains("Unable to download webpage", ignoreCase = true) ||
            rawError.contains("Failed to connect", ignoreCase = true) ->
                "Connection Error: Please check your internet connection and try again."
                
            else -> "Error: $rawError"
        }
    }

    /** Add a log entry to the session state. */
    fun addLog(message: String, tag: LogTag) {
        val entry = LogEntry(message = message, tag = tag)
        _sessionState.update { it.copy(logEntries = it.logEntries + entry) }
    }

    private fun addResult(result: DownloadResult) {
        _sessionState.update { it.copy(results = it.results + result) }
    }
}
