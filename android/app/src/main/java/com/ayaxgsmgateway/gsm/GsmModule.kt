package com.ayaxgsmgateway.gsm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import com.facebook.react.bridge.*
import android.telephony.SmsManager
import android.content.Intent
import android.net.Uri

class GsmModule(private val reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext) {

  override fun getName(): String {
    return "GsmModule"
  }

  @ReactMethod
fun sendSms(phoneNumber: String, message: String, promise: Promise) {
  try {
    if (
      reactContext.checkSelfPermission(Manifest.permission.SEND_SMS)
      != PackageManager.PERMISSION_GRANTED
    ) {
      promise.reject("PERMISSION_DENIED", "SEND_SMS permission not granted")
      return
    }

    val smsManager = SmsManager.getDefault()

    val parts = smsManager.divideMessage(message)

    smsManager.sendMultipartTextMessage(
      phoneNumber,
      null,
      parts,
      null,
      null
    )

    val result = Arguments.createMap()
    result.putBoolean("success", true)
    result.putString("phoneNumber", phoneNumber)
    result.putString("message", message)

    promise.resolve(result)
  } catch (e: Exception) {
    promise.reject("SMS_ERROR", e.message)
  }
}
@ReactMethod
fun sendUssd(
  ussdCode: String,
  reference: String,
  deviceId: String,
  secretKey: String,
  promise: Promise
) {
  try {
    if (
      reactContext.checkSelfPermission(Manifest.permission.CALL_PHONE)
      != PackageManager.PERMISSION_GRANTED
    ) {
      promise.reject("PERMISSION_DENIED", "CALL_PHONE permission not granted")
      return
    }

    val prefs = reactContext.getSharedPreferences("AYAX_USSD", Context.MODE_PRIVATE)

    prefs.edit()
      .putString("reference", reference)
      .putString("deviceId", deviceId)
      .putString("secretKey", secretKey)
      .apply()

    val encodedHash = Uri.encode("#")
    val finalCode = ussdCode.replace("#", encodedHash)

    val intent = Intent(Intent.ACTION_CALL)
    intent.data = Uri.parse("tel:$finalCode")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    reactContext.startActivity(intent)

    val result = Arguments.createMap()
    result.putBoolean("success", true)
    result.putString("ussdCode", ussdCode)
    result.putString("message", "USSD command started")

    promise.resolve(result)
  } catch (e: Exception) {
    promise.reject("USSD_ERROR", e.message)
  }
}

@ReactMethod
fun saveDeviceCredentials(deviceId: String, secretKey: String, promise: Promise) {
  try {
    val prefs = reactContext.getSharedPreferences("AYAX_DEVICE", Context.MODE_PRIVATE)

    prefs.edit()
      .putString("deviceId", deviceId)
      .putString("secretKey", secretKey)
      .apply()

    promise.resolve(true)
  } catch (e: Exception) {
    promise.reject("SAVE_DEVICE_ERROR", e.message)
  }
}

  @ReactMethod
  fun getSimInfo(promise: Promise) {
    try {
      if (
        reactContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
        != PackageManager.PERMISSION_GRANTED
      ) {
        promise.reject("PERMISSION_DENIED", "READ_PHONE_STATE permission not granted")
        return
      }

      val subscriptionManager =
        reactContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

      val sims = Arguments.createArray()
      val activeSims = subscriptionManager.activeSubscriptionInfoList

      activeSims?.forEach { sim ->
        val item = Arguments.createMap()

        item.putInt("subscriptionId", sim.subscriptionId)
        item.putInt("slotIndex", sim.simSlotIndex)
        item.putString("carrierName", sim.carrierName?.toString() ?: "Unknown")
        item.putString("displayName", sim.displayName?.toString() ?: "Unknown")
        item.putString("countryIso", sim.countryIso ?: "")
        item.putString("number", sim.number ?: "")
        item.putInt("mcc", sim.mcc)
        item.putInt("mnc", sim.mnc)

        sims.pushMap(item)
      }

      val result = Arguments.createMap()
      result.putInt("simCount", activeSims?.size ?: 0)
      result.putArray("sims", sims)

      promise.resolve(result)
    } catch (e: Exception) {
      promise.reject("GSM_ERROR", e.message)
    }
  }
}