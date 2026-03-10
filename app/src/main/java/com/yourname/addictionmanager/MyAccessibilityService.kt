package com.yourname.addictionmanager

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.yourname.addictionmanager.camera.WarningState
import com.yourname.addictionmanager.data.PasswordManager
import com.yourname.addictionmanager.data.db.AppDatabase
import com.yourname.addictionmanager.ui.password.AppLockActivity
import com.yourname.addictionmanager.utils.UsageStatsHelper
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class MyAccessibilityService : AccessibilityService() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private lateinit var db: AppDatabase
    private lateinit var passwordManager: PasswordManager

    private var currentForegroundPackage: String? = null
    private var liveAppUsageMillis: Long = 0L
    private var liveTotalUsageMillis: Long = 0L
    private var stopwatchJob: Job? = null
    
    private val notifiedThresholds = mutableMapOf<String, MutableSet<Int>>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        db = AppDatabase.get(this)
        passwordManager = PasswordManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == null) return

        val packageName = event.packageName.toString()
        val eventType = event.eventType

        if (packageName == this.packageName) return // Ignore our own app

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            scope.launch {
                val globalConfig = db.usageLimitDao().getOnce()
                if (globalConfig?.enabled != true) return@launch

                val foregroundPackageName = getForegroundPackageName() ?: packageName

                if (foregroundPackageName != currentForegroundPackage) {
                    // Logic to reset the one-time unlock state:
                    // If we switch to a third-party app that is NOT the one we just unlocked,
                    // we reset the state so the next time they open the locked app, it asks for PIN again.
                    if (foregroundPackageName != this@MyAccessibilityService.packageName && 
                        foregroundPackageName != WarningState.unlockedPackage) {
                        WarningState.unlockedPackage = null
                    }
                    
                    currentForegroundPackage = foregroundPackageName

                    if (currentForegroundPackage != null && currentForegroundPackage != this@MyAccessibilityService.packageName) {
                        startOrUpdateStopwatch(currentForegroundPackage!!)
                    } else {
                        stopStopwatch()
                    }
                }
            }
        }
    }

    private fun getForegroundPackageName(): String? {
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val interval = UsageStatsManager.INTERVAL_BEST
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(interval, currentTime - 5000, currentTime)
        
        var foregroundPackage: String? = null
        var lastEventTime = 0L

        if (stats != null) {
            for (stat in stats) {
                if (stat.lastTimeUsed > lastEventTime) {
                    lastEventTime = stat.lastTimeUsed
                    foregroundPackage = stat.packageName
                }
            }
        }
        return foregroundPackage
    }

    private fun startOrUpdateStopwatch(packageName: String) {
        stopStopwatch()

        stopwatchJob = scope.launch {
            val globalConfig = db.usageLimitDao().getOnce() ?: return@launch
            if (!globalConfig.enabled) return@launch

            // Fetch initial usage from system stats
            liveAppUsageMillis = UsageStatsHelper.getTodayAppUsage(this@MyAccessibilityService, packageName)
            liveTotalUsageMillis = UsageStatsHelper.getTodayScreenTime(this@MyAccessibilityService)

            while (isActive) {
                if (WarningState.isSuppressed(packageName)) {
                    delay(1000)
                    liveAppUsageMillis += 1000
                    liveTotalUsageMillis += 1000
                    continue
                }

                val appLimit = db.appLimitDao().getLimit(packageName)
                val isAddictionApp = UsageStatsHelper.isAddictionApp(this@MyAccessibilityService, packageName)

                // 1. Check for Manual Lock (timeLimit == 0) or Ultimate Lock flag
                if (appLimit != null && (appLimit.timeLimit == 0L || appLimit.ultimateLockEnabled)) {
                    launchAppLockActivity(packageName, if (appLimit.ultimateLockEnabled) "ULTIMATE_LOCK" else "MANUAL_LOCK")
                    break
                }

                // 2. Determine the relevant time limit for this app
                val currentAppLimitMinutes = when {
                    appLimit != null && appLimit.timeLimit > 0 -> appLimit.timeLimit.toInt()
                    isAddictionApp -> globalConfig.minutesLimit
                    else -> -1
                }

                if (currentAppLimitMinutes != -1) {
                    val limitMillis = TimeUnit.MINUTES.toMillis(currentAppLimitMinutes.toLong())
                    
                    // Trigger Lock Screen directly when limit reached
                    if (liveAppUsageMillis >= limitMillis) {
                        launchAppLockActivity(packageName, "LIMIT_REACHED")
                        break
                    }

                    // Check Threshold Notifications (50% and 90%)
                    if (globalConfig.notifications) {
                        checkThresholds(packageName, liveAppUsageMillis, limitMillis)
                    }
                }

                // 3. Check Global Total Screen Time Limit
                if (globalConfig.lockApps) {
                    val totalLimitMillis = TimeUnit.MINUTES.toMillis(globalConfig.totalMinutesLimit.toLong())
                    if (liveTotalUsageMillis >= totalLimitMillis) {
                        launchAppLockActivity(packageName, "TOTAL_LIMIT_REACHED")
                        break
                    }
                }

                delay(1000)
                liveAppUsageMillis += 1000
                liveTotalUsageMillis += 1000
            }
        }
    }

    private fun checkThresholds(packageName: String, current: Long, limit: Long) {
        if (limit <= 0) return
        
        val percent = (current.toFloat() / limit.toFloat() * 100).toInt()
        val thresholds = notifiedThresholds.getOrPut(packageName) { mutableSetOf() }

        if (percent >= 90 && !thresholds.contains(90)) {
            sendThresholdNotification(packageName, "90% of your time limit for this app has been reached!")
            thresholds.add(90)
        } else if (percent >= 50 && !thresholds.contains(50)) {
            sendThresholdNotification(packageName, "50% of your time limit for this app has been reached.")
            thresholds.add(50)
        }
    }

    private fun sendThresholdNotification(packageName: String, message: String) {
        val appName = try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) { packageName }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, MainApplication.ALERT_CHANNEL_ID)
            .setContentTitle("Usage Alert: $appName")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(packageName.hashCode(), notification)
    }

    private fun stopStopwatch() {
        stopwatchJob?.cancel()
        stopwatchJob = null
    }

    private fun launchAppLockActivity(packageName: String, reason: String) {
        val intent = Intent(this, AppLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra("REASON", reason)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
