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

        val eventText =
            event.text
                ?.joinToString(" ")
                ?.trim()
                .orEmpty()

        val rootText =
            rootInActiveWindow
                ?.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val message =
            if (eventText.isNotBlank()) eventText else rootText

        if (message.isBlank()) return

        Log.d("AYAX_USSD", "Captured USSD:")
        Log.d("AYAX_USSD", message)

        sendResultToBackend(message)
    }

    private fun sendResultToBackend(message: String) {

        val prefs =
            getSharedPreferences("AYAX_USSD", MODE_PRIVATE)

        val reference =
            prefs.getString("reference", null)

        val deviceId =
            prefs.getString("deviceId", null)

        val secretKey =
            prefs.getString("secretKey", null)

        if (
            reference.isNullOrBlank() ||
            deviceId.isNullOrBlank() ||
            secretKey.isNullOrBlank()
        ) {
            Log.e(
                "AYAX_USSD",
                "Reference or device credentials missing"
            )
            return
        }

        val json = JSONObject().apply {

            put("deviceId", deviceId)

            put("secretKey", secretKey)

            put("reference", reference)

            put("status", "SUCCESSFUL")

            put("message", message)

            put("response", message)

        }

        val body =
            json.toString()
                .toRequestBody(
                    "application/json".toMediaType()
                )

        val request =
            Request.Builder()
                .url("https://ayax-api-marketplace.onrender.com/api/v1/gateway/result")
                .post(body)
                .build()

        client.newCall(request).enqueue(

            object : Callback {

                override fun onFailure(
                    call: Call,
                    e: IOException
                ) {

                    Log.e(
                        "AYAX_USSD",
                        "Backend callback failed: ${e.message}"
                    )

                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {

                    val body =
                        response.body?.string()

                    Log.d(
                        "AYAX_USSD",
                        "Backend response: ${response.code}"
                    )

                    Log.d(
                        "AYAX_USSD",
                        body ?: ""
                    )

                    if (response.isSuccessful) {

                        prefs.edit()
                            .remove("reference")
                            .apply()

                    }

                    response.close()

                }

            }

        )

    }

    override fun onInterrupt() {
        Log.d(
            "AYAX_USSD",
            "Accessibility interrupted"
        )
    }
}