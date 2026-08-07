package com.ayaxgsmgateway.gsm

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import com.facebook.react.bridge.*
import android.telephony.SmsManager
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ayaxgsmgateway.gsm.helpers.SmsHelper
import com.ayaxgsmgateway.gsm.helpers.UssdHelper
import com.ayaxgsmgateway.gsm.manager.UssdReplyManager

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

      val result = Arguments.createMap().apply {
        putBoolean("success", true)
        putString("phoneNumber", phoneNumber)
        putString("message", message)
      }

      promise.resolve(result)
    } catch (e: Exception) {
      Log.e(TAG, "sendSms failed", e)
      promise.reject("SMS_ERROR", e.message)
    }
  }

  @ReactMethod
  fun setUssdReplies(
    replies: ReadableArray,
    promise: Promise
  ) {
    try {
      val replyList = mutableListOf<String>()
      for (i in 0 until replies.size()) {
        replies.getString(i)?.let { replyList.add(it) }
      }
      
      UssdReplyManager.addAll(replyList)
      promise.resolve(true)
    } catch (e: Exception) {
      Log.e(TAG, "setUssdReplies failed", e)
      promise.reject("USSD_REPLY_ERROR", e.message)
    }
  }

  @ReactMethod
  fun sendSmsWithSim(
    phoneNumber: String,
    message: String,
    simSlot: Int,
    reference: String,
    deviceId: String,
    secretKey: String,
    promise: Promise
  ) {
    try {
      SmsHelper.sendSms(
        reactContext,
        phoneNumber,
        message,
        simSlot,
        reference,
        deviceId,
        secretKey
      )

      val result = Arguments.createMap().apply {
        putBoolean("success", true)
        putString("phoneNumber", phoneNumber)
        putString("message", message)
        putInt("simSlot", simSlot)
        putString("reference", reference)
      }

      promise.resolve(result)
    } catch (e: Exception) {
      Log.e(TAG, "sendSmsWithSim failed", e)
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

      val prefs = reactContext.getSharedPreferences(PREFS_USSD, Context.MODE_PRIVATE)

      prefs.edit()
        .putString("reference", reference)
        .putString("deviceId", deviceId)
        .putString("secretKey", secretKey)
        .putInt("simSlot", DEFAULT_SIM_SLOT)
        .putString("ussdCode", ussdCode)
        .apply()

      val encodedHash = Uri.encode("#")
      val finalCode = ussdCode.replace("#", encodedHash)

      val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$finalCode")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }

      reactContext.startActivity(intent)

      val result = Arguments.createMap().apply {
        putBoolean("success", true)
        putString("ussdCode", ussdCode)
        putString("message", "USSD command started")
      }

      promise.resolve(result)

    } catch (e: Exception) {
      Log.e(TAG, "sendUssd failed", e)
      promise.reject("USSD_ERROR", e.message)
    }
  }

  @ReactMethod
  fun sendUssdWithSim(
    ussdCode: String,
    reference: String,
    deviceId: String,
    secretKey: String,
    simSlot: Int,
    simId: String,
    balanceType: String,
    service: String,
    network: String,
    promise: Promise
  ) {
    try {
      val prefs = reactContext.getSharedPreferences(PREFS_USSD, Context.MODE_PRIVATE)

      Log.d(TAG, "USSD=$ussdCode")
      Log.d(TAG, "SIM=$simSlot")

      prefs.edit()
        .putString("reference", reference)
        .putString("deviceId", deviceId)
        .putString("secretKey", secretKey)
        .putInt("simSlot", simSlot)
        .putString("simId", simId)
        .putString("balanceType", balanceType)
        .putString("service", service)
        .putString("network", network)
        .putString("ussdCode", ussdCode)
        .apply()

      UssdHelper.sendUssd(
        context = reactContext,
        ussdCode = ussdCode,
        simSlot = simSlot,
        onSuccess = { response ->
          val map = Arguments.createMap().apply {
            putBoolean("success", true)
            putString("response", response)
            putString("reference", reference)
            putInt("simSlot", simSlot)
          }
          promise.resolve(map)
        },
        onError = { error ->
          promise.reject("USSD_ERROR", error)
        }
      )

    } catch (e: Exception) {
      Log.e(TAG, "sendUssdWithSim failed", e)
      promise.reject("USSD_ERROR", e.message)
    }
  }

  @ReactMethod
  fun saveDeviceCredentials(deviceId: String, secretKey: String, promise: Promise) {
    try {
      val prefs = reactContext.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)

      prefs.edit()
        .putString("deviceId", deviceId)
        .putString("secretKey", secretKey)
        .apply()

      promise.resolve(true)
    } catch (e: Exception) {
      Log.e(TAG, "saveDeviceCredentials failed", e)
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
        val item = Arguments.createMap().apply {
          putInt("subscriptionId", sim.subscriptionId)
          putInt("slotIndex", sim.simSlotIndex)
          putString("carrierName", sim.carrierName?.toString() ?: "Unknown")
          putString("displayName", sim.displayName?.toString() ?: "Unknown")
          putString("countryIso", sim.countryIso ?: "")
          putString("number", sim.number ?: "")
          putInt("mcc", sim.mcc)
          putInt("mnc", sim.mnc)
        }
        sims.pushMap(item)
      }

      val result = Arguments.createMap().apply {
        putInt("simCount", activeSims?.size ?: 0)
        putArray("sims", sims)
      }

      promise.resolve(result)
    } catch (e: Exception) {
      Log.e(TAG, "getSimInfo failed", e)
      promise.reject("GSM_ERROR", e.message)
    }
  }

  companion object {
    private const val TAG = "AYAX_TEST"
    private const val PREFS_USSD = "AYAX_USSD"
    private const val PREFS_DEVICE = "AYAX_DEVICE"
    private const val DEFAULT_SIM_SLOT = 0
  }
}