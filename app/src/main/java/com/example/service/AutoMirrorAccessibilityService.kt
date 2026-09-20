package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoMirrorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoMirrorA11y"

        @Volatile
        var instance: AutoMirrorAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AutoMirror Accessibility Service Connected - Reverse Touch Control Active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not inspecting UI elements, solely for gesture injection
    }

    override fun onInterrupt() {
        Log.d(TAG, "AutoMirror Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "AutoMirror Accessibility Service Destroyed")
    }

    fun injectTap(xNorm: Float, yNorm: Float) {
        val metrics = resources.displayMetrics
        val screenX = (xNorm * metrics.widthPixels).coerceIn(0f, metrics.widthPixels.toFloat())
        val screenY = (yNorm * metrics.heightPixels).coerceIn(0f, metrics.heightPixels.toFloat())

        val path = Path().apply {
            moveTo(screenX, screenY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun injectSwipe(startXNorm: Float, startYNorm: Float, endXNorm: Float, endYNorm: Float, durationMs: Long = 200L) {
        val metrics = resources.displayMetrics
        val startX = (startXNorm * metrics.widthPixels).coerceIn(0f, metrics.widthPixels.toFloat())
        val startY = (startYNorm * metrics.heightPixels).coerceIn(0f, metrics.heightPixels.toFloat())
        val endX = (endXNorm * metrics.widthPixels).coerceIn(0f, metrics.widthPixels.toFloat())
        val endY = (endYNorm * metrics.heightPixels).coerceIn(0f, metrics.heightPixels.toFloat())

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, maxOf(durationMs, 50L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
}
