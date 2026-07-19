package com.universaldownloader.service

import android.content.Context
import androidx.work.*
import com.universaldownloader.DownloaderApp
import com.universaldownloader.data.model.LogTag
import com.universaldownloader.data.repository.DownloadSettings
import com.universaldownloader.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val linksText = inputData.getString("links_text") ?: return@withContext Result.failure()
        val settingsJson = inputData.getString("settings_json") ?: return@withContext Result.failure()
        
        // Parse settings (Simplified for now, should ideally use a proper JSON parser or separate fields)
        val app = applicationContext as DownloaderApp
        val repository = app.downloadRepository
        val settings = app.settingsRepository.downloadSettings // Use current settings or pass via Data
        
        // Create initial notification
        setForeground(getForegroundInfo())

        try {
            // We need a way to run startDownload without needing a specific CoroutineScope passed in, 
            // as startDownload currently takes one.
            // Actually, startDownload should ideally manage its own scope or use the one provided.
            // In Worker, we use the Worker's scope.
            
            // Re-fetching latest settings from repository flow (collect first)
            // For simplicity, let's assume we use what's in the inputData or just the current ones.
            
            // NOTE: Repository.startDownload needs to be adapted or called with this scope.
            repository.startDownload(linksText, app.settingsRepository.getCurrentSettings(), this)
            Result.success()
        } catch (e: Exception) {
            app.downloadEngine.addLog("❌ Worker failed: ${e.message}", LogTag.ERROR)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationHelper.createNotificationChannel(applicationContext)
        val notification = NotificationHelper.buildProgressNotification(
            applicationContext, "Background Download", "Starting...", "", 0
        )
        return ForegroundInfo(NotificationHelper.NOTIFICATION_ID, notification)
    }
}
