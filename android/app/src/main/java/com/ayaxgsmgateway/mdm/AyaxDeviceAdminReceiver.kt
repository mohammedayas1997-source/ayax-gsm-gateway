package com.ayaxgsmgateway.mdm

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AyaxDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(
            context,
            "Ayax Gateway Admin Enabled",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(
            context,
            "Ayax Gateway Admin Disabled",
            Toast.LENGTH_LONG
        ).show()
    }
}