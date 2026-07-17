package com.universaldownloader.data.model

/**
 * Represents a single video entry found in a playlist or URL analysis.
 */
data class PlaylistEntry(
    val url: String,
    val title: String,
    val isSelected: Boolean = true
)
