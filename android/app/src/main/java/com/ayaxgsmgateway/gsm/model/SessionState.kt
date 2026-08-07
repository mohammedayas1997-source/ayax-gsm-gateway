package com.ayaxgsmgateway.gsm.model

data class SessionState(
    val reference: String,
    val ussdCode: String,
    val simSlot: Int,
    val subscriptionId: Int,
    val requestType: String,
    var status: Status = Status.IDLE,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "SessionState(reference='$reference', ussdCode='$ussdCode', simSlot=$simSlot, subscriptionId=$subscriptionId, requestType='$requestType', status=$status, createdAt=$createdAt)"
    }
}

enum class Status {
    IDLE,
    RUNNING,
    WAITING,
    SUCCESS,
    FAILED,
    TIMEOUT
}