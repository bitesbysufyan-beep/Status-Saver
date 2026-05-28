package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_logs")
data class DownloadLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mobileNumber: String = "Unknown",
    val isVideo: Boolean,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaUrl: String? = null
)
