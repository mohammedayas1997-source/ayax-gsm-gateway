package com.ayaxgsmgateway.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ayaxgsmgateway.R

class AlarmService : Service() {

    private var player: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()

        val audio =
            getSystemService(AUDIO_SERVICE) as AudioManager

        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        player = MediaPlayer.create(this, R.raw.alarm)

        player?.isLooping = true

        player?.start()

        startForeground(
            1001,
            createNotification()
        )
    }

    private fun createNotification(): Notification {

        val channelId = "ayax_alarm"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    channelId,
                    "Ayax Alarm",
                    NotificationManager.IMPORTANCE_HIGH
                )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Ayax Security Alarm")
            .setContentText("Alarm is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        return START_STICKY
    }

    override fun onDestroy() {

        player?.stop()
        player?.release()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}