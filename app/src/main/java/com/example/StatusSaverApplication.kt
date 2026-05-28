package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.work.*
import com.example.data.StatusSaverWorker
import java.util.concurrent.TimeUnit

class StatusSaverApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Create the notification channel required for Android Oreo and above
        createNotificationChannel()

        // Schedule WorkManager tasks for automatic background scan/saving
        scheduleBackgroundTasks()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "status_saver_channel"
            val channelName = "Status Saver Notifications"
            val descriptionText = "Notifications for auto-saved WhatsApp statuses and activity reminders"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleBackgroundTasks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // Works offline scanning local storage
            .build()

        // Periodic background worker running every 1 hour to check and save statuses
        val periodicWorkRequest = PeriodicWorkRequestBuilder<StatusSaverWorker>(
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "StatusSaverWorker",
            ExistingPeriodicWorkPolicy.KEEP, // Retain existing schedule to avoid unnecessary resets
            periodicWorkRequest
        )
    }
}
