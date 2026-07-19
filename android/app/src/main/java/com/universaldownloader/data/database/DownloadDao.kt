package com.universaldownloader.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DownloadHistoryEntity)

    @Delete
    suspend fun delete(entry: DownloadHistoryEntity)

    @Query("DELETE FROM download_history")
    suspend fun deleteAll()
}
