package com.universaldownloader.data.model

/**
 * Real-time progress data for an active download.
 * Replaces the TqdmProgressHook from the desktop version.
 */
data class DownloadProgress(
    val filename: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speed: Double? = null,
    val eta: Int? = null,
    val status: DownloadStatus = DownloadStatus.IDLE
)

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    FINISHED,
    ERROR,
    CANCELLED
}

/**
 * Overall state of the download session.
 * Central state object observed by the UI via StateFlow.
 */
data class DownloadSessionState(
    val isRunning: Boolean = false,
    val links: List<LinkEntry> = emptyList(),
    val results: List<DownloadResult> = emptyList(),
    val currentProgress: Map<String, DownloadProgress> = emptyMap(),
    val logEntries: List<LogEntry> = emptyList(),
    val totalProcessed: Int = 0,
    val totalLinks: Int = 0
)

/**
 * A single log entry with color tag.
 * Port of the tagged log lines from desktop gui/widgets.py LogPanel.
 */
data class LogEntry(
    val id: Long = System.nanoTime(),
    val message: String,
    val tag: LogTag = LogTag.INFO,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Log severity tags — maps to the same color scheme as the desktop version.
 */
enum class LogTag {
    INFO,       // TextSecondary (dim white)
    SUCCESS,    // Green (#57CC99)
    WARNING,    // Orange (#F4A261)
    ERROR,      // Red (#E63946)
    DOWNLOAD    // Cyan (#48CAE4)
}
