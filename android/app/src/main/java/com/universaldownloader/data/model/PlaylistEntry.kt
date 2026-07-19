package com.universaldownloader.data.model

/**
 * Represents a single video entry found in a playlist or URL analysis.
 */
data class PlaylistEntry(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val isSelected: Boolean = true,
    val isPlaylist: Boolean = false,
    val formats: List<VideoFormat> = emptyList()
)

data class VideoFormat(
    val formatId: String,
    val resolution: String,
    val ext: String,
    val fileSize: Long,
    val type: String, // "video" or "audio"
    val height: Int
)
