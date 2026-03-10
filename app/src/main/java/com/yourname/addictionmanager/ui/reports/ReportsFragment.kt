package com.yourname.addictionmanager.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.yourname.addictionmanager.R
import com.yourname.addictionmanager.adapter.AppUsageAdapter
import com.yourname.addictionmanager.model.AppUsage
import java.util.concurrent.TimeUnit

class ReportsFragment : Fragment() {

    private val viewModel: ReportsViewModel by viewModels()

    private lateinit var totalUsageText: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var lineChart: UsageLineChartView
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_reports, container, false)
        totalUsageText = view.findViewById(R.id.total_usage_text)
        chipGroup = view.findViewById(R.id.chip_group_filter)
        lineChart = view.findViewById(R.id.usage_line_chart)
        recyclerView = view.findViewById(R.id.rv_app_details)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.timeRangeOptions.observe(viewLifecycleOwner) { options ->
            setupChips(options)
        }

        viewModel.totalScreenTime.observe(viewLifecycleOwner) { totalTime ->
            totalUsageText.text = "Total: $totalTime"
        }

        // Use hourly points for the Line Chart
        viewModel.hourlyUsagePoints.observe(viewLifecycleOwner) { points ->
            val labels = (0..23).map { String.format("%02d:00", it) }
            // Note: If all points are 0, it might seem invisible.
            // Let's ensure it draws something even with 0 data.
            lineChart.setData(points.ifEmpty { List(24) { 0f } }, labels)
        }

        // Use app usage data for the RecyclerView
        viewModel.addictionAppUsage.observe(viewLifecycleOwner) { usageList ->
            val appUsageList = usageList.map { (packageName, time) ->
                AppUsage(getAppName(packageName), formatMillis(time))
            }
            recyclerView.adapter = AppUsageAdapter(appUsageList)
        }
    }

    private fun setupChips(options: List<TimeRangeOption>) {
        chipGroup.removeAllViews()
        options.forEachIndexed { index, option ->
            val chip = Chip(requireContext()).apply {
                text = option.label
                isCheckable = true
                id = index // Use index as ID to ensure we can check it
                setOnClickListener { viewModel.loadUsageDataForRange(option) }
            }
            chipGroup.addView(chip)
            if (index == 0) {
                chip.isChecked = true
                viewModel.loadUsageDataForRange(option)
            }
        }
    }

    private fun getAppName(packageName: String): String {
        val pm = requireContext().packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName.split(".").last()
        }
    }

    private fun formatMillis(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
