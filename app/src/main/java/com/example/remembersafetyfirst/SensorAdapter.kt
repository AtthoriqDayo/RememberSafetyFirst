package com.example.remembersafetyfirst

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SensorAdapter(
    private val sensorList: List<Map<String, Any>>
) : RecyclerView.Adapter<SensorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_sensor_name)
        val location: TextView = view.findViewById(R.id.tv_sensor_location)
        val value: TextView = view.findViewById(R.id.tv_sensor_value)
        val status: TextView = view.findViewById(R.id.tv_sensor_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sensor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sensor = sensorList[position]

        val name = sensor["name"] as? String ?: "Unknown Sensor"
        val location = sensor["location"] as? String ?: "No Location"
        val type = sensor["type"] as? String ?: "generic"
        val value = sensor["value"].toString().toDoubleOrNull() ?: 0.0
        val status = sensor["status"].toString().toIntOrNull() ?: 0 // 0=Safe, 1=Alert

        holder.name.text = name
        holder.location.text = location

        // Custom Display Logic based on Type
        if (type.equals("flood", ignoreCase = true)) {
            holder.value.text = "Water Level: ${value} cm"
        } else {
            holder.value.text = "Value: $value"
        }

        if (status == 1) {
            holder.status.text = "⚠️ ALERT"
            holder.status.setTextColor(Color.RED)
        } else {
            holder.status.text = "Online / Safe"
            holder.status.setTextColor(Color.parseColor("#4CAF50"))
        }
    }

    override fun getItemCount() = sensorList.size
}