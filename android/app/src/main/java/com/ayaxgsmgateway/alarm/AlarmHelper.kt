package com.ayaxgsmgateway.alarm

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import com.ayaxgsmgateway.R

object AlarmHelper {

    private var player: MediaPlayer? = null

    fun startAlarm(context: Context) {

        stopAlarm()

        val audio =
            context.getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL

        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        player =
            MediaPlayer.create(
                context,
                R.raw.alarm
            )

        player?.isLooping = true

        player?.start()

    }

    fun stopAlarm() {

        player?.stop()

        player?.release()

        player = null

    }

}