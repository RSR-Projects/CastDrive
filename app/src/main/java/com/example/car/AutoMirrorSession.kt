package com.example.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

/**
 * Manages the Android Auto session lifecycle and provides the root screen.
 */
class AutoMirrorSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return MirrorCarScreen(carContext)
    }
}
