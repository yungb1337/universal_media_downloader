package com.universaldownloader.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

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
        if (path.startsWith("content://")) return true // SAF handles its own "existence"
        val dir = File(path)
        return if (!dir.exists()) dir.mkdirs() else true
    }

    /**
     * Move a file from internal storage to a SAF directory.
     */
    fun moveFileToSafUri(context: Context, sourceFile: File, treeUri: Uri): Boolean {
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            
            // Create the file in the target directory
            val mimeType = when (sourceFile.extension.lowercase()) {
                "mp4" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                else -> "application/octet-stream"
            }
            val targetFile = root.createFile(mimeType, sourceFile.name) ?: return false
            
            // Copy data
            context.contentResolver.openOutputStream(targetFile.uri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(output)
                }
            }
            
            // Delete source
            sourceFile.delete()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Get a human-readable name for a SAF URI.
     */
    fun getDisplayName(context: Context, uri: Uri): String {
        if (uri.scheme != "content") return uri.path ?: uri.toString()
        
        return DocumentFile.fromTreeUri(context, uri)?.name 
            ?: DocumentFile.fromSingleUri(context, uri)?.name 
            ?: uri.path ?: uri.toString()
    }
}
