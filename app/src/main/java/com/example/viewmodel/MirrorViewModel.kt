package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppRole
import com.example.model.DiscoveredDevice
import com.example.model.NetworkInfo
import com.example.model.QualityPreset
import com.example.model.ScaleMode
import com.example.model.StreamStats
import com.example.network.NetworkManager
import com.example.service.ScreenMirrorService
import com.example.service.StreamServiceHub
import com.example.stream.ClientState
import com.example.stream.StreamClient
import com.example.util.TetheringHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MirrorViewModel(application: Application) : AndroidViewModel(application) {

    val networkManager = NetworkManager(application)
    val streamClient = StreamClient()

    val isAudioStreaming: StateFlow<Boolean> = streamClient.isAudioStreaming

    private val _currentRole = MutableStateFlow(AppRole.SENDER)
    val currentRole: StateFlow<AppRole> = _currentRole.asStateFlow()

    private val _qualityPreset = MutableStateFlow(QualityPreset.BALANCED)
    val qualityPreset: StateFlow<QualityPreset> = _qualityPreset.asStateFlow()

    private val _isMirroring = MutableStateFlow(StreamServiceHub.isServiceRunning)
    val isMirroring: StateFlow<Boolean> = _isMirroring.asStateFlow()

    private val _scaleMode = MutableStateFlow(ScaleMode.FIT)
    val scaleMode: StateFlow<ScaleMode> = _scaleMode.asStateFlow()

    private val _rotationAngle = MutableStateFlow(0)
    val rotationAngle: StateFlow<Int> = _rotationAngle.asStateFlow()

    private val _showReceiverHud = MutableStateFlow(true)
    val showReceiverHud: StateFlow<Boolean> = _showReceiverHud.asStateFlow()

    private val _manualIpInput = MutableStateFlow("")
    val manualIpInput: StateFlow<String> = _manualIpInput.asStateFlow()

    // Active network interfaces
    val activeNetworks: StateFlow<List<NetworkInfo>> = networkManager.activeNetworks

    // Discovered senders on network
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = networkManager.discoveredDevices

    // Live sender stream stats
    val senderStats: StateFlow<StreamStats> = StreamServiceHub.server.stats

    // Receiver client state & stats
    val receiverState: StateFlow<ClientState> = streamClient.clientState
    val receiverFrame: StateFlow<Bitmap?> = streamClient.latestFrame
    val receiverStats: StateFlow<StreamStats> = streamClient.stats

    init {
        // Refresh interfaces periodically
        refreshNetworks()
        // Ensure web server (8080) and TCP server (8088) are listening immediately
        StreamServiceHub.server.start(viewModelScope)
    }

    fun setRole(role: AppRole) {
        _currentRole.value = role
        if (role == AppRole.RECEIVER) {
            networkManager.startListeningForDevices(viewModelScope)
        } else {
            networkManager.stopListening()
        }
    }

    fun setQualityPreset(preset: QualityPreset) {
        _qualityPreset.value = preset
        StreamServiceHub.currentPreset = preset
    }

    fun setScaleMode(mode: ScaleMode) {
        _scaleMode.value = mode
    }

    fun cycleRotation() {
        _rotationAngle.value = (_rotationAngle.value + 90) % 360
    }

    fun toggleHud() {
        _showReceiverHud.value = !_showReceiverHud.value
    }

    fun setManualIp(ip: String) {
        _manualIpInput.value = ip
    }

    fun refreshNetworks() {
        networkManager.refreshNetworkInterfaces()
    }

    fun startScreenMirroring(resultCode: Int, data: Intent) {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, ScreenMirrorService::class.java).apply {
            action = ScreenMirrorService.ACTION_START
            putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenMirrorService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenMirrorService.EXTRA_QUALITY_PRESET, _qualityPreset.value.name)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        _isMirroring.value = true

        // Start broadcasting discovery packet so stereo discovers this phone automatically
        networkManager.startBroadcasting(
            viewModelScope,
            deviceName = Build.MODEL ?: "Android Phone",
            tcpPort = NetworkManager.STREAM_TCP_PORT,
            webPort = NetworkManager.STREAM_HTTP_PORT
        )
    }

    fun stopScreenMirroring() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, ScreenMirrorService::class.java).apply {
            action = ScreenMirrorService.ACTION_STOP
        }
        context.startService(serviceIntent)

        _isMirroring.value = false
        networkManager.stopBroadcasting()
    }

    fun connectToDevice(device: DiscoveredDevice) {
        streamClient.connect(viewModelScope, device.ip, device.port)
    }

    fun connectToManualIp(ip: String) {
        val cleanIp = ip.trim()
        if (cleanIp.isNotEmpty()) {
            streamClient.connect(viewModelScope, cleanIp, NetworkManager.STREAM_TCP_PORT)
        }
    }

    fun connectToUsbTetheringDefault() {
        viewModelScope.launch {
            val networks = networkManager.refreshNetworkInterfaces()
            val usbNet = networks.firstOrNull { it.isUsbTethering }
            val candidateIps = mutableListOf<String>()

            // If local receiver has an IP like 192.168.42.129, sender is usually 192.168.42.1
            // If local receiver has an IP like 192.168.42.1, sender is usually 192.168.42.129
            if (usbNet != null) {
                if (usbNet.ipAddress.endsWith(".129")) {
                    candidateIps.add("192.168.42.1")
                    candidateIps.add("192.168.42.129")
                } else if (usbNet.ipAddress.endsWith(".1")) {
                    candidateIps.add("192.168.42.129")
                    candidateIps.add("192.168.42.1")
                } else {
                    candidateIps.add("192.168.42.129")
                    candidateIps.add("192.168.42.1")
                }
            } else {
                candidateIps.add("192.168.42.129")
                candidateIps.add("192.168.42.1")
            }

            val targetIp = TetheringHelper.findResponsiveUsbHost(candidateIps, NetworkManager.STREAM_TCP_PORT)
                ?: candidateIps.first()

            streamClient.connect(viewModelScope, targetIp, NetworkManager.STREAM_TCP_PORT)
        }
    }

    fun openTetheringSettings(): Boolean {
        return TetheringHelper.openTetheringSettings(getApplication())
    }

    fun disconnectReceiver() {
        streamClient.disconnect()
    }

    fun launchAppShortcut(packageName: String, webFallback: String? = null) {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else if (webFallback != null) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webFallback)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open app", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "App not installed on device", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendTouch(action: String, xNorm: Float, yNorm: Float) {
        streamClient.sendTouch(action, xNorm, yNorm)
    }

    fun sendKey(key: String) {
        streamClient.sendKey(key)
    }

    override fun onCleared() {
        super.onCleared()
        networkManager.stopBroadcasting()
        networkManager.stopListening()
        streamClient.disconnect()
    }
}
