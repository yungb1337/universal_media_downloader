package com.universaldownloader.engine

import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.universaldownloader.data.model.DownloadResult
import com.universaldownloader.data.model.PlaylistEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bridge between Kotlin and the Python yt-dlp backend.
 * Uses Chaquopy to call download_bridge.py functions.
 *
 * This is the Android replacement for the desktop's direct yt-dlp import.
 * The Python bridge preserves all the download logic from ytdlp_backend.py.
 */
class PythonBridge {

    companion object {
        private val _shouldCancel = AtomicBoolean(false)

        @JvmStatic
        fun setCancel(cancel: Boolean) {
            _shouldCancel.set(cancel)
        }

        @JvmStatic
        fun isCancelled(): Boolean {
            return _shouldCancel.get()
        }
    }

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
        maxRetries: Int = 3,
        customName: String? = null,
        formatId: String? = null,
        audioFormat: String? = null,
        audioQuality: String? = null,
        onProgress: ((String) -> Unit)? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        setCancel(false)
        val ffmpegPath = getFFmpegPath()

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
            formatId ?: "auto",
            audioFormat ?: "m4a",
            audioQuality ?: "320",
            listener
        ).toString()

        parseDownloadResult(resultJson, url)
    }

    /**
     * Fetch playlist or video metadata using yt-dlp.
     */
    suspend fun getPlaylistInfo(url: String, cookiesFile: String? = null): List<PlaylistEntry> = withContext(Dispatchers.IO) {
        try {
            val resultJson = bridge.callAttr(
                "get_playlist_info",
                url,
                cookiesFile ?: ""
            ).toString()

            val obj = JSONObject(resultJson)
            if (!obj.optBoolean("success", false)) {
                throw Exception(obj.optString("error", "Unknown analysis error"))
            }

            val results = mutableListOf<PlaylistEntry>()
            val entriesArray = obj.getJSONArray("entries")
            for (i in 0 until entriesArray.length()) {
                val entryObj = entriesArray.getJSONObject(i)
                
                // Parse formats for each entry
                val formatsList = mutableListOf<com.universaldownloader.data.model.VideoFormat>()
                val formatsArray = entryObj.optJSONArray("formats")
                if (formatsArray != null) {
                    for (j in 0 until formatsArray.length()) {
                        val f = formatsArray.getJSONObject(j)
                        formatsList.add(
                            com.universaldownloader.data.model.VideoFormat(
                                formatId = f.getString("id"),
                                resolution = f.getString("res"),
                                ext = f.getString("ext"),
                                fileSize = f.optLong("size", 0),
                                type = f.optString("type", "video"),
                                height = f.optInt("height", 0)
                            )
                        )
                    }
                }

                results.add(
                    PlaylistEntry(
                        url = entryObj.getString("url"),
                        title = entryObj.getString("title"),
                        thumbnail = entryObj.optString("thumb", null),
                        isSelected = true,
                        isPlaylist = obj.optBoolean("is_playlist", false),
                        formats = formatsList
                    )
                )
            }
            results
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Get the path to the bundled FFmpeg binary.
     */
    private fun getFFmpegPath(): String? {
        return try {
            val platform = Python.getPlatform() as com.chaquo.python.android.AndroidPlatform
            val context = platform.getApplication()
            val libDir = context.applicationInfo.nativeLibraryDir
            val ffmpegFile = java.io.File(libDir, "libffmpeg.so")
            if (ffmpegFile.exists()) ffmpegFile.absolutePath else null
        } catch (e: Exception) {
            null
        }
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
                thumbnail = if (obj.has("thumbnail") && !obj.isNull("thumbnail"))
                    obj.getString("thumbnail") else null,
                quality = if (obj.has("quality") && !obj.isNull("quality"))
                    obj.getString("quality") else null
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
