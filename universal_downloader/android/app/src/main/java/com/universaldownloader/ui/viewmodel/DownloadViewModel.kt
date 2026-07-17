package com.universaldownloader.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.webkit.CookieManager
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universaldownloader.data.model.DownloadSessionState
import com.universaldownloader.data.model.PlaylistEntry
import com.universaldownloader.data.repository.DownloadRepository
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.service.DownloadService
import com.universaldownloader.util.FileUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _playlistEntries = MutableStateFlow<List<PlaylistEntry>>(emptyList())
    val playlistEntries: StateFlow<List<PlaylistEntry>> = _playlistEntries

    private val _showPlaylistDialog = MutableStateFlow(false)
    val showPlaylistDialog: StateFlow<Boolean> = _showPlaylistDialog

    private val _showLoginBrowser = MutableStateFlow(false)
    val showLoginBrowser: StateFlow<Boolean> = _showLoginBrowser

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
        val textToDownload = if (_playlistEntries.value.isNotEmpty() && _showPlaylistDialog.value) {
            // If we are coming from the dialog, only download selected
            _playlistEntries.value.filter { it.isSelected }.joinToString("\n") { it.url }
        } else {
            _linksText.value
        }

        if (textToDownload.isBlank()) return
        dismissPlaylistDialog()

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
            downloadService?.startDownloading(textToDownload, effectiveSettings)
        }
    }

    fun analyzePlaylist(settings: DownloadSettings) {
        val firstUrl = _linksText.value.lines()
            .map { it.trim() }
            .firstOrNull { it.startsWith("http") && !it.startsWith("[DONE]") }
            ?.split(Regex("\\s+"))?.get(0)

        if (firstUrl == null) {
            Toast.makeText(context, "No valid URL found to analyze", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val app = context.applicationContext as com.universaldownloader.DownloaderApp
                val entries = app.pythonBridge.getPlaylistInfo(firstUrl, settings.cookiesFile)
                _playlistEntries.value = entries
                _showPlaylistDialog.value = true
            } catch (e: Exception) {
                Toast.makeText(context, "Analysis failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun togglePlaylistEntry(url: String) {
        _playlistEntries.value = _playlistEntries.value.map {
            if (it.url == url) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun setAllPlaylistSelection(selected: Boolean) {
        _playlistEntries.value = _playlistEntries.value.map { it.copy(isSelected = selected) }
    }

    fun dismissPlaylistDialog() {
        _showPlaylistDialog.value = false
        _playlistEntries.value = emptyList()
    }

    fun openLoginBrowser() {
        _showLoginBrowser.value = true
    }

    fun dismissLoginBrowser() {
        _showLoginBrowser.value = false
    }

    fun extractAndSaveCookies(url: String, onSaved: (String) -> Unit) {
        val rawCookies = CookieManager.getInstance().getCookie(url)
        if (rawCookies.isNullOrBlank()) {
            Toast.makeText(context, "No cookies found. Please log in first.", Toast.LENGTH_SHORT).show()
            return
        }

        val netscapeContent = FileUtils.formatCookiesToNetscape(url, rawCookies)
        val file = FileUtils.saveExtractedCookies(context, netscapeContent)
        
        onSaved(file.absolutePath)
        dismissLoginBrowser()
        Toast.makeText(context, "Cookies extracted & saved!", Toast.LENGTH_SHORT).show()
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
