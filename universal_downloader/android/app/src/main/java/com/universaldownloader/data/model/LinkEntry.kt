package com.universaldownloader.data.model

/**
 * A single URL parsed from the links list.
 * Direct port of Python's core/models.py LinkEntry.
 */
data class LinkEntry(
    val url: String,
    val lineNumber: Int,
    val customName: String? = null
)
