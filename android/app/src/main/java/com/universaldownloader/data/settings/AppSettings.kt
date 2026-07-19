package com.universaldownloader.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * App settings backed by Preferences DataStore.
 * Replaces the desktop .env file + Config singleton.
 *
 * Mapping from desktop .env variables:
 *   HIGHEST_RES              → highestRes  (Boolean)
 *   DOWNLOAD_AUDIO_ONLY      → audioOnly   (Boolean)
 *   DOWNLOAD_DIR             → downloadDir (String path)
 *   MAX_CONCURRENT_DOWNLOADS → maxConcurrent (Int)
 *   MAX_RETRIES              → maxRetries  (Int)
 *   COOKIES_FILE             → cookiesFile (String path)
 */

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppSettings(private val context: Context) {

    companion object {
        val HIGHEST_RES = booleanPreferencesKey("highest_res")
        val AUDIO_ONLY = booleanPreferencesKey("audio_only")
        val DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        val DOWNLOAD_DIR_URI = stringPreferencesKey("download_dir_uri")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        val MAX_RETRIES = intPreferencesKey("max_retries")
        val COOKIES_FILE = stringPreferencesKey("cookies_file")
        val AUDIO_FORMAT = stringPreferencesKey("audio_format")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")

        // Defaults matching the desktop .env.example
        const val DEFAULT_MAX_CONCURRENT = 1
        const val DEFAULT_MAX_RETRIES = 3
    }

    val highestRes: Flow<Boolean> = context.dataStore.data.map {
        it[HIGHEST_RES] ?: false
    }

    val audioOnly: Flow<Boolean> = context.dataStore.data.map {
        it[AUDIO_ONLY] ?: false
    }

    val audioFormat: Flow<String> = context.dataStore.data.map {
        it[AUDIO_FORMAT] ?: "m4a"
    }

    val audioQuality: Flow<String> = context.dataStore.data.map {
        it[AUDIO_QUALITY] ?: "320"
    }

    val downloadDir: Flow<String> = context.dataStore.data.map {
        it[DOWNLOAD_DIR] ?: ""
    }

    val downloadDirUri: Flow<String> = context.dataStore.data.map {
        it[DOWNLOAD_DIR_URI] ?: ""
    }

    val maxConcurrent: Flow<Int> = context.dataStore.data.map {
        it[MAX_CONCURRENT] ?: DEFAULT_MAX_CONCURRENT
    }

    val maxRetries: Flow<Int> = context.dataStore.data.map {
        it[MAX_RETRIES] ?: DEFAULT_MAX_RETRIES
    }

    val cookiesFile: Flow<String> = context.dataStore.data.map {
        it[COOKIES_FILE] ?: ""
    }

    suspend fun setHighestRes(value: Boolean) {
        context.dataStore.edit { it[HIGHEST_RES] = value }
    }

    suspend fun setAudioOnly(value: Boolean) {
        context.dataStore.edit { it[AUDIO_ONLY] = value }
    }

    suspend fun setDownloadDir(value: String) {
        context.dataStore.edit { it[DOWNLOAD_DIR] = value }
    }

    suspend fun setDownloadDirUri(value: String) {
        context.dataStore.edit { it[DOWNLOAD_DIR_URI] = value }
    }

    suspend fun setMaxConcurrent(value: Int) {
        context.dataStore.edit { it[MAX_CONCURRENT] = value.coerceIn(1, 16) }
    }

    suspend fun setMaxRetries(value: Int) {
        context.dataStore.edit { it[MAX_RETRIES] = value.coerceIn(1, 20) }
    }

    suspend fun setCookiesFile(value: String) {
        context.dataStore.edit { it[COOKIES_FILE] = value }
    }

    suspend fun setAudioFormat(value: String) {
        context.dataStore.edit { it[AUDIO_FORMAT] = value }
    }

    suspend fun setAudioQuality(value: String) {
        context.dataStore.edit { it[AUDIO_QUALITY] = value }
    }
}
