package com.ayaxgsmgateway.gsm.helpers

object UssdReplyManager {

    private val replies = mutableListOf<String>()

    fun load(flow: String) {

        replies.clear()

        if (flow.isBlank()) return

        flow.split(",")

            .map { it.trim() }

            .filter { it.isNotBlank() }

            .forEach {

                replies.add(it)

            }
    }

    fun hasNext(): Boolean {

        return replies.isNotEmpty()

    }

    fun next(): String {

        return replies.removeAt(0)

    }

    fun clear() {

        replies.clear()

    }

}