package com.example.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/**
 * Entry point for Android Auto and Android Automotive head units.
 * Allows the vehicle infotainment display to discover and launch AutoMirror.
 */
class AutoMirrorCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // Allows connection from any Android Auto head unit, emulator, DHU, and wireless projection
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return AutoMirrorSession()
    }
}
