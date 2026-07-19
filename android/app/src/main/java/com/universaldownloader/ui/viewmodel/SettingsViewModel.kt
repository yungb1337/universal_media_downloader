package com.universaldownloader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settingsState: StateFlow<DownloadSettings> = settingsRepository.downloadSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadSettings()
        )

    fun toggleHighestRes(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateHighestRes(value)
        }
    }

    fun toggleAudioOnly(value: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateAudioOnly(value)
        }
    }

    fun updateDownloadDir(path: String, uri: String) {
        viewModelScope.launch {
            settingsRepository.updateDownloadDir(path)
            settingsRepository.updateDownloadDirUri(uri)
        }
    }

    fun updateMaxConcurrent(value: Int) {
        viewModelScope.launch {
            settingsRepository.updateMaxConcurrent(value)
        }
    }

    fun updateMaxRetries(value: Int) {
        viewModelScope.launch {
            settingsRepository.updateMaxRetries(value)
        }
    }

    fun updateCookiesFile(path: String) {
        viewModelScope.launch {
            settingsRepository.updateCookiesFile(path)
        }
    }

    fun updateAudioFormat(value: String) {
        viewModelScope.launch {
            settingsRepository.updateAudioFormat(value)
        }
    }

    fun updateAudioQuality(value: String) {
        viewModelScope.launch {
            settingsRepository.updateAudioQuality(value)
        }
    }
}
