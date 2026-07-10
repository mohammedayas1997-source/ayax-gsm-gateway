package com.ayaxgsmgateway

import android.app.Application
import com.ayaxgsmgateway.gsm.GsmPackage
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.ayaxgsmgateway.location.LocationPackage
import com.ayaxgsmgateway.device.DeviceManagerPackage
import com.ayaxgsmgateway.mdm.DevicePolicyPackage
class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          add(GsmPackage())
          add(LocationPackage())
          add(DeviceManagerPackage())
          add(DevicePolicyPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    loadReactNative(this)
  }
}