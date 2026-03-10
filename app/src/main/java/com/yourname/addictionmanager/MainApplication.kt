package com.yourname.addictionmanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.yourname.addictionmanager.services.AppMonitorService

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppMonitorService.createNotificationChannel(this)
        createAlertNotificationChannel(this)
    }

    private fun createAlertNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Usage Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ALERT_CHANNEL_ID = "UsageAlertChannel"
    }
}
