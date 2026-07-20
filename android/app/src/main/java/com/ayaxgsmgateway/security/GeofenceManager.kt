package com.ayaxgsmgateway.security

import android.content.Context
import android.location.Location
import com.ayaxgsmgateway.alarm.AlarmService
import android.content.Intent

object GeofenceManager {

    private const val OFFICE_RADIUS = 100f

    fun checkLocation(
        context: Context,
        latitude: Double,
        longitude: Double,
        officeLatitude: Double,
        officeLongitude: Double
    ) {

        val office = Location("office")
        office.latitude = officeLatitude
        office.longitude = officeLongitude

        val current = Location("current")
        current.latitude = latitude
        current.longitude = longitude

        val distance =
            current.distanceTo(office)

        if (distance > OFFICE_RADIUS) {

            SecurityManager.sendSecurityAlert(

                context,

                "OUTSIDE_GEOFENCE",

                "Gateway moved ${distance.toInt()} meters away."

            )

            val intent =
                Intent(
                    context,
                    AlarmService::class.java
                )


        }

    }

}