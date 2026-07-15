package com.ayaxgsmgateway.gsm.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

object UssdHelper {

    fun sendUssd(
        context: Context,
        ussdCode: String,
        simSlot: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (
                context.checkSelfPermission(Manifest.permission.CALL_PHONE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                onError("CALL_PHONE permission not granted")
                return
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                onError("USSD callback requires Android 8.0 or above")
                return
            }

            if (ussdCode.isBlank()) {
                onError("USSD code is required")
                return
            }

            val subscriptionId =
                SubscriptionHelper.getSubscriptionIdBySlot(
                    context,
                    simSlot
                )

            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE)
                    as TelephonyManager

            val simTelephonyManager =
                telephonyManager.createForSubscriptionId(subscriptionId)

            simTelephonyManager.sendUssdRequest(
                ussdCode,
                object : TelephonyManager.UssdResponseCallback() {

                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        response: CharSequence?
                    ) {
                        val message = response
                            ?.toString()
                            ?.trim()
                            .orEmpty()

                        if (message.isBlank()) {
                            onError("Empty USSD response received")
                            return
                        }

                        onSuccess(message)
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        failureCode: Int
                    ) {
                        onError(
                            "USSD failed with code: $failureCode"
                        )
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (error: Exception) {
            onError(error.message ?: "USSD failed")
        }
    }
}