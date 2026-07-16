package com.universaldownloader.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Android file system utilities.
 * Replaces pathlib-based file operations from the desktop version.
 */
object FileUtils {

    /**
     * Get the default download directory (app-specific external storage).
     * Files here don't require runtime permissions on API 26+.
     */
    fun getDefaultDownloadDir(context: Context): File {
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "UniversalDownloader"
        )
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Get the internal links storage directory.
     */
    fun getLinksDir(context: Context): File {
        val dir = File(context.filesDir, "links")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Get the default links file path.
     */
    fun getDefaultLinksFile(context: Context): File {
        return File(getLinksDir(context), "links.txt")
    }

    /**
     * Get the logs directory.
     */
    fun getLogsDir(context: Context): File {
        val dir = File(context.filesDir, "logs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Read text content from a URI (SAF support).
     */
    fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Write text content to a URI (SAF support).
     */
    fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(content)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ensure a directory exists, creating it if necessary.
     */
    fun ensureDirectoryExists(path: String): Boolean {
        val dir = File(path)
        return if (!dir.exists()) dir.mkdirs() else true
    }
}
