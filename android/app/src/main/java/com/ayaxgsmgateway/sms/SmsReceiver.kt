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

        private const val BACKEND_URL =
            "https://ayax-api-marketplace.onrender.com/api/v1/gateway/incoming-sms"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val pendingResult = goAsync()

        try {
            val prefs = context.getSharedPreferences(
                "AYAX_DEVICE",
                Context.MODE_PRIVATE
            )

            val deviceId = prefs.getString("deviceId", null)
            val secretKey = prefs.getString("secretKey", null)

            if (deviceId.isNullOrBlank() || secretKey.isNullOrBlank()) {
                Log.e(TAG, "Device credentials not found")
                pendingResult.finish()
                return
            }

            val smsMessages =
                Telephony.Sms.Intents.getMessagesFromIntent(intent)

            if (smsMessages.isNullOrEmpty()) {
                Log.e(TAG, "No SMS messages found")
                pendingResult.finish()
                return
            }

            val sender =
                smsMessages.firstOrNull()?.originatingAddress
                    ?: "Unknown"

            val fullMessage = smsMessages
                .mapNotNull { it.messageBody }
                .joinToString(separator = "")

            if (fullMessage.isBlank()) {
                Log.e(TAG, "SMS message body is empty")
                pendingResult.finish()
                return
            }

            val subscriptionId = resolveSubscriptionId(intent)

            val slotIndex = resolveSlotIndex(
                context,
                subscriptionId
            )

            Log.d(
                TAG,
                "SMS received. sender=$sender, " +
                    "subscriptionId=$subscriptionId, " +
                    "slotIndex=$slotIndex, message=$fullMessage"
            )

            sendSmsToBackend(
                deviceId = deviceId,
                secretKey = secretKey,
                phoneNumber = sender,
                message = fullMessage,
                subscriptionId = subscriptionId,
                slotIndex = slotIndex,
                onComplete = {
                    pendingResult.finish()
                }
            )
        } catch (error: Exception) {
            Log.e(TAG, "SMS receiver failed", error)
            pendingResult.finish()
        }
    }

    private fun resolveSubscriptionId(intent: Intent): Int {
        val keys = listOf(
            "subscription",
            "subscription_id",
            "subscriptionId",
            "sub_id",
            "subId",
            "android.telephony.extra.SUBSCRIPTION_INDEX"
        )

        for (key in keys) {
            val value = intent.getIntExtra(
                key,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            )

            if (
                value !=
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            ) {
                return value
            }
        }

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID
    }

    private fun resolveSlotIndex(
        context: Context,
        subscriptionId: Int
    ): Int {
        if (
            subscriptionId ==
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) {
            return 0
        }

        return try {
            val manager =
                context.getSystemService(
                    Context.TELEPHONY_SUBSCRIPTION_SERVICE
                ) as SubscriptionManager

            val info =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.LOLLIPOP_MR1
                ) {
                    manager.getActiveSubscriptionInfo(
                        subscriptionId
                    )
                } else {
                    null
                }

            info?.simSlotIndex ?: 0
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Unable to resolve SIM slot",
                error
            )

            0
        }
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
            put("receivedAt", System.currentTimeMillis())
        }

        val body = json
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

        val request = Request.Builder()
            .url(BACKEND_URL)
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
                        "SMS sync failed: ${error.message}",
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
                            it.body?.string().orEmpty()

                        if (it.isSuccessful) {
                            Log.d(
                                TAG,
                                "SMS synced successfully: " +
                                    "${it.code} $responseBody"
                            )
                        } else {
                            Log.e(
                                TAG,
                                "SMS sync rejected: " +
                                    "${it.code} $responseBody"
                            )
                        }
                    }

                    onComplete()
                }
            }
        )
    }
}