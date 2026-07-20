package com.ayaxgsmgateway.mdm

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class DevicePolicyModule(
    private val reactContext: ReactApplicationContext
) : ReactContextBaseJavaModule(reactContext) {

    private val devicePolicyManager: DevicePolicyManager by lazy {
        reactContext.getSystemService(
            Context.DEVICE_POLICY_SERVICE
        ) as DevicePolicyManager
    }

    private val adminComponent: ComponentName by lazy {
        ComponentName(
            reactContext,
            AyaxDeviceAdminReceiver::class.java
        )
    }

    override fun getName(): String = "DevicePolicyModule"

    @ReactMethod
    fun isAdminEnabled(promise: Promise) {
        promise.resolve(
            devicePolicyManager.isAdminActive(adminComponent)
        )
    }

    @ReactMethod
    fun isDeviceOwner(promise: Promise) {
        promise.resolve(
            devicePolicyManager.isDeviceOwnerApp(
                reactContext.packageName
            )
        )
    }

    @ReactMethod
    fun applyOwnerPolicies(promise: Promise) {
        try {
            if (!devicePolicyManager.isDeviceOwnerApp(
                    reactContext.packageName
                )
            ) {
                promise.reject(
                    "NOT_DEVICE_OWNER",
                    "App is not Device Owner"
                )
                return
            }

            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_FACTORY_RESET
            )

            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_SAFE_BOOT
            )

            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_USB_FILE_TRANSFER
            )

            devicePolicyManager.setUninstallBlocked(
                adminComponent,
                reactContext.packageName,
                true
            )

            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject(
                "POLICY_ERROR",
                error.message,
                error
            )
        }
    }

    @ReactMethod
    fun lockDevice(promise: Promise) {
        try {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                promise.reject(
                    "NOT_ADMIN",
                    "Device Admin is not enabled"
                )
                return
            }

            devicePolicyManager.lockNow()
            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject(
                "LOCK_ERROR",
                error.message,
                error
            )
        }
    }

    @ReactMethod
    fun allowAppRemoval(promise: Promise) {
        try {
            if (!devicePolicyManager.isDeviceOwnerApp(
                    reactContext.packageName
                )
            ) {
                promise.reject(
                    "NOT_DEVICE_OWNER",
                    "App is not Device Owner"
                )
                return
            }

            devicePolicyManager.setUninstallBlocked(
                adminComponent,
                reactContext.packageName,
                false
            )

            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject(
                "REMOVE_POLICY_ERROR",
                error.message,
                error
            )
        }
    }
}