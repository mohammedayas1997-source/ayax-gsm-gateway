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
import java.util.concurrent.atomic.AtomicBoolean

object UssdHelper {

    private const val TAG = "AYAX_USSD"
    private const val PREFS_NAME = "AYAX_USSD"

    /*
     * Idan native USSD bai dawo da response ba,
     * bayan sakan 12 za a gwada Dialer.
     */
    private const val NATIVE_TIMEOUT_MS = 12_000L

    fun sendUssd(
        context: Context,
        ussdCode: String,
        simSlot: Int,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (
                context.checkSelfPermission(
                    Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                onError(
                    "CALL_PHONE permission not granted"
                )
                return
            }

            val cleanUssdCode =
                ussdCode
                    .replace(" ", "")
                    .trim()

            if (cleanUssdCode.isBlank()) {
                onError("USSD code is required")
                return
            }

            if (
                !cleanUssdCode.startsWith("*") ||
                !cleanUssdCode.endsWith("#")
            ) {
                onError(
                    "Invalid USSD format: $cleanUssdCode"
                )
                return
            }

            val subscriptionId =
                SubscriptionHelper
                    .getSubscriptionIdBySlot(
                        context,
                        simSlot
                    )

            if (
                subscriptionId ==
                SubscriptionManager
                    .INVALID_SUBSCRIPTION_ID
            ) {
                onError(
                    "No active subscription found for SIM ${simSlot + 1}"
                )
                return
            }

            val requestType =
                detectRequestType(
                    cleanUssdCode
                )

            Log.d(
                TAG,
                "USSD command received: " +
                    "code=$cleanUssdCode, " +
                    "type=$requestType, " +
                    "simSlot=$simSlot, " +
                    "subscriptionId=$subscriptionId"
            )

            savePendingMetadata(
                context = context,
                ussdCode = cleanUssdCode,
                simSlot = simSlot,
                subscriptionId =
                    subscriptionId
            )

            when (requestType) {

                /*
                 * DATA:
                 * An bar tsarin DATA yadda yake.
                 */
                "DATA" -> {
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.O
                    ) {
                        sendWithCallback(
                            context = context,
                            ussdCode =
                                cleanUssdCode,
                            subscriptionId =
                                subscriptionId,
                            simSlot = simSlot,
                            useTimeoutFallback =
                                false,
                            onSuccess =
                                onSuccess,
                            onError =
                                onError
                        )
                    } else {
                        openDialerFallback(
                            context = context,
                            ussdCode =
                                cleanUssdCode,
                            subscriptionId =
                                subscriptionId,
                            simSlot = simSlot,
                            onSuccess =
                                onSuccess,
                            onError =
                                onError
                        )
                    }
                }

                /*
                 * AIRTIME:
                 * Native callback da farko.
                 * Idan bai dawo ba, Dialer fallback.
                 */
                "AIRTIME" -> {
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.O
                    ) {
                        sendWithCallback(
                            context = context,
                            ussdCode =
                                cleanUssdCode,
                            subscriptionId =
                                subscriptionId,
                            simSlot = simSlot,
                            useTimeoutFallback =
                                true,
                            onSuccess =
                                onSuccess,
                            onError =
                                onError
                        )
                    } else {
                        openDialerFallback(
                            context = context,
                            ussdCode =
                                cleanUssdCode,
                            subscriptionId =
                                subscriptionId,
                            simSlot = simSlot,
                            onSuccess =
                                onSuccess,
                            onError =
                                onError
                        )
                    }
                }

                /*
                 * Sauran USSD commands.
                 */
                else -> {
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.O
                    ) {
                        sendWithCallback(
                            context = context,
                            ussdCode =
                                cleanUssdCode,
                            subscriptionId =
                                subscriptionId,
                            simSlot = simSlot,
                            useTimeoutFallback =
                                true,
                            onSuccess =
                                onSuccess,
                            onError =
                                onError
                        )
                    } else {
                        openDialerFallback(
                            context = context,
                            ussdCode =
                                cleanUssdCode,
                            subscriptionId =
                                subscriptionId,
                            simSlot = simSlot,
                            onSuccess =
                                onSuccess,
                            onError =
                                onError
                        )
                    }
                }
            }
        } catch (error: Exception) {
            Log.e(
                TAG,
                "sendUssd failed",
                error
            )

            onError(
                error.message
                    ?: "USSD command failed"
            )
        }
    }

    private fun sendWithCallback(
        context: Context,
        ussdCode: String,
        subscriptionId: Int,
        simSlot: Int,
        useTimeoutFallback: Boolean,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler =
            Handler(Looper.getMainLooper())

        val completed =
            AtomicBoolean(false)

        var timeoutRunnable:
            Runnable? = null

        try {
            val baseTelephonyManager =
                context.getSystemService(
                    Context.TELEPHONY_SERVICE
                ) as TelephonyManager

            val simTelephonyManager =
                baseTelephonyManager
                    .createForSubscriptionId(
                        subscriptionId
                    )

            Log.d(
                TAG,
                "Sending native USSD: " +
                    "$ussdCode on SIM ${simSlot + 1}, " +
                    "subscription=$subscriptionId"
            )

            if (useTimeoutFallback) {
                timeoutRunnable =
                    Runnable {
                        if (
                            completed.compareAndSet(
                                false,
                                true
                            )
                        ) {
                            Log.w(
                                TAG,
                                "Native USSD timeout. " +
                                    "Opening dialer fallback."
                            )

                            openDialerFallback(
                                context = context,
                                ussdCode =
                                    ussdCode,
                                subscriptionId =
                                    subscriptionId,
                                simSlot =
                                    simSlot,
                                onSuccess =
                                    onSuccess,
                                onError =
                                    onError
                            )
                        }
                    }

                mainHandler.postDelayed(
                    timeoutRunnable,
                    NATIVE_TIMEOUT_MS
                )
            }

            simTelephonyManager.sendUssdRequest(
                ussdCode,

                object :
                    TelephonyManager
                        .UssdResponseCallback() {

                    override fun onReceiveUssdResponse(
                        telephonyManager:
                            TelephonyManager?,
                        request: String?,
                        response:
                            CharSequence?
                    ) {
                        if (
                            !completed.compareAndSet(
                                false,
                                true
                            )
                        ) {
                            return
                        }

                        timeoutRunnable?.let {
                            mainHandler
                                .removeCallbacks(it)
                        }

                        val message =
                            response
                                ?.toString()
                                ?.trim()
                                .orEmpty()

                        Log.d(
                            TAG,
                            "Native USSD response: $message"
                        )

                        if (message.isBlank()) {
                            openDialerFallback(
                                context =
                                    context,
                                ussdCode =
                                    ussdCode,
                                subscriptionId =
                                    subscriptionId,
                                simSlot =
                                    simSlot,
                                onSuccess =
                                    onSuccess,
                                onError =
                                    onError
                            )

                            return
                        }

                        onSuccess(message)
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager:
                            TelephonyManager?,
                        request: String?,
                        failureCode: Int
                    ) {
                        if (
                            !completed.compareAndSet(
                                false,
                                true
                            )
                        ) {
                            return
                        }

                        timeoutRunnable?.let {
                            mainHandler
                                .removeCallbacks(it)
                        }

                        Log.w(
                            TAG,
                            "Native USSD failed: " +
                                "code=$failureCode, " +
                                "request=$request"
                        )

                        openDialerFallback(
                            context = context,
                            ussdCode =
                                ussdCode,
                            subscriptionId =
                                subscriptionId,
                            simSlot = simSlot,
                            onSuccess =
                                onSuccess,
                            onError =
                                onError
                        )
                    }
                },

                mainHandler
            )
        } catch (error: Exception) {
            timeoutRunnable?.let {
                mainHandler.removeCallbacks(it)
            }

            Log.e(
                TAG,
                "Native USSD failed; " +
                    "opening dialer fallback",
                error
            )

            if (
                completed.compareAndSet(
                    false,
                    true
                )
            ) {
                openDialerFallback(
                    context = context,
                    ussdCode = ussdCode,
                    subscriptionId =
                        subscriptionId,
                    simSlot = simSlot,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
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
                subscriptionId =
                    subscriptionId
            )

            /*
             * Uri.encode() zai kare # da sauran
             * special characters yadda ya dace.
             */
            val encodedCode =
                Uri.encode(ussdCode)

            val phoneAccountHandle =
                findPhoneAccountHandle(
                    context,
                    subscriptionId,
                    simSlot
                )

            val intent =
                Intent(
                    Intent.ACTION_CALL,
                    Uri.parse(
                        "tel:$encodedCode"
                    )
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )

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

                    if (
                        phoneAccountHandle !=
                        null
                    ) {
                        putExtra(
                            TelecomManager
                                .EXTRA_PHONE_ACCOUNT_HANDLE,
                            phoneAccountHandle
                        )
                    }
                }

            context.startActivity(intent)

            Log.d(
                TAG,
                "Dialer fallback opened: " +
                    "$ussdCode on SIM ${simSlot + 1}, " +
                    "subscription=$subscriptionId"
            )

            /*
             * Kada a kira onSuccess a nan.
             * Accessibility Service ne zai karɓi
             * USSD dialog response.
             */
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Dialer fallback failed",
                error
            )

            onError(
                error.message
                    ?: "Unable to execute USSD on selected SIM"
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
            prefs.getString(
                "reference",
                null
            )

        val requestType =
            detectRequestType(
                ussdCode
            )

        prefs.edit()
            .putString(
                "reference",
                existingReference
            )
            .putString(
                "ussdCode",
                ussdCode
            )
            .putInt(
                "simSlot",
                simSlot
            )
            .putInt(
                "subscriptionId",
                subscriptionId
            )
            .putString(
                "requestType",
                requestType
            )
            .putBoolean(
                "waitingForSms",
                false
            )
            .putLong(
                "requestedAt",
                System.currentTimeMillis()
            )
            .apply()
    }

    private fun detectRequestType(
        ussdCode: String
    ): String {
        val normalized =
            ussdCode
                .replace(" ", "")
                .uppercase()

        /*
         * BANGAREN DATA:
         * An bar shi yadda yake.
         */
        return when {
            normalized.contains("*323#") ||
                normalized.contains("*312#") ||
                normalized.contains("*140#") ||
                normalized.contains("*127#") ->
                "DATA"

            normalized.startsWith("*310") ||
                normalized.startsWith("*556") ||
                normalized.startsWith("*123") ->
                "AIRTIME"

            else ->
                "USSD"
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
                telecomManager
                    .callCapablePhoneAccounts

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