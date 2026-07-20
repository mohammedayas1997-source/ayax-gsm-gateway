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
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ayaxgsmgateway.R

class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.ayaxgsmgateway.alarm.START"
        const val ACTION_STOP = "com.ayaxgsmateway.alarm.STOP"

        private const val TAG = "AYAX_ALARM"
        private const val CHANNEL_ID = "ayax_security_alarm"
        private const val NOTIFICATION_ID = 1001
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * Muhimmi:
         * Duk lokacin da aka kira startForegroundService(),
         * dole service ya kira startForeground() nan take.
         */
        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        when (intent?.action) {
            ACTION_STOP -> {
                stopAlarmSound()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_START -> {
                if (mediaPlayer?.isPlaying != true) {
                    startAlarmSound()
                }
            }

            else -> {
                /*
                 * Wannan yana hana crash idan wani old code,
                 * GeofenceManager ko LocationModule ya fara service
                 * ba tare da action ba.
                 */
                Log.w(
                    TAG,
                    "AlarmService started without action; stopping safely"
                )

                stopAlarmSound()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startAlarmSound() {
        stopAlarmSound()

        try {
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
                Log.w(
                    TAG,
                    "Could not change alarm volume",
                    error
                )
            }

            val attributes =
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()

            val player = MediaPlayer.create(
                applicationContext,
                R.raw.alarm,
                attributes,
                0
            )

            if (player == null) {
                Log.w(
                    TAG,
                    "Raw alarm sound unavailable; using default alarm"
                )

                startDefaultAlarm()
                return
            }

            player.apply {
                isLooping = true
                setVolume(1f, 1f)

                setOnErrorListener { mp, what, extra ->
                    Log.e(
                        TAG,
                        "MediaPlayer error: what=$what extra=$extra"
                    )

                    try {
                        mp.release()
                    } catch (_: Exception) {
                    }

                    mediaPlayer = null
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()

                    true
                }

                start()
            }

            mediaPlayer = player
            Log.d(TAG, "Alarm started successfully")

        } catch (error: Exception) {
            Log.e(TAG, "Raw alarm failed", error)
            startDefaultAlarm()
        }
    }

    private fun startDefaultAlarm() {
        try {
            val alarmUri =
                RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_ALARM
                ) ?: RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_RINGTONE
                )

            if (alarmUri == null) {
                Log.e(TAG, "No alarm sound available")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            val player =
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(
                                AudioAttributes.USAGE_ALARM
                            )
                            .setContentType(
                                AudioAttributes.CONTENT_TYPE_SONIFICATION
                            )
                            .build()
                    )

                    setDataSource(
                        applicationContext,
                        alarmUri
                    )

                    isLooping = true
                    setVolume(1f, 1f)
                    prepare()
                    start()
                }

            mediaPlayer = player
            Log.d(TAG, "Default alarm started")

        } catch (error: Exception) {
            Log.e(TAG, "Default alarm failed", error)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopAlarmSound() {
        val player = mediaPlayer ?: return

        try {
            if (player.isPlaying) {
                player.stop()
            }
        } catch (_: Exception) {
        }

        try {
            player.reset()
            player.release()
        } catch (_: Exception) {
        }

        mediaPlayer = null
        Log.d(TAG, "Alarm stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Ayax Security Alarm",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description =
                        "Remote gateway security alarm"

                    setSound(null, null)
                }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Ayax Security Alarm")
            .setContentText("Remote alarm service is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        stopAlarmSound()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}