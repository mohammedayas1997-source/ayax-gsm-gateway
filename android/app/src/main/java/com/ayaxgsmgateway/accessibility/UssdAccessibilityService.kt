package com.ayaxgsmgateway.accessibility

import android.accessibilityservice.AccessibilityService
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {

        if (event == null) return

        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val reference = prefs.getString(KEY_REFERENCE, null)

        if (reference.isNullOrBlank()) {
            return
        }

        val root = rootInActiveWindow ?: return

        val eventText = event.text?.joinToString(" ")?.trim().orEmpty()
        val rootText = collectNodeText(root)

        val message = when {
            rootText.isNotBlank() -> rootText
            eventText.isNotBlank() -> eventText
            else -> ""
        }.trim()

        Log.e(TAG, "STEP 1")

        if (message.isBlank()) {
            return
        }

        if (isOnlyActionButtonText(message)) {
            return
        }

        val now = SystemClock.elapsedRealtime()

        Log.d(TAG, "USSD => $message")

        val result = UssdParser.parse(message)

        Log.e(TAG, "STEP 2 = ${result.type}")

        val backendStatus = when (result.type) {
            UssdParser.ResultType.SUCCESS -> "SUCCESSFUL"
            UssdParser.ResultType.FAILED -> "FAILED"
            UssdParser.ResultType.WAITING -> "PROCESSING"
            else -> "UNKNOWN"
        }

        // Duplicate response protection: skip only if it's the exact same
        // message + status we already handled inside the dedup window.
        // This keeps rapid duplicate accessibility events from re-triggering
        // replies/backend calls, while still allowing a legitimately repeated
        // screen (after DUPLICATE_WINDOW_MS) to be processed again.
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

        Log.e(TAG, "STEP 3")

        when (result.type) {

            UssdParser.ResultType.SUCCESS -> {
                UssdSessionManager.success()
            }

            UssdParser.ResultType.FAILED -> {
                UssdSessionManager.failed()
            }

            UssdParser.ResultType.WAITING -> {
                UssdSessionManager.waiting()
            }

            else -> {}
        }

        if (result.type == UssdParser.ResultType.WAITING) {

            if (UssdReplyManager.hasNext()) {

                val reply = UssdReplyManager.next()

                if (reply != null) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        sendReply(rootInActiveWindow, reply)
                    }, 1200)
                }
            }
        }

        Log.e(TAG, "STEP 4")

        sendResultToBackend(
            message,
            backendStatus,
            backendStatus == "SUCCESSFUL" || backendStatus == "FAILED"
        )

        Log.e(TAG, "STEP 5")

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
            builder.append(it.toString()).append("\n")
        }

        node.contentDescription?.let {
            builder.append(it.toString()).append("\n")
        }

        for (i in 0 until node.childCount) {
            builder.append(collectNodeText(node.getChild(i)))
        }

        return builder.toString().trim()
    }

    private fun isOnlyActionButtonText(value: String): Boolean {

        val tokens = value.split("\\s+".toRegex())

        if (tokens.isEmpty()) {
            return false
        }

        return tokens.all {
            ACTION_BUTTONS.contains(it.trim().uppercase())
        }
    }

    private fun clickCloseButton(root: AccessibilityNodeInfo?) {

        if (root == null) return

        ACTION_BUTTONS.forEach { action ->

            val nodes = root.findAccessibilityNodeInfosByText(action)

            if (!nodes.isNullOrEmpty()) {

                val ok = nodes.first().performAction(AccessibilityNodeInfo.ACTION_CLICK)

                Log.d(TAG, "Close clicked = $ok")
                return
            }
        }
    }

    private fun sendReply(root: AccessibilityNodeInfo?, reply: String) {

        if (root == null) return

        val editText = findEditText(root)

        if (editText == null) {
            Log.d(TAG, "EditText not found")
            return
        }

        val args = Bundle()

        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            reply
        )

        val success = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        // Diagnostic only — kept local so it can never send an
        // out-of-contract status ("DEBUG") to the backend.
        Log.d(TAG, "Reply Typed = $success")

        Handler(Looper.getMainLooper()).postDelayed({

            val clicked = clickSendButton(root)

            if (!clicked) {
                Log.e(TAG, "SEND button not found")
            }

        }, 500)
    }

    private fun findEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {

        if (node == null) return null

        if (node.className?.toString()?.contains("EditText", true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {

            val result = findEditText(node.getChild(i))

            if (result != null) {
                return result
            }
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

                Log.d(TAG, "Clicked -> $word")

                // Diagnostic only — kept local, not sent to backend.
                Log.d(TAG, "Clicked button: $word")

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

        val reference = prefs.getString(KEY_REFERENCE, null)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null)
        val secretKey = prefs.getString(KEY_SECRET_KEY, null)
        val simSlot = prefs.getInt(KEY_SIM_SLOT, 0)
        val requestType = prefs.getString(KEY_REQUEST_TYPE, "USSD") ?: "USSD"

        if (reference.isNullOrBlank() || deviceId.isNullOrBlank() || secretKey.isNullOrBlank()) {
            return
        }

        val json = JSONObject()

        json.put("deviceId", deviceId)
        json.put("secretKey", secretKey)
        json.put("reference", reference)
        json.put("status", status)
        json.put("message", message)
        json.put("response", message)
        json.put("simSlot", simSlot)
        json.put("requestType", requestType)
        json.put("simId", prefs.getString("simId", ""))
        json.put("balanceType", prefs.getString("balanceType", ""))
        json.put("service", prefs.getString("service", ""))
        json.put("network", prefs.getString("network", ""))

        val body = json.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(RESULT_URL)
            .post(body)
            .build()

        Log.e(TAG, "STEP 6")

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "STEP 7")
                Log.e(TAG, "Backend Error", e)
            }

            override fun onResponse(call: Call, response: Response) {

                Log.e(TAG, "STEP 8")

                response.use {

                    Log.e(TAG, "Backend Code = ${it.code}")
                    Log.e(TAG, it.body?.string().orEmpty())

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
            "OK", "SEND", "YES", "NEXT", "GO", "CONTINUE", "DONE", "CLOSE", "DISMISS"
        )
    }
}