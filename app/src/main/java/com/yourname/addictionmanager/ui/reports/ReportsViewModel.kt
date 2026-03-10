package com.yourname.addictionmanager.ui.reports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.yourname.addictionmanager.utils.UsageStatsHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TimeRangeOption(val label: String, val startTime: Long, val endTime: Long)

class ReportsViewModel(application: Application) : AndroidViewModel(application) {

    private val _totalScreenTime = MutableLiveData<String>()
    val totalScreenTime: LiveData<String> = _totalScreenTime

    private val _addictionAppUsage = MutableLiveData<List<Pair<String, Long>>>()
    val addictionAppUsage: LiveData<List<Pair<String, Long>>> = _addictionAppUsage

    private val _timeRangeOptions = MutableLiveData<List<TimeRangeOption>>()
    val timeRangeOptions: LiveData<List<TimeRangeOption>> = _timeRangeOptions

    private val _hourlyUsagePoints = MutableLiveData<List<Float>>()
    val hourlyUsagePoints: LiveData<List<Float>> = _hourlyUsagePoints

    init {
        generateTimeRangeOptions()
    }

    private fun generateTimeRangeOptions() {
        val options = mutableListOf<TimeRangeOption>()
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())

        for (i in 0..6) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val label = when(i) {
                0 -> "Today"
                1 -> "Yesterday"
                else -> dayFormat.format(calendar.time)
            }
            options.add(TimeRangeOption(label, getStartOfDay(calendar).timeInMillis, getEndOfDay(calendar).timeInMillis))
        }

        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())
        for (i in 0..5) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(Calendar.MONTH, -i)
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val startTime = getStartOfDay(calendar).timeInMillis
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
            options.add(TimeRangeOption(monthFormat.format(calendar.time), startTime, getEndOfDay(calendar).timeInMillis))
        }
        _timeRangeOptions.postValue(options)
    }

    fun loadUsageDataForRange(option: TimeRangeOption) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            if (UsageStatsHelper.hasUsagePermission(context)) {
                val usage = UsageStatsHelper.getFilteredAppUsage(context, option.startTime, option.endTime) { true }
                val totalTime = usage.sumOf { it.second }
                _totalScreenTime.postValue(formatMilliseconds(totalTime))
                _addictionAppUsage.postValue(usage)

                val points = UsageStatsHelper.getHourlyUsage(context, option.startTime, option.endTime)
                _hourlyUsagePoints.postValue(points)
            }
        }
    }

    private fun getStartOfDay(calendar: Calendar) = (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun getEndOfDay(calendar: Calendar) = (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }

    private fun formatMilliseconds(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return "${hours}h ${minutes}m"
    }
}
