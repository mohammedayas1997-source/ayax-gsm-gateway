package com.ayaxgsmgateway.security

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import com.ayaxgsmgateway.alarm.AlarmService

object GpsMonitor {

    fun checkGps(context: Context) {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val gpsEnabled =
            try {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (e: Exception) {
                false
            }

        if (!gpsEnabled) {
            SecurityManager.sendSecurityAlert(
                context,
                "GPS_DISABLED",
                "GPS/Location was disabled on gateway device."
            )

            val alarmIntent = Intent(context, AlarmService::class.java).apply {
                putExtra("reason", "GPS_DISABLED")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(alarmIntent)
            } else {
                context.startService(alarmIntent)
            }
        }
    }
}