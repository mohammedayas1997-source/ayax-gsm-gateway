package com.ayaxgsmgateway.gsm.helpers

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log

object SmsHelper {

    private const val TAG = "AYAX_SMS_HELPER"
    private const val PREFS_NAME = "AYAX_SMS"

    fun sendSms(
        context: Context,
        phoneNumber: String,
        message: String,
        simSlot: Int,
        reference: String,
        deviceId: String,
        secretKey: String
    ) {
        Log.d(TAG, "Preparing to send SMS to $phoneNumber via SIM slot $simSlot (Ref: $reference)")

        if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission not granted")
            throw Exception("SEND_SMS permission not granted")
        }

        if (phoneNumber.isBlank() || message.isBlank()) {
            Log.e(TAG, "Phone number or message is blank")
            throw Exception("Phone number and message are required")
        }

        // Ajiye bayanan command domin SmsStatusReceiver ya yi amfani da su
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("reference", reference)
            .putString("deviceId", deviceId)
            .putString("secretKey", secretKey)
            .putString("phoneNumber", phoneNumber)
            .putLong("sentAt", System.currentTimeMillis())
            .apply()

        // Amfani da SubscriptionHelper domin samun daidaitaccen subscriptionId tare da fallback
        val subscriptionId = SubscriptionHelper.getSubscriptionIdBySlot(context, simSlot)
        Log.d(TAG, "Resolved subscriptionId: $subscriptionId for SIM slot: $simSlot")

        val smsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (subscriptionId != -1) {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
                } else {
                    context.getSystemService(SmsManager::class.java)
                }
            } else {
                if (subscriptionId != -1) {
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                } else {
                    SmsManager.getDefault()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create SmsManager for subscription $subscriptionId, falling back to default.", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
        }

        val parts = smsManager.divideMessage(message)

        val sentIntent = PendingIntent.getBroadcast(
            context,
            1001,
            Intent("AYAX_SMS_SENT").setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            1002,
            Intent("AYAX_SMS_DELIVERED").setPackage(context.packageName),
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

        Log.d(TAG, "Multipart SMS successfully dispatched to SmsManager queue.")
    }
}