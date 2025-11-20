package com.example.remembersafetyfirst

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import android.graphics.Color

class FirstFragment : Fragment() {

    private lateinit var txtGasValue: TextView
    private lateinit var txtGasStatus: TextView
    private lateinit var txtFireValue: TextView
    private lateinit var txtFireStatus: TextView
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_first, container, false)

        txtGasValue = root.findViewById(R.id.text_gas_value)
        txtGasStatus = root.findViewById(R.id.text_gas_status)
        txtFireValue = root.findViewById(R.id.text_fire_value)
        txtFireStatus = root.findViewById(R.id.text_fire_status)

        // 1. Start the Background Service (It will keep running when app closes)
        val serviceIntent = Intent(requireContext(), SafetyService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }

        // 2. Update UI Text (Visual only)
        updateDashboardUI()

        return root
    }

    // 3. STOP ALARM WHEN APP IS OPENED
    override fun onResume() {
        super.onResume()
        val stopIntent = Intent(requireContext(), SafetyService::class.java)
        stopIntent.action = "STOP_SOUND"
        requireContext().startService(stopIntent)
    }

    private fun updateDashboardUI() {
        val basePath = "users/test-user-id/baseStations/test-base-id/sensors"

        // Just update text/color. The Service handles the Sound/Notification now.
        db.collection(basePath).document("sensor-101").addSnapshotListener { snapshot, _ ->
            val value = snapshot?.getDouble("value")
            val status = snapshot?.getLong("status")?.toInt()
            txtGasValue.text = "Value: $value"
            if (status == 1) {
                txtGasStatus.text = "Status: ⚠️ ALERT"
                txtGasStatus.setTextColor(Color.RED)
            } else {
                txtGasStatus.text = "Status: Normal"
                txtGasStatus.setTextColor(Color.GREEN)
            }
        }

        db.collection(basePath).document("sensor-102").addSnapshotListener { snapshot, _ ->
            val value = snapshot?.getDouble("value")
            val status = snapshot?.getLong("status")?.toInt()
            txtFireValue.text = "Temp: $value"
            if (status == 1) {
                txtFireStatus.text = "Status: 🔥 FIRE"
                txtFireStatus.setTextColor(Color.RED)
            } else {
                txtFireStatus.text = "Status: Safe"
                txtFireStatus.setTextColor(Color.GREEN)
            }
        }
    }
}