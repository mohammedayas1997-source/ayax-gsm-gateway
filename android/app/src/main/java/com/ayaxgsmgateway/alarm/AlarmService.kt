package com.ayaxgsmgateway.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ayaxgsmgateway.R

class AlarmService : Service() {

    companion object {
        private const val TAG = "AYAX_ALARM"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ayax_security_alarm"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    private val audioManager: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onCreate() {
        super.onCreate()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        startAlarmSound()
        startVibration()

        return START_STICKY
    }

    private fun startAlarmSound() {
        stopAlarmSound()

        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0
            )

            requestAudioFocus()

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val player = MediaPlayer.create(
                this,
                R.raw.alarm,
                attributes,
                audioManager.generateAudioSessionId()
            )

            if (player == null) {
                Log.e(TAG, "R.raw.alarm could not be loaded")
                startDefaultAlarm()
                return
            }

            mediaPlayer = player.apply {
                isLooping = true
                setVolume(1.0f, 1.0f)

                setOnErrorListener { _, what, extra ->
                    Log.e(
                        TAG,
                        "Raw alarm error. what=$what extra=$extra"
                    )

                    startDefaultAlarm()
                    true
                }

                setOnCompletionListener {
                    Log.d(TAG, "Alarm playback completed")
                }

                start()
            }

            if (mediaPlayer?.isPlaying == true) {
                Log.d(TAG, "Alarm started successfully")
            } else {
                Log.e(TAG, "MediaPlayer started but isPlaying is false")
                startDefaultAlarm()
            }
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Raw alarm failed: ${error.message}",
                error
            )

            startDefaultAlarm()
        }
    }

    private fun startDefaultAlarm() {
        stopAlarmSound()

        try {
            val alarmUri =
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_RINGTONE
                    )
                    ?: RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_NOTIFICATION
                    )

            if (alarmUri == null) {
                Log.e(TAG, "No default alarm, ringtone or notification sound")
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )

                setDataSource(applicationContext, alarmUri)
                isLooping = true
                setVolume(1.0f, 1.0f)

                setOnPreparedListener { player ->
                    player.start()
                    Log.d(TAG, "Default alarm started")
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(
                        TAG,
                        "Default alarm error. what=$what extra=$extra"
                    )
                    true
                }

                prepareAsync()
            }
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Default alarm failed: ${error.message}",
                error
            )
        }
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                    .setAudioAttributes(attributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                    }
                    .build()

                audioManager.requestAudioFocus(
                    audioFocusRequest!!
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "Audio focus failed: ${error.message}")
        }
    }

    private fun startVibration() {
        try {
            vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager =
                        getSystemService(
                            Context.VIBRATOR_MANAGER_SERVICE
                        ) as VibratorManager

                    manager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(
                        Context.VIBRATOR_SERVICE
                    ) as Vibrator
                }

            val pattern = longArrayOf(
                0,
                800,
                400,
                800,
                400
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        pattern,
                        0
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Vibration failed: ${error.message}")
        }
    }

    private fun stopAlarmSound() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
        } catch (_: Exception) {
        }

        try {
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }

        mediaPlayer = null
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }

        vibrator = null
    }

    private fun abandonAudioFocus() {
        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                audioFocusRequest != null
            ) {
                audioManager.abandonAudioFocusRequest(
                    audioFocusRequest!!
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }

        audioFocusRequest = null
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ayax Security Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Remote gateway security alarm"
                setSound(null, null)
                enableVibration(false)
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ayax Security Alarm")
            .setContentText("Remote alarm is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        stopAlarmSound()
        stopVibration()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}