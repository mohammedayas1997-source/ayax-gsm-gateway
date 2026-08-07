package com.ayaxgsmgateway.gsm.model

data class UssdRequest(
    val reference: String,
    val ussdCode: String,
    val simSlot: Int,
    val subscriptionId: Int,
    val requestType: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun toString(): String {
        return "UssdRequest(reference='$reference', ussdCode='$ussdCode', simSlot=$simSlot, subscriptionId=$subscriptionId, requestType='$requestType', createdAt=$createdAt)"
    }
}