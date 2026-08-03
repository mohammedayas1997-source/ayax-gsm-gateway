package com.ayaxgsmgateway.gsm.model

data class SessionState(

    val reference: String,

    val ussdCode: String,

    val simSlot: Int,

    val subscriptionId: Int,

    val requestType: String,

    var status: Status = Status.IDLE,

    val createdAt: Long = System.currentTimeMillis()

)

enum class Status {

    IDLE,

    RUNNING,

    WAITING,

    SUCCESS,

    FAILED,

    TIMEOUT

}