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

class AddDeviceActivity : AppCompatActivity() {

    private val ESP_AP_SSID = "SafetyFirst-Setup"
    private val ESP_IP_ADDRESS = "http://192.168.4.1/setup"

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnStart: Button
    private lateinit var ssidInput: EditText
    private lateinit var passInput: EditText

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Permission Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                // Permission granted, proceed with connection
                val ssid = ssidInput.text.toString()
                val pass = passInput.text.toString()
                connectToDeviceAndConfigure(ssid, pass)
            } else {
                Toast.makeText(this, "Permissions required to scan for device", Toast.LENGTH_LONG).show()
                resetUI()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_device)

        ssidInput = findViewById(R.id.input_wifi_ssid)
        passInput = findViewById(R.id.input_wifi_pass)
        statusText = findViewById(R.id.tv_status)
        progressBar = findViewById(R.id.progress_bar)
        btnStart = findViewById(R.id.btn_start_setup)

        btnStart.setOnClickListener {
            val ssid = ssidInput.text.toString()
            val pass = passInput.text.toString()

            if (ssid.isNotBlank() && pass.isNotBlank()) {
                checkPermissionsAndStart()
            } else {
                Toast.makeText(this, "Please enter Wi-Fi details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        // Determine which permission is needed based on Android Version
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ needs NEARBY_WIFI_DEVICES
            arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            // Android 12 and below needs FINE_LOCATION to find Wi-Fi
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            // Already have permission
            val ssid = ssidInput.text.toString()
            val pass = passInput.text.toString()
            connectToDeviceAndConfigure(ssid, pass)
        } else {
            // Request permission
            requestPermissionLauncher.launch(permissions)
        }
    }

    private fun connectToDeviceAndConfigure(homeSsid: String, homePass: String) {
        updateStatus("Scanning for Device ($ESP_AP_SSID)...")
        btnStart.isEnabled = false
        progressBar.visibility = View.VISIBLE

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(android.os.PatternMatcher(ESP_AP_SSID, android.os.PatternMatcher.PATTERN_PREFIX))
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

                runOnUiThread { updateStatus("Connected to Device! Sending configuration...") }
                sendConfigToEsp(network, homeSsid, homePass)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                runOnUiThread {
                    updateStatus("Device not found. Make sure it's in setup mode.")
                    resetUI()
                }
            }
        }

        try {
            connectivityManager.requestNetwork(request, networkCallback!!)
        } catch (e: SecurityException) {
            updateStatus("Permission Error: ${e.message}")
            resetUI()
        }
    }

    private fun sendConfigToEsp(network: Network, wifiSsid: String, wifiPass: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonParam = JSONObject()
                jsonParam.put("uid", uid)
                jsonParam.put("ssid", wifiSsid)
                jsonParam.put("password", wifiPass)

                val url = URL(ESP_IP_ADDRESS)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val outputStream = OutputStreamWriter(connection.outputStream)
                outputStream.write(jsonParam.toString())
                outputStream.flush()
                outputStream.close()

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    val jsonResponse = JSONObject(response.toString())
                    // Assuming ESP sends back {"mac": "..."}
                    val macAddress = jsonResponse.optString("mac", "unknown_mac")

                    withContext(Dispatchers.Main) {
                        updateStatus("Config Sent! Saving...")
                        saveDeviceToFirestore(uid, macAddress)
                    }
                } else {
                    throw Exception("ESP Error: $responseCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    updateStatus("Connection Failed: ${e.message}")
                    resetUI()
                }
            } finally {
                // Clean up network binding
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.bindProcessToNetwork(null)
                if (networkCallback != null) cm.unregisterNetworkCallback(networkCallback!!)
            }
        }
    }

    private fun saveDeviceToFirestore(uid: String, macAddress: String) {
        val db = FirebaseFirestore.getInstance()
        val deviceData = hashMapOf(
            "macAddress" to macAddress,
            "name" to "New Base Station",
            "dateAdded" to com.google.firebase.Timestamp.now()
        )

        db.collection("users").document(uid)
            .collection("baseStations").document(macAddress)
            .set(deviceData)
            .addOnSuccessListener {
                Toast.makeText(this, "Device Setup Complete!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                updateStatus("Failed to save to cloud.")
                resetUI()
            }
    }

    private fun updateStatus(text: String) {
        statusText.text = text
    }

    private fun resetUI() {
        btnStart.isEnabled = true
        progressBar.visibility = View.GONE
    }
}