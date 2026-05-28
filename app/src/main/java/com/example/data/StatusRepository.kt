package com.example.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val timestamp: Long
)

class StatusRepository(private val context: Context) {

    private val WA_STATUS_PATHS = listOf(
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses",
        "/storage/emulated/0/WhatsApp/Media/.Statuses"
    )

    private val WA_BUSINESS_STATUS_PATHS = listOf(
        "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses",
        "/storage/emulated/0/WhatsApp Business/Media/.Statuses"
    )

    suspend fun getStatuses(isBusiness: Boolean = false): List<StatusItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<StatusItem>()
        val paths = if (isBusiness) WA_BUSINESS_STATUS_PATHS else WA_STATUS_PATHS
        try {
            for (path in paths) {
                val dir = File(path)
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        if (file.isDirectory) continue
                        if (file.name.endsWith(".nomedia")) continue
                        val isVideo = file.name.endsWith(".mp4")
                        if (file.name.endsWith(".jpg") || isVideo) {
                            result.add(
                                StatusItem(
                                    uri = Uri.fromFile(file),
                                    name = file.name,
                                    isVideo = isVideo,
                                    timestamp = file.lastModified()
                                )
                            )
                        }
                    }
                }
            }
            result.sortByDescending { it.timestamp }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext result
    }

    suspend fun copyStatusToAppDir(sourceUri: Uri, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val destDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath?.replace("Android/data/${context.packageName}/files/Pictures", "Pictures/Status Saver") ?: "/storage/emulated/0/Pictures/Status Saver").apply {
                if (!exists()) mkdirs()
            }
            val destFile = File(destDir, fileName)
            if (destFile.exists() && destFile.length() > 0) {
                return@withContext destFile
            }
            val sourceFile = File(sourceUri.path ?: return@withContext null)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext destFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun saveMediaFromInputStream(inputStream: java.io.InputStream, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val destDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath?.replace("Android/data/${context.packageName}/files/Pictures", "Pictures/Status Saver") ?: "/storage/emulated/0/Pictures/Status Saver").apply {
                if (!exists()) mkdirs()
            }
            val destFile = File(destDir, fileName)
            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            return@withContext destFile
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    suspend fun getSavedStatuses(): List<StatusItem> = withContext(Dispatchers.IO) {
        val destDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath?.replace("Android/data/${context.packageName}/files/Pictures", "Pictures/Status Saver") ?: "/storage/emulated/0/Pictures/Status Saver")
        if (!destDir.exists()) return@withContext emptyList()
        val files = destDir.listFiles() ?: return@withContext emptyList()
        return@withContext files.map { file ->
            StatusItem(
                uri = Uri.fromFile(file),
                name = file.name,
                isVideo = file.name.endsWith(".mp4"),
                timestamp = file.lastModified()
            )
        }.sortedByDescending { it.timestamp }
    }
}
