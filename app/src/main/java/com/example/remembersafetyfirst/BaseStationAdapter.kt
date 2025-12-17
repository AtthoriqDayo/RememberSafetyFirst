package com.example.remembersafetyfirst

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Renamed from BaseAdapter to BaseStationAdapter to avoid conflict
class BaseStationAdapter(
    private val deviceList: List<Map<String, Any>>,
    private val onClick: (String, String) -> Unit
) : RecyclerView.Adapter<BaseStationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_device_name)
        val mac: TextView = view.findViewById(R.id.tv_device_mac)
        val status: TextView = view.findViewById(R.id.tv_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_base_station, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = deviceList[position]
        val mac = device["macAddress"] as? String ?: "Unknown ID"
        val name = device["name"] as? String ?: "Unnamed Base"

        holder.name.text = name
        holder.mac.text = mac

        holder.itemView.setOnClickListener {
            onClick(mac, name)
        }
    }

    override fun getItemCount() = deviceList.size
}