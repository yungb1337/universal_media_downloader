package com.universaldownloader.engine

import com.universaldownloader.data.model.LinkEntry
import java.io.File

/**
 * Parses links text and manages [DONE] marking.
 * Direct port of Python's core/parser.py.
 *
 * Same rules:
 *   - One URL per line
 *   - Lines starting with # are comments (ignored)
 *   - Blank lines are ignored
 *   - Lines starting with [DONE] are already downloaded (skipped)
 *   - URLs must start with http:// or https://
 *   - Optional custom name after a space: "URL CustomName"
 */
object LinkParser {

    /**
     * Parse links text content into a list of LinkEntry objects.
     * Port of parse_links() from parser.py.
     */
    fun parseLinks(content: String): List<LinkEntry> {
        val entries = mutableListOf<LinkEntry>()

        content.lines().forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trim()

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            // Skip already-downloaded entries
            if (line.startsWith("[DONE]")) return@forEachIndexed

            // Split URL and optional custom name
            val parts = line.split(Regex("\\s+"), limit = 2)
            val url = parts[0]
            val customName = if (parts.size > 1) parts[1].trim() else null

            // Validate URL scheme
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@forEachIndexed
            }

            entries.add(LinkEntry(url = url, lineNumber = lineNumber, customName = customName))
        }

        return entries
    }

    /**
     * Mark a line as [DONE] in the links content.
     * Port of mark_done() from parser.py.
     * Returns the updated content string.
     */
    fun markDone(content: String, lineNumber: Int): String {
        val lines = content.lines().toMutableList()
        val index = lineNumber - 1

        if (index in lines.indices) {
            val original = lines[index].trim()
            if (!original.startsWith("[DONE]")) {
                lines[index] = "[DONE]-$original"
            }
        }

        return lines.joinToString("\n")
    }

    /**
     * Parse links from a file.
     */
    fun parseLinksFromFile(file: File): List<LinkEntry> {
        if (!file.exists()) return emptyList()
        return parseLinks(file.readText(Charsets.UTF_8))
    }

    /**
     * Mark a line as done in a file.
     * Thread-safe via @Synchronized (replaces Python's threading.Lock).
     */
    @Synchronized
    fun markDoneInFile(file: File, lineNumber: Int) {
        if (!file.exists()) return
        val content = file.readText(Charsets.UTF_8)
        val updated = markDone(content, lineNumber)
        file.writeText(updated, Charsets.UTF_8)
    }
}
