package com.yourname.addictionmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yourname.addictionmanager.R
import com.yourname.addictionmanager.model.AppUsage

class AppUsageAdapter(
    private val appList: List<AppUsage>
) : RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvAppName)
        val time: TextView = view.findViewById(R.id.tvAppTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = appList[position]
        holder.name.text = app.appName
        holder.time.text = app.usageTime
    }

    override fun getItemCount(): Int = appList.size
}
