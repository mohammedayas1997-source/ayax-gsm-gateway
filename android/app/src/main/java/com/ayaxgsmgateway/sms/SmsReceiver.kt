package com.ayaxgsmgateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class SmsReceiver : BroadcastReceiver() {

  private val client = OkHttpClient()

  override fun onReceive(context: Context?, intent: Intent?) {
    if (context == null) return
    if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

    val prefs = context.getSharedPreferences("AYAX_DEVICE", Context.MODE_PRIVATE)
    val deviceId = prefs.getString("deviceId", null)
    val secretKey = prefs.getString("secretKey", null)

    if (deviceId.isNullOrBlank() || secretKey.isNullOrBlank()) {
      Log.e("AYAX_SMS", "Device credentials not found")
      return
    }

    messages.forEach { sms ->
      val phoneNumber = sms.originatingAddress ?: "Unknown"
      val message = sms.messageBody ?: ""

      Log.d("AYAX_SMS", "$phoneNumber: $message")

      sendSmsToBackend(deviceId, secretKey, phoneNumber, message)
    }
  }

  private fun sendSmsToBackend(
    deviceId: String,
    secretKey: String,
    phoneNumber: String,
    message: String
  ) {
    val json = JSONObject()
    json.put("deviceId", deviceId)
    json.put("secretKey", secretKey)
    json.put("phoneNumber", phoneNumber)
    json.put("message", message)

    val body = json.toString()
      .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
      .url("https://ayax-api-marketplace.onrender.com/api/v1/gateway/incoming-sms")
      .post(body)
      .build()

    client.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        Log.e("AYAX_SMS", "SMS sync failed: ${e.message}")
      }

      override fun onResponse(call: Call, response: Response) {
        Log.d("AYAX_SMS", "SMS synced: ${response.code}")
        response.close()
      }
    })
  }
}