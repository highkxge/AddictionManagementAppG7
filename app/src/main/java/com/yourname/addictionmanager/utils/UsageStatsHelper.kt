package com.yourname.addictionmanager.utils

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Process
import java.util.Calendar
import java.util.concurrent.TimeUnit

object UsageStatsHelper {

    enum class TimePeriod {
        DAY, WEEK, MONTH
    }

    fun hasUsagePermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getTodayScreenTime(context: Context): Long {
        return getAddictionAppUsage(context, TimePeriod.DAY).sumOf { it.second }
    }

    fun getTodayAppUsage(context: Context, packageName: String): Long {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        return stats[packageName]?.totalTimeInForeground ?: 0L
    }

    // Helper used by Accessibility Service to identify target apps
    fun isAddictionApp(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            
            val isAddictiveCategory = appInfo.category == ApplicationInfo.CATEGORY_SOCIAL ||
                    appInfo.category == ApplicationInfo.CATEGORY_VIDEO ||
                    appInfo.category == ApplicationInfo.CATEGORY_GAME
            
            val isCommonAddictiveApp = packageName in listOf(
                "com.google.android.youtube",
                "com.android.chrome",
                "com.google.android.apps.maps",
                "com.google.android.googlequicksearchbox"
            )
            
            isAddictiveCategory || isCommonAddictiveApp
        } catch (e: Exception) {
            false
        }
    }

    fun getAddictionAppUsage(context: Context, timePeriod: TimePeriod): List<Pair<String, Long>> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        
        when (timePeriod) {
            TimePeriod.DAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
            TimePeriod.MONTH -> calendar.add(Calendar.MONTH, -1)
        }
        val startTime = calendar.timeInMillis

        return getFilteredAppUsage(context, startTime, endTime) { appInfo ->
            isAddictionApp(context, appInfo.packageName)
        }
    }

    fun getAllAppUsage(context: Context, timePeriod: TimePeriod): List<Pair<String, Long>> {
        val calendar = Calendar.getInstance()
        val endTime = calendar.timeInMillis
        when (timePeriod) {
            TimePeriod.DAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
            }
            TimePeriod.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
            TimePeriod.MONTH -> calendar.add(Calendar.MONTH, -1)
        }
        return getFilteredAppUsage(context, calendar.timeInMillis, endTime) { true }
    }

    fun getUsagePoints(context: Context, startTime: Long, endTime: Long): Pair<List<Float>, List<String>> {
        val diff = endTime - startTime
        val isSingleDay = diff <= TimeUnit.DAYS.toMillis(1) + 1000L

        return if (isSingleDay) {
            val points = getHourlyUsage(context, startTime, endTime)
            val labels = (0..23).map { String.format("%02d:00", it) }
            Pair(points, labels)
        } else {
            val points = mutableListOf<Float>()
            val labels = mutableListOf<String>()
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = startTime
            
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

            while (calendar.timeInMillis < endTime) {
                val dayStart = calendar.timeInMillis
                val dayLabel = calendar.get(Calendar.DAY_OF_MONTH).toString()
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val dayEnd = calendar.timeInMillis
                
                val dailyStats = usageStatsManager.queryAndAggregateUsageStats(dayStart, dayEnd)
                val dayTotal = dailyStats.values.sumOf { it.totalTimeInForeground }.toFloat() / (1000 * 60)
                
                points.add(dayTotal)
                labels.add(dayLabel)
            }
            Pair(points, labels)
        }
    }

    fun getHourlyUsage(context: Context, startTime: Long, endTime: Long): List<Float> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usageStatsManager.queryEvents(startTime, endTime)
        
        val hourlyData = MutableList(24) { 0f }
        val lastEventTime = mutableMapOf<String, Long>()
        val calendar = Calendar.getInstance()

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val packageName = event.packageName
            val eventTime = event.timeStamp
            val eventType = event.eventType

            if (eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastEventTime[packageName] = eventTime
            } else if (eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                val start = lastEventTime[packageName]
                if (start != null) {
                    val duration = eventTime - start
                    if (duration > 0) {
                        calendar.timeInMillis = start
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)
                        if (hour in 0..23) {
                            hourlyData[hour] += duration.toFloat() / (1000 * 60)
                        }
                    }
                    lastEventTime.remove(packageName)
                }
            }
        }
        return hourlyData
    }

    fun getFilteredAppUsage(
        context: Context,
        startTime: Long,
        endTime: Long,
        filterPredicate: (ApplicationInfo) -> Boolean
    ): List<Pair<String, Long>> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager
        val stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
        val result = mutableMapOf<String, Long>()

        for ((packageName, usage) in stats) {
            if (usage.totalTimeInForeground < 1000) continue
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                if (pm.getLaunchIntentForPackage(packageName) == null) continue
                
                if (filterPredicate(appInfo)) {
                    result[packageName] = (result[packageName] ?: 0L) + usage.totalTimeInForeground
                }
            } catch (_: Exception) {}
        }
        return result.toList().sortedByDescending { it.second }
    }
}
