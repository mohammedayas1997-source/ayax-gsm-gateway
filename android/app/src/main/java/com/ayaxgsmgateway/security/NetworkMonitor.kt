package com.ayaxgsmgateway.security

import android.content.Context
import android.net.*

object NetworkMonitor {

    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start(context: Context) {

        val manager =
            context.getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        if (callback != null) return

        callback =
            object : ConnectivityManager.NetworkCallback() {

                override fun onLost(network: Network) {

                    SecurityManager.sendSecurityAlert(

                        context,

                        "NETWORK_LOST",

                        "Gateway lost internet connection."

                    )

                }

                override fun onAvailable(network: Network) {

                    SecurityManager.sendSecurityAlert(

                        context,

                        "NETWORK_CONNECTED",

                        "Gateway internet restored."

                    )

                }

            }

        manager.registerDefaultNetworkCallback(callback!!)

    }

}