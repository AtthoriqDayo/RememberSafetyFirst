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
            listenForDeviceStatus(baseMacAddress!!)
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

    private fun listenForDeviceStatus(mac: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val docRef = db.collection("users").document(userId).collection("baseStations").document(mac)

        // Assign to the global variable so we can clean it up later
        confirmListener = docRef.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener

            if (snapshot != null && snapshot.exists()) {
                // Check for the Magic Word
                val confirm = snapshot.getString("confirmDestroy")

                if (confirm == "yes") {
                    // Prevent duplicate calls
                    confirmListener?.remove()

                    if (isAdded && context != null) {
                        Toast.makeText(requireContext(), "Reset Confirmed by Device. Removing...", Toast.LENGTH_LONG).show()
                    }

                    // Perform the final cleanup
                    deleteBaseDocument(userId, mac)
                }
            }
        }
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

        updateStatusToast("Sending Reset Command...")
        btnRemoveBase.isEnabled = false // Prevent spamming

        val docRef = db.collection("users").document(userId).collection("baseStations").document(mac)

        // 1. Just Send the Command
        docRef.update("command", "RESET")
            .addOnFailureListener {
                // If we can't write, show the force delete option
                if (isAdded) showForceDeleteOption(userId, mac)
            }

        // 2. Set a Timeout (10 seconds)
        // If the listener doesn't trigger deleteBaseDocument() within 10s, assume device is offline
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isAdded && btnRemoveBase.isEnabled == false) {
                // If button is still disabled, it means we haven't popped the stack yet
                showForceDeleteOption(userId, mac)
            }
        }, 10000)
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