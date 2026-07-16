package com.universaldownloader.util

import kotlin.math.abs

/**
 * Utility formatting functions.
 * Direct port of Python's utils/helpers.py.
 */
object Formatters {

    private val VIDEO_ID_REGEX = Regex("""\[([a-zA-Z0-9_-]{11})]""")
    private val INVALID_FILENAME_CHARS = Regex("""[\\/:*?"<>|]""")

    /**
     * Format seconds into HH:MM:SS or MM:SS.
     * Port of format_duration() from helpers.py.
     */
    fun formatDuration(seconds: Double?): String {
        if (seconds == null) return "??:??"
        val totalSecs = seconds.toInt()
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val secs = totalSecs % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }

    /**
     * Format bytes into human-readable string.
     * Port of format_filesize() from helpers.py.
     */
    fun formatFileSize(sizeBytes: Long?): String {
        if (sizeBytes == null || sizeBytes <= 0) return "?? MB"
        var size = sizeBytes.toDouble()
        for (unit in listOf("B", "KB", "MB", "GB", "TB")) {
            if (abs(size) < 1024.0) {
                return "%.1f %s".format(size, unit)
            }
            size /= 1024.0
        }
        return "%.1f PB".format(size)
    }

    /**
     * Remove characters invalid for file systems.
     * Port of sanitize_filename() from helpers.py.
     */
    fun sanitizeFilename(filename: String): String {
        if (filename.isBlank()) return "Unknown"
        var sanitized = INVALID_FILENAME_CHARS.replace(filename, "")
        sanitized = sanitized.replace(Regex("""\s+"""), " ")
        sanitized = sanitized.replace(Regex("""\.{2,}"""), ".")
        return sanitized.trim()
    }

    /**
     * Extract 11-char YouTube video ID from a filename.
     * Port of extract_video_id() from helpers.py.
     */
    fun extractVideoId(filename: String): String? {
        return VIDEO_ID_REGEX.find(filename)?.groupValues?.get(1)
    }
}
