package com.ayaxgsmgateway.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import com.ayaxgsmgateway.gsm.manager.UssdReplyManager
import com.ayaxgsmgateway.gsm.manager.UssdSessionManager
import com.ayaxgsmgateway.gsm.parser.UssdParser

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

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var lastCapturedMessage = ""
    private var lastCapturedAt = 0L
    private var lastBackendStatus = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
        Log.d(TAG, "UssdAccessibilityService connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val root = rootInActiveWindow ?: event.source ?: return

        val eventText = event.text?.joinToString(" ")?.trim().orEmpty()
        val rootText = collectNodeText(root)

        val message = when {
            rootText.isNotBlank() && !isOnlyActionButtonText(rootText) -> rootText
            eventText.isNotBlank() && !isOnlyActionButtonText(eventText) -> eventText
            else -> ""
        }.trim()

        if (message.isBlank()) {
            return
        }

        if (isOnlyActionButtonText(message)) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val result = UssdParser.parse(message)

        Log.e(TAG, "USSD Parsed Type: ${result.type} -> Message: $message")

        val backendStatus = when (result.type) {
            UssdParser.ResultType.SUCCESS -> "SUCCESSFUL"
            UssdParser.ResultType.FAILED -> "FAILED"
            else -> "PROCESSING" // Kar a taba barin ya zama SUCCESSFUL da wuri sai dai idan UssdParser ya tabbatar
        }

        val isDuplicate =
            message == lastCapturedMessage &&
            backendStatus == lastBackendStatus &&
            (now - lastCapturedAt) < DUPLICATE_WINDOW_MS

        if (isDuplicate) {
            return
        }

        lastCapturedMessage = message
        lastCapturedAt = now
        lastBackendStatus = backendStatus

        when (result.type) {
            UssdParser.ResultType.SUCCESS -> {
                try { UssdSessionManager.success() } catch (e: Exception) { Log.e(TAG, "Session error", e) }
            }
            UssdParser.ResultType.FAILED -> {
                try { UssdSessionManager.failed() } catch (e: Exception) { Log.e(TAG, "Session error", e) }
            }
            UssdParser.ResultType.WAITING -> {
                try { UssdSessionManager.waiting() } catch (e: Exception) { Log.e(TAG, "Session error", e) }
            }
            else -> {}
        }

        if (result.type == UssdParser.ResultType.WAITING) {
            try {
                if (UssdReplyManager.hasNext()) {
                    val reply = UssdReplyManager.next()
                    if (reply != null) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendReply(rootInActiveWindow, reply)
                        }, 1200)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reply manager error", e)
            }
        }

        // Tura ainihin sakon (message) zuwa ga Backend ta yadda zai bayyana a dashboard
        sendResultToBackend(
            message,
            backendStatus,
            backendStatus == "SUCCESSFUL" || backendStatus == "FAILED"
        )

        if (
            result.type == UssdParser.ResultType.SUCCESS ||
            result.type == UssdParser.ResultType.FAILED
        ) {
            Handler(Looper.getMainLooper()).postDelayed({
                clickCloseButton(rootInActiveWindow)
            }, 1000)
        }
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val builder = StringBuilder()

        node.text?.let {
            val textVal = it.toString().trim()
            if (textVal.isNotEmpty() && !ACTION_BUTTONS.contains(textVal.uppercase())) {
                builder.append(textVal).append("\n")
            }
        }

        node.contentDescription?.let {
            val descVal = it.toString().trim()
            if (descVal.isNotEmpty() && !ACTION_BUTTONS.contains(descVal.uppercase())) {
                builder.append(descVal).append("\n")
            }
        }

        for (i in 0 until node.childCount) {
            builder.append(collectNodeText(node.getChild(i)))
        }

        return builder.toString().trim()
    }

    private fun isOnlyActionButtonText(value: String): Boolean {
        val cleanVal = value.replace("\n", " ").trim()
        val tokens = cleanVal.split("\\s+".toRegex())
        if (tokens.isEmpty()) return false
        return tokens.all { ACTION_BUTTONS.contains(it.trim().uppercase()) }
    }

    private fun clickCloseButton(root: AccessibilityNodeInfo?) {
        if (root == null) return
        ACTION_BUTTONS.forEach { action ->
            val nodes = root.findAccessibilityNodeInfosByText(action)
            if (!nodes.isNullOrEmpty()) {
                nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }
    }

    private fun sendReply(root: AccessibilityNodeInfo?, reply: String) {
        if (root == null) return
        val editText = findEditText(root) ?: return

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
        }

        editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        Handler(Looper.getMainLooper()).postDelayed({
            clickSendButton(root)
        }, 500)
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.className?.toString()?.contains("EditText", true) == true) {
            return node
        }
        for (i in 0 until node.childCount) {
            val result = findEditText(node.getChild(i))
            if (result != null) return result
        }
        return null
    }

    private fun clickSendButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val keywords = listOf("SEND", "OK", "YES", "NEXT", "CONTINUE", "GO")

        for (word in keywords) {
            val nodes = node.findAccessibilityNodeInfosByText(word)
            if (!nodes.isNullOrEmpty()) {
                nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        for (i in 0 until node.childCount) {
            if (clickSendButton(node.getChild(i))) {
                return true
            }
        }
        return false
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Interrupted")
    }

    private fun sendResultToBackend(
        message: String,
        status: String,
        clearPendingRequest: Boolean
    ) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val reference = prefs.getString(KEY_REFERENCE, "DEFAULT_REF_${System.currentTimeMillis()}")
        val deviceId = prefs.getString(KEY_DEVICE_ID, "UNKNOWN_DEVICE")
        val secretKey = prefs.getString(KEY_SECRET_KEY, "")
        val simSlot = prefs.getInt(KEY_SIM_SLOT, 0)
        val requestType = prefs.getString(KEY_REQUEST_TYPE, "USSD") ?: "USSD"

        val json = JSONObject().apply {
            put("deviceId", deviceId ?: "")
            put("secretKey", secretKey ?: "")
            put("reference", reference ?: "")
            put("status", status)
            put("message", message)
            put("response", message)
            put("simSlot", simSlot)
            put("requestType", requestType)
            put("simId", prefs.getString("simId", ""))
            put("balanceType", prefs.getString("balanceType", ""))
            put("service", prefs.getString("service", ""))
            put("network", prefs.getString("network", ""))
        }

        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(RESULT_URL)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Backend Connection Failure", e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful && clearPendingRequest) {
                        clearPendingRequest(prefs)
                    }
                }
            }
        })
    }

    private fun clearPendingRequest(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_REFERENCE)
            .remove(KEY_SIM_SLOT)
            .remove(KEY_REQUEST_TYPE)
            .remove(KEY_USSD_CODE)
            .remove(KEY_WAITING_FOR_SMS)
            .remove(KEY_WAITING_SINCE)
            .apply()
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

        private const val DUPLICATE_WINDOW_MS = 2500L
        private const val RESULT_URL = "https://api.ayaxapis.com/api/v1/gateway/result"

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val ACTION_BUTTONS = setOf(
            "OK", "SEND", "YES", "NEXT", "GO", "CONTINUE", "DONE", "CLOSE", "DISMISS", "CANCEL"
        )
    }
}