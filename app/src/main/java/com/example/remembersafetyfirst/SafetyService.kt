package com.example.remembersafetyfirst

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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

class SafetyService : Service() {

    private val CHANNEL_ID = "safety_service_channel"
    private val ALERT_CHANNEL_ID = "safety_alerts"
    private var mediaPlayer: MediaPlayer? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle commands sent from the UI (like "STOP_SOUND")
        if (intent?.action == "STOP_SOUND") {
            stopAlarm()
        } else {
            // Default: Start Monitoring
            startForegroundService()
            listenToSensors()
        }
        // START_STICKY means "If the system kills me, restart me automatically"
        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()

        // This is the notification that proves the app is running in the background
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Safety Monitor Active")
            .setContentText("Monitoring Fire and Gas levels...")
            .setSmallIcon(android.R.drawable.ic_secure)
            .build()

        startForeground(1, notification)
    }

    private fun listenToSensors() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) return


        // Point to the logged-in user's folder
        val basePath = "users/$userId/baseStations/test-base-id/sensors"
        // GAS SENSOR
        db.collection(basePath).document("sensor-101")
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getLong("status")?.toInt()
                if (status == 1) triggerAlarm("⚠️ GAS LEAK!", "High gas detected in Kitchen!")
            }

        // FIRE SENSOR
        db.collection(basePath).document("sensor-102")
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getLong("status")?.toInt()
                if (status == 1) triggerAlarm("🔥 FIRE ALERT!", "High temp detected!")
            }
    }

    private fun triggerAlarm(title: String, message: String) {
        // 1. Play Sound if not already playing
        if (mediaPlayer == null || mediaPlayer?.isPlaying == false) {
            playCriticalAlarm()
        }

        // 2. Send the High Priority Notification
        // Clicking this notification opens MainActivity
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Optional: Add a "STOP" button directly on the notification
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
            .setContentIntent(pendingIntent) // Click opens app
            .addAction(android.R.drawable.ic_media_pause, "STOP ALARM", stopPendingIntent) // Button stops sound
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun playCriticalAlarm() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // 1. Max Volume
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            mediaPlayer = MediaPlayer()

            // 2. Force 'Alarm' Audio Attributes
            val attribute = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            mediaPlayer?.setAudioAttributes(attribute)

            // 3. TRY TO LOAD CUSTOM SOUND
            try {
                // This looks for 'res/raw/alert_sound.mp3'
                val afd = resources.openRawResourceFd(R.raw.alert_sound)
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } catch (e: Exception) {
                // Fallback: If custom sound fails/missing, use default
                Log.e("SafetyService", "Custom sound not found, using default", e)
                val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
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

            // Channel 1: Silent Background Service Indicator
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Safety Service", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(serviceChannel)

            // Channel 2: LOUD ALERTS
            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(alertChannel)
        }
    }
}