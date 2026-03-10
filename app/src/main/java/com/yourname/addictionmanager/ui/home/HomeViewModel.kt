package com.yourname.addictionmanager.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yourname.addictionmanager.data.db.AppDatabase
import com.yourname.addictionmanager.data.db.UsageLimitEntity
import com.yourname.addictionmanager.utils.UsageStatsHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class LegendItem(val name: String, val usage: String, val color: Int)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)

    private val _totalScreenTimeString = MutableLiveData<String>()
    val totalScreenTimeString: LiveData<String> = _totalScreenTimeString

    private val _usageStatusPhrase = MutableLiveData<String>()
    val usageStatusPhrase: LiveData<String> = _usageStatusPhrase

    private val _ringSegments = MutableLiveData<List<Float>>()
    val ringSegments: LiveData<List<Float>> = _ringSegments

    private val _legendItems = MutableLiveData<List<LegendItem>>()
    val legendItems: LiveData<List<LegendItem>> = _legendItems

    private val _usageLimit = MutableLiveData<UsageLimitEntity?>()
    val usageLimit: LiveData<UsageLimitEntity?> = _usageLimit

    fun loadUsageData() {
        viewModelScope.launch {
            db.usageLimitDao().observeLimit().collectLatest { limit ->
                _usageLimit.postValue(limit)
                updateStatusPhrase()
            }
        }

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            if (UsageStatsHelper.hasUsagePermission(context)) {
                val allUsage = UsageStatsHelper.getAddictionAppUsage(context, UsageStatsHelper.TimePeriod.DAY)

                val top4 = allUsage.take(4)
                val others = allUsage.drop(4)
                val othersUsage = others.sumOf { it.second }

                val processedUsage = top4.toMutableList()
                if (others.isNotEmpty()) {
                    processedUsage.add(Pair("Others", othersUsage))
                }

                val totalUsage = processedUsage.sumOf { it.second }
                _totalScreenTimeString.postValue(formatMilliseconds(totalUsage))

                _ringSegments.postValue(processedUsage.map { it.second.toFloat() })

                val pm = context.packageManager
                val legend = processedUsage.mapIndexed { index, (packageName, usage) ->
                    val name = if (packageName == "Others") "Others" else {
                        try { pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString() } catch (e: Exception) { packageName }
                    }
                    LegendItem(name, formatMilliseconds(usage), 0)
                }
                _legendItems.postValue(legend)
                
                updateStatusPhrase()
            }
        }
    }

    private fun updateStatusPhrase() {
        val context = getApplication<Application>().applicationContext
        val totalUsageMillis = UsageStatsHelper.getTodayScreenTime(context)
        val limit = _usageLimit.value

        if (limit == null || !limit.enabled || limit.totalMinutesLimit <= 0) {
            _usageStatusPhrase.postValue("Tracking your digital journey...")
            return
        }

        val limitMillis = limit.totalMinutesLimit * 60 * 1000L
        val percent = (totalUsageMillis.toFloat() / limitMillis) * 100

        val phrase = when {
            percent < 30 -> "Great start! You're well within your limit."
            percent < 60 -> "Steady progress. Halfway to your limit."
            percent < 85 -> "Heads up! You're approaching your daily goal."
            percent < 100 -> "Almost there! Wind down soon."
            else -> "Limit exceeded. Time for a break!"
        }
        _usageStatusPhrase.postValue(phrase)
    }

    private fun formatMilliseconds(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return "${hours}h ${minutes}m"
    }
}
