package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object TetheringHelper {
    private const val TAG = "TetheringHelper"

    /**
     * Directly opens the Android System Tethering / Hotspot / USB Tethering settings page.
     */
    fun openTetheringSettings(context: Context): Boolean {
        val intents = listOf(
            // Direct Samsung / Pixel / AOSP tethering activities
            Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.TetherSettings")),
            Intent("android.settings.TETHER_SETTINGS"),
            Intent("com.android.settings.TETHER_SETTINGS"),
            // Wireless & Networks settings
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            // Generic fallback
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(TAG, "Tethering intent failed, trying next: ${e.message}")
            }
        }
        return false
    }

    /**
     * Determines best candidate IP addresses for connecting to a phone hosting USB tethering.
     * Android phones typically take 192.168.42.129 or 192.168.42.1 as gateway.
     */
    suspend fun findResponsiveUsbHost(candidateIps: List<String>, port: Int, timeoutMs: Int = 1200): String? = withContext(Dispatchers.IO) {
        for (ip in candidateIps) {
            try {
                val s = Socket()
                s.connect(InetSocketAddress(ip, port), timeoutMs)
                s.close()
                Log.d(TAG, "Found responsive USB host at $ip:$port")
                return@withContext ip
            } catch (ignored: Exception) {
                // Not responding on this candidate IP
            }
        }
        // If probe fails (e.g. strict firewall or not open yet), return first candidate as fallback
        candidateIps.firstOrNull()
    }
}
