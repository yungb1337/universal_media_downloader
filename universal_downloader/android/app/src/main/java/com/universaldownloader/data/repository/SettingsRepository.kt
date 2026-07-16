package com.universaldownloader.data.repository

import com.universaldownloader.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Snapshot of all download-related settings.
 * Replaces the desktop Config object properties.
 */
data class DownloadSettings(
    val highestRes: Boolean = false,
    val audioOnly: Boolean = false,
    val downloadDir: String = "",
    val downloadDirUri: String = "",
    val maxConcurrent: Int = 1,
    val maxRetries: Int = 3,
    val cookiesFile: String = ""
)

/**
 * Repository for reading and writing app settings.
 * Exposes a combined Flow of all settings and individual update methods.
 */
class SettingsRepository(private val settings: AppSettings) {

    /**
     * Combined flow of all download settings.
     * Uses nested combine() for type safety with 7 flows.
     */
    val downloadSettings: Flow<DownloadSettings> = combine(
        settings.highestRes,
        settings.audioOnly,
        settings.downloadDir,
        settings.downloadDirUri,
        settings.maxConcurrent,
    ) { highestRes, audioOnly, downloadDir, downloadDirUri, maxConcurrent ->
        DownloadSettings(
            highestRes = highestRes,
            audioOnly = audioOnly,
            downloadDir = downloadDir,
            downloadDirUri = downloadDirUri,
            maxConcurrent = maxConcurrent,
        )
    }.combine(settings.maxRetries) { partial, maxRetries ->
        partial.copy(maxRetries = maxRetries)
    }.combine(settings.cookiesFile) { partial, cookiesFile ->
        partial.copy(cookiesFile = cookiesFile)
    }

    suspend fun updateHighestRes(value: Boolean) = settings.setHighestRes(value)
    suspend fun updateAudioOnly(value: Boolean) = settings.setAudioOnly(value)
    suspend fun updateDownloadDir(value: String) = settings.setDownloadDir(value)
    suspend fun updateDownloadDirUri(value: String) = settings.setDownloadDirUri(value)
    suspend fun updateMaxConcurrent(value: Int) = settings.setMaxConcurrent(value)
    suspend fun updateMaxRetries(value: Int) = settings.setMaxRetries(value)
    suspend fun updateCookiesFile(value: String) = settings.setCookiesFile(value)
}
