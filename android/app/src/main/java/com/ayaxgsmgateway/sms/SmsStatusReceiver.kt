package com.ayaxgsmgateway.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
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
        private const val PREFS_NAME = "AYAX_SMS"

        private const val ACTION_SMS_SENT = "AYAX_SMS_SENT"
        private const val ACTION_SMS_DELIVERED = "AYAX_SMS_DELIVERED"

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
                PREFS_NAME,
                Context.MODE_PRIVATE
            )

            /*
             * Prefer credentials attached to the PendingIntent.
             * Fall back to SharedPreferences for older commands.
             */
            val reference =
                intent.getStringExtra("reference")
                    ?: prefs.getString("reference", null)

            val deviceId =
                intent.getStringExtra("deviceId")
                    ?: prefs.getString("deviceId", null)

            val secretKey =
                intent.getStringExtra("secretKey")
                    ?: prefs.getString("secretKey", null)

            val phoneNumber =
                intent.getStringExtra("phoneNumber")
                    ?: prefs.getString("phoneNumber", null)

            val simSlot =
                intent.getIntExtra(
                    "simSlot",
                    prefs.getInt("simSlot", 0)
                )

            if (
                reference.isNullOrBlank() ||
                deviceId.isNullOrBlank() ||
                secretKey.isNullOrBlank()
            ) {
                Log.e(
                    TAG,
                    "SMS command credentials not found"
                )

                pendingResult.finish()
                return
            }

            val statusResult =
                resolveStatus(
                    action = intent.action,
                    resultCode = resultCode
                )

            Log.d(
                TAG,
                "SMS status received: " +
                    "reference=$reference, " +
                    "status=${statusResult.status}, " +
                    "message=${statusResult.message}, " +
                    "phone=$phoneNumber, " +
                    "simSlot=$simSlot"
            )

            sendStatus(
                reference = reference,
                deviceId = deviceId,
                secretKey = secretKey,
                status = statusResult.status,
                message = statusResult.message,
                phoneNumber = phoneNumber,
                simSlot = simSlot
            ) { synced ->

                /*
                 * Clear the pending SMS only when the backend accepted the
                 * terminal result. SENT is not terminal because DELIVERED may
                 * still arrive later.
                 */
                if (
                    synced &&
                    (
                        statusResult.status == "DELIVERED" ||
                        statusResult.status == "FAILED"
                    )
                ) {
                    clearPendingSms(prefs)
                }

                pendingResult.finish()
            }

        } catch (error: Exception) {
            Log.e(
                TAG,
                "SMS status receiver failed",
                error
            )

            pendingResult.finish()
        }
    }

    private fun resolveStatus(
        action: String?,
        resultCode: Int
    ): SmsStatusResult {
        return when (action) {
            ACTION_SMS_SENT -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        SmsStatusResult(
                            status = "SENT",
                            message = "SMS sent successfully"
                        )
                    }

                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        SmsStatusResult(
                            status = "FAILED",
                            message = "SMS failed: generic failure"
                        )
                    }

                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        SmsStatusResult(
                            status = "FAILED",
                            message = "SMS failed: no mobile network service"
                        )
                    }

                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        SmsStatusResult(
                            status = "FAILED",
                            message = "SMS failed: invalid message data"
                        )
                    }

                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        SmsStatusResult(
                            status = "FAILED",
                            message = "SMS failed: mobile radio is off"
                        )
                    }

                    else -> {
                        SmsStatusResult(
                            status = "FAILED",
                            message = "SMS failed with result code: $resultCode"
                        )
                    }
                }
            }

            ACTION_SMS_DELIVERED -> {
                if (resultCode == Activity.RESULT_OK) {
                    SmsStatusResult(
                        status = "DELIVERED",
                        message = "SMS delivered successfully"
                    )
                } else {
                    SmsStatusResult(
                        status = "FAILED",
                        message = "SMS delivery failed with result code: $resultCode"
                    )
                }
            }

            else -> {
                SmsStatusResult(
                    status = "FAILED",
                    message = "Unknown SMS status action: $action"
                )
            }
        }
    }

    private fun sendStatus(
        reference: String,
        deviceId: String,
        secretKey: String,
        status: String,
        message: String,
        phoneNumber: String?,
        simSlot: Int,
        onComplete: (Boolean) -> Unit
    ) {
        val json = JSONObject().apply {
            put("reference", reference)
            put("deviceId", deviceId)
            put("secretKey", secretKey)
            put("status", status)
            put("message", message)
            put("simSlot", simSlot)

            if (!phoneNumber.isNullOrBlank()) {
                put("phoneNumber", phoneNumber)
            }
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

                    onComplete(false)
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
                                "SMS status sent: " +
                                    "${it.code} $responseBody"
                            )

                            onComplete(true)
                        } else {
                            Log.e(
                                TAG,
                                "SMS status rejected: " +
                                    "${it.code} $responseBody"
                            )

                            onComplete(false)
                        }
                    }
                }
            }
        )
    }

    private fun clearPendingSms(
        prefs: android.content.SharedPreferences
    ) {
        prefs.edit()
            .remove("reference")
            .remove("deviceId")
            .remove("secretKey")
            .remove("phoneNumber")
            .remove("simSlot")
            .apply()

        Log.d(
            TAG,
            "Pending SMS command cleared"
        )
    }

    private data class SmsStatusResult(
        val status: String,
        val message: String
    )
}