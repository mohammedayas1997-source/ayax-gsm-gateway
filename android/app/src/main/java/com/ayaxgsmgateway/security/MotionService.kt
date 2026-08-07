package com.ayaxgsmgateway.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ayaxgsmgateway.R
import com.ayaxgsmgateway.alarm.AlarmService
import kotlin.math.sqrt

class MotionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var lastAlertTime = 0L

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "ayax_motion"
        private const val FORCE_THRESHOLD = 18.0
        private const val ALERT_COOLDOWN = 60000L // 1 minute
    }

    override fun onCreate() {
        super.onCreate()

        startForeground(NOTIFICATION_ID, createNotification())

        sensorManager =
            getSystemService(SENSOR_SERVICE) as SensorManager

        val accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer != null) {
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val force = sqrt((x * x + y * y + z * z).toDouble())

        if (force > FORCE_THRESHOLD) {
            val now = System.currentTimeMillis()

            if (now - lastAlertTime > ALERT_COOLDOWN) {
                lastAlertTime = now

                SecurityManager.sendSecurityAlert(
                    this,
                    "DEVICE_MOVED",
                    "Gateway device was moved or shaken (Force: ${force.toInt()})."
                )

                val alarmIntent = Intent(this, AlarmService::class.java).apply {
                    putExtra("reason", "DEVICE_MOVED")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(alarmIntent)
                } else {
                    startService(alarmIntent)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ayax Motion Security",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring gateway device movement"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ayax Motion Security")
            .setContentText("Monitoring gateway device movement")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try {
            sensorManager.unregisterListener(this)
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}