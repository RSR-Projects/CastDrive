package com.example.model

enum class AppRole {
    SENDER,    // Phone broadcasting screen
    RECEIVER,  // Car stereo receiving screen
    WEB_GUIDE, // Instructions for car stereo browser
    GUIDE      // Setup & troubleshooting guide
}

enum class ConnectionType(val title: String, val badge: String) {
    USB_TETHERING("USB Tethering", "Fastest • Zero Latency"),
    WIFI_HOTSPOT("Wi-Fi Hotspot", "Wireless • No Router"),
    LOCAL_WIFI("Local Wi-Fi", "Home / Pocket Wi-Fi"),
    UNKNOWN("Network", "Unknown")
}

enum class QualityPreset(
    val label: String,
    val description: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val frameRate: Int,
    val jpegQuality: Int
) {
    ULTRA_FAST("Performance (Ultra Low Lag)", "540p @ 60fps - Best for older Android 7/8 stereos", 960, 540, 60, 65),
    BALANCED("Balanced (Recommended)", "720p @ 30fps - Crisp maps & smooth playback", 1280, 720, 30, 75),
    HIGH_RES("High Definition", "1080p @ 30fps - For powerful stereos (PX6/Snapdragon)", 1920, 1080, 30, 85)
}

enum class ScaleMode(val label: String) {
    FIT("Fit (Maintain Aspect)"),
    FILL("Fill (Full Stereo Screen)"),
    ORIGINAL("Original 1:1")
}

data class StreamStats(
    val fps: Int = 0,
    val bitrateKbps: Long = 0L,
    val latencyMs: Long = 0L,
    val frameCount: Long = 0L,
    val clientCount: Int = 0,
    val resolution: String = "0x0"
)

data class DiscoveredDevice(
    val name: String,
    val ip: String,
    val port: Int = 8088,
    val webPort: Int = 8080,
    val connectionType: ConnectionType = ConnectionType.UNKNOWN,
    val lastSeenMs: Long = System.currentTimeMillis()
)

data class NetworkInfo(
    val ipAddress: String,
    val interfaceName: String,
    val connectionType: ConnectionType,
    val isUsbTethering: Boolean,
    val isHotspot: Boolean
)
