package com.example.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import kotlinx.coroutines.tasks.await

class StatusSaverWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val repository = StatusRepository(context)
            val db = AppDatabase.getDatabase(context)
            val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val firestore = try { com.google.firebase.firestore.FirebaseFirestore.getInstance() } catch (e: Exception) { null }
            val storage = try { com.google.firebase.storage.FirebaseStorage.getInstance() } catch (e: Exception) { null }

            // 1. Scan for standard and business WhatsApp statuses
            val standardStatuses = repository.getStatuses(isBusiness = false)
            val businessStatuses = repository.getStatuses(isBusiness = true)
            val allScannedStatuses = standardStatuses + businessStatuses

            // 2. Retrieve already saved statuses list
            val savedStatuses = repository.getSavedStatuses()
            val savedNames = savedStatuses.map { it.name }.toSet()

            // 3. Filter for truly new statuses we haven't auto-saved yet
            val newStatuses = allScannedStatuses.filter { it.name !in savedNames }

            var savedCount = 0
            val mobile = prefs.getString("user_mobile", "Unknown") ?: "Unknown"

            for (status in newStatuses) {
                val savedFile = repository.copyStatusToAppDir(status.uri, status.name)
                if (savedFile != null) {
                    savedCount++
                    var mediaUrl: String? = null
                    
                    // Upload file to Firebase Storage in background worker if possible
                    if (savedFile.exists() && storage != null) {
                        try {
                            val storageRef = storage.reference.child("saved_statuses/$mobile/${status.name}")
                            val fileUri = Uri.fromFile(savedFile)
                            storageRef.putFile(fileUri).await()
                            mediaUrl = storageRef.downloadUrl.await().toString()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Log locally in SQLite Room DB
                    try {
                        db.downloadLogDao().insertLog(
                            DownloadLog(mobileNumber = mobile, isVideo = status.isVideo, fileName = status.name, mediaUrl = mediaUrl)
                        )
                        
                        // Sync logs to Cloud Firestore if connected
                        val logData = hashMapOf(
                            "mobileNumber" to mobile,
                            "isVideo" to status.isVideo,
                            "fileName" to status.name,
                            "timestamp" to System.currentTimeMillis()
                        )
                        if (mediaUrl != null) {
                            logData["mediaUrl"] = mediaUrl
                        }
                        firestore?.collection("downloads")?.add(logData)?.await()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // Show confirmation notification if new statuses are saved automatically
            if (savedCount > 0) {
                showAutoSaveNotification(context, savedCount)
            }

            // 4. Handle inactivity reminder notification (if app is unopened for 24 hours)
            val lastOpenTime = prefs.getLong("last_app_open_time", 0L)
            if (lastOpenTime > 0) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastOpenTime >= 24 * 60 * 60 * 1000) {
                    val lastReminderTime = prefs.getLong("last_inactivity_notification_time", 0L)
                    // Check if we haven't displayed inactivity notification today to avoid spamming
                    if (currentTime - lastReminderTime >= 24 * 60 * 60 * 1000) {
                        showInactivityNotification(context)
                        prefs.edit().putLong("last_inactivity_notification_time", currentTime).apply()
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    private fun showAutoSaveNotification(context: Context, count: Int) {
        val channelId = "status_saver_channel"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or getImmutableFlag()
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("⚡ Auto-Saved New Statuses!")
            .setContentText("Successfully auto-saved $count new WhatsApp status(es) in background! 😍")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(1001, notification)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun showInactivityNotification(context: Context) {
        val channelId = "status_saver_channel"
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or getImmutableFlag()
        )

        val title = "⏰ Status updates are disappearing!"
        val message = "Your friends' whatsapp statuses are active now. Don't miss out on saving them! Tap to open. 😉📱"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.star_on)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                notificationManager.notify(1002, notification)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    private fun getImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }
}
