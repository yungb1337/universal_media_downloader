package com.universaldownloader.data.repository

import android.content.Context
import com.universaldownloader.data.model.DownloadSessionState
import com.universaldownloader.engine.DownloadEngine
import com.universaldownloader.engine.LinkParser
import com.universaldownloader.engine.PythonBridge
import com.universaldownloader.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository coordinating downloads between the UI and engine layers.
 * Provides a clean API for ViewModels to start/stop downloads and observe state.
 */
class DownloadRepository(
    private val context: Context,
    private val engine: DownloadEngine,
    private val settingsRepository: SettingsRepository
) {
    /** Observable download session state. */
    val sessionState: StateFlow<DownloadSessionState> = engine.sessionState

    // Centralized tracker to ensure multiple workers (Batch + Resume) don't conflict
    private val processedFiles = ConcurrentHashMap.newKeySet<String>()

    /**
     * Start downloading all links in the given text.
     *
     * Flow:
     * 1. Parse links text into LinkEntry objects
     * 2. Save links to internal file (for mark_done support)
     * 3. Ensure download directory exists
     * 4. Run the engine pipeline
     */
    suspend fun startDownload(
        linksText: String,
        settings: DownloadSettings,
        scope: CoroutineScope,
        formatsMap: Map<String, String> = emptyMap()
    ) {
        val links = LinkParser.parseLinks(linksText)
        if (links.isEmpty()) return

        val isSaf = settings.downloadDirUri.isNotEmpty() && settings.downloadDirUri.startsWith("content://")
        val finalTargetUri = if (isSaf) Uri.parse(settings.downloadDirUri) else null
        
        // Use an internal workspace for yt-dlp to avoid direct storage permission issues
        // and handle the "two copies" cleanup properly.
        val workspaceDir = File(context.cacheDir, "download_work")
        if (!workspaceDir.exists()) workspaceDir.mkdirs()
        
        val effectiveDownloadDir = workspaceDir.absolutePath

        // Ensure download directory exists
        if (!FileUtils.ensureDirectoryExists(effectiveDownloadDir)) {
            engine.addLog("❌ Failed to create download workspace: $effectiveDownloadDir", com.universaldownloader.data.model.LogTag.ERROR)
            return
        }

        // Save links to internal file so mark_done can update it
        val linksFile = FileUtils.getDefaultLinksFile(context)
        linksFile.writeText(linksText, Charsets.UTF_8)

        val effectiveSettings = settings.copy(downloadDir = effectiveDownloadDir)

        // Launch a collector to move files to their final home as they finish
        val moveJob = scope.launch {
            engine.sessionState.collect { state ->
                // Check all current results for anything new that needs moving
                for (result in state.results) {
                    if (result.success && !result.skipped && result.filePath != null) {
                        val path = result.filePath
                        
                        // Thread-safe check: only move if not already processed
                        if (!processedFiles.add(path)) continue
                        
                        val sourceFile = File(path)
                        if (sourceFile.exists()) {
                            var finalDestination: String = path

                            if (isSaf && finalTargetUri != null) {
                                // Option A: Move to user-selected SAF folder
                                engine.addLog("📂 Saving to chosen folder: ${sourceFile.name}", com.universaldownloader.data.model.LogTag.INFO)
                                val moved = FileUtils.moveFileToSafUri(context, sourceFile, finalTargetUri)
                                if (moved) {
                                    engine.addLog("✅ Saved: ${sourceFile.name}", com.universaldownloader.data.model.LogTag.SUCCESS)
                                    finalDestination = sourceFile.name 
                                } else {
                                    engine.addLog("❌ Error: Failed to save to chosen folder. Check permissions.", com.universaldownloader.data.model.LogTag.ERROR)
                                }
                            } else {
                                // Option B: Default - Move to Public Downloads/Universal Downloader
                                engine.addLog("📂 Saving to Public Downloads (Universal Downloader)...", com.universaldownloader.data.model.LogTag.INFO)
                                val newUri = FileUtils.saveFileToMediaStore(context, sourceFile)
                                if (newUri != null) {
                                    engine.addLog("✅ Download Complete: Saved to Public Downloads folder", com.universaldownloader.data.model.LogTag.SUCCESS)
                                    finalDestination = newUri.toString()
                                } else {
                                    engine.addLog("❌ Error: Failed to save to Public Downloads. Storage may be full.", com.universaldownloader.data.model.LogTag.ERROR)
                                }
                            }
                            
                            // Save to database (History) with the FINAL destination
                            saveToHistory(result, finalDestination)
                        }
                    }
                }
            }
        }

        try {
            engine.run(links, effectiveSettings, linksFile.absolutePath, formatsMap)
        } finally {
            // Give the collector a moment to finish any last-second moves
            kotlinx.coroutines.delay(500)
            moveJob.cancel()
        }
    }

    private suspend fun saveToHistory(result: com.universaldownloader.data.model.DownloadResult, finalPath: String? = null) {
        try {
            val db = com.universaldownloader.data.database.DownloadDatabase.getDatabase(context)
            val entity = com.universaldownloader.data.database.DownloadHistoryEntity(
                url = result.url,
                title = result.title ?: "Unknown",
                filePath = finalPath ?: result.filePath,
                thumbnailUri = result.thumbnail,
                fileSize = result.fileSizeBytes,
                duration = result.durationSeconds,
                quality = result.quality ?: "auto"
            )
            db.downloadDao().insert(entity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Stop all active downloads. */
    fun stopDownload() {
        engine.cancel()
    }

    /**
     * Pause a single download job.
     * Immediately updates the UI state so the user sees the pause button response.
     * The Python side detects this state change and blocks in the progress_hook.
     */
    fun pauseDownload(url: String) {
        PythonBridge.setJobState(url, PythonBridge.JobState.PAUSED)
        engine.setDownloadPaused(url)
    }

    /**
     * Resume a single paused download.
     * Updates the UI state back to DOWNLOADING *before* Python's blocked
     * progress_hook unblocks, so the transition is seamless.
     */
    fun resumeDownload(url: String) {
        PythonBridge.setJobState(url, PythonBridge.JobState.RUNNING)
        engine.setDownloadRunning(url)
    }

    /**
     * Stop (cancel) a single download job.
     * Removes it from the UI immediately and signals Python to abort.
     */
    fun stopSingleDownload(url: String) {
        PythonBridge.setJobState(url, PythonBridge.JobState.STOPPED)
        engine.removeDownload(url)
    }

    /** Check if a download session is currently running. */
    fun isRunning(): Boolean = engine.isRunning()

    /** Check if a specific URL has an active yt-dlp call in progress. */
    fun isDownloadActive(url: String): Boolean = engine.isUrlActive(url)

    /** Clear the log panel. */
    fun clearLogs() {
        engine.clearLogs()
    }

    /**
     * Get the updated links text with [DONE] markers applied.
     * Used to refresh the UI text field after downloads complete.
     */
    fun getUpdatedLinksText(): String? {
        val linksFile = FileUtils.getDefaultLinksFile(context)
        return if (linksFile.exists()) linksFile.readText(Charsets.UTF_8) else null
    }
}
