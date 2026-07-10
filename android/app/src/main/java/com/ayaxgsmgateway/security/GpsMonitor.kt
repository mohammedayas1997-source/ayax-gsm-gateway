package com.ayaxgsmgateway.security

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import com.ayaxgsmgateway.alarm.AlarmService

object GpsMonitor {

    fun checkGps(context: Context) {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val gpsEnabled =
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!gpsEnabled) {
            SecurityManager.sendSecurityAlert(
                context,
                "GPS_DISABLED",
                "GPS/Location was disabled on gateway device."
            )

            val alarmIntent = Intent(context, AlarmService::class.java)
            context.startForegroundService(alarmIntent)
        }
    }
}