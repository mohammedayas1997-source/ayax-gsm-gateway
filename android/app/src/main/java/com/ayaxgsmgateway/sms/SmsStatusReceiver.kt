package com.ayaxgsmgateway.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

class SmsStatusReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AYAX_SMS_STATUS"

        private const val RESULT_URL =
            "https://ayax-api-marketplace.onrender.com/api/v1/gateway/result"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val pendingResult = goAsync()

        try {
            val prefs = context.getSharedPreferences(
                "AYAX_SMS",
                Context.MODE_PRIVATE
            )

            val reference = prefs.getString(
                "reference",
                null
            )

            val deviceId = prefs.getString(
                "deviceId",
                null
            )

            val secretKey = prefs.getString(
                "secretKey",
                null
            )

            if (
                reference.isNullOrBlank() ||
                deviceId.isNullOrBlank() ||
                secretKey.isNullOrBlank()
            ) {
                Log.e(TAG, "SMS command credentials not found")
                pendingResult.finish()
                return
            }

            val status = when (intent.action) {
                "AYAX_SMS_SENT" -> {
                    if (resultCode == Activity.RESULT_OK) {
                        "SENT"
                    } else {
                        "FAILED"
                    }
                }

                "AYAX_SMS_DELIVERED" -> {
                    if (resultCode == Activity.RESULT_OK) {
                        "DELIVERED"
                    } else {
                        "FAILED"
                    }
                }

                else -> "FAILED"
            }

            sendStatus(
                reference = reference,
                deviceId = deviceId,
                secretKey = secretKey,
                status = status,
                onComplete = {
                    if (
                        status == "DELIVERED" ||
                        status == "FAILED"
                    ) {
                        prefs.edit()
                            .remove("reference")
                            .remove("deviceId")
                            .remove("secretKey")
                            .apply()
                    }

                    pendingResult.finish()
                }
            )
        } catch (error: Exception) {
            Log.e(TAG, "SMS status receiver failed", error)
            pendingResult.finish()
        }
    }

    private fun sendStatus(
        reference: String,
        deviceId: String,
        secretKey: String,
        status: String,
        onComplete: () -> Unit
    ) {
        val json = JSONObject().apply {
            put("reference", reference)
            put("deviceId", deviceId)
            put("secretKey", secretKey)
            put("status", status)
            put("message", status)
        }

        val body = json
            .toString()
            .toRequestBody(
                "application/json; charset=utf-8".toMediaType()
            )

        val request = Request.Builder()
            .url(RESULT_URL)
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
                        "SMS status sync failed: ${error.message}",
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
                                "SMS status sent: ${it.code} $responseBody"
                            )
                        } else {
                            Log.e(
                                TAG,
                                "SMS status rejected: ${it.code} $responseBody"
                            )
                        }
                    }

                    onComplete()
                }
            }
        )
    }
}