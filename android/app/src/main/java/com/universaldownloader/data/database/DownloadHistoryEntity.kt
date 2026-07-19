package com.universaldownloader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val filePath: String?,
    val thumbnailUri: String?,
    val fileSize: Long,
    val duration: Double,
    val quality: String,
    val timestamp: Long = System.currentTimeMillis()
)
