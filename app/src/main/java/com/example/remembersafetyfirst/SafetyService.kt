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
import androidx.core.app.ServiceCompat
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
        if (intent?.action == "STOP_SOUND") {
            stopAlarm()
        } else {
            // Attempt to start monitoring
            startForegroundServiceSafety()
            listenToSensors()
        }
        return START_STICKY
    }

    private fun startForegroundServiceSafety() {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Safety Monitor Active")
            .setContentText("Monitoring Fire and Gas levels...")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        try {
            // Android 14+ requires specifying the type explicitly in startForeground
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                // The app is in the background and cannot start the service.
                // We must stop the service to avoid the crash.
                Log.e("SafetyService", "Failed to start foreground service: App is in background.")
                stopSelf()
            } else {
                // Re-throw other exceptions
                throw e
            }
        }
    }

    private fun listenToSensors() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // NOTE: In production, loop through all devices.
        // For now, checking the test path as defined in your fragments.
        val basePath = "users/$userId/baseStations/test-base-id/sensors"

        db.collection(basePath).document("sensor-101")
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getLong("status")?.toInt()
                if (status == 1) triggerAlarm("⚠️ GAS LEAK!", "High gas detected in Kitchen!")
            }

        db.collection(basePath).document("sensor-102")
            .addSnapshotListener { snapshot, _ ->
                val status = snapshot?.getLong("status")?.toInt()
                if (status == 1) triggerAlarm("🔥 FIRE ALERT!", "High temp detected!")
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
            .setFullScreenIntent(pendingIntent, true) // Important for high priority
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

            val serviceChannel = NotificationChannel(CHANNEL_ID, "Safety Service", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(serviceChannel)

            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Emergency Alerts", NotificationManager.IMPORTANCE_HIGH)
            alertChannel.description = "Critical alerts for Fire and Gas"
            // Important: Allow the sound to bypass Do Not Disturb if possible
            alertChannel.setBypassDnd(true)
            manager.createNotificationChannel(alertChannel)
        }
    }
}