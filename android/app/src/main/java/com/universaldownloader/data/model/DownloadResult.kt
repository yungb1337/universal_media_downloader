package com.universaldownloader.data.model

/**
 * Result of a single download attempt.
 * Direct port of Python's core/models.py DownloadResult.
 */
data class DownloadResult(
    val url: String,
    val success: Boolean,
    val filePath: String? = null,
    val title: String? = null,
    val error: String? = null,
    val durationSeconds: Double = 0.0,
    val fileSizeBytes: Long = 0,
    val skipped: Boolean = false,
    val lineNumber: Int = 0
)
