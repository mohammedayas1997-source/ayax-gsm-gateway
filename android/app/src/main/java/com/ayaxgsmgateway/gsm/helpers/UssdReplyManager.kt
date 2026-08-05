package com.ayaxgsmgateway.gsm.helpers

object UssdReplyManager {

    private val replies = mutableListOf<String>()

    fun load(flow: String) {

        replies.clear()

        if (flow.isBlank()) return

        flow
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach {
                replies.add(it)
            }
    }

    fun hasNext(): Boolean {
        return replies.isNotEmpty()
    }

    fun next(): String? {

        if (replies.isEmpty()) {
            return null
        }

        return replies.removeAt(0)
    }

    fun size(): Int {
        return replies.size
    }

    fun clear() {
        replies.clear()
    }
}