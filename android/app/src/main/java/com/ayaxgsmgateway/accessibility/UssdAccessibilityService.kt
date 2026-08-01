package com.ayaxgsmgateway.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

class UssdAccessibilityService : AccessibilityService() {

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

    private var lastCapturedMessage = ""
    private var lastCapturedAt = 0L

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        if (event == null) return

        Log.d(
            TAG,
            "Event: ${event.eventType} " +
                "Package=${event.packageName} " +
                "Class=${event.className}"
        )

        if (
            event.eventType !=
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType !=
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )

        val reference =
            prefs.getString(
                KEY_REFERENCE,
                null
            )

        if (reference.isNullOrBlank()) {
            return
        }

        val eventText =
            event.text
                ?.joinToString(" ")
                ?.trim()
                .orEmpty()

        val rootText =
            collectNodeText(
                rootInActiveWindow
            ).trim()

        val message =
            when {
                rootText.isNotBlank() -> rootText
                eventText.isNotBlank() -> eventText
                else -> ""
            }.trim()

        Log.d(TAG, "ROOT = $rootText")
        Log.d(TAG, "EVENT = $eventText")
        Log.d(TAG, "FINAL = $message")

        if (message.isBlank()) {
            return
        }

        if (isOnlyActionButtonText(message)) {
            return
        }

        val pendingCode =
            prefs.getString(
                KEY_USSD_CODE,
                ""
            ).orEmpty()

        if (
            pendingCode == "*310#" &&
            message.contains(
                "Invalid selection",
                ignoreCase = true
            )
        ) {
            Log.w(
                TAG,
                "Ignoring invalid selection popup for *310#: $message"
            )

            clickCloseButton(
                rootInActiveWindow
            )

            return
        }

        val now =
            SystemClock.elapsedRealtime()

        if (
            message == lastCapturedMessage &&
            now - lastCapturedAt <
            DUPLICATE_WINDOW_MS
        ) {
            return
        }

        lastCapturedMessage = message
        lastCapturedAt = now

        Log.d(
            TAG,
            "Captured USSD response: $message"
        )

        val waitingForSms =
            indicatesSmsResponse(
                message
            )

        if (waitingForSms) {
            prefs.edit()
                .putBoolean(
                    KEY_WAITING_FOR_SMS,
                    true
                )
                .putLong(
                    KEY_WAITING_SINCE,
                    System.currentTimeMillis()
                )
                .apply()

            sendResultToBackend(
                message = message,
                status = "PROCESSING",
                clearPendingRequest = false
            )
        } else {
            sendResultToBackend(
                message = message,
                status = "SUCCESSFUL",
                clearPendingRequest = true
            )
        }

        clickCloseButton(
            rootInActiveWindow
        )
    }

    private fun collectNodeText(
        node: AccessibilityNodeInfo?
    ): String {
        if (node == null) {
            return ""
        }

        val parts = mutableListOf<String>()

        val text =
            node.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (
            text.isNotBlank() &&
            !isActionButtonText(text)
        ) {
            parts.add(text)
        }

        val description =
            node.contentDescription
                ?.toString()
                ?.trim()
                .orEmpty()

        if (
            description.isNotBlank() &&
            !isActionButtonText(description) &&
            !parts.contains(description)
        ) {
            parts.add(description)
        }

        for (
            index in
            0 until node.childCount
        ) {
            val childText =
                collectNodeText(
                    node.getChild(index)
                )

            if (childText.isNotBlank()) {
                parts.add(childText)
            }
        }

        return parts
            .distinct()
            .joinToString(" ")
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun isActionButtonText(
        value: String
    ): Boolean {
        return value
            .trim()
            .uppercase() in ACTION_BUTTONS
    }

    private fun isOnlyActionButtonText(
        value: String
    ): Boolean {
        val tokens =
            value
                .split(
                    Regex("\\s+")
                )
                .map {
                    it.trim().uppercase()
                }
                .filter {
                    it.isNotBlank()
                }

        return tokens.isNotEmpty() &&
            tokens.all {
                it in ACTION_BUTTONS
            }
    }

    private fun indicatesSmsResponse(
        message: String
    ): Boolean {
        val normalized = message.lowercase()

        return normalized.contains("receive an sms") ||
            normalized.contains("sent to you via sms") ||
            normalized.contains("balance details shortly") ||
            normalized.contains("check your sms") ||
            normalized.contains("you will receive") ||
            normalized.contains("an sms will be sent")
    }

    private fun clickCloseButton(
        root: AccessibilityNodeInfo?
    ) {
        if (root == null) {
            return
        }

        for (buttonText in ACTION_BUTTONS) {
            val nodes =
                root.findAccessibilityNodeInfosByText(
                    buttonText
                )

            val clickableNode =
                nodes?.firstOrNull {
                    it.isClickable &&
                        it.isEnabled
                }

            if (clickableNode != null) {
                clickableNode.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )
                return
            }
        }

        findAndClickActionableNode(root)
    }

    private fun findAndClickActionableNode(
        node: AccessibilityNodeInfo?
    ): Boolean {
        if (node == null) {
            return false
        }

        val nodeText =
            node.text
                ?.toString()
                ?.trim()
                ?.uppercase()
                .orEmpty()

        if (
            nodeText in ACTION_BUTTONS &&
            node.isEnabled
        ) {
            var target: AccessibilityNodeInfo? = node

            while (target != null) {
                if (target.isClickable) {
                    target.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                    return true
                }

                target = target.parent
            }
        }

        for (index in 0 until node.childCount) {
            if (
                findAndClickActionableNode(
                    node.getChild(index)
                )
            ) {
                return true
            }
        }

        return false
    }

    private fun sendResultToBackend(
        message: String,
        status: String,
        clearPendingRequest: Boolean
    ) {
        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )

        val reference = prefs.getString(KEY_REFERENCE, null)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        val secretKey = prefs.getString(KEY_SECRET_KEY, null)
        val simSlot = prefs.getInt(KEY_SIM_SLOT, 0)
        val requestType =
            prefs.getString(
                KEY_REQUEST_TYPE,
                "USSD"
            ) ?: "USSD"

        if (
            reference.isNullOrBlank() ||
            deviceId.isNullOrBlank() ||
            secretKey.isNullOrBlank()
        ) {
            Log.e(
                TAG,
                "Pending USSD credentials are missing"
            )
            return
        }

        val json =
            JSONObject().apply {
                put("deviceId", deviceId)
                put("secretKey", secretKey)
                put("reference", reference)
                put("status", status)
                put("message", message)
                put("response", message)
                put("simSlot", simSlot)
                put("requestType", requestType)
            }

        val body =
            json
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)

        val request =
            Request.Builder()
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
                        "Backend callback failed: ${error.message}",
                        error
                    )
                }

                override fun onResponse(
                    call: Call,
                    response: Response
                ) {
                    response.use {
                        val responseBody =
                            it.body?.string().orEmpty()

                        Log.d(
                            TAG,
                            "Backend response: ${it.code} $responseBody"
                        )

                        if (
                            it.isSuccessful &&
                            clearPendingRequest
                        ) {
                            clearPendingRequest(prefs)
                        }
                    }
                }
            }
        )
    }

    private fun clearPendingRequest(
        prefs: SharedPreferences
    ) {
        prefs.edit()
            .remove(KEY_REFERENCE)
            .remove(KEY_SIM_SLOT)
            .remove(KEY_REQUEST_TYPE)
            .remove(KEY_USSD_CODE)
            .remove(KEY_WAITING_FOR_SMS)
            .remove(KEY_WAITING_SINCE)
            .apply()
    }

    override fun onInterrupt() {
        Log.d(
            TAG,
            "Accessibility service interrupted"
        )
    }

    companion object {
        private const val TAG = "AYAX_USSD"
        private const val PREFS_NAME = "AYAX_USSD"

        private const val KEY_REFERENCE = "reference"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_SECRET_KEY = "secretKey"
        private const val KEY_SIM_SLOT = "simSlot"
        private const val KEY_REQUEST_TYPE = "requestType"
        private const val KEY_USSD_CODE = "ussdCode"
        private const val KEY_WAITING_FOR_SMS = "waitingForSms"
        private const val KEY_WAITING_SINCE = "waitingSince"

        private const val RESULT_URL =
            "https://api.ayaxapis.com/api/v1/gateway/result"

        private const val DUPLICATE_WINDOW_MS = 2_500L

        private val ACTION_BUTTONS =
            setOf(
                "OK",
                "CLOSE",
                "CANCEL",
                "DONE",
                "DISMISS",
                "SEND"
            )

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaType()
    }
}