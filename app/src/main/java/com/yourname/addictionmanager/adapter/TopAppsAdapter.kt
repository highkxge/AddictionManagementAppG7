package com.yourname.addictionmanager.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yourname.addictionmanager.R

class TopAppsAdapter(
    private var items: List<Pair<String, String>>
) : RecyclerView.Adapter<TopAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.tvAppName)
        val appTime: TextView = view.findViewById(R.id.tvAppTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_app, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, time) = items[position]
        holder.appName.text = name
        holder.appTime.text = time
    }

    // ✅ THIS is what HomeFragment is calling
    fun update(newItems: List<Pair<String, String>>) {
        items = newItems
        notifyDataSetChanged()
    }
}
