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
        // Primary location: "Universal Downloader" folder in internal storage root
        // Note: Modern Android restricts direct root access. 
        // We use system Downloads as the most accessible "Default".
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Universal Downloader"
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

    /**
     * Format raw cookie string from WebView into Netscape format.
     */
    fun formatCookiesToNetscape(url: String, cookieString: String?): String {
        if (cookieString.isNullOrBlank()) return ""
        
        val uri = Uri.parse(url)
        var domain = uri.host ?: ""
        
        // Normalize domain for Netscape format: .example.com
        if (domain.startsWith("www.")) {
            domain = domain.substring(3) // Result: .youtube.com
        } else if (!domain.startsWith(".") && domain.contains(".")) {
            domain = ".$domain"
        }
        
        val secureFlag = if (url.startsWith("https")) "TRUE" else "FALSE"
        
        val sb = StringBuilder()
        // Standard Netscape header
        sb.append("# Netscape HTTP Cookie File\n")
        sb.append("# This file was automatically generated. Do not edit.\n\n")

        cookieString.split(";").forEach { cookie ->
            val trimmed = cookie.trim()
            if (trimmed.isBlank()) return@forEach
            
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0]
                val value = parts[1]
                
                // Column 1: Domain
                // Column 2: Flag (Include subdomains)
                // Column 3: Path
                // Column 4: Secure flag
                // Column 5: Expiration (Unix timestamp) - 2147483647 is Jan 2038
                // Column 6: Name
                // Column 7: Value
                sb.append(domain).append("\t")
                  .append("TRUE").append("\t")
                  .append("/").append("\t")
                  .append(secureFlag).append("\t")
                  .append("2147483647").append("\t")
                  .append(name).append("\t")
                  .append(value).append("\n")
            }
        }
        return sb.toString()
    }

    /**
     * Save extracted cookies to a fixed internal file.
     */
    fun saveExtractedCookies(context: Context, content: String): File {
        val dir = getCookiesDir(context)
        val file = File(dir, "extracted_cookies.txt")
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    private fun getCookiesDir(context: Context): File {
        val dir = File(context.filesDir, "cookies")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
