package com.ayaxgsmgateway.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ayaxgsmgateway.alarm.AlarmService

class TheftReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        try {
            when (action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    SecurityManager.sendSecurityAlert(
                        context,
                        "POWER_DISCONNECTED",
                        "Gateway charger was removed. Auto alarm started."
                    )

                    val alarmIntent = Intent(context, AlarmService::class.java).apply {
                        putExtra("reason", "POWER_DISCONNECTED")
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(alarmIntent)
                    } else {
                        context.startService(alarmIntent)
                    }
                }

                Intent.ACTION_POWER_CONNECTED -> {
                    SecurityManager.sendSecurityAlert(
                        context,
                        "POWER_CONNECTED",
                        "Gateway charger was connected."
                    )
                }
            }
        } catch (e: Exception) {
            // Log exception if needed
        }
    }
}