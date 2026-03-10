package com.yourname.addictionmanager.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yourname.addictionmanager.MainApplication
import com.yourname.addictionmanager.data.db.AppDatabase
import com.yourname.addictionmanager.ui.password.AppLockActivity
import com.yourname.addictionmanager.utils.UsageStatsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AppMonitorService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var db: AppDatabase
    
    private val notifiedApps50 = mutableSetOf<String>()
    private val notifiedApps90 = mutableSetOf<String>()
    private val notifiedApps100 = mutableSetOf<String>()
    private var notifiedTotal80 = false
    private var notifiedTotal100 = false
    private var lastCheckDay = -1

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.get(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createPersistentNotification())

        scope.launch {
            while (true) {
                try {
                    checkUsage()
                } catch (e: Exception) {
                    Log.e("AppMonitorService", "Error checking usage", e)
                }
                delay(CHECK_INTERVAL)
            }
        }

        return START_STICKY
    }

    private suspend fun checkUsage() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (today != lastCheckDay) {
            resetDailyNotifications(today)
        }

        val limitSettings = db.usageLimitDao().getOnce() ?: return
        if (!limitSettings.enabled) return

        val context = applicationContext
        val allUsage = UsageStatsHelper.getAllAppUsage(context, UsageStatsHelper.TimePeriod.DAY)
        val addictionUsage = UsageStatsHelper.getAddictionAppUsage(context, UsageStatsHelper.TimePeriod.DAY)
        val totalUsageMillis = addictionUsage.sumOf { it.second }

        // 1. Check TOTAL Limit (based on addictive apps)
        if (limitSettings.notifications) {
            val totalLimitMillis = TimeUnit.MINUTES.toMillis(limitSettings.totalMinutesLimit.toLong())
            if (totalLimitMillis > 0) {
                if (totalUsageMillis > totalLimitMillis * 0.8 && !notifiedTotal80) {
                    sendAlertNotification("Daily Limit Nearing", "You've used 80% of your total daily screen time.")
                    notifiedTotal80 = true
                }
                if (totalUsageMillis >= totalLimitMillis && !notifiedTotal100) {
                    sendAlertNotification("Daily Limit Reached", "You've reached your total daily screen time limit.")
                    notifiedTotal100 = true
                }
            }
        }

        // 2. Check CUSTOM APP Limits & Global Per-App Limits
        val customLimits = db.appLimitDao().getAllLimits().first()
        val customLimitMap = customLimits.associate { it.packageName to it }

        for ((pkg, usage) in allUsage) {
            val appLimit = customLimitMap[pkg]
            
            val appLimitMinutes = if (appLimit != null) {
                appLimit.timeLimit
            } else if (limitSettings.lockApps) {
                limitSettings.minutesLimit.toLong()
            } else {
                -1L
            }

            if (appLimitMinutes <= 0) continue 

            val appLimitMillis = TimeUnit.MINUTES.toMillis(appLimitMinutes)
            val appName = getAppName(pkg)

            // Notifications at 50% and 90%
            if (limitSettings.notifications) {
                if (usage > appLimitMillis * 0.5) {
                    if (!notifiedApps50.contains(pkg)) {
                        sendAlertNotification("App Limit 50%", "$appName usage has reached 50% of its daily limit.")
                        notifiedApps50.add(pkg)
                    }
                } else {
                    notifiedApps50.remove(pkg)
                }

                if (usage > appLimitMillis * 0.9) {
                    if (!notifiedApps90.contains(pkg)) {
                        sendAlertNotification("App Limit 90%", "$appName usage is at 90% of its daily limit.")
                        notifiedApps90.add(pkg)
                    }
                } else {
                    notifiedApps90.remove(pkg)
                }
            }

            // 100% Limit reached
            if (usage >= appLimitMillis) {
                if (!notifiedApps100.contains(pkg)) {
                    notifiedApps100.add(pkg)
                    
                    // Automatically turn on Ultimate Lock in the database if limit reached
                    if (appLimit != null) {
                        db.appLimitDao().setUltimateLockEnabled(pkg, true)
                    }

                    Log.d("AppMonitorService", "Limit reached for $appName. Triggering lock activity.")
                    
                    val lockIntent = Intent(this, AppLockActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, pkg)
                        putExtra("REASON", "LIMIT_REACHED")
                    }
                    startActivity(lockIntent)
                }
            } else {
                notifiedApps100.remove(pkg)
            }
        }
    }

    private fun resetDailyNotifications(today: Int) {
        lastCheckDay = today
        notifiedApps50.clear()
        notifiedApps90.clear()
        notifiedApps100.clear()
        notifiedTotal80 = false
        notifiedTotal100 = false
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.split(".").last()
        }
    }

    private fun sendAlertNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, MainApplication.ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(pkgToId(title + message), notification)
    }
    
    private fun pkgToId(pkg: String): Int = pkg.hashCode()

    private fun createPersistentNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Addiction Manager")
        .setContentText("Monitoring your digital well-being...")
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setOngoing(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "AppMonitorServiceChannel"
        private const val CHECK_INTERVAL = 10000L

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Digital Health Monitor",
                    NotificationManager.IMPORTANCE_LOW
                )
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        }
    }
}
