package com.yourname.addictionmanager.ui.apps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yourname.addictionmanager.R

class AppsAdapter(
    private val apps: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppsAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.appName.text = app.name
        holder.appIcon.setImageDrawable(app.icon)
        holder.appUsage.text = app.usageTime
        holder.itemView.setOnClickListener {
            onItemClick(app)
        }
    }

    override fun getItemCount() = apps.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val appUsage: TextView = itemView.findViewById(R.id.app_usage)
    }
}

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: android.graphics.drawable.Drawable,
    val usageTime: String
)
