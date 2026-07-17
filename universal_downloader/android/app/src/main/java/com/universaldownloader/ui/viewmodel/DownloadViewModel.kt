package com.universaldownloader.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universaldownloader.data.model.DownloadSessionState
import com.universaldownloader.data.repository.DownloadRepository
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.service.DownloadService
import com.universaldownloader.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.os.IBinder

class DownloadViewModel(
    private val context: Context,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val sessionState: StateFlow<DownloadSessionState> = downloadRepository.sessionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadSessionState()
        )

    private val _linksText = MutableStateFlow("")
    val linksText: StateFlow<String> = _linksText

    private var downloadService: DownloadService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DownloadService.LocalBinder
            downloadService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            isBound = false
        }
    }

    init {
        bindToService()
        // Load initial links text if file exists
        viewModelScope.launch {
            val savedText = downloadRepository.getUpdatedLinksText()
            if (savedText != null) {
                _linksText.value = savedText
            }
        }
    }

    fun onLinksTextChange(text: String) {
        _linksText.value = text
    }

    fun startDownload(settings: DownloadSettings) {
        // Ensure we have a download directory
        val effectiveSettings = if (settings.downloadDir.isBlank()) {
            val defaultDir = FileUtils.getDefaultDownloadDir(context).absolutePath
            settings.copy(downloadDir = defaultDir)
        } else {
            settings
        }

        val intent = Intent(context, DownloadService::class.java)
        // Start foreground service
        context.startForegroundService(intent)

        viewModelScope.launch {
            // Wait slightly for service connection to establish if not already bound
            if (!isBound) {
                bindToService()
                var attempts = 0
                while (!isBound && attempts < 10) {
                    kotlinx.coroutines.delay(100)
                    attempts++
                }
            }
            downloadService?.startDownloading(_linksText.value, effectiveSettings)
        }
    }

    fun stopDownload() {
        downloadService?.stopDownloading()
    }

    fun clearLogs() {
        downloadRepository.clearLogs()
    }

    fun refreshLinksFromDisk() {
        val text = downloadRepository.getUpdatedLinksText()
        if (text != null) {
            _linksText.value = text
        }
    }

    private fun bindToService() {
        val intent = Intent(context, DownloadService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCleared() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
        super.onCleared()
    }
}
