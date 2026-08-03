package com.ayaxgsmgateway.gsm.model

data class UssdRequest(

    val reference: String,

    val ussdCode: String,

    val simSlot: Int,

    val subscriptionId: Int,

    val requestType: String,

    val createdAt: Long = System.currentTimeMillis()

)