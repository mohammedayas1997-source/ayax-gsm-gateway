package com.ayaxgsmgateway.device

import android.content.Intent
import com.facebook.react.bridge.*
import com.ayaxgsmgateway.alarm.AlarmService
import com.ayaxgsmgateway.security.NetworkMonitor
import com.ayaxgsmgateway.security.GpsMonitor

class DeviceManagerModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "DeviceManagerModule"
    }

    @ReactMethod
    fun startAlarm(promise: Promise) {
        try {
            val intent =
                Intent(
                    reactContext,
                    AlarmService::class.java
                )

            reactContext.startForegroundService(intent)

            promise.resolve(true)

        } catch (e: Exception) {
            promise.reject("ALARM_START_ERROR", e.message)
        }
    }

    @ReactMethod
    fun stopAlarm(promise: Promise) {
        try {
            val intent =
                Intent(
                    reactContext,
                    AlarmService::class.java
                )

            reactContext.stopService(intent)

            promise.resolve(true)

        } catch (e: Exception) {
            promise.reject("ALARM_STOP_ERROR", e.message)
        }
    }
}

@ReactMethod
fun startMotionSecurity(promise: Promise) {
    try {

        val intent =
            Intent(
                reactContext,
                com.ayaxgsmgateway.security.MotionService::class.java
            )

        reactContext.startForegroundService(intent)

        NetworkMonitor.start(reactContext)
        GpsMonitor.checkGps(reactContext)

        promise.resolve(true)

    } catch (e: Exception) {

        promise.reject(
            "MOTION_START_ERROR",
            e.message
        )

    }
}