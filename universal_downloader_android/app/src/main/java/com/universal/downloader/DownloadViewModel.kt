package com.universal.downloader

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class DownloadStatus {
    QUEUED, DOWNLOADING, COMPLETED, PAUSED, FAILED
}

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String = "Pending Metadata...",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val error: String? = null
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    // UI Configuration States
    val downloadDir = mutableStateOf(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "UniversalDownloader").absolutePath
    )
    val highestRes = mutableStateOf(false)
    val audioOnly = mutableStateOf(false)
    val maxRetries = mutableStateOf(5)
    val maxConcurrent = mutableStateOf(1)
    val cookiesFile = mutableStateOf("")

    // Log tracking for the Settings/Logs tab
    val logs = mutableStateListOf<String>()

    // Core lists for the Tab views
    val downloadQueue = mutableStateListOf<DownloadItem>()
    
    // Track active workers to allow pausing
    private var activePythonRunner: PyObject? = null
    private var isDownloadingQueue = false

    init {
        // Initialize Chaquopy Python runtime
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(application))
        }
        
        // Ensure default download directory exists
        File(downloadDir.value).mkdirs()
    }

    fun addUrlToQueue(url: String, customName: String? = null) {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return
        
        val titleText = if (!customName.isNullOrEmpty()) customName else cleanUrl
        downloadQueue.add(
            DownloadItem(
                url = cleanUrl,
                title = titleText,
                status = DownloadStatus.QUEUED
            )
        )
        logs.add("Added to queue: $cleanUrl")
    }

    fun startQueueDownloads() {
        if (isDownloadingQueue) return
        isDownloadingQueue = true
        
        viewModelScope.launch(Dispatchers.Default) {
            while (isDownloadingQueue) {
                // Find the next item that is queued or paused
                val nextItem = downloadQueue.firstOrNull { 
                    it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED 
                }
                
                if (nextItem == null) {
                    isDownloadingQueue = false
                    withContext(Dispatchers.Main) {
                        logs.add("🏁 Queue finished or empty.")
                    }
                    break
                }
                
                downloadSingleItem(nextItem)
            }
        }
    }

    private suspend fun downloadSingleItem(item: DownloadItem) {
        // Update status to Downloading
        updateItemStatus(item.id, DownloadStatus.DOWNLOADING, progress = 0)

        withContext(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val downloaderModule = py.getModule("android_downloader")
                activePythonRunner = downloaderModule // Store to cancel if requested

                // Set up the dynamic environment configurations
                val configMap = mapOf(
                    "downloads_dir" to downloadDir.value,
                    "highest_res" to highestRes.value,
                    "audio_only" to audioOnly.value,
                    "max_retries" to maxRetries.value,
                    "cookies_file" to cookiesFile.value.ifEmpty { null },
                    "ffmpeg_location" to getFFmpegPath()
                )

                // Define progress callback
                val progressCallback = { percent: Int, downloaded: Long, total: Long ->
                    viewModelScope.launch(Dispatchers.Main) {
                        updateItemProgress(item.id, percent, downloaded, total)
                    }
                }

                // Define stdout log callback
                val logCallback = { msg: String ->
                    viewModelScope.launch(Dispatchers.Main) {
                        logs.add(msg)
                    }
                }

                // Run python downloader
                val resultObj = downloaderModule.callAttr(
                    "download_single",
                    item.url,
                    configMap,
                    progressCallback,
                    logCallback
                )

                val result = resultObj
                val success = result?.get("success")?.toBoolean() ?: false
                val title = result?.get("title")?.toString() ?: item.title
                val error = result?.get("error")?.toString()
                val skipped = result?.get("skipped")?.toBoolean() ?: false
                val fileSize = result?.get("file_size")?.toLong() ?: 0L

                withContext(Dispatchers.Main) {
                    if (success) {
                        updateItemStatus(
                            item.id, 
                            DownloadStatus.COMPLETED, 
                            title = title, 
                            progress = 100, 
                            downloaded = fileSize, 
                            total = fileSize
                        )
                        logs.add("Saved file: $title")
                    } else {
                        if (error == "PAUSED") {
                            updateItemStatus(item.id, DownloadStatus.PAUSED, title = title)
                            logs.add("Paused: $title")
                        } else {
                            updateItemStatus(item.id, DownloadStatus.FAILED, title = title, error = error)
                            logs.add("Failed: $title. Error: $error")
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val errorMsg = e.message ?: "Unknown Exception"
                    if (errorMsg.contains("ABORTED") || errorMsg.contains("PAUSED")) {
                        updateItemStatus(item.id, DownloadStatus.PAUSED)
                        logs.add("Paused by user: ${item.title}")
                    } else {
                        updateItemStatus(item.id, DownloadStatus.FAILED, error = errorMsg)
                        logs.add("Engine error: $errorMsg")
                    }
                }
            } finally {
                activePythonRunner = null
            }
        }
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = downloadQueue.firstOrNull { it.id == id }
            if (item != null && item.status == DownloadStatus.DOWNLOADING) {
                val py = Python.getInstance()
                val downloaderModule = py.getModule("android_downloader")
                downloaderModule.callAttr("cancel_download")
                logs.add("Pausing download for: ${item.title}...")
            }
        }
    }

    fun stopQueue() {
        isDownloadingQueue = false
        // Trigger cancel on current download
        viewModelScope.launch(Dispatchers.IO) {
            val py = Python.getInstance()
            val downloaderModule = py.getModule("android_downloader")
            downloaderModule.callAttr("cancel_download")
            logs.add("Stopping download queue...")
        }
    }

    fun removeItem(id: String) {
        val item = downloadQueue.firstOrNull { it.id == id }
        if (item != null) {
            if (item.status == DownloadStatus.DOWNLOADING) {
                pauseDownload(id)
            }
            downloadQueue.remove(item)
            logs.add("Removed from queue: ${item.title}")
        }
    }

    private fun updateItemProgress(id: String, percent: Int, downloaded: Long, total: Long) {
        val index = downloadQueue.indexOfFirst { it.id == id }
        if (index != -1) {
            val current = downloadQueue[index]
            downloadQueue[index] = current.copy(
                progress = percent,
                downloadedBytes = downloaded,
                totalBytes = total
            )
        }
    }

    private fun updateItemStatus(
        id: String, 
        status: DownloadStatus, 
        title: String? = null, 
        progress: Int? = null,
        downloaded: Long? = null,
        total: Long? = null,
        error: String? = null
    ) {
        val index = downloadQueue.indexOfFirst { it.id == id }
        if (index != -1) {
            val current = downloadQueue[index]
            downloadQueue[index] = current.copy(
                status = status,
                title = title ?: current.title,
                progress = progress ?: current.progress,
                downloadedBytes = downloaded ?: current.downloadedBytes,
                totalBytes = total ?: current.totalBytes,
                error = error ?: current.error
            )
        }
    }

    private fun getFFmpegPath(): String {
        // FFmpeg Kit does not provide a standalone executable path.
        // You may need to bundle a standalone FFmpeg binary if yt-dlp requires it.
        // For now, returning empty string to allow compilation.
        return ""
    }
}
