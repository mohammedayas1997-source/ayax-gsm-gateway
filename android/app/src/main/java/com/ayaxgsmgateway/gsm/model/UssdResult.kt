package com.ayaxgsmgateway.gsm.model

data class UssdResult(
    val success: Boolean,
    val message: String,
    val requestType: String,
    val simSlot: Int,
    val subscriptionId: Int,
    val receivedAt: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "UssdResult(success=$success, message='$message', requestType='$requestType', simSlot=$simSlot, subscriptionId=$subscriptionId, receivedAt=$receivedAt)"
    }
}