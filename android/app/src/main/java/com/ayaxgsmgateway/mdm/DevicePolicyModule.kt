package com.ayaxgsmgateway.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.facebook.react.bridge.*

class DevicePolicyModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val dpm by lazy {
        reactContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val admin by lazy {
        ComponentName(
            reactContext,
            AyaxDeviceAdminReceiver::class.java
        )
    }

    override fun getName() = "DevicePolicyModule"

    @ReactMethod
    fun isAdminEnabled(promise: Promise) {
        promise.resolve(dpm.isAdminActive(admin))
    }

    @ReactMethod
    fun isDeviceOwner(promise: Promise) {
        promise.resolve(dpm.isDeviceOwnerApp(reactContext.packageName))
    }

    @ReactMethod
    fun lockDevice(promise: Promise) {
        try {
            if (!dpm.isAdminActive(admin)) {
                promise.reject("NOT_ADMIN", "Device Admin is not enabled")
                return
            }

            dpm.lockNow()
            promise.resolve(true)

        } catch (e: Exception) {
            promise.reject("LOCK_ERROR", e.message)
        }
    }
}