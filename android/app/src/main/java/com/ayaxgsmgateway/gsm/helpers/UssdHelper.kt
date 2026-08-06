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
import android.telephony.TelephonyManager
import android.util.Log

import com.ayaxgsmgateway.gsm.manager.UssdSessionManager
import com.ayaxgsmgateway.gsm.model.SessionState

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

        Log.d(TAG, "sendUssd() called")

        Log.e(TAG, "============== SEND USSD START ==============")
        Log.e(TAG, "USSD = $ussdCode")
        Log.e(TAG, "SIM = $simSlot")

        try {

            if (
                context.checkSelfPermission(Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
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
                context,
                ussdCode,
                simSlot,
                subscriptionId
            )

            startSessionTracking(
                context,
                ussdCode,
                simSlot,
                subscriptionId
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                sendWithCallback(
                    context,
                    ussdCode,
                    subscriptionId,
                    simSlot,
                    onSuccess,
                    onError
                )

            } else {

                openDialerFallback(
                    context,
                    ussdCode,
                    subscriptionId,
                    simSlot,
                    onSuccess,
                    onError
                )

            }

        } catch (e: Exception) {

            Log.e(TAG, "sendUssd failed", e)
            onError(e.message ?: "USSD failed")

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
                telephonyManager.createForSubscriptionId(subscriptionId)

            simTelephonyManager.sendUssdRequest(
                ussdCode,
                object : TelephonyManager.UssdResponseCallback() {

                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        response: CharSequence?
                    ) {

                        val message =
                            response?.toString()?.trim().orEmpty()

                        if (message.isBlank()) {

                            openDialerFallback(
                                context,
                                ussdCode,
                                subscriptionId,
                                simSlot,
                                onSuccess,
                                onError
                            )

                            return
                        }

                        onSuccess(message)
                    }

                    override fun onReceiveUssdResponseFailed(
    telephonyManager: TelephonyManager?,
    request: String?,
    failureCode: Int
) {

    Log.e(
        TAG,
        "USSD FAILED = $failureCode"
    )

    if (failureCode == -1) {

        openDialerFallback(
            context,
            ussdCode,
            subscriptionId,
            simSlot,
            onSuccess,
            onError
        )

        return
    }

    onError("USSD failed: $failureCode")
}

                },
                Handler(Looper.getMainLooper())
            )

        } catch (e: Exception) {

            openDialerFallback(
                context,
                ussdCode,
                subscriptionId,
                simSlot,
                onSuccess,
                onError
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

            // Note: metadata + session tracking are already saved by the
            // sendUssd() call that dispatched here (either directly, or via
            // sendWithCallback()'s failure/exception paths) — re-saving here
            // was a redundant duplicate of that same work.

            val encoded =
                ussdCode.replace("#", Uri.encode("#"))

            val account =
                findPhoneAccountHandle(
                    context,
                    subscriptionId,
                    simSlot
                )

            val intent =
                Intent(
                    Intent.ACTION_CALL,
                    Uri.parse("tel:$encoded")
                ).apply {

                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    putExtra("slot", simSlot)
                    putExtra("simSlot", simSlot)
                    putExtra("subscription", subscriptionId)
                    putExtra("subscription_id", subscriptionId)
                    putExtra(
                        "android.telephony.extra.SUBSCRIPTION_INDEX",
                        subscriptionId
                    )

                    if (account != null) {
                        putExtra(
                            TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                            account
                        )
                    }
                }
            Log.d(TAG, "Launching ACTION_CALL")

            context.startActivity(intent)

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

        val prefs =
            context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

        prefs.edit()
            .putString("ussdCode", ussdCode)
            .putInt("simSlot", simSlot)
            .putInt("subscriptionId", subscriptionId)
            .putString(
                "requestType",
                detectRequestType(ussdCode)
            )
            .putLong(
                "requestedAt",
                System.currentTimeMillis()
            )
            .apply()
    }

    /**
     * Wires up UssdSessionManager, which previously had no caller anywhere
     * in the project — startSession() was never invoked, so currentSession
     * stayed null and every success()/failed()/waiting()/timeout() call
     * from UssdAccessibilityService was a silent no-op. The reference is
     * read back from prefs because GsmModule already writes it there
     * (from both sendUssd() and sendUssdWithSim()) before calling here.
     */
    private fun startSessionTracking(
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

        val reference = prefs.getString("reference", null)

        if (reference.isNullOrBlank()) {
            Log.e(TAG, "startSessionTracking: no reference in prefs, skipping")
            return
        }

        UssdSessionManager.startSession(
            SessionState(
                reference = reference,
                ussdCode = ussdCode,
                simSlot = simSlot,
                subscriptionId = subscriptionId,
                requestType = detectRequestType(ussdCode)
            )
        )
    }

    private fun detectRequestType(
        ussdCode: String
    ): String {

        val code =
            ussdCode.replace(" ", "").uppercase()

        return when {

            code.contains("*323#") ||
                    code.contains("*312#") ||
                    code.contains("*140#") ||
                    code.contains("*127#") ->
                "DATA"

            code.contains("*310#") ||
                    code.contains("*556#") ||
                    code.contains("*123#") ->
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

            val telecom =
                context.getSystemService(
                    Context.TELECOM_SERVICE
                ) as TelecomManager

            telecom.callCapablePhoneAccounts.firstOrNull {
                it.id.contains(subscriptionId.toString())
            } ?: telecom.callCapablePhoneAccounts.getOrNull(simSlot)

        } catch (e: Exception) {

            null

        }
    }
}