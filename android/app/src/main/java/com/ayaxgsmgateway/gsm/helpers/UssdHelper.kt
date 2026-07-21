package com.ayaxgsmgateway.gsm.helpers

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
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
        try {
            if (
                context.checkSelfPermission(Manifest.permission.CALL_PHONE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                onError("CALL_PHONE permission not granted")
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

            savePendingMetadata(
                context = context,
                ussdCode = ussdCode,
                simSlot = simSlot,
                subscriptionId = subscriptionId
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                sendWithCallback(
                    context = context,
                    ussdCode = ussdCode,
                    subscriptionId = subscriptionId,
                    simSlot = simSlot,
                    onSuccess = onSuccess,
                    onError = onError
                )
            } else {
                openDialerFallback(
                    context = context,
                    ussdCode = ussdCode,
                    subscriptionId = subscriptionId,
                    simSlot = simSlot,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
        } catch (error: Exception) {
            Log.e(TAG, "sendUssd failed", error)
            onError(error.message ?: "USSD failed")
        }
    }

    private fun sendWithCallback(
        context: Context,
        ussdCode: String,
        subscriptionId: Int,
        simSlot: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE)
                    as TelephonyManager

            val simTelephonyManager =
                telephonyManager.createForSubscriptionId(
                    subscriptionId
                )

            simTelephonyManager.sendUssdRequest(
                ussdCode,
                object : TelephonyManager.UssdResponseCallback() {

                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        response: CharSequence?
                    ) {
                        val message =
                            response
                                ?.toString()
                                ?.trim()
                                .orEmpty()

                        if (message.isBlank()) {
                            openDialerFallback(
                                context = context,
                                ussdCode = ussdCode,
                                subscriptionId = subscriptionId,
                                simSlot = simSlot,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                            return
                        }

                        Log.d(TAG, "Native USSD response: $message")
                        onSuccess(message)
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        failureCode: Int
                    ) {
                        Log.w(
                            TAG,
                            "Native USSD failed with code: $failureCode"
                        )

                        if (failureCode == -1) {
                            openDialerFallback(
                                context = context,
                                ussdCode = ussdCode,
                                subscriptionId = subscriptionId,
                                simSlot = simSlot,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                            return
                        }

                        onError(
                            "USSD failed with code: $failureCode"
                        )
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Native USSD callback failed; using dialer fallback",
                error
            )

            openDialerFallback(
                context = context,
                ussdCode = ussdCode,
                subscriptionId = subscriptionId,
                simSlot = simSlot,
                onSuccess = onSuccess,
                onError = onError
            )
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
                context = context,
                ussdCode = ussdCode,
                simSlot = simSlot,
                subscriptionId = subscriptionId
            )

            val encodedCode =
                ussdCode.replace(
                    "#",
                    Uri.encode("#")
                )

            val phoneAccountHandle =
                findPhoneAccountHandle(
                    context,
                    subscriptionId,
                    simSlot
                )

            val intent =
                Intent(
                    Intent.ACTION_CALL,
                    Uri.parse("tel:$encodedCode")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    putExtra(
                        "com.android.phone.extra.slot",
                        simSlot
                    )

                    putExtra(
                        "slot",
                        simSlot
                    )

                    putExtra(
                        "simSlot",
                        simSlot
                    )

                    putExtra(
                        "subscription",
                        subscriptionId
                    )

                    putExtra(
                        "subscription_id",
                        subscriptionId
                    )

                    putExtra(
                        "android.telephony.extra.SUBSCRIPTION_INDEX",
                        subscriptionId
                    )

                    if (phoneAccountHandle != null) {
                        putExtra(
                            TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                            phoneAccountHandle
                        )
                    }
                }

            context.startActivity(intent)

            Log.d(
                TAG,
                "Dialer fallback started on SIM slot $simSlot, subscription $subscriptionId"
            )

            onSuccess(
                "USSD request opened on SIM ${simSlot + 1}; awaiting network response"
            )
        } catch (error: Exception) {
            Log.e(TAG, "Dialer fallback failed", error)

            onError(
                error.message
                    ?: "Unable to open USSD on selected SIM"
            )
        }
    }

    private fun savePendingMetadata(
        context: Context,
        ussdCode: String,
        simSlot: Int,
        subscriptionId: Int
    ) {
        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        val existingReference =
            prefs.getString("reference", null)

        val requestType =
            detectRequestType(ussdCode)

        prefs.edit()
            .putString("reference", existingReference)
            .putString("ussdCode", ussdCode)
            .putInt("simSlot", simSlot)
            .putInt("subscriptionId", subscriptionId)
            .putString("requestType", requestType)
            .putBoolean("waitingForSms", false)
            .putLong("requestedAt", System.currentTimeMillis())
            .apply()
    }

    private fun detectRequestType(
        ussdCode: String
    ): String {
        val normalized =
            ussdCode
                .replace(" ", "")
                .uppercase()

        return when {
            normalized.contains("*323#") ||
            normalized.contains("*312#") ||
            normalized.contains("*140#") ||
            normalized.contains("*127#") ->
                "DATA"

            normalized.contains("*310#") ||
            normalized.contains("*556#") ||
            normalized.contains("*123#") ->
                "AIRTIME"

            else -> "USSD"
        }
    }

    private fun findPhoneAccountHandle(
        context: Context,
        subscriptionId: Int,
        simSlot: Int
    ): PhoneAccountHandle? {
        return try {
            val telecomManager =
                context.getSystemService(
                    Context.TELECOM_SERVICE
                ) as TelecomManager

            val accounts =
                telecomManager.callCapablePhoneAccounts

            accounts.firstOrNull { handle ->
                handle.id.contains(
                    subscriptionId.toString(),
                    ignoreCase = true
                )
            } ?: accounts.getOrNull(simSlot)
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to resolve phone account handle",
                error
            )

            null
        }
    }
}