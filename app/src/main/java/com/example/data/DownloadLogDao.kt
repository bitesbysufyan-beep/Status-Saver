package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadLogDao {
    @Query("SELECT * FROM download_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<DownloadLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DownloadLog)
}
