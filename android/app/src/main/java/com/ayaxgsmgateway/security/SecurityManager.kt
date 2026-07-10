package com.ayaxgsmgateway.security

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SecurityManager {

    private val client = OkHttpClient()

    fun sendSecurityAlert(
        context: Context,
        type: String,
        message: String
    ) {
        try {
            val prefs =
                context.getSharedPreferences(
                    "AYAX_DEVICE",
                    Context.MODE_PRIVATE
                )

            val deviceId =
                prefs.getString("deviceId", null) ?: return

            val secretKey =
                prefs.getString("secretKey", null) ?: return

            val json = JSONObject()
            json.put("deviceId", deviceId)
            json.put("secretKey", secretKey)
            json.put("type", type)
            json.put("message", message)

            val body =
                json.toString()
                    .toRequestBody("application/json".toMediaType())

            val request =
                Request.Builder()
                    .url("https://ayax-api-marketplace.onrender.com/api/v1/gateway/security-alert")
                    .post(body)
                    .build()

            client.newCall(request).execute()

        } catch (e: Exception) {
            Log.e("AYAX_SECURITY", e.message ?: "Security alert failed")
        }
    }
}