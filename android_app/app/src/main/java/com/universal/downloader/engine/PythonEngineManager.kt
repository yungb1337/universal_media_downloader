package com.universal.downloader.engine

import android.content.Context
import android.util.Log
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.universal.downloader.model.DownloadItem
import com.universal.downloader.model.MediaFormatType
import com.universal.downloader.model.VideoProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class PythonEngineManager private constructor(private val context: Context) {

    private var backendInstance: PyObject? = null
    private val updatesDir = File(context.filesDir, "python_updates").apply { mkdirs() }

    init {
        ensurePythonStarted(context)
        loadBackend()
    }

    @Synchronized
    fun reloadEngine() {
        loadBackend()
    }

    private fun loadBackend() {
        try {
            val py = Python.getInstance()
            val backendModule = py.getModule("backend")
            val downloaderClass = backendModule.get("AndroidDownloader")
            backendInstance = downloaderClass?.call(updatesDir.absolutePath)
            Log.i(TAG, "Backend engine initialized with version: ${getEngineVersion()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load Python backend engine", e)
        }
    }

    fun getEngineVersion(): String {
        return try {
            val py = Python.getInstance()
            val loaderModule = py.getModule("engine_loader")
            loaderModule.callAttr("get_engine_version").toString()
        } catch (e: Exception) {
            "Error: ${e.localizedMessage}"
        }
    }

    fun getEnginePath(): String {
        return try {
            val py = Python.getInstance()
            val loaderModule = py.getModule("engine_loader")
            loaderModule.callAttr("get_engine_path").toString()
        } catch (e: Exception) {
            "Unknown"
        }
    }

    suspend fun probeUrl(url: String, cookieFile: File? = null): Result<VideoProbeResult> =
        withContext(Dispatchers.IO) {
            try {
                val instance = backendInstance ?: throw IllegalStateException("Backend engine not loaded")
                val cookiePath = cookieFile?.takeIf { it.exists() }?.absolutePath
                val jsonStr = instance.callAttr("probe_url", url, cookiePath).toString()
                val json = JSONObject(jsonStr)

                if (json.optString("status") == "success") {
                    val resolutions = mutableListOf<String>()
                    val resArray = json.optJSONArray("available_resolutions")
                    if (resArray != null) {
                        for (i in 0 until resArray.length()) {
                            resolutions.add(resArray.getString(i))
                        }
                    }

                    Result.success(
                        VideoProbeResult(
                            url = json.optString("url", url),
                            title = json.optString("title", "Unknown Title"),
                            uploader = json.optString("uploader", "Unknown Uploader"),
                            thumbnail = json.optString("thumbnail", ""),
                            durationSeconds = json.optLong("duration", 0L),
                            isPlaylist = json.optBoolean("is_playlist", false),
                            availableResolutions = resolutions.ifEmpty { listOf("Best", "1080p", "720p", "480p", "360p") }
                        )
                    )
                } else {
                    Result.failure(Exception(json.optString("message", "Probe extraction failed")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error probing URL: $url", e)
                Result.failure(e)
            }
        }

    suspend fun downloadMedia(
        item: DownloadItem,
        outputDir: File,
        cookieFile: File? = null,
        onProgress: (percent: Float, downloadedBytes: Long, totalBytes: Long, speed: Long, eta: Long) -> Unit,
        isCancelled: () -> Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val instance = backendInstance ?: throw IllegalStateException("Backend engine not loaded")
            val formatStr = if (item.formatType == MediaFormatType.AUDIO) "audio" else "video"
            val cookiePath = cookieFile?.takeIf { it.exists() }?.absolutePath

            // Progress callback proxy
            val progressCallback: (String) -> Unit = { eventJson ->
                try {
                    val obj = JSONObject(eventJson)
                    if (obj.optString("status") == "downloading") {
                        val percent = obj.optDouble("percent", 0.0).toFloat()
                        val downloaded = obj.optLong("downloaded_bytes", 0L)
                        val total = obj.optLong("total_bytes", 0L)
                        val speed = obj.optLong("speed_bytes_per_sec", 0L)
                        val eta = obj.optLong("eta_seconds", 0L)
                        onProgress(percent, downloaded, total, speed, eta)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing progress hook event", e)
                }
            }

            val cancelCallback: () -> Boolean = {
                isCancelled()
            }

            val resultJsonStr = instance.callAttr(
                "download",
                item.url,
                outputDir.absolutePath,
                formatStr,
                item.resolution,
                cookiePath,
                progressCallback,
                cancelCallback
            ).toString()

            val resObj = JSONObject(resultJsonStr)
            when (resObj.optString("status")) {
                "completed" -> {
                    val filePath = resObj.getString("file_path")
                    Result.success(filePath)
                }
                "cancelled" -> {
                    Result.failure(Exception("Download cancelled by user"))
                }
                else -> {
                    Result.failure(Exception(resObj.optString("message", "Download failed")))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during media download", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "PythonEngineManager"

        @Volatile
        private var instance: PythonEngineManager? = null

        fun getInstance(context: Context): PythonEngineManager {
            return instance ?: synchronized(this) {
                instance ?: PythonEngineManager(context.applicationContext).also { instance = it }
            }
        }

        private fun ensurePythonStarted(context: Context) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(context))
            }
        }
    }
}
