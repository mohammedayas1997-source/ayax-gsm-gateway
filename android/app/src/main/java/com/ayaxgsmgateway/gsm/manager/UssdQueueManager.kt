package com.ayaxgsmgateway.gsm.manager

import android.util.Log
import com.ayaxgsmgateway.gsm.model.UssdRequest
import java.util.concurrent.ConcurrentLinkedQueue

object UssdQueueManager {

    private const val TAG = "AYAX_QUEUE_MANAGER"
    private val queue = ConcurrentLinkedQueue<UssdRequest>()

    @Volatile
    private var processing = false

    @Synchronized
    fun enqueue(request: UssdRequest) {
        queue.add(request)
        Log.d(TAG, "Request added to queue. Total queue size: ${queue.size}")
    }

    @Synchronized
    fun dequeue(): UssdRequest? {
        val request = queue.poll()
        if (request != null) {
            Log.d(TAG, "Request dequeued. Remaining queue size: ${queue.size}")
        }
        return request
    }

    @Synchronized
    fun peek(): UssdRequest? {
        return queue.peek()
    }

    @Synchronized
    fun hasNext(): Boolean {
        return queue.isNotEmpty()
    }

    @Synchronized
    fun size(): Int {
        return queue.size
    }

    @Synchronized
    fun clear() {
        queue.clear()
        processing = false
        Log.d(TAG, "UssdQueueManager cleared and processing reset to false.")
    }

    @Synchronized
    fun isProcessing(): Boolean {
        return processing
    }

    @Synchronized
    fun setProcessing(value: Boolean) {
        processing = value
        Log.d(TAG, "Processing state updated to: $value")
    }
}