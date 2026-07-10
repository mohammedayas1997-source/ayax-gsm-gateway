package com.ayaxgsmgateway.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ayaxgsmgateway.alarm.AlarmService

class TheftReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            Intent.ACTION_POWER_DISCONNECTED -> {
                SecurityManager.sendSecurityAlert(
                    context,
                    "POWER_DISCONNECTED",
                    "Gateway charger was removed. Auto alarm started."
                )

                val alarmIntent = Intent(context, AlarmService::class.java)
                context.startForegroundService(alarmIntent)
            }

            Intent.ACTION_POWER_CONNECTED -> {
                SecurityManager.sendSecurityAlert(
                    context,
                    "POWER_CONNECTED",
                    "Gateway charger was connected."
                )
            }
        }
    }
}