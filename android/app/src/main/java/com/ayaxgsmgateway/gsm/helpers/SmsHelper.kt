package com.ayaxgsmgateway.gsm.helpers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager

object SmsHelper {

    fun sendSms(
        context: Context,
        phoneNumber: String,
        message: String,
        simSlot: Int,
        reference: String,
        deviceId: String,
        secretKey: String
    ) {

        if (
            context.checkSelfPermission(Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw Exception("SEND_SMS permission not granted")
        }

        // Save command info for SmsStatusReceiver
        val prefs =
            context.getSharedPreferences(
                "AYAX_SMS",
                Context.MODE_PRIVATE
            )

        prefs.edit()
            .putString("reference", reference)
            .putString("deviceId", deviceId)
            .putString("secretKey", secretKey)
            .apply()

        val subscriptionId =
            SubscriptionHelper.getSubscriptionIdBySlot(
                context,
                simSlot
            )

        val smsManager =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getSmsManagerForSubscriptionId(
                    subscriptionId
                )
            }

        val parts = smsManager.divideMessage(message)

        val sentIntent = PendingIntent.getBroadcast(
            context,
            1001,
            Intent("AYAX_SMS_SENT"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            1002,
            Intent("AYAX_SMS_DELIVERED"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sentIntents = ArrayList<PendingIntent>()
        val deliveredIntents = ArrayList<PendingIntent>()

        repeat(parts.size) {
            sentIntents.add(sentIntent)
            deliveredIntents.add(deliveredIntent)
        }

        smsManager.sendMultipartTextMessage(
            phoneNumber,
            null,
            parts,
            sentIntents,
            deliveredIntents
        )
    }
}