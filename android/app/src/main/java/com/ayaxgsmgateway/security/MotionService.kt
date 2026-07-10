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

    override fun onCreate() {
        super.onCreate()

        startForeground(2001, createNotification())

        sensorManager =
            getSystemService(SENSOR_SERVICE) as SensorManager

        val accelerometer =
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val force = sqrt((x * x + y * y + z * z).toDouble())

        if (force > 18) {
            val now = System.currentTimeMillis()

            if (now - lastAlertTime > 60000) {
                lastAlertTime = now

                SecurityManager.sendSecurityAlert(
                    this,
                    "DEVICE_MOVED",
                    "Gateway device was moved or shaken."
                )

                val alarmIntent = Intent(this, AlarmService::class.java)
                startForegroundService(alarmIntent)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotification(): Notification {
        val channelId = "ayax_motion"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Ayax Motion Security",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ayax Motion Security")
            .setContentText("Monitoring gateway device movement")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}