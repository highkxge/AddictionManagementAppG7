package com.yourname.addictionmanager.ui.apps

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.yourname.addictionmanager.utils.UsageStatsHelper
import java.util.concurrent.TimeUnit

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val _apps = MutableLiveData<List<AppInfo>>()
    val apps: LiveData<List<AppInfo>> = _apps

    fun loadApps() {
        val context = getApplication<Application>().applicationContext
        val pm = context.packageManager
        
        val usageData = UsageStatsHelper.getAllAppUsage(context, UsageStatsHelper.TimePeriod.DAY)
        val usageMap = usageData.associate { it.first to it.second }

        val installedApps = pm.getInstalledApplications(0)
            .filter { appInfo ->
                val pkg = appInfo.packageName
                // 1. Must have a launcher intent
                val hasLauncher = pm.getLaunchIntentForPackage(pkg) != null
                // 2. Must NOT be a protected/generic app or this app itself
                pkg != context.packageName && !isProtectedPackage(context, pkg) && hasLauncher
            }
            .distinctBy { it.packageName }
            .map { appInfo ->
                val usageMillis = usageMap[appInfo.packageName] ?: 0L
                val usageString = formatMilliseconds(usageMillis)
                AppInfo(
                    name = appInfo.loadLabel(pm).toString(),
                    packageName = appInfo.packageName,
                    icon = appInfo.loadIcon(pm),
                    usageTime = usageString
                )
            }
            .sortedByDescending { app -> usageMap[app.packageName] ?: 0L }

        _apps.value = installedApps
    }

    private fun isProtectedPackage(context: Context, pkg: String): Boolean {
        // Same list as MyAccessibilityService
        val protectedPrefixes = listOf(
            "com.android.dialer", "com.google.android.dialer",
            "com.android.contacts", "com.google.android.contacts",
            "com.android.phone", "com.android.server.telecom",
            "com.android.gallery", "com.android.gallery3d", "com.google.android.apps.photos",
            "com.android.camera", "com.google.android.GoogleCamera",
            "com.android.calculator", "com.google.android.calculator",
            "com.android.deskclock", "com.google.android.deskclock",
            "com.android.calendar", "com.google.android.calendar",
            "com.android.documentsui", "com.google.android.documentsui",
            "com.android.vending", "com.android.settings", "com.google.android.settings",
            "com.android.systemui", "android"
        )

        if (protectedPrefixes.any { pkg.startsWith(it) }) return true
        if (pkg.contains("launcher") || pkg.contains("home")) return true
        
        // Keyboards
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.inputMethodList.any { it.packageName == pkg }
        } catch (e: Exception) {
            false
        }
    }

    private fun formatMilliseconds(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
