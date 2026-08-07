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

    @Synchronized
    fun enqueue(
        context: Context,
        request: UssdRequest
    ) {
        Log.d(TAG, "Queue Request: ${request.reference} [${request.ussdCode}]")
        UssdQueueManager.enqueue(request)
        processNext(context)
    }

    @Synchronized
    fun processNext(
        context: Context
    ) {
        if (UssdQueueManager.isProcessing()) {
            Log.d(TAG, "Already processing a USSD request. Skipping for now.")
            return
        }

        val request = UssdQueueManager.dequeue()
        if (request == null) {
            Log.d(TAG, "Queue is empty. No more requests to process.")
            return
        }

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

        Log.d(TAG, "Initiating USSD code: ${request.ussdCode} on SIM slot: ${request.simSlot}")

        UssdHelper.sendUssd(
            context = context,
            ussdCode = request.ussdCode,
            simSlot = request.simSlot,
            onSuccess = { successMessage ->
                Log.d(TAG, "USSD Success callback: $successMessage")
                // A nan ba a kiran finish(context) nan take ba saboda ana jiran 
                // Accessibility Service ta kammala karanta amsoshin menu (Multi-step flow).
            },
            onError = { errorMessage ->
                Log.e(TAG, "USSD Error callback: $errorMessage")
                UssdSessionManager.failed()
                finish(context)
            }
        )
    }

    @Synchronized
    fun finish(
        context: Context
    ) {
        Log.d(TAG, "Finishing current USSD session and cleaning up state.")
        UssdQueueManager.setProcessing(false)
        UssdSessionManager.clear()
        
        // Ci gaba da sarrafa sauran buƙatun dake cikin queue idan akwai su
        processNext(context)
    }
}