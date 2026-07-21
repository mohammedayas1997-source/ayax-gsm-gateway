package com.ayaxgsmgateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AYAX_SMS"

        private const val DEVICE_PREFS = "AYAX_DEVICE"
        private const val USSD_PREFS = "AYAX_USSD"

        private const val INCOMING_SMS_URL =
            "https://ayax-api-marketplace.onrender.com/api/v1/gateway/incoming-sms"

        private const val COMMAND_RESULT_URL =
            "https://ayax-api-marketplace.onrender.com/api/v1/gateway/result"

        // Kada tsohon pending USSD ya kama SMS bayan dogon lokaci.
        private const val PENDING_REQUEST_TIMEOUT_MS =
            5 * 60 * 1000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onReceive(
        context: Context?,
        intent: Intent?
    ) {
        if (context == null || intent == null) return

        if (
            intent.action !=
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) {
            return
        }

        val pendingResult = goAsync()

        try {
            val devicePrefs =
                context.getSharedPreferences(
                    DEVICE_PREFS,
                    Context.MODE_PRIVATE
                )

            val deviceId =
                devicePrefs.getString("deviceId", null)

            val secretKey =
                devicePrefs.getString("secretKey", null)

            if (
                deviceId.isNullOrBlank() ||
                secretKey.isNullOrBlank()
            ) {
                Log.e(TAG, "Device credentials not found")
                pendingResult.finish()
                return
            }

            val messages =
                Telephony.Sms.Intents
                    .getMessagesFromIntent(intent)

            if (messages.isNullOrEmpty()) {
                Log.e(TAG, "No SMS messages found")
                pendingResult.finish()
                return
            }

            val sender =
                messages.firstOrNull()
                    ?.originatingAddress
                    ?.trim()
                    .orEmpty()
                    .ifBlank { "Unknown" }

            val fullMessage =
                messages
                    .mapNotNull { it.messageBody }
                    .joinToString("")
                    .trim()

            if (fullMessage.isBlank()) {
                Log.e(TAG, "SMS body is empty")
                pendingResult.finish()
                return
            }

            val subscriptionId =
                resolveSubscriptionId(intent)

            val slotIndex =
                resolveSlotIndex(
                    context,
                    subscriptionId
                )

            Log.d(
                TAG,
                "SMS received: sender=$sender, " +
                    "subscriptionId=$subscriptionId, " +
                    "slotIndex=$slotIndex, " +
                    "message=$fullMessage"
            )

            val ussdPrefs =
                context.getSharedPreferences(
                    USSD_PREFS,
                    Context.MODE_PRIVATE
                )

            val reference =
                ussdPrefs.getString(
                    "reference",
                    null
                )

            val requestType =
                ussdPrefs.getString(
                    "requestType",
                    "USSD"
                ) ?: "USSD"

            val requestedSlot =
                ussdPrefs.getInt(
                    "simSlot",
                    -1
                )

            val waitingForSms =
                ussdPrefs.getBoolean(
                    "waitingForSms",
                    false
                )

            val requestedAt =
                ussdPrefs.getLong(
                    "requestedAt",
                    ussdPrefs.getLong(
                        "waitingSince",
                        0L
                    )
                )

            val requestStillValid =
                requestedAt > 0L &&
                    System.currentTimeMillis() -
                    requestedAt <=
                    PENDING_REQUEST_TIMEOUT_MS

            val slotMatches =
                requestedSlot < 0 ||
                    slotIndex < 0 ||
                    requestedSlot == slotIndex

            val isExpectedBalanceSms =
                !reference.isNullOrBlank() &&
                    requestStillValid &&
                    slotMatches &&
                    isBalanceSms(
                        message = fullMessage,
                        requestType = requestType
                    )

            if (isExpectedBalanceSms) {
                Log.d(
                    TAG,
                    "Balance SMS matched pending command. " +
                        "reference=$reference, " +
                        "requestType=$requestType, " +
                        "requestedSlot=$requestedSlot, " +
                        "receivedSlot=$slotIndex, " +
                        "waitingForSms=$waitingForSms"
                )

                sendBalanceCommandResult(
                    deviceId = deviceId,
                    secretKey = secretKey,
                    reference = reference!!,
                    message = fullMessage,
                    slotIndex = slotIndex,
                    requestType = requestType
                ) { resultSent ->

                    if (resultSent) {
                        clearPendingUssdRequest(
                            ussdPrefs
                        )
                    }

                    // Ko balance callback ya yi nasara ko ya gaza,
                    // har yanzu mu adana SMS ɗin a inbox.
                    sendSmsToBackend(
                        deviceId = deviceId,
                        secretKey = secretKey,
                        phoneNumber = sender,
                        message = fullMessage,
                        subscriptionId = subscriptionId,
                        slotIndex = slotIndex
                    ) {
                        pendingResult.finish()
                    }
                }

                return
            }

            // Normal incoming SMS.
            sendSmsToBackend(
                deviceId = deviceId,
                secretKey = secretKey,
                phoneNumber = sender,
                message = fullMessage,
                subscriptionId = subscriptionId,
                slotIndex = slotIndex
            ) {
                pendingResult.finish()
            }

        } catch (error: Exception) {
            Log.e(
                TAG,
                "SMS receiver failed",
                error
            )

            pendingResult.finish()
        }
    }

    private fun isBalanceSms(
        message: String,
        requestType: String
    ): Boolean {
        val text =
            message
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()

        val hasDataUnit =
            Regex(
                """\b\d+(?:\.\d+)?\s*(kb|mb|gb|tb)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(text)

        val hasMoney =
            Regex(
                """(?:₦|ngn|n)\s*\d+(?:[,.]\d+)?""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(text)

        val dataKeywords = listOf(
            "data balance",
            "bundle balance",
            "remaining data",
            "available data",
            "data bundle",
            "binge bundle",
            "youtube night",
            "night bundle",
            "social bundle",
            "bonus data",
            "main data",
            "data:",
            "bundle:"
        )

        val airtimeKeywords = listOf(
            "airtime balance",
            "account balance",
            "main balance",
            "credit balance",
            "available balance",
            "your balance is",
            "balance:"
        )

        val normalizedType =
            requestType.uppercase()

        return when (normalizedType) {
            "DATA" -> {
                hasDataUnit ||
                    dataKeywords.any {
                        text.contains(it)
                    }
            }

            "AIRTIME" -> {
                hasMoney ||
                    airtimeKeywords.any {
                        text.contains(it)
                    }
            }

            else -> {
                hasDataUnit ||
                    hasMoney ||
                    dataKeywords.any {
                        text.contains(it)
                    } ||
                    airtimeKeywords.any {
                        text.contains(it)
                    }
            }
        }
    }

    private fun resolveSubscriptionId(
        intent: Intent
    ): Int {
        val keys = listOf(
            "subscription",
            "subscription_id",
            "subscriptionId",
            "sub_id",
            "subId",
            "android.telephony.extra.SUBSCRIPTION_INDEX"
        )

        for (key in keys) {
            val value =
                intent.getIntExtra(
                    key,
                    SubscriptionManager
                        .INVALID_SUBSCRIPTION_ID
                )

            if (
                value !=
                SubscriptionManager
                    .INVALID_SUBSCRIPTION_ID
            ) {
                return value
            }
        }

        return SubscriptionManager
            .INVALID_SUBSCRIPTION_ID
    }

    private fun resolveSlotIndex(
        context: Context,
        subscriptionId: Int
    ): Int {
        if (
            subscriptionId ==
            SubscriptionManager
                .INVALID_SUBSCRIPTION_ID
        ) {
            return -1
        }

        return try {
            val manager =
                context.getSystemService(
                    Context
                        .TELEPHONY_SUBSCRIPTION_SERVICE
                ) as SubscriptionManager

            val info =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES
                        .LOLLIPOP_MR1
                ) {
                    manager
                        .getActiveSubscriptionInfo(
                            subscriptionId
                        )
                } else {
                    null
                }

            info?.simSlotIndex ?: -1

        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to resolve SIM slot",
                error
            )

            -1
        }
    }

    private fun sendBalanceCommandResult(
        deviceId: String,
        secretKey: String,
        reference: String,
        message: String,
        slotIndex: Int,
        requestType: String,
        onComplete: (Boolean) -> Unit
    ) {
        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("secretKey", secretKey)
            put("reference", reference)
            put("status", "SUCCESSFUL")
            put("message", message)
            put("response", message)
            put("simSlot", slotIndex)
            put("requestType", requestType)
        }

        val body =
            json.toString()
                .toRequestBody(
                    "application/json; charset=utf-8"
                        .toMediaType()
                )

        Log.d(
            TAG,
            "Sending balance result: " +
                "reference=$reference, " +
                "slotIndex=$slotIndex, " +
                "requestType=$requestType, " +
                "message=$message"
        )

        val request = Request.Builder()
            .url(COMMAND_RESULT_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    error: IOException
                ) {
                    Log.e(
                        TAG,
                        "Balance result failed: " +
                            "${error.message}",
                        error
                    )

                    onComplete(false)
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        val responseBody =
                            it.body
                                ?.string()
                                .orEmpty()

                        if (it.isSuccessful) {
                            Log.d(
                                TAG,
                                "Balance result sent: " +
                                    "${it.code} " +
                                    responseBody
                            )

                            onComplete(true)
                        } else {
                            Log.e(
                                TAG,
                                "Balance result rejected: " +
                                    "${it.code} " +
                                    responseBody
                            )

                            // Kada a goge reference idan backend ya ƙi.
                            onComplete(false)
                        }
                    }
                }
            }
        )
    }

    private fun sendSmsToBackend(
        deviceId: String,
        secretKey: String,
        phoneNumber: String,
        message: String,
        subscriptionId: Int,
        slotIndex: Int,
        onComplete: () -> Unit
    ) {
        val json = JSONObject().apply {
            put("deviceId", deviceId)
            put("secretKey", secretKey)
            put("phoneNumber", phoneNumber)
            put("message", message)
            put("subscriptionId", subscriptionId)
            put("slotIndex", slotIndex)
            put(
                "receivedAt",
                System.currentTimeMillis()
            )
        }

        val body =
            json.toString()
                .toRequestBody(
                    "application/json; charset=utf-8"
                        .toMediaType()
                )

        val request = Request.Builder()
            .url(INCOMING_SMS_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(
            object : Callback {

                override fun onFailure(
                    call: Call,
                    error: IOException
                ) {
                    Log.e(
                        TAG,
                        "SMS sync failed: " +
                            "${error.message}",
                        error
                    )

                    onComplete()
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        val responseBody =
                            it.body
                                ?.string()
                                .orEmpty()

                        if (it.isSuccessful) {
                            Log.d(
                                TAG,
                                "SMS synced: " +
                                    "${it.code} " +
                                    responseBody
                            )
                        } else {
                            Log.e(
                                TAG,
                                "SMS sync rejected: " +
                                    "${it.code} " +
                                    responseBody
                            )
                        }
                    }

                    onComplete()
                }
            }
        )
    }

    private fun clearPendingUssdRequest(
        prefs: android.content.SharedPreferences
    ) {
        prefs.edit()
            .remove("reference")
            .remove("simSlot")
            .remove("subscriptionId")
            .remove("requestType")
            .remove("ussdCode")
            .remove("waitingForSms")
            .remove("waitingSince")
            .remove("requestedAt")
            .apply()

        Log.d(
            TAG,
            "Pending USSD request cleared"
        )
    }
}