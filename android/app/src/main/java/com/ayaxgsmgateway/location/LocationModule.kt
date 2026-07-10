package com.ayaxgsmgateway.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import com.facebook.react.bridge.*
import com.ayaxgsmgateway.security.GeofenceManager

class LocationModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "LocationModule"
    }

    @ReactMethod
    fun getCurrentLocation(promise: Promise) {
        try {
            if (
                reactContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                promise.reject("PERMISSION_DENIED", "Location permission not granted")
                return
            }

            val locationManager =
                reactContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            val provider =
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    LocationManager.GPS_PROVIDER
                } else {
                    LocationManager.NETWORK_PROVIDER
                }

            val location = locationManager.getLastKnownLocation(provider)

            if (location == null) {
                promise.reject("LOCATION_NOT_FOUND", "Unable to get device location")
                return
            }

            val result = Arguments.createMap()
            result.putDouble("latitude", location.latitude)
            result.putDouble("longitude", location.longitude)
            result.putDouble("accuracy", location.accuracy.toDouble())
            result.putDouble("speed", location.speed.toDouble())
            result.putDouble("bearing", location.bearing.toDouble())
            result.putDouble("time", location.time.toDouble())

            promise.resolve(result)
            GeofenceManager.checkLocation(
                reactContext,
                location.latitude,
                location.longitude,
                12.0022,   // OFFICE LATITUDE
                8.5920     // OFFICE LONGITUDE
            )

        } catch (e: Exception) {
            promise.reject("LOCATION_ERROR", e.message)
        }
    }
}