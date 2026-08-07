package com.ayaxgsmgateway.gsm.manager

import android.util.Log
import com.ayaxgsmgateway.gsm.model.SessionState
import com.ayaxgsmgateway.gsm.model.Status

object UssdSessionManager {

    private const val TAG = "AYAX_SESSION_MANAGER"
    private var currentSession: SessionState? = null

    @Synchronized
    fun startSession(session: SessionState) {
        session.status = Status.RUNNING
        currentSession = session
        Log.d(TAG, "Session started. Status set to RUNNING.")
    }

    @Synchronized
    fun getSession(): SessionState? {
        return currentSession
    }

    @Synchronized
    fun isRunning(): Boolean {
        return currentSession != null && currentSession?.status == Status.RUNNING
    }

    @Synchronized
    fun success() {
        currentSession?.let {
            it.status = Status.SUCCESS
            Log.d(TAG, "Session status updated to SUCCESS.")
        }
    }

    @Synchronized
    fun failed() {
        currentSession?.let {
            it.status = Status.FAILED
            Log.d(TAG, "Session status updated to FAILED.")
        }
    }

    @Synchronized
    fun waiting() {
        currentSession?.let {
            it.status = Status.WAITING
            Log.d(TAG, "Session status updated to WAITING.")
        }
    }

    @Synchronized
    fun timeout() {
        currentSession?.let {
            it.status = Status.TIMEOUT
            Log.d(TAG, "Session status updated to TIMEOUT.")
        }
    }

    @Synchronized
    fun clear() {
        currentSession = null
        Log.d(TAG, "Session cleared.")
    }
}