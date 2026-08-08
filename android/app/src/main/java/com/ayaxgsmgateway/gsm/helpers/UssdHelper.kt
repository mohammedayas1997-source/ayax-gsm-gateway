package com.ayaxgsmgateway.gsm.helpers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

object UssdHelper {

    private const val TAG = "AYAX_USSD"
    private const val PREFS_NAME = "AYAX_USSD"

    fun sendUssd(
        context: Context,
        ussdCode: String,
        simSlot: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Log.e(TAG, "============== SEND USSD START ==============")
        Log.e(TAG, "USSD = $ussdCode")
        Log.e(TAG, "SIM = $simSlot")

        try {
            if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                onError("CALL_PHONE permission not granted")
                return
            }

            if (ussdCode.isBlank()) {
                onError("USSD code is required")
                return
            }

            val subscriptionId = SubscriptionHelper.getSubscriptionIdBySlot(context, simSlot)

            savePendingMetadata(
                context,
                ussdCode,
                simSlot,
                subscriptionId
            )

            openDialerFallback(
                context,
                ussdCode,
                subscriptionId,
                simSlot,
                onSuccess,
                onError
            )

        } catch (e: Exception) {
            Log.e(TAG, "sendUssd failed", e)
            onError(e.message ?: "USSD failed")
        }
    }

    private fun openDialerFallback(
        context: Context,
        ussdCode: String,
        subscriptionId: Int,
        simSlot: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            savePendingMetadata(
                context,
                ussdCode,
                simSlot,
                subscriptionId
            )

            val encoded = ussdCode.replace("#", Uri.encode("#"))
            val account = findPhoneAccountHandle(context, subscriptionId, simSlot)

            val intent = Intent(
                Intent.ACTION_CALL,
                Uri.parse("tel:$encoded")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                putExtra("slot", simSlot)
                putExtra("simSlot", simSlot)
                putExtra("subscription", subscriptionId)
                putExtra("subscription_id", subscriptionId)
                putExtra("android.telephony.extra.SUBSCRIPTION_INDEX", subscriptionId)

                if (account != null) {
                    putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
                }
            }

            context.startActivity(intent)
            
            // Mun maidawa onSuccess bayani mara rudani ko kuma kawai mu bar shi yana cewa USSD initiated,
            // amma Accessibility Service ce za ta kama ainihin sakamakon karshe (SUCCESSFUL/FAILED)
            onSuccess("USSD initiated successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Dialer fallback failed", e)
            onError(e.message ?: "Unable to open USSD")
        }
    }

    private fun savePendingMetadata(
        context: Context,
        ussdCode: String,
        simSlot: Int,
        subscriptionId: Int
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        prefs.edit()
            .putString("ussdCode", ussdCode)
            .putInt("simSlot", simSlot)
            .putInt("subscriptionId", subscriptionId)
            .putString("requestType", detectRequestType(ussdCode))
            .putLong("requestedAt", System.currentTimeMillis())
            .apply()
    }

    private fun detectRequestType(ussdCode: String): String {
        val code = ussdCode.replace(" ", "").uppercase()

        return when {
            code.contains("*323*4#") ||
                    code.contains("*312#") ||
                    code.contains("*140#") ||
                    code.contains("*127#") -> "DATA"

            code.contains("*310#") ||
                    code.contains("*556#") ||
                    code.contains("*123#") -> "AIRTIME"

            else -> "USSD"
        }
    }

    private fun findPhoneAccountHandle(
        context: Context,
        subscriptionId: Int,
        simSlot: Int
    ): PhoneAccountHandle? {
        return try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

            telecom.callCapablePhoneAccounts.firstOrNull {
                it.id.contains(subscriptionId.toString())
            } ?: telecom.callCapablePhoneAccounts.getOrNull(simSlot)

        } catch (e: Exception) {
            null
        }
    }
}