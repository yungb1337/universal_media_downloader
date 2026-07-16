package com.universaldownloader.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.universaldownloader.DownloaderApp
import com.universaldownloader.data.model.DownloadSessionState
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service for active downloads.
 *
 * Keeps downloads alive when the app is backgrounded and shows a persistent
 * progress notification. Without this, Android would kill the download process
 * after ~1 minute in the background.
 *
 * The service delegates actual download work to DownloadRepository/DownloadEngine.
 * It only manages the foreground notification lifecycle.
 */
class DownloadService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notificationJob: Job? = null

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
    }

    /**
     * Start the download session within this foreground service.
     * Called from DownloadViewModel after binding to the service.
     */
    fun startDownloading(linksText: String, settings: DownloadSettings) {
        // Start foreground immediately with an initial notification
        val notification = NotificationHelper.buildProgressNotification(
            this, "Preparing downloads...", 0
        )
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)

        val app = application as DownloaderApp
        val repository = app.downloadRepository

        serviceScope.launch {
            // Monitor session state to update the notification
            notificationJob = launch {
                repository.sessionState.collectLatest { state ->
                    updateNotification(state)

                    // Auto-stop service when downloads finish
                    if (!state.isRunning && state.totalProcessed > 0) {
                        // Show completion notification
                        val succeeded = state.results.count { it.success }
                        val failed = state.results.count { !it.success }
                        val completionNotif = NotificationHelper.buildCompletionNotification(
                            this@DownloadService, succeeded, failed
                        )
                        NotificationHelper.updateNotification(
                            this@DownloadService, completionNotif
                        )

                        delay(500)
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                }
            }

            // Start the actual download
            repository.startDownload(linksText, settings, serviceScope)
        }
    }

    /**
     * Stop all active downloads and shut down the service.
     */
    fun stopDownloading() {
        val app = application as DownloaderApp
        app.downloadRepository.stopDownload()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(state: DownloadSessionState) {
        if (!state.isRunning) return

        val total = state.totalLinks
        val processed = state.totalProcessed
        val progress = if (total > 0) (processed * 100) / total else 0
        val title = "Downloading: $processed/$total"

        val notification = NotificationHelper.buildProgressNotification(
            this, title, progress
        )
        NotificationHelper.updateNotification(this, notification)
    }

    override fun onDestroy() {
        notificationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
