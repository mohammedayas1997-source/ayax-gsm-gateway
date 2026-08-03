package com.ayaxgsmgateway.gsm.manager

import com.ayaxgsmgateway.gsm.model.SessionState
import com.ayaxgsmgateway.gsm.model.Status

object UssdSessionManager {

    private var currentSession: SessionState? = null

    fun startSession(
        session: SessionState
    ) {

        session.status = Status.RUNNING

        currentSession = session

    }

    fun getSession(): SessionState? {

        return currentSession

    }

    fun isRunning(): Boolean {

        return currentSession != null

    }

    fun success() {

        currentSession?.status = Status.SUCCESS

    }

    fun failed() {

        currentSession?.status = Status.FAILED

    }

    fun waiting() {

        currentSession?.status = Status.WAITING

    }

    fun timeout() {

        currentSession?.status = Status.TIMEOUT

    }

    fun clear() {

        currentSession = null

    }

}