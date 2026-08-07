package com.ayaxgsmgateway.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class DeviceStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AYAX_DEVICE_STATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val action = intent.action ?: return

            when (action) {
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
                    }
                }

                "android.intent.action.SIM_STATE_CHANGED" -> {
                    SecurityManager.sendSecurityAlert(
                        context,
                        "SIM_STATE_CHANGED",
                        "SIM state changed. Possible SIM removed or replaced."
                    )
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
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Device state receiver error",
                error
            )
        }
    }
}