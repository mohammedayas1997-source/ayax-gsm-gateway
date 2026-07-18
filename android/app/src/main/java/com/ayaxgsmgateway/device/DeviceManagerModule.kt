package com.ayaxgsmgateway.device

import android.content.Intent
import com.facebook.react.bridge.*
import com.ayaxgsmgateway.alarm.AlarmService
import com.ayaxgsmgateway.security.NetworkMonitor
import com.ayaxgsmgateway.security.GpsMonitor
import com.ayaxgsmgateway.security.MotionService

class DeviceManagerModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "DeviceManagerModule"
    }

    @ReactMethod
fun startAlarm(promise: Promise) {
    try {
        val intent = Intent(
            reactContext,
            AlarmService::class.java
        ).apply {
            action = AlarmService.ACTION_START
        }

        reactContext.startForegroundService(intent)
        promise.resolve(true)
    } catch (error: Exception) {
        promise.reject(
            "ALARM_START_ERROR",
            error.message,
            error
        )
    }
}

@ReactMethod
fun stopAlarm(promise: Promise) {
    try {
        val intent = Intent(
            reactContext,
            AlarmService::class.java
        ).apply {
            action = AlarmService.ACTION_STOP
        }

        reactContext.startService(intent)
        promise.resolve(true)
    } catch (error: Exception) {
        promise.reject(
            "ALARM_STOP_ERROR",
            error.message,
            error
        )
    }
}