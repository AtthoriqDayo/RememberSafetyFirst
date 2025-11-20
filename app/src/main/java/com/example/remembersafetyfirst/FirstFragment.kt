package com.example.remembersafetyfirst

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent
import android.app.PendingIntent

class FirstFragment : Fragment() {

    private lateinit var txtGasValue: TextView
    private lateinit var txtGasStatus: TextView
    private lateinit var txtFireValue: TextView
    private lateinit var txtFireStatus: TextView

    private val db = FirebaseFirestore.getInstance()
    private val CHANNEL_ID = "safety_alerts"
    private var mediaPlayer: MediaPlayer? = null // Keep reference to stop it later

    // Permission Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("SafetyApp", "Notification permission granted")
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_first, container, false)

        txtGasValue = root.findViewById(R.id.text_gas_value)
        txtGasStatus = root.findViewById(R.id.text_gas_status)
        txtFireValue = root.findViewById(R.id.text_fire_value)
        txtFireStatus = root.findViewById(R.id.text_fire_status)

        createNotificationChannel()
        askForPermission()
        listenToSensors()

        return root
    }

    private fun askForPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun listenToSensors() {
        val basePath = "users/test-user-id/baseStations/test-base-id/sensors"

        // GAS SENSOR
        db.collection(basePath).document("sensor-101")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val value = snapshot.getDouble("value")
                    val status = snapshot.getLong("status")?.toInt()

                    txtGasValue.text = "Value: $value"
                    if (status == 1) {
                        txtGasStatus.text = "Status: ⚠️ GAS LEAK!"
                        txtGasStatus.setTextColor(Color.RED)

                        // TRIGGER ALERT
                        sendNotification("Gas Alert", "High gas levels detected in Kitchen!")
                        playCriticalAlarm() // <--- PLAYS LOUD SOUND

                    } else {
                        txtGasStatus.text = "Status: Normal"
                        txtGasStatus.setTextColor(Color.GREEN)
                    }
                }
            }

        // FIRE SENSOR
        db.collection(basePath).document("sensor-102")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val value = snapshot.getDouble("value")
                    val status = snapshot.getLong("status")?.toInt()

                    txtFireValue.text = "Temp: $value °C"
                    if (status == 1) {
                        txtFireStatus.text = "Status: 🔥 FIRE DETECTED"
                        txtFireStatus.setTextColor(Color.RED)

                        // TRIGGER ALERT
                        sendNotification("Fire Alert", "High temperature detected in Living Room!")
                        playCriticalAlarm() // <--- PLAYS LOUD SOUND

                    } else {
                        txtFireStatus.text = "Status: Safe"
                        txtFireStatus.setTextColor(Color.GREEN)
                    }
                }
            }
    }

    private fun sendNotification(title: String, message: String) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // 1. Create Intent for the Full Screen Activity
        val fullScreenIntent = Intent(requireContext(), EmergencyActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            requireContext(),
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(requireContext(), CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX) // MAX Priority pop-up
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Tells system this is an Alarm
            .setFullScreenIntent(fullScreenPendingIntent, true)

            .setAutoCancel(true)

        with(NotificationManagerCompat.from(requireContext())) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    // --- NEW FUNCTION: FORCE SOUND EVEN ON SILENT ---
    // ... inside FirstFragment class ...

    private fun playCriticalAlarm() {
        try {
            val context = requireContext()
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // 1. FORCE VOLUME TO MAX (The "Nuclear Option")
            // We mute the 'Notification' stream to avoid distractions and BLAST the 'Alarm' stream
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // 2. Stop any existing alarm to prevent overlapping sounds
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }

            // 3. Create MediaPlayer manually (Not using .create())
            // This is critical to ensure it attaches to the ALARM stream immediately
            mediaPlayer = MediaPlayer()

            val attribute = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)             // FORCE ALARM USAGE
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            mediaPlayer?.setAudioAttributes(attribute)

            // 4. Select the Sound Source
            // OPTION A: Use your custom file (if you added alert_sound.mp3 to res/raw)
            val afd = context.resources.openRawResourceFd(R.raw.alert_sound)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()


            // 5. Prepare and Start
            mediaPlayer?.setLooping(true) // LOOP IT so it doesn't stop until you fix the fire
            mediaPlayer?.prepare()
            mediaPlayer?.start()

        } catch (e: Exception) {
            Log.e("SafetyApp", "Error playing alarm", e)
        }
    }

    // Add this to stop the sound when you leave the app or fix the issue
    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Safety Alerts"
            val descriptionText = "Notifications for Fire and Gas alerts"
            // IMPORTANCE_HIGH makes it pop up on screen
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}