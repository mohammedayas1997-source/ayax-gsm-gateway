package com.ayaxgsmgateway.gsm.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log

object SubscriptionHelper {

    private const val TAG = "AYAX_SUBSCRIPTION"

    fun hasPhonePermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun getActiveSubscriptions(context: Context): List<SubscriptionInfo> {
        if (!hasPhonePermission(context)) {
            Log.e(TAG, "READ_PHONE_STATE permission not granted")
            return emptyList()
        }

        return try {
            val manager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val subList = manager?.activeSubscriptionInfoList
            
            if (subList.isNullOrEmpty()) {
                Log.w(TAG, "No active subscriptions found or list is null.")
                emptyList()
            } else {
                subList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active subscriptions", e)
            emptyList()
        }
    }

    fun getSubscriptionBySlot(
        context: Context,
        simSlot: Int
    ): SubscriptionInfo? {
        val sims = getActiveSubscriptions(context)
        
        // Da farko muna neman wanda ya dace da simSlot index kai tsaye
        var sim = sims.find { it.simSlotIndex == simSlot }

        // Idan aka rasa ta hanyar simSlot index, amma akwai akalla SIM daya a aiki, zamu iya amfani da ita matsayin fallback
        if (sim == null && sims.isNotEmpty()) {
            Log.w(TAG, "SIM slot $simSlot not directly matched, falling back to available subscription index.")
            sim = sims.getOrNull(0)
        }

        return sim
    }

    fun getSubscriptionIdBySlot(
        context: Context,
        simSlot: Int
    ): Int {
        val subscriptionInfo = getSubscriptionBySlot(context, simSlot)
        
        return if (subscriptionInfo != null) {
            subscriptionInfo.subscriptionId
        } else {
            Log.e(TAG, "Failed to resolve subscriptionId for slot $simSlot, returning default -1")
            -1
        }
    }
}