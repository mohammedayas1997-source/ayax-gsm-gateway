package com.ayaxgsmgateway.gsm.repository

import android.content.Context
import android.util.Log
import com.ayaxgsmgateway.gsm.helpers.UssdHelper
import com.ayaxgsmgateway.gsm.manager.UssdQueueManager
import com.ayaxgsmgateway.gsm.manager.UssdSessionManager
import com.ayaxgsmgateway.gsm.model.SessionState
import com.ayaxgsmgateway.gsm.model.UssdRequest

object UssdRepository {

    private const val TAG = "AYAX_REPOSITORY"

    fun enqueue(
        context: Context,
        request: UssdRequest
    ) {

        Log.d(TAG, "Queue Request ${request.reference}")

        UssdQueueManager.enqueue(request)

        processNext(context)
    }

    fun processNext(
        context: Context
    ) {

        if (UssdQueueManager.isProcessing()) {
            return
        }

        val request =
            UssdQueueManager.dequeue()
                ?: return

        UssdQueueManager.setProcessing(true)

        UssdSessionManager.startSession(
            SessionState(
                reference = request.reference,
                ussdCode = request.ussdCode,
                simSlot = request.simSlot,
                subscriptionId = request.subscriptionId,
                requestType = request.requestType
            )
        )

        UssdHelper.sendUssd(
            context = context,
            ussdCode = request.ussdCode,
            simSlot = request.simSlot,

            onSuccess = {

                Log.d(TAG, "USSD Success")

            },

            onError = {

                Log.e(TAG, it)

                finish(context)
            }
        )
    }

    fun finish(
        context: Context
    ) {

        UssdQueueManager.setProcessing(false)

        UssdSessionManager.clear()

        processNext(context)
    }

}