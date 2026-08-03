package com.ayaxgsmgateway.gsm.manager

import com.ayaxgsmgateway.gsm.model.UssdRequest
import java.util.concurrent.ConcurrentLinkedQueue

object UssdQueueManager {

    private val queue =
        ConcurrentLinkedQueue<UssdRequest>()

    @Volatile
    private var processing = false

    fun enqueue(request: UssdRequest) {
        queue.add(request)
    }

    fun dequeue(): UssdRequest? {
        return queue.poll()
    }

    fun peek(): UssdRequest? {
        return queue.peek()
    }

    fun hasNext(): Boolean {
        return queue.isNotEmpty()
    }

    fun size(): Int {
        return queue.size
    }

    fun clear() {
        queue.clear()
        processing = false
    }

    fun isProcessing(): Boolean {
        return processing
    }

    fun setProcessing(value: Boolean) {
        processing = value
    }
}