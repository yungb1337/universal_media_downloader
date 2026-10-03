package com.universal.downloader.model

import java.util.UUID

enum class DownloadStatus {
    QUEUED,
    PROBING,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class MediaFormatType {
    VIDEO,
    AUDIO
}

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "Extracting video info...",
    val uploader: String = "",
    val thumbnail: String = "",
    val durationSeconds: Long = 0,
    val formatType: MediaFormatType = MediaFormatType.VIDEO,
    val resolution: String = "1080p",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadSpeedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val outputPath: String? = null,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class VideoProbeResult(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val durationSeconds: Long,
    val isPlaylist: Boolean,
    val availableResolutions: List<String>
)

data class EngineStatus(
    val version: String = "Initializing...",
    val location: String = "Bundled",
    val isCustomUpdated: Boolean = false,
    val lastCheckedTimestamp: Long = 0L,
    val updateAvailable: Boolean = false,
    val latestVersionAvailable: String? = null,
    val isUpdating: Boolean = false,
    val updateLog: String = ""
)
