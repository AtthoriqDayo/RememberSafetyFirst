package com.example.remembersafetyfirst

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class FirstFragment : Fragment() {

    private lateinit var layoutEmpty: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnAddBase: Button
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_first, container, false)

        layoutEmpty = root.findViewById(R.id.layout_empty_state)
        recyclerView = root.findViewById(R.id.recycler_bases) // New ID we need to add to XML
        btnAddBase = root.findViewById(R.id.btn_add_base)

        // Setup RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(context)

        btnAddBase.setOnClickListener {
            startActivity(Intent(requireContext(), AddDeviceActivity::class.java))
        }

        loadBaseStations()

        // Also start the background service just in case
        val serviceIntent = Intent(requireContext(), SafetyService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        loadBaseStations() // Refresh list on return
    }

    private fun loadBaseStations() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        db.collection("users").document(userId).collection("baseStations")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    layoutEmpty.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    layoutEmpty.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE

                    // Convert documents to list
                    val deviceList = documents.map { doc ->
                        doc.data
                    }

                    // Create Adapter
                    val adapter = BaseStationAdapter(deviceList) { mac, name ->
                        // On Click Item: Navigate to Details
                        val bundle = Bundle().apply {
                            putString("macAddress", mac)
                            putString("name", name)
                        }
                        findNavController().navigate(R.id.action_FirstFragment_to_BaseDetailFragment, bundle)
                    }
                    recyclerView.adapter = adapter
                }
            }
    }
}