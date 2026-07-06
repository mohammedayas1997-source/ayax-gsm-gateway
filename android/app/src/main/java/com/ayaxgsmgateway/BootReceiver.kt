package com.ayaxgsmgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    if (
      intent?.action == Intent.ACTION_BOOT_COMPLETED ||
      intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
    ) {
      Log.d("AYAX_BOOT", "Device booted or app updated")

      val launchIntent =
        context.packageManager.getLaunchIntentForPackage(context.packageName)

      launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      context.startActivity(launchIntent)
    }
  }
}