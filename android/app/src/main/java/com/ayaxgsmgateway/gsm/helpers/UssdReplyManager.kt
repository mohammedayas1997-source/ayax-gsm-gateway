package com.ayaxgsmgateway.gsm.helpers

import android.util.Log

object UssdReplyManager {

    private const val TAG = "AYAX_REPLY_MANAGER"
    private val replies = mutableListOf<String>()

    @Synchronized
    fun load(flow: String?) {
        replies.clear()
        if (flow.isNullOrBlank()) return

        flow.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach {
                replies.add(it)
            }
        
        Log.d(TAG, "Loaded ${replies.size} steps for USSD flow: $flow")
    }

    @Synchronized
    fun hasNext(): Boolean {
        return replies.isNotEmpty()
    }

    @Synchronized
    fun next(): String? {
        if (replies.isEmpty()) {
            return null
        }
        val nextReply = replies.removeAt(0)
        Log.d(TAG, "Fetching next USSD reply: $nextReply, Remaining: ${replies.size}")
        return nextReply
    }

    @Synchronized
    fun size(): Int {
        return replies.size
    }

    @Synchronized
    fun clear() {
        replies.clear()
        Log.d(TAG, "UssdReplyManager cleared.")
    }
}