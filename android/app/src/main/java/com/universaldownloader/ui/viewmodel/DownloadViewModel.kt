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
import com.universaldownloader.data.database.DownloadHistoryEntity
import com.universaldownloader.data.model.DownloadSessionState
import com.universaldownloader.data.model.PlaylistEntry
import com.universaldownloader.data.repository.DownloadRepository
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.service.DownloadService
import com.universaldownloader.util.FileUtils
import com.universaldownloader.util.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(
    private val context: Context,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val db = com.universaldownloader.data.database.DownloadDatabase.getDatabase(context)

    val sessionState: StateFlow<DownloadSessionState> = downloadRepository.sessionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadSessionState()
        )

    val downloadHistory: StateFlow<List<DownloadHistoryEntity>> = db.downloadDao().getAllHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _availableSpace = MutableStateFlow(0L)
    val availableSpace: StateFlow<Long> = _availableSpace

    private val _clipboardText = MutableStateFlow<String?>(null)
    val clipboardText: StateFlow<String?> = _clipboardText

    private val _linksText = MutableStateFlow("")
    val linksText: StateFlow<String> = _linksText

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    private val _playlistEntries = MutableStateFlow<List<PlaylistEntry>>(emptyList())
    val playlistEntries: StateFlow<List<PlaylistEntry>> = _playlistEntries

    private val _selectedFormats = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedFormats: StateFlow<Map<String, String>> = _selectedFormats

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
        
        // Initial cleanup and storage check
        FileUtils.clearTempDirectory(context)
        updateAvailableSpace()
        checkClipboard()
    }

    fun updateAvailableSpace() {
        _availableSpace.value = FileUtils.getAvailableDiskSpace(context)
    }

    fun checkClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val text = item?.text?.toString()
                if (text != null && (text.contains("youtube.com") || text.contains("youtu.be") || text.contains("instagram.com"))) {
                    _clipboardText.value = text
                } else {
                    _clipboardText.value = null
                }
            }
        } catch (_: Exception) {}
    }

    fun onLinksTextChange(text: String) {
        _linksText.value = text
    }

    fun startDownload(settings: DownloadSettings, forceAudio: Boolean = false) {
        val selectedEntries = if (_playlistEntries.value.isNotEmpty() && _showPlaylistDialog.value) {
            _playlistEntries.value.filter { it.isSelected }
        } else {
            emptyList()
        }

        val textToDownload = if (selectedEntries.isNotEmpty()) {
            selectedEntries.joinToString("\n") { it.url }
        } else {
            _linksText.value
        }

        if (textToDownload.isBlank()) return
        
        val formatsMap = selectedEntries.associate { it.url to (_selectedFormats.value[it.url] ?: "auto") }
        val formatsJson = org.json.JSONObject(formatsMap).toString()

        // Pass to WorkManager for robustness
        val data = androidx.work.Data.Builder()
            .putString("links_text", textToDownload)
            .putString("formats_json", formatsJson)
            .putBoolean("force_audio", forceAudio)
            .build()

        val downloadRequest = androidx.work.OneTimeWorkRequestBuilder<com.universaldownloader.service.DownloadWorker>()
            .setInputData(data)
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
            "download_work",
            androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
            downloadRequest
        )

        dismissPlaylistDialog()
        Toast.makeText(context, "Download started in background", Toast.LENGTH_SHORT).show()
    }
    
    fun setFormatForUrl(url: String, formatId: String) {
        _selectedFormats.value = _selectedFormats.value + (url to formatId)
    }

    fun deleteHistoryEntry(entry: DownloadHistoryEntity) {
        viewModelScope.launch {
            db.downloadDao().delete(entry)
        }
    }

    fun playFile(filePath: String) {
        val intent = Intent(context, com.universaldownloader.ui.player.PlayerActivity::class.java).apply {
            putExtra("file_path", filePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
                
                // Smart Selection Logic: Default to 1080p or highest below
                val newDefaults = mutableMapOf<String, String>()
                entries.forEach { entry ->
                    val videoFormats = entry.formats.filter { it.type == "video" }
                    if (videoFormats.isNotEmpty()) {
                        // Find exactly 1080p
                        val p1080 = videoFormats.find { 
                            it.resolution.contains("1080")
                        }
                        
                        if (p1080 != null) {
                            newDefaults[entry.url] = p1080.formatId
                        } else {
                            // Find highest below 1080p
                            val highestBelow = videoFormats
                                .filter { format ->
                                    val heightStr = format.resolution.takeWhile { it.isDigit() }
                                    val height = heightStr.toIntOrNull() ?: 0
                                    height < 1080
                                }
                                .maxByOrNull { format ->
                                    val heightStr = format.resolution.takeWhile { it.isDigit() }
                                    heightStr.toIntOrNull() ?: 0
                                }
                            
                            if (highestBelow != null) {
                                newDefaults[entry.url] = highestBelow.formatId
                            } else {
                                // Fallback to first video format if all else fails
                                newDefaults[entry.url] = videoFormats.first().formatId
                            }
                        }
                    } else {
                        newDefaults[entry.url] = "auto"
                    }
                }
                
                _selectedFormats.value = newDefaults
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
        // 1. Cancel WorkManager job (This stops the Worker and its coroutines)
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("download_work")
        
        // 2. Also signal internal engine directly for immediate UI response
        downloadRepository.stopDownload()
        
        Toast.makeText(context, "Download stopping...", Toast.LENGTH_SHORT).show()
    }

    fun clearHistory() {
        viewModelScope.launch {
            db.downloadDao().deleteAll()
        }
    }

    fun clearCache() {
        FileUtils.clearTempDirectory(context)
        updateAvailableSpace()
        Toast.makeText(context, "Temporary cache cleared!", Toast.LENGTH_SHORT).show()
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
