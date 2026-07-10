package com.ayaxgsmgateway.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ayaxgsmgateway.alarm.AlarmService

class DeviceStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        when (intent.action) {

            Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                val isAirplaneModeOn =
                    Settings.Global.getInt(
                        context.contentResolver,
                        Settings.Global.AIRPLANE_MODE_ON,
                        0
                    ) != 0

                if (isAirplaneModeOn) {
                    SecurityManager.sendSecurityAlert(
                        context,
                        "AIRPLANE_MODE_ON",
                        "Airplane mode was enabled on gateway device."
                    )

                    startAlarm(context)
                }
            }

            "android.intent.action.SIM_STATE_CHANGED" -> {
                SecurityManager.sendSecurityAlert(
                    context,
                    "SIM_STATE_CHANGED",
                    "SIM state changed. Possible SIM removed or replaced."
                )

                startAlarm(context)
            }

            "android.location.PROVIDERS_CHANGED" -> {
                SecurityManager.sendSecurityAlert(
                    context,
                    "GPS_PROVIDER_CHANGED",
                    "Location/GPS setting was changed."
                )
            }

            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                SecurityManager.sendSecurityAlert(
                    context,
                    "CONNECTIVITY_CHANGED",
                    "Internet connectivity changed on gateway device."
                )
            }
        }
    }

    private fun startAlarm(context: Context) {
        val alarmIntent = Intent(context, AlarmService::class.java)
        context.startForegroundService(alarmIntent)
    }
}