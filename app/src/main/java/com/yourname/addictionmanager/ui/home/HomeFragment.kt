package com.yourname.addictionmanager.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yourname.addictionmanager.R

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var legendAdapter: LegendAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        val ringView: ScreenTimeRingView = view.findViewById(R.id.screen_time_ring_view)
        val tvTime: TextView = view.findViewById(R.id.tvTotalTime)
        val tvPhrase: TextView = view.findViewById(R.id.tvUsageStatusPhrase)
        val recycler: RecyclerView = view.findViewById(R.id.rvTopApps)

        // Adapter
        legendAdapter = LegendAdapter(emptyList())
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = legendAdapter

        // Observe ViewModel data
        viewModel.totalScreenTimeString.observe(viewLifecycleOwner) { timeString ->
            tvTime.text = timeString
        }

        viewModel.usageStatusPhrase.observe(viewLifecycleOwner) { phrase ->
            tvPhrase.text = phrase
        }

        viewModel.ringSegments.observe(viewLifecycleOwner) { segments ->
            ringView.submitSegments(segments)
        }

        viewModel.legendItems.observe(viewLifecycleOwner) { legendItems ->
            legendAdapter.updateData(legendItems)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time the fragment is shown
        viewModel.loadUsageData()
    }
}
