package com.example.sleepdetector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SleepTrackingService : Service() {

    private lateinit var sleepManager: SleepDetectionManager
    private val CHANNEL_ID = "SleepTrackingChannel"
    private val NOTIFICATION_ID = 1

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sleepManager = SleepDetectionManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // This will now start the Heart Rate monitor first
        sleepManager.startListening()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sleepManager.stopListening()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Tracking Active",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Sleep Tracker")
            .setContentText("Monitoring vitals...")
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure this icon exists in your res folder
            .setOngoing(true)
            .build()
    }
}