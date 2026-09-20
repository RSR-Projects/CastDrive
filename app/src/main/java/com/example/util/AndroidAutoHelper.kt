package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast

object AndroidAutoHelper {
    private const val TAG = "AndroidAutoHelper"
    const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"

    /**
     * Attempts to open Android Auto settings on the mobile device.
     */
    fun openAndroidAutoSettings(context: Context): Boolean {
        // Attempt 1: Direct DefaultSettingsActivity in Android Auto (Gearhead)
        try {
            val directIntent = Intent().apply {
                component = ComponentName(
                    GEARHEAD_PACKAGE,
                    "com.google.android.projection.gearhead.companion.settings.DefaultSettingsActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(directIntent)
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Direct Gearhead settings activity not reachable, trying App Details: ${e.message}")
        }

        // Attempt 2: Application Details Settings for Android Auto
        return openAndroidAutoAppInfo(context)
    }

    /**
     * Directly opens the Android Auto App Info page so the user can tap
     * 'Force Stop' or 'Clear Cache' to make Android Auto instantly reload installed apps.
     */
    fun openAndroidAutoAppInfo(context: Context): Boolean {
        try {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$GEARHEAD_PACKAGE")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appDetailsIntent)
            return true
        } catch (e: Exception) {
            Log.d(TAG, "App details settings not reachable, trying Connected Devices: ${e.message}")
        }

        // Attempt 3: Connected devices or system settings
        try {
            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            Toast.makeText(
                context,
                "Go to Apps -> Android Auto in Settings",
                Toast.LENGTH_LONG
            ).show()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Could not open settings", e)
            Toast.makeText(
                context,
                "Please open phone Settings -> Android Auto",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
    }
}
