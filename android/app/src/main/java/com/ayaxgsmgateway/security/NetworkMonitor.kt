package com.ayaxgsmgateway.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

object NetworkMonitor {

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context) {
        val appContext = context.applicationContext
        val manager =
            appContext.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        if (callback != null) return

        callback =
            object : ConnectivityManager.NetworkCallback() {

                override fun onLost(network: Network) {
                    SecurityManager.sendSecurityAlert(
                        appContext,
                        "NETWORK_LOST",
                        "Gateway lost internet connection."
                    )
                }

                override fun onAvailable(network: Network) {
                    SecurityManager.sendSecurityAlert(
                        appContext,
                        "NETWORK_CONNECTED",
                        "Gateway internet restored."
                    )
                }
            }

        try {
            manager.registerDefaultNetworkCallback(callback!!)
        } catch (e: Exception) {
            // Log or handle exception if registration fails
        }
    }

    fun stop(context: Context) {
        if (callback != null) {
            try {
                val manager =
                    context.applicationContext.getSystemService(
                        Context.CONNECTIVITY_SERVICE
                    ) as ConnectivityManager
                manager.unregisterNetworkCallback(callback!!)
            } catch (e: Exception) {
                // Ignore if already unregistered
            } finally {
                callback = null
            }
        }
    }
}