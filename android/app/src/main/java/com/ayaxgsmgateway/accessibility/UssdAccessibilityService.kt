package com.ayaxgsmgateway.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class UssdAccessibilityService : AccessibilityService() {

  private val client = OkHttpClient()

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return

    val text = event.text?.joinToString(" ") ?: ""

    if (text.isBlank()) return

    Log.d("AYAX_USSD", text)

    sendResultToBackend(text)
  }

  private fun sendResultToBackend(message: String) {
    val prefs = getSharedPreferences("AYAX_USSD", MODE_PRIVATE)

    val reference = prefs.getString("reference", null) ?: return
    val deviceId = prefs.getString("deviceId", null) ?: return
    val secretKey = prefs.getString("secretKey", null) ?: return

    val json = JSONObject()
    json.put("deviceId", deviceId)
    json.put("secretKey", secretKey)
    json.put("reference", reference)
    json.put("status", "SUCCESSFUL")
    json.put("message", message)

    val body = json.toString()
      .toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
      .url("https://ayax-api-marketplace.onrender.com/api/v1/gateway/result")
      .post(body)
      .build()

    client.newCall(request).enqueue(object : Callback {
      override fun onFailure(call: Call, e: IOException) {
        Log.e("AYAX_USSD", "Callback failed: ${e.message}")
      }

      override fun onResponse(call: Call, response: Response) {
        Log.d("AYAX_USSD", "Callback sent: ${response.code}")
        response.close()
      }
    })
  }

  override fun onInterrupt() {
    Log.d("AYAX_USSD", "Accessibility interrupted")
  }
}