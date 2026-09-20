package com.example.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.example.network.NetworkManager
import com.example.service.StreamServiceHub

/**
 * Android Auto screen showing stream status, network URLs, and display options.
 */
class AutoMirrorCarInfoScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        val isStreaming = StreamServiceHub.isServiceRunning
        val statusText = if (isStreaming) {
            "Broadcasting live (${StreamServiceHub.currentPreset.label})"
        } else {
            "Standby (Start mirroring in CastDrive on your phone)"
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Mirroring Status")
                .addText(statusText)
                .build()
        )

        val networkManager = NetworkManager(carContext)
        val networks = networkManager.refreshNetworkInterfaces()
        val streamUrl = if (networks.isNotEmpty()) {
            networks.joinToString(" • ") { "http://${it.ipAddress}:8080/stream" }
        } else {
            "http://127.0.0.1:8080/stream (or connect to Wi-Fi/Hotspot)"
        }

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Browser & LAN Stream URL")
                .addText(streamUrl)
                .build()
        )

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Display Mode")
                .addText("Hardware Surface rendering. Use the top Action buttons to toggle Fit/Fill scaling or Rotate display.")
                .build()
        )

        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Android Auto Projection")
                .addText("Direct zero-latency rendering to vehicle head-unit. Audio plays through car stereo speakers.")
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("CastDrive Info & Setup")
            .setHeaderAction(Action.BACK)
            .build()
    }
}
