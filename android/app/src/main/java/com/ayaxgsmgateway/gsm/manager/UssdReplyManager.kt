package com.ayaxgsmgateway.gsm.manager

import android.util.Log

object UssdReplyManager {

    private const val TAG = "AYAX_REPLY_MANAGER"
    private val replies = mutableListOf<String>()

    @Synchronized
    fun clear() {
        replies.clear()
        Log.d(TAG, "UssdReplyManager cleared.")
    }

    @Synchronized
    fun add(reply: String) {
        if (reply.isNotBlank()) {
            replies.add(reply.trim())
            Log.d(TAG, "Added reply: $reply. Total replies: ${replies.size}")
        }
    }

    @Synchronized
    fun addAll(list: List<String>) {
        val trimmed = list.map { it.trim() }.filter { it.isNotBlank() }
        replies.addAll(trimmed)
        Log.d(TAG, "Added ${trimmed.size} replies. Total replies: ${replies.size}")
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
        Log.d(TAG, "Fetched next reply: $nextReply. Remaining replies: ${replies.size}")
        return nextReply
    }
}