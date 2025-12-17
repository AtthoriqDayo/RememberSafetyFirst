package com.example.remembersafetyfirst

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class SafetyService : Service() {

    private val CHANNEL_ID = "safety_service_channel"
    private val ALERT_CHANNEL_ID = "safety_alerts"
    private var mediaPlayer: MediaPlayer? = null
    private val db = FirebaseFirestore.getInstance()

    // Store listeners to clean them up later
    private val listeners = mutableListOf<ListenerRegistration>()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SOUND") {
            stopAlarm()
        } else {
            startForegroundServiceSafety()
            listenToAllDevices()
        }
        return START_STICKY
    }

    private fun listenToAllDevices() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. Get all Base Stations
        db.collection("users").document(userId).collection("baseStations")
            .get()
            .addOnSuccessListener { documents ->
                // Clear old listeners if service restarted
                listeners.forEach { it.remove() }
                listeners.clear()

                for (document in documents) {
                    val macAddress = document.id
                    // 2. Listen to sensors for EACH base station
                    monitorSensorsForBase(userId, macAddress)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SafetyService", "Error fetching bases: ", e)
            }
    }

    private fun monitorSensorsForBase(userId: String, macAddress: String) {
        val sensorsPath = "users/$userId/baseStations/$macAddress/sensors"

        // Listen to the entire 'sensors' collection for this base
        val registration = db.collection(sensorsPath)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("SafetyService", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    for (doc in snapshots) {
                        val status = doc.getLong("status")?.toInt() ?: 0
                        val name = doc.getString("name") ?: "Unknown Sensor"
                        val type = doc.getString("type") ?: "generic"

                        // Trigger Alarm if status is 1
                        if (status == 1) {
                            val alertTitle = if (type.contains("gas", true)) "⚠️ GAS LEAK!" else "🔥 FIRE ALERT!"
                            triggerAlarm(alertTitle, "Danger detected at: $name")
                        }
                    }
                }
            }

        listeners.add(registration)
    }

    // --- (Keep the rest of your logic the same) ---

    private fun startForegroundServiceSafety() {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Safety Monitor Active")
            .setContentText("Monitoring all sensors...")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            // Handle Android 14 background start restrictions
            stopSelf()
        }
    }

    private fun triggerAlarm(title: String, message: String) {
        if (mediaPlayer == null || mediaPlayer?.isPlaying == false) {
            playCriticalAlarm()
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, SafetyService::class.java).apply { action = "STOP_SOUND" }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .addAction(android.R.drawable.ic_media_pause, "STOP ALARM", stopPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun playCriticalAlarm() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            mediaPlayer = MediaPlayer()
            val attribute = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            mediaPlayer?.setAudioAttributes(attribute)

            try {
                val afd = resources.openRawResourceFd(R.raw.alert_sound)
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } catch (e: Exception) {
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer?.setDataSource(this, alertUri)
            }

            mediaPlayer?.setLooping(true)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopAlarm() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Safety Service", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(serviceChannel)
            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH)
            alertChannel.setBypassDnd(true)
            manager.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        listeners.forEach { it.remove() }
    }
}