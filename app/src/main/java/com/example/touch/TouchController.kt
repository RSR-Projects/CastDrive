package com.example.touch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.example.service.AutoMirrorAccessibilityService
import kotlin.math.sqrt

object TouchController {
    private const val TAG = "AutoMirrorTouch"

    @Volatile
    private var downX = 0f

    @Volatile
    private var downY = 0f

    @Volatile
    private var lastX = 0f

    @Volatile
    private var lastY = 0f

    @Volatile
    private var downTime = 0L

    fun isAccessibilityEnabled(context: Context): Boolean {
        if (AutoMirrorAccessibilityService.isRunning) return true
        val expectedComponentName = ComponentName(context, AutoMirrorAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    fun openAccessibilitySettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings", e)
            false
        }
    }

    fun handleTouch(action: String, xNorm: Float, yNorm: Float) {
        val service = AutoMirrorAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "Touch received ($action at $xNorm, $yNorm), but AutoMirror Accessibility Service is not enabled")
            return
        }

        val clampedX = xNorm.coerceIn(0f, 1f)
        val clampedY = yNorm.coerceIn(0f, 1f)

        when (action.lowercase()) {
            "down" -> {
                downX = clampedX
                downY = clampedY
                lastX = clampedX
                lastY = clampedY
                downTime = System.currentTimeMillis()
            }
            "move" -> {
                lastX = clampedX
                lastY = clampedY
            }
            "up" -> {
                val dx = lastX - downX
                val dy = lastY - downY
                val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                val duration = (System.currentTimeMillis() - downTime).coerceIn(50L, 500L)

                if (dist < 0.03f) {
                    // Tap
                    service.injectTap(downX, downY)
                } else {
                    // Swipe / Drag
                    service.injectSwipe(downX, downY, lastX, lastY, duration)
                }
            }
        }
    }

    fun handleKey(key: String) {
        val service = AutoMirrorAccessibilityService.instance
        if (service == null) {
            Log.w(TAG, "Key received ($key), but Accessibility Service is not enabled")
            return
        }

        when (key.lowercase()) {
            "back" -> service.performBack()
            "home" -> service.performHome()
            "recents" -> service.performRecents()
        }
    }
}
