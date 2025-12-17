package com.example.remembersafetyfirst

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class BaseDetailFragment : Fragment() {

    private lateinit var btnAddSensor: Button
    private lateinit var btnRemoveBase: Button
    private lateinit var title: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SensorAdapter

    private val db = FirebaseFirestore.getInstance()
    private var baseMacAddress: String? = null

    // Keep track of the listener to remove it properly
    private var confirmListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_base_detail, container, false)

        baseMacAddress = arguments?.getString("macAddress")
        val baseName = arguments?.getString("name")

        title = root.findViewById(R.id.tv_base_title)
        btnAddSensor = root.findViewById(R.id.btn_add_new_sensor)
        btnRemoveBase = root.findViewById(R.id.btn_remove_base)
        recyclerView = root.findViewById(R.id.recycler_sensors)

        title.text = baseName ?: "Base Station Details"

        recyclerView.layoutManager = LinearLayoutManager(context)

        if (baseMacAddress != null) {
            startRealtimeMonitoring(baseMacAddress!!)
        }

        btnAddSensor.setOnClickListener {
            val intent = Intent(requireContext(), AddSensorActivity::class.java)
            intent.putExtra("baseMac", baseMacAddress)
            startActivity(intent)
        }

        btnRemoveBase.setOnClickListener {
            confirmRemoveBase()
        }

        return root
    }

    private fun startRealtimeMonitoring(mac: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val sensorsPath = "users/$userId/baseStations/$mac/sensors"

        db.collection(sensorsPath).addSnapshotListener { snapshots, _ ->
            if (snapshots != null) {
                val sensorList = snapshots.documents.map { it.data!! }
                // Ensure context is valid before updating UI
                if (isAdded) {
                    adapter = SensorAdapter(sensorList)
                    recyclerView.adapter = adapter
                }
            }
        }
    }

    private fun confirmRemoveBase() {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Base Station?")
            .setMessage("This will reset the device and remove it from your account.")
            .setPositiveButton("Remove") { _, _ ->
                executeRemoveBase()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeRemoveBase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val mac = baseMacAddress ?: return

        updateStatusToast("Contacting Base Station...")
        btnRemoveBase.isEnabled = false

        val docRef = db.collection("users").document(userId).collection("baseStations").document(mac)

        // 1. Set Listener for Confirmation "yes"
        confirmListener = docRef.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener

            if (snapshot != null && snapshot.exists()) {
                val confirm = snapshot.getString("confirmDestroy")
                if (confirm == "yes") {
                    // STOP LISTENING to prevent double triggers
                    confirmListener?.remove()

                    // FIX: Check if fragment is attached before showing Toast
                    if (isAdded && context != null) {
                        Toast.makeText(requireContext(), "Device Reset Confirmed. Deleting...", Toast.LENGTH_SHORT).show()
                    }

                    // Proceed to delete
                    deleteBaseDocument(userId, mac)
                }
            }
        }

        // 2. Send "RESET" Command
        docRef.update("command", "RESET")
            .addOnFailureListener {
                if (isAdded) showForceDeleteOption(userId, mac)
            }

        // 3. Set a Timeout (15 seconds)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isAdded && btnRemoveBase.isEnabled == false) {
                confirmListener?.remove()
                showForceDeleteOption(userId, mac)
            }
        }, 15000)
    }

    private fun showForceDeleteOption(uid: String, mac: String) {
        if (!isAdded) return

        AlertDialog.Builder(requireContext())
            .setTitle("Device Not Responding")
            .setMessage("The Base Station did not confirm the reset. It might be offline.\n\nDo you want to force remove it?")
            .setPositiveButton("Force Remove") { _, _ ->
                deleteBaseDocument(uid, mac)
            }
            .setNegativeButton("Cancel") { _, _ ->
                btnRemoveBase.isEnabled = true
            }
            .show()
    }

    private fun deleteBaseDocument(uid: String, mac: String) {
        db.collection("users").document(uid)
            .collection("baseStations").document(mac)
            .delete()
            .addOnSuccessListener {
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), "Base Station Removed", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener {
                if (isAdded && context != null) {
                    Toast.makeText(requireContext(), "Error removing data", Toast.LENGTH_SHORT).show()
                    btnRemoveBase.isEnabled = true
                }
            }
    }

    private fun updateStatusToast(msg: String) {
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up listener when view is destroyed to prevent crashes
        confirmListener?.remove()
    }
}