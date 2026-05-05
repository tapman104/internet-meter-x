package com.internetspeed.meterlite.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

class ConnectivityProvider(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    enum class NetworkType {
        WIFI, MOBILE, NONE
    }

    fun getNetworkType(): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
            else -> NetworkType.NONE
        }
    }

    /**
     * Returns true if the device is acting as a WiFi hotspot (AP mode).
     * When true, TRANSPORT_WIFI traffic in TrafficStats includes tethered client
     * traffic that is actually routed over mobile data, so it must be reclassified.
     *
     * isWifiApEnabled() is a @hide API not present in the public SDK stubs, so it
     * must be invoked via reflection.
     */
    fun isHotspotActive(): Boolean {
        return try {
            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wifiManager) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }
}
