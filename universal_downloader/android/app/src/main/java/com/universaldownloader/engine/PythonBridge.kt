package com.universaldownloader.engine

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.universaldownloader.data.model.DownloadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Bridge between Kotlin and the Python yt-dlp backend.
 * Uses Chaquopy to call download_bridge.py functions.
 *
 * This is the Android replacement for the desktop's direct yt-dlp import.
 * The Python bridge preserves all the download logic from ytdlp_backend.py.
 */
class PythonBridge {

    private val python: Python by lazy { Python.getInstance() }
    private val bridge: PyObject by lazy { python.getModule("download_bridge") }

    /**
     * Callback interface for progress updates.
     * Python code calls onProgress(jsonString) on this object via Chaquopy interop.
     */
    open class ProgressListener {
        open fun onProgress(data: String) {}
    }

    /**
     * Download a single URL using yt-dlp via the embedded Python runtime.
     *
     * This method:
     * 1. Calls download_bridge.download_url() in Python
     * 2. Passes all settings as parameters
     * 3. Passes a ProgressListener for real-time progress callbacks
     * 4. Parses the JSON result into a DownloadResult
     *
     * Must be called from a background thread (Dispatchers.IO).
     */
    suspend fun downloadUrl(
        url: String,
        outputDir: String,
        highestRes: Boolean = false,
        audioOnly: Boolean = false,
        cookiesFile: String? = null,
        ffmpegPath: String? = null,
        maxRetries: Int = 3,
        customName: String? = null,
        onProgress: ((String) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {

        val listener = onProgress?.let { callback ->
            object : ProgressListener() {
                override fun onProgress(data: String) {
                    callback(data)
                }
            }
        }

        val resultJson = bridge.callAttr(
            "download_url",
            url,
            outputDir,
            highestRes,
            audioOnly,
            cookiesFile ?: "",
            ffmpegPath ?: "",
            maxRetries,
            customName ?: "",
            listener
        ).toString()

        parseDownloadResult(resultJson, url)
    }

    /**
     * Parse the JSON result from Python into a DownloadResult.
     */
    private fun parseDownloadResult(json: String, fallbackUrl: String): DownloadResult {
        return try {
            val obj = JSONObject(json)
            DownloadResult(
                url = obj.optString("url", fallbackUrl),
                success = obj.optBoolean("success", false),
                filePath = if (obj.has("file_path") && !obj.isNull("file_path"))
                    obj.getString("file_path") else null,
                title = if (obj.has("title") && !obj.isNull("title"))
                    obj.getString("title") else null,
                error = if (obj.has("error") && !obj.isNull("error"))
                    obj.getString("error") else null,
                durationSeconds = obj.optDouble("duration_seconds", 0.0),
                fileSizeBytes = obj.optLong("file_size_bytes", 0),
                skipped = obj.optBoolean("skipped", false),
            )
        } catch (e: Exception) {
            DownloadResult(
                url = fallbackUrl,
                success = false,
                error = "Failed to parse Python result: ${e.message}"
            )
        }
    }
}
