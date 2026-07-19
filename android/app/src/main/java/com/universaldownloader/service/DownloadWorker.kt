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
        val formatsJson = inputData.getString("formats_json") ?: "{}"
        val forceAudio = inputData.getBoolean("force_audio", false)
        
        val app = applicationContext as DownloaderApp
        val repository = app.downloadRepository
        
        val formatsMap = mutableMapOf<String, String>()
        try {
            val json = org.json.JSONObject(formatsJson)
            json.keys().forEach { key ->
                formatsMap[key] = json.getString(key)
            }
        } catch (_: Exception) {}

        // Show foreground notification
        setForeground(getForegroundInfo())

        try {
            var settings = app.settingsRepository.getCurrentSettings()
            if (forceAudio) {
                settings = settings.copy(audioOnly = true)
            }
            repository.startDownload(linksText, settings, this, formatsMap)
            Result.success()
        } catch (e: Exception) {
            app.downloadEngine.addLog("❌ Worker failed: ${e.message}", LogTag.ERROR)
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationHelper.createNotificationChannel(applicationContext)
        val notification = NotificationHelper.buildProgressNotification(
            applicationContext, 
            "Background Download", 
            "Processing URLs...", 
            "", 
            0
        )
        // Note: For Android 14+, you must specify the foreground service type in the manifest for the worker too.
        // But for simplicity, we use the standard NOTIFICATION_ID.
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }
}
