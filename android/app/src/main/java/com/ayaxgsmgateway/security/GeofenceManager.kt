package com.ayaxgsmgateway.security

import android.content.Context
import android.location.Location
import com.ayaxgsmgateway.alarm.AlarmService
import android.content.Intent
import android.os.Build

object GeofenceManager {

    private const val OFFICE_RADIUS = 100f

    fun checkLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        officeLatitude: Double,
        officeLongitude: Double
    ) {
        val office = Location("office").apply {
            this.latitude = officeLatitude
            this.longitude = officeLongitude
        }

        val current = Location("current").apply {
            this.latitude = latitude
            this.longitude = longitude
        }

        val distance = current.distanceTo(office)

        if (distance > OFFICE_RADIUS) {
            SecurityManager.sendSecurityAlert(
                context,
                "OUTSIDE_GEOFENCE",
                "Gateway moved ${distance.toInt()} meters away."
            )

            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra("reason", "OUTSIDE_GEOFENCE")
                putExtra("distance", distance)
            }

            // Tabbatar an fara sabis din (Foreground Service) domin tana bukata a sabbin sigogin Android
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}