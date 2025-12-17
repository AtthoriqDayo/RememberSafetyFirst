package com.example.remembersafetyfirst

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL


class AddSensorActivity : AppCompatActivity() {

    private val SENSOR_AP_SSID = "SafetyFirst-Sensor-Setup"
    private val SENSOR_IP = "http://192.168.4.1/config_sensor"

    private lateinit var etLocation: EditText
    private lateinit var btnScan: Button
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    private var baseMacAddress: String = ""
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.all { it.value }) {
                startProvisioningProcess()
            } else {
                Toast.makeText(this, "Permissions needed to find sensor", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_sensor)

        baseMacAddress = intent.getStringExtra("baseMac") ?: ""

        etLocation = findViewById(R.id.et_sensor_location)
        btnScan = findViewById(R.id.btn_scan_sensor)
        statusText = findViewById(R.id.tv_status_sensor)
        progressBar = findViewById(R.id.progress_sensor)

        btnScan.setOnClickListener {
            if (etLocation.text.isNotBlank()) {
                checkPermissionsAndStart()
            } else {
                Toast.makeText(this, "Please enter a location name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CHANGE_NETWORK_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            startProvisioningProcess()
        } else {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startProvisioningProcess() {
        updateStatus("Scanning for Sensor ($SENSOR_AP_SSID)...")
        btnScan.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(android.os.PatternMatcher(SENSOR_AP_SSID, android.os.PatternMatcher.PATTERN_PREFIX))
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                connectivityManager.bindProcessToNetwork(network)
                runOnUiThread { updateStatus("Connected! Injecting Base Address...") }

                sendConfigToSensor(network)
            }

            override fun onUnavailable() {
                runOnUiThread {
                    updateStatus("Sensor not found.")
                    resetUI()
                }
            }
        }
        connectivityManager.requestNetwork(request, networkCallback!!)
    }

    private fun sendConfigToSensor(network: Network) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val baseLocationName = etLocation.text.toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Prepare JSON
                val jsonParam = JSONObject()
                jsonParam.put("baseMac", baseMacAddress)

                val url = URL(SENSOR_IP)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 5000

                val outputStream = OutputStreamWriter(connection.outputStream)
                outputStream.write(jsonParam.toString())
                outputStream.flush()
                outputStream.close()

                if (connection.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) response.append(line)
                    reader.close()

                    // 3. NEW: Parse Array of Sensors
                    val jsonResp = JSONObject(response.toString())

                    if (jsonResp.has("sensors")) {
                        val sensorsArray = jsonResp.getJSONArray("sensors")

                        withContext(Dispatchers.Main) {
                            updateStatus("Found ${sensorsArray.length()} Sensors! Saving...")
                        }

                        // Loop through all sensors in the array
                        for (i in 0 until sensorsArray.length()) {
                            val sensorObj = sensorsArray.getJSONObject(i)
                            val sId = sensorObj.getString("id")
                            val sType = sensorObj.getString("type")

                            // Combine Name: "Kitchen (Flood)", "Kitchen (Quake)"
                            val combinedName = "$baseLocationName ($sType)"

                            saveSensorToFirestore(uid, sId, sType, combinedName)
                        }
                    } else {
                        // Fallback for old single sensor format (optional)
                        val sId = jsonResp.getString("sensorId")
                        val sType = jsonResp.optString("type", "Generic")
                        saveSensorToFirestore(uid, sId, sType, baseLocationName)
                    }

                } else {
                    throw Exception("Sensor Error: ${connection.responseCode}")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    updateStatus("Error: ${e.message}")
                    resetUI()
                }
            } finally {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.bindProcessToNetwork(null)
                if (networkCallback != null) cm.unregisterNetworkCallback(networkCallback!!)
            }
        }
    }

    private fun saveSensorToFirestore(uid: String, sensorId: String, type: String, location: String) {
        val db = FirebaseFirestore.getInstance()
        val path = "users/$uid/baseStations/$baseMacAddress/sensors"

        val sensorData = hashMapOf(
            "name" to "Sensor ($location)", // Label stored ONLY in App/Cloud
            "location" to location,
            "type" to type,
            "status" to 0,
            "value" to 0.0,
            "linkedBase" to baseMacAddress,
            "dateAdded" to com.google.firebase.Timestamp.now()
        )

        db.collection(path).document(sensorId).set(sensorData)
            .addOnSuccessListener {
                Toast.makeText(this, "Sensor Added Successfully!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                updateStatus("Failed to save to Cloud.")
                resetUI()
            }
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }

    private fun resetUI() {
        btnScan.isEnabled = true
        progressBar.visibility = View.GONE
    }
}