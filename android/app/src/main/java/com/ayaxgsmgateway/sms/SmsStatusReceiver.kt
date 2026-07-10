package com.ayaxgsmgateway.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SmsStatusReceiver : BroadcastReceiver() {

    private val client = OkHttpClient()

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        val prefs =
            context.getSharedPreferences(
                "AYAX_SMS",
                Context.MODE_PRIVATE
            )

        val reference =
            prefs.getString(
                "reference",
                null
            ) ?: return

        val deviceId =
            prefs.getString(
                "deviceId",
                null
            ) ?: return

        val secretKey =
            prefs.getString(
                "secretKey",
                null
            ) ?: return

        val status =
            when (intent.action) {

                "AYAX_SMS_SENT" -> {

                    if (resultCode == Activity.RESULT_OK)
                        "SENT"
                    else
                        "FAILED"

                }

                "AYAX_SMS_DELIVERED" -> {

                    if (resultCode == Activity.RESULT_OK)
                        "DELIVERED"
                    else
                        "FAILED"

                }

                else -> "FAILED"
            }

        sendStatus(
            reference,
            deviceId,
            secretKey,
            status
        )

    }

    private fun sendStatus(
        reference: String,
        deviceId: String,
        secretKey: String,
        status: String
    ) {

        try {

            val json = JSONObject()

            json.put("reference", reference)
            json.put("deviceId", deviceId)
            json.put("secretKey", secretKey)
            json.put("status", status)
            json.put("message", status)

            val body =
                json.toString()
                    .toRequestBody(
                        "application/json"
                            .toMediaType()
                    )

            val request =
                Request.Builder()

                    .url("https://ayax-api-marketplace.onrender.com/api/v1/gateway/result")

                    .post(body)

                    .build()

            client.newCall(request).execute()

        } catch (e: Exception) {

            Log.e(
                "AYAX_SMS_STATUS",
                e.message ?: "Unknown Error"
            )

        }

    }

}