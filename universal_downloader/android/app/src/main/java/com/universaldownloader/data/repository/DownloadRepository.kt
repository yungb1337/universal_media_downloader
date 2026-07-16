package com.universaldownloader.data.repository

import android.content.Context
import com.universaldownloader.data.model.DownloadSessionState
import com.universaldownloader.engine.DownloadEngine
import com.universaldownloader.engine.LinkParser
import com.universaldownloader.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

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

        // Ensure download directory exists
        FileUtils.ensureDirectoryExists(settings.downloadDir)

        // Save links to internal file so mark_done can update it
        val linksFile = FileUtils.getDefaultLinksFile(context)
        linksFile.writeText(linksText, Charsets.UTF_8)

        engine.run(links, settings, linksFile.absolutePath, scope)
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
