package com.ayaxgsmgateway.gsm.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

object SubscriptionHelper {

  fun hasPhonePermission(context: Context): Boolean {
    return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
      PackageManager.PERMISSION_GRANTED
  }

  fun getActiveSubscriptions(context: Context): List<SubscriptionInfo> {
    if (!hasPhonePermission(context)) {
      throw Exception("READ_PHONE_STATE permission not granted")
    }

    val manager =
      context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

    return manager.activeSubscriptionInfoList ?: emptyList()
  }

  fun getSubscriptionBySlot(
    context: Context,
    simSlot: Int
  ): SubscriptionInfo {
    val sims = getActiveSubscriptions(context)

    val sim = sims.find { it.simSlotIndex == simSlot }

    if (sim == null) {
      throw Exception("SIM slot $simSlot not found or not active")
    }

    return sim
  }

  fun getSubscriptionIdBySlot(
    context: Context,
    simSlot: Int
  ): Int {
    return getSubscriptionBySlot(context, simSlot).subscriptionId
  }
}