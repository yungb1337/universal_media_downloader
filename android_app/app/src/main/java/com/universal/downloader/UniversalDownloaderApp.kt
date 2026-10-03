package com.universal.downloader

import android.app.Application
import com.universal.downloader.engine.PythonEngineManager
import com.universal.downloader.service.NotificationHelper

class UniversalDownloaderApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize Notification Channels
        NotificationHelper(this)
        // Warm up Python environment in background
        Thread {
            PythonEngineManager.getInstance(this)
        }.start()
    }
}
