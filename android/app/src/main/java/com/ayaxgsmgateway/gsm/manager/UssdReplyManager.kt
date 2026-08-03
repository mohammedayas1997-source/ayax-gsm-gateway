package com.ayaxgsmgateway.gsm.manager

object UssdReplyManager {

    private val replies = mutableListOf<String>()

    fun clear() {
        replies.clear()
    }

    fun add(reply: String) {
        replies.add(reply)
    }

    fun addAll(list: List<String>) {
        replies.addAll(list)
    }

    fun hasNext(): Boolean {
        return replies.isNotEmpty()
    }

    fun next(): String? {
        if (replies.isEmpty()) return null
        return replies.removeAt(0)
    }
}