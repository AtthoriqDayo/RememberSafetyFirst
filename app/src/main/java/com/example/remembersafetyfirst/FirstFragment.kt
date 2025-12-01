package com.example.remembersafetyfirst

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirstFragment : Fragment() {

    private lateinit var txtGasValue: TextView
    private lateinit var txtGasStatus: TextView
    private lateinit var txtFireValue: TextView
    private lateinit var txtFireStatus: TextView

    // Containers
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var layoutContent: LinearLayout
    private lateinit var btnAddBase: Button

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

        layoutEmpty = root.findViewById(R.id.layout_empty_state)
        layoutContent = root.findViewById(R.id.layout_content_state)
        btnAddBase = root.findViewById(R.id.btn_add_base)

        // Handle Add Base Click
        btnAddBase.setOnClickListener {
            val intent = Intent(requireContext(), AddDeviceActivity::class.java)
            startActivity(intent)
        }

        checkIfBaseStationExists()

        return root
    }

    private fun checkIfBaseStationExists() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Check if the user has any documents in the 'baseStations' sub-collection
        db.collection("users").document(userId).collection("baseStations")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // No devices found -> Show Empty State
                    layoutEmpty.visibility = View.VISIBLE
                    layoutContent.visibility = View.GONE
                } else {
                    // Devices found -> Show Content
                    layoutEmpty.visibility = View.GONE
                    layoutContent.visibility = View.VISIBLE

                    // For now, we just load the test sensor.
                    // In the future, you should loop through 'documents' to get real MAC addresses.
                    startSensorMonitoring()

                    // Start Background Service
                    val serviceIntent = Intent(requireContext(), SafetyService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        requireContext().startForegroundService(serviceIntent)
                    } else {
                        requireContext().startService(serviceIntent)
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        val stopIntent = Intent(requireContext(), SafetyService::class.java)
        stopIntent.action = "STOP_SOUND"
        requireContext().startService(stopIntent)

        // Refresh list in case user just added a device
        checkIfBaseStationExists()
    }

    private fun startSensorMonitoring() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // NOTE: Currently hardcoded for testing.
        // Once connected, replace 'test-base-id' with the actual document ID from the previous check.
        val basePath = "users/$userId/baseStations/test-base-id/sensors"

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