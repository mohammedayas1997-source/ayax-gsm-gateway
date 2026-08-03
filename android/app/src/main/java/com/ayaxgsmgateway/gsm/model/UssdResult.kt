package com.ayaxgsmgateway.gsm.model

data class UssdResult(

    val success: Boolean,

    val message: String,

    val requestType: String,

    val simSlot: Int,

    val subscriptionId: Int,

    val receivedAt: Long = System.currentTimeMillis()

)