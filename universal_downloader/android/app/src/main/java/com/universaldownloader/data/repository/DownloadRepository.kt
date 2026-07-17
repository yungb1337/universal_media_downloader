package com.universaldownloader.data.repository

import android.content.Context
import com.universaldownloader.data.model.DownloadSessionState
import com.universaldownloader.engine.DownloadEngine
import com.universaldownloader.engine.LinkParser
import com.universaldownloader.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.net.Uri

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
        scope: CoroutineScope
    ) {
        val links = LinkParser.parseLinks(linksText)
        if (links.isEmpty()) return

        val isSaf = settings.downloadDirUri.startsWith("content://")
        val finalTargetUri = if (isSaf) Uri.parse(settings.downloadDirUri) else null
        
        // Use a temporary directory for the actual download if using SAF
        val effectiveDownloadDir = if (isSaf) {
            val tempDir = File(context.cacheDir, "download_work")
            if (!tempDir.exists()) tempDir.mkdirs()
            tempDir.absolutePath
        } else {
            settings.downloadDir
        }

        // Ensure download directory exists
        if (!FileUtils.ensureDirectoryExists(effectiveDownloadDir)) {
            engine.addLog("❌ Failed to create download directory: $effectiveDownloadDir", com.universaldownloader.data.model.LogTag.ERROR)
            return
        }

        // Save links to internal file so mark_done can update it
        val linksFile = FileUtils.getDefaultLinksFile(context)
        linksFile.writeText(linksText, Charsets.UTF_8)

        val effectiveSettings = settings.copy(downloadDir = effectiveDownloadDir)

        // Launch a collector to move files to SAF as they finish
        var moveJob: kotlinx.coroutines.Job? = null
        if (isSaf && finalTargetUri != null) {
            moveJob = scope.launch {
                var lastResultCount = 0
                engine.sessionState.collectLatest { state ->
                    if (state.results.size > lastResultCount) {
                        val newResults = state.results.drop(lastResultCount)
                        lastResultCount = state.results.size
                        
                        for (result in newResults) {
                            if (result.success && !result.skipped && result.filePath != null) {
                                val sourceFile = File(result.filePath)
                                if (sourceFile.exists()) {
                                    engine.addLog("📂 Moving to final folder: ${sourceFile.name}", com.universaldownloader.data.model.LogTag.INFO)
                                    val moved = FileUtils.moveFileToSafUri(context, sourceFile, finalTargetUri)
                                    if (moved) {
                                        engine.addLog("✅ Moved: ${sourceFile.name}", com.universaldownloader.data.model.LogTag.SUCCESS)
                                    } else {
                                        engine.addLog("❌ Failed to move to target folder", com.universaldownloader.data.model.LogTag.ERROR)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        try {
            engine.run(links, effectiveSettings, linksFile.absolutePath, scope)
        } finally {
            moveJob?.cancel()
        }
    }

    /** Stop all active downloads. */
    fun stopDownload() {
        engine.cancel()
    }

    /** Check if a download session is currently running. */
    fun isRunning(): Boolean = engine.isRunning()

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
