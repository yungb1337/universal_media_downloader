package com.universal.downloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.universal.downloader.engine.PythonEngineManager
import com.universal.downloader.model.DownloadItem
import com.universal.downloader.model.DownloadStatus
import com.universal.downloader.model.EngineStatus
import com.universal.downloader.model.MediaFormatType
import com.universal.downloader.model.VideoProbeResult
import com.universal.downloader.service.DownloadService
import com.universal.downloader.updater.EngineUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val engineManager = PythonEngineManager.getInstance(application)
    private val updater = EngineUpdater(application)

    private val _inputUrl = MutableStateFlow("")
    val inputUrl: StateFlow<String> = _inputUrl.asStateFlow()

    private val _isProbing = MutableStateFlow(false)
    val isProbing: StateFlow<Boolean> = _isProbing.asStateFlow()

    private val _probedInfo = MutableStateFlow<VideoProbeResult?>(null)
    val probedInfo: StateFlow<VideoProbeResult?> = _probedInfo.asStateFlow()

    private val _selectedFormat = MutableStateFlow(MediaFormatType.VIDEO)
    val selectedFormat: StateFlow<MediaFormatType> = _selectedFormat.asStateFlow()

    private val _selectedResolution = MutableStateFlow("1080p")
    val selectedResolution: StateFlow<String> = _selectedResolution.asStateFlow()

    val engineStatus: StateFlow<EngineStatus> = updater.status

    val downloads: StateFlow<List<DownloadItem>> = _downloadsFlow

    fun setInputUrl(url: String) {
        _inputUrl.value = url
    }

    fun setSelectedFormat(format: MediaFormatType) {
        _selectedFormat.value = format
    }

    fun setSelectedResolution(resolution: String) {
        _selectedResolution.value = resolution
    }

    fun probeCurrentUrl() {
        val url = _inputUrl.value.trim()
        if (url.isEmpty()) return

        viewModelScope.launch {
            _isProbing.value = true
            val cookieFile = File(getApplication<Application>().filesDir, "cookies.txt").takeIf { it.exists() }
            val result = engineManager.probeUrl(url, cookieFile)
            _isProbing.value = false

            result.onSuccess { info ->
                _probedInfo.value = info
                if (info.availableResolutions.isNotEmpty()) {
                    _selectedResolution.value = info.availableResolutions.first()
                }
            }.onFailure {
                // If probe fails, create fallback dummy probe so user can still attempt direct download
                _probedInfo.value = VideoProbeResult(
                    url = url,
                    title = "Media from link",
                    uploader = "Web",
                    thumbnail = "",
                    durationSeconds = 0,
                    isPlaylist = false,
                    availableResolutions = listOf("Best", "1080p", "720p", "480p", "360p")
                )
            }
        }
    }

    fun startDownloadWithCurrentProbe() {
        val probe = _probedInfo.value ?: return
        val newItem = DownloadItem(
            url = probe.url,
            title = probe.title,
            uploader = probe.uploader,
            thumbnail = probe.thumbnail,
            durationSeconds = probe.durationSeconds,
            formatType = _selectedFormat.value,
            resolution = _selectedResolution.value,
            status = DownloadStatus.QUEUED
        )
        addDownloadItem(newItem)
        _probedInfo.value = null
        _inputUrl.value = ""
        DownloadService.startDownload(getApplication(), newItem.id)
    }

    fun addDirectUrls(urls: List<String>, format: MediaFormatType, resolution: String) {
        urls.forEach { rawUrl ->
            val clean = rawUrl.trim()
            if (clean.isNotEmpty()) {
                val newItem = DownloadItem(
                    url = clean,
                    title = "Downloading $clean",
                    formatType = format,
                    resolution = resolution,
                    status = DownloadStatus.QUEUED
                )
                addDownloadItem(newItem)
                DownloadService.startDownload(getApplication(), newItem.id)
            }
        }
    }

    fun cancelDownload(itemId: String) {
        cancelledItemIds[itemId] = true
        updateItemStatus(itemId, DownloadStatus.CANCELLED)
    }

    fun retryDownload(itemId: String) {
        cancelledItemIds.remove(itemId)
        val item = _queue.find { it.id == itemId } ?: return
        updateItemStatus(itemId, DownloadStatus.QUEUED)
        DownloadService.startDownload(getApplication(), item.id)
    }

    fun removeDownload(itemId: String) {
        cancelledItemIds[itemId] = true
        _queue.removeAll { it.id == itemId }
        _downloadsFlow.value = _queue.toList()
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            updater.checkForUpdates()
        }
    }

    fun applyUpdate(targetVersion: String) {
        viewModelScope.launch {
            updater.downloadAndApplyUpdate(targetVersion)
        }
    }

    fun resetEngine() {
        viewModelScope.launch {
            updater.resetToBundledEngine()
        }
    }

    fun saveCookies(cookieContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val cookieFile = File(getApplication<Application>().filesDir, "cookies.txt")
            cookieFile.writeText(cookieContent)
        }
    }

    fun hasCookies(): Boolean {
        val cookieFile = File(getApplication<Application>().filesDir, "cookies.txt")
        return cookieFile.exists() && cookieFile.length() > 0
    }

    fun clearCookies() {
        val cookieFile = File(getApplication<Application>().filesDir, "cookies.txt")
        if (cookieFile.exists()) {
            cookieFile.delete()
        }
    }

    companion object {
        private val _queue = mutableListOf<DownloadItem>()
        private val _downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
        private val cancelledItemIds = ConcurrentHashMap<String, Boolean>()

        val activeQueue: List<DownloadItem>
            get() = synchronized(_queue) { _queue.toList() }

        fun isItemCancelled(id: String): Boolean {
            return cancelledItemIds[id] == true
        }

        fun addDownloadItem(item: DownloadItem) {
            synchronized(_queue) {
                _queue.add(0, item)
                _downloadsFlow.value = _queue.toList()
            }
        }

        fun updateItemStatus(id: String, status: DownloadStatus) {
            synchronized(_queue) {
                val idx = _queue.indexOfFirst { it.id == id }
                if (idx != -1) {
                    _queue[idx] = _queue[idx].copy(status = status)
                    _downloadsFlow.value = _queue.toList()
                }
            }
        }

        fun updateItemProgress(id: String, progress: Float, downloaded: Long, total: Long, speed: Long, eta: Long) {
            synchronized(_queue) {
                val idx = _queue.indexOfFirst { it.id == id }
                if (idx != -1) {
                    _queue[idx] = _queue[idx].copy(
                        progress = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        downloadSpeedBytesPerSec = speed,
                        etaSeconds = eta,
                        status = DownloadStatus.DOWNLOADING
                    )
                    _downloadsFlow.value = _queue.toList()
                }
            }
        }

        fun updateItemCompleted(id: String, outputPath: String) {
            synchronized(_queue) {
                val idx = _queue.indexOfFirst { it.id == id }
                if (idx != -1) {
                    _queue[idx] = _queue[idx].copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 100f,
                        outputPath = outputPath
                    )
                    _downloadsFlow.value = _queue.toList()
                }
            }
        }

        fun updateItemFailed(id: String, errorMessage: String) {
            synchronized(_queue) {
                val idx = _queue.indexOfFirst { it.id == id }
                if (idx != -1) {
                    _queue[idx] = _queue[idx].copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = errorMessage
                    )
                    _downloadsFlow.value = _queue.toList()
                }
            }
        }
    }
}
