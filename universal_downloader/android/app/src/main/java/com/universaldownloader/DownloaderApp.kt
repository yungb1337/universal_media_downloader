package com.universaldownloader

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.universaldownloader.data.repository.DownloadRepository
import com.universaldownloader.data.repository.SettingsRepository
import com.universaldownloader.data.settings.AppSettings
import com.universaldownloader.engine.DownloadEngine
import com.universaldownloader.engine.PythonBridge

/**
 * Application class where we initialize dependencies and the Python runtime.
 */
class DownloaderApp : Application() {

    lateinit var appSettings: AppSettings
    lateinit var settingsRepository: SettingsRepository
    lateinit var pythonBridge: PythonBridge
    lateinit var downloadEngine: DownloadEngine
    lateinit var downloadRepository: DownloadRepository

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Chaquopy Python environment
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // 2. Initialize dependencies
        appSettings = AppSettings(this)
        settingsRepository = SettingsRepository(appSettings)
        pythonBridge = PythonBridge()
        downloadEngine = DownloadEngine(pythonBridge)
        downloadRepository = DownloadRepository(this, downloadEngine, settingsRepository)
    }
}
