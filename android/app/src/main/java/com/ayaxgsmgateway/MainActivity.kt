package com.ayaxgsmgateway

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "AyaxGSMGateway"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(
            this,
            mainComponentName,
            fabricEnabled
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isAccessibilityEnabled()) {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service =
            "$packageName/com.ayaxgsmgateway.accessibility.UssdAccessibilityService"

        val enabled =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

        return enabled.contains(service, true)
    }
}