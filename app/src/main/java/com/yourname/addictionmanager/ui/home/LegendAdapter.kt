package com.yourname.addictionmanager.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yourname.addictionmanager.R

class LegendAdapter(private var items: List<LegendItem>) : RecyclerView.Adapter<LegendAdapter.ViewHolder>() {

    private val colors = listOf(
        R.color.green_ok,
        R.color.orange_warn,
        R.color.red_danger,
        R.color.purple_200,
        R.color.teal_200
    )

    fun updateData(newItems: List<LegendItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_legend, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.appName.text = item.name
        holder.appUsage.text = item.usage
        holder.colorDot.background.setTint(ContextCompat.getColor(holder.itemView.context, colors[position % colors.size]))
    }

    override fun getItemCount() = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val colorDot: View = itemView.findViewById(R.id.color_dot)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val appUsage: TextView = itemView.findViewById(R.id.app_usage)
    }
}
