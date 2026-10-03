package com.universal.downloader.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.universal.downloader.engine.PythonEngineManager
import com.universal.downloader.model.DownloadItem
import com.universal.downloader.model.DownloadStatus
import com.universal.downloader.storage.MediaStoreManager
import com.universal.downloader.ui.viewmodel.DownloadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var engineManager: PythonEngineManager
    private lateinit var mediaStoreManager: MediaStoreManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        engineManager = PythonEngineManager.getInstance(this)
        mediaStoreManager = MediaStoreManager(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UniversalDownloader::DownloadWakeLock")
        wakeLock?.acquire(3 * 60 * 60 * 1000L) // Max 3 hours

        val initialNotification = notificationHelper.buildForegroundNotification(
            title = "Universal Downloader",
            progress = 0,
            speedText = "Starting background download service..."
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.FOREGROUND_NOTIFICATION_ID, initialNotification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_START_DOWNLOAD) {
            val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
            if (itemId != null) {
                processDownloadItem(itemId)
            }
        } else if (action == ACTION_STOP_SERVICE) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun processDownloadItem(itemId: String) {
        serviceScope.launch {
            val item = DownloadViewModel.activeQueue.find { it.id == itemId } ?: return@launch
            val tempDir = File(cacheDir, "downloads").apply { mkdirs() }
            val cookieFile = File(filesDir, "cookies.txt").takeIf { it.exists() }

            DownloadViewModel.updateItemStatus(itemId, DownloadStatus.DOWNLOADING)

            var lastNotificationUpdateTime = 0L

            val result = engineManager.downloadMedia(
                item = item,
                outputDir = tempDir,
                cookieFile = cookieFile,
                onProgress = { percent, downloaded, total, speed, eta ->
                    DownloadViewModel.updateItemProgress(itemId, percent, downloaded, total, speed, eta)

                    val now = System.currentTimeMillis()
                    if (now - lastNotificationUpdateTime > 1000L) { // Throttle notification updates to 1/sec
                        lastNotificationUpdateTime = now
                        val speedMb = speed / (1024f * 1024f)
                        val speedStr = String.format("%.1f MB/s", speedMb)
                        notificationHelper.updateProgress(
                            NotificationHelper.FOREGROUND_NOTIFICATION_ID,
                            item.title,
                            percent.toInt(),
                            "$speedStr - ${percent.toInt()}%"
                        )
                    }
                },
                isCancelled = {
                    DownloadViewModel.isItemCancelled(itemId)
                }
            )

            result.onSuccess { downloadedFilePath ->
                DownloadViewModel.updateItemStatus(itemId, DownloadStatus.PROCESSING)
                val downloadedFile = File(downloadedFilePath)

                // Save to public Android gallery/music collection
                val mediaStoreResult = mediaStoreManager.saveMediaToPublicGallery(
                    sourceFile = downloadedFile,
                    title = item.title,
                    formatType = item.formatType
                )

                mediaStoreResult.onSuccess { savedUri ->
                    DownloadViewModel.updateItemCompleted(itemId, savedUri.toString())
                    notificationHelper.notifyCompleted(item.title)
                }.onFailure { error ->
                    DownloadViewModel.updateItemFailed(itemId, "Storage Error: ${error.localizedMessage}")
                }
            }.onFailure { error ->
                if (error.message?.contains("cancelled", true) == true) {
                    DownloadViewModel.updateItemStatus(itemId, DownloadStatus.CANCELLED)
                } else {
                    DownloadViewModel.updateItemFailed(itemId, error.localizedMessage ?: "Unknown download error")
                }
            }

            // Check if queue is finished
            val hasPending = DownloadViewModel.activeQueue.any {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PROBING
            }
            if (!hasPending) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_DOWNLOAD = "com.universal.downloader.action.START_DOWNLOAD"
        const val ACTION_STOP_SERVICE = "com.universal.downloader.action.STOP_SERVICE"
        const val EXTRA_ITEM_ID = "extra_item_id"

        fun startDownload(context: Context, itemId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
