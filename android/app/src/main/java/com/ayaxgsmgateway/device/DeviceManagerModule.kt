package com.ayaxgsmgateway.device

import android.content.Intent
import android.os.Build
import com.ayaxgsmgateway.alarm.AlarmService
import com.ayaxgsmgateway.security.GpsMonitor
import com.ayaxgsmgateway.security.MotionService
import com.ayaxgsmgateway.security.NetworkMonitor
import com.facebook.react.bridge.*

class DeviceManagerModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String = "DeviceManagerModule"

    @ReactMethod
    fun startAlarm(promise: Promise) {
        try {

            val intent = Intent(
                reactContext,
                AlarmService::class.java
            ).apply {
                action = AlarmService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactContext.startForegroundService(intent)
            } else {
                reactContext.startService(intent)
            }

            promise.resolve(true)

        } catch (e: Exception) {

            promise.reject(
                "ALARM_START_ERROR",
                e.message,
                e
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

        } catch (e: Exception) {

            promise.reject(
                "ALARM_STOP_ERROR",
                e.message,
                e
            )

        }
    }

    @ReactMethod
    fun startMotionSecurity(promise: Promise) {
        try {

            val intent = Intent(
                reactContext,
                MotionService::class.java
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                reactContext.startForegroundService(intent)
            } else {
                reactContext.startService(intent)
            }

            NetworkMonitor.start(reactContext)
            GpsMonitor.checkGps(reactContext)

            promise.resolve(true)

        } catch (e: Exception) {

            promise.reject(
                "MOTION_START_ERROR",
                e.message,
                e
            )

        }
    }

    @ReactMethod
    fun stopMotionSecurity(promise: Promise) {
        try {

            val intent = Intent(
                reactContext,
                MotionService::class.java
            )

            reactContext.stopService(intent)

            promise.resolve(true)

        } catch (e: Exception) {

            promise.reject(
                "MOTION_STOP_ERROR",
                e.message,
                e
            )

        }
    }
}