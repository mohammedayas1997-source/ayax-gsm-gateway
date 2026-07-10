package com.ayaxgsmgateway.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ayaxgsmgateway.R

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()

        try {
            createNotificationChannel()

            startForeground(
                NOTIFICATION_ID,
                createNotification()
            )

            startAlarmSound()
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to start alarm service",
                error
            )

            stopSelf()
        }
    }

    private fun startAlarmSound() {
        stopAlarmSound()

        val audioManager =
            getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            audioManager.ringerMode =
                AudioManager.RINGER_MODE_NORMAL

            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(
                    AudioManager.STREAM_ALARM
                ),
                0
            )
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to change alarm volume",
                error
            )
        }

        val player = MediaPlayer.create(
            applicationContext,
            R.raw.alarm
        )

        if (player == null) {
            Log.e(TAG, "alarm.mp3 could not be loaded")
            stopSelf()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            player.setAudioStreamType(
                AudioManager.STREAM_ALARM
            )
        }

        player.isLooping = true

        player.setOnErrorListener { mp, what, extra ->
            Log.e(
                TAG,
                "MediaPlayer error: what=$what extra=$extra"
            )

            try {
                mp.stop()
            } catch (_: Exception) {
            }

            mp.release()
            mediaPlayer = null
            stopSelf()

            true
        }

        player.start()
        mediaPlayer = player
    }

    private fun stopAlarmSound() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }

                player.release()
            }
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to stop alarm",
                error
            )
        } finally {
            mediaPlayer = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Ayax Security Alarm",
                    NotificationManager.IMPORTANCE_HIGH
                )

            channel.description =
                "Remote security alarm service"

            channel.setSound(null, null)

            val notificationManager =
                getSystemService(
                    NotificationManager::class.java
                )

            notificationManager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Ayax Security Alarm")
            .setContentText("Gateway alarm is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(
                NotificationCompat.PRIORITY_HIGH
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (mediaPlayer == null) {
            try {
                startAlarmSound()
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "Unable to restart alarm sound",
                    error
                )

                stopSelf()
            }
        }

        // Kada Android ya tayar da alarm service da kansa
        // bayan process ya mutu ko lokacin app ya sake buɗewa.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopAlarmSound()
        stopForeground(STOP_FOREGROUND_REMOVE)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "AYAX_ALARM"
        private const val CHANNEL_ID = "ayax_alarm"
        private const val NOTIFICATION_ID = 1001
    }
}