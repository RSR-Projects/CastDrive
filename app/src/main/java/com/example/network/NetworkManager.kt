package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import com.example.model.ConnectionType
import com.example.model.DiscoveredDevice
import com.example.model.NetworkInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

class NetworkManager(private val context: Context) {
    companion object {
        private const val TAG = "AutoMirrorNetwork"
        const val UDP_DISCOVERY_PORT = 8888
        const val STREAM_TCP_PORT = 8088
        const val STREAM_HTTP_PORT = 8080
    }

    private val _activeNetworks = MutableStateFlow<List<NetworkInfo>>(emptyList())
    val activeNetworks: StateFlow<List<NetworkInfo>> = _activeNetworks.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        refreshNetworkInterfaces()
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val request = NetworkRequest.Builder().build()
                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        refreshNetworkInterfaces()
                    }
                    override fun onLost(network: Network) {
                        refreshNetworkInterfaces()
                    }
                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        refreshNetworkInterfaces()
                    }
                }
                cm.registerNetworkCallback(request, callback)
                networkCallback = callback
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register NetworkCallback: ${e.message}")
        }
    }

    fun refreshNetworkInterfaces(): List<NetworkInfo> {
        val list = mutableListOf<NetworkInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = Collections.list(iface.inetAddresses)
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false) {
                        val host = addr.hostAddress ?: continue
                        val name = iface.name.lowercase()

                        val isUsb = name.contains("rndis") || name.contains("usb") ||
                                name.contains("ncm") || name.contains("geth") ||
                                name.contains("usbnet") || name.contains("sec_rndis") ||
                                host.startsWith("192.168.42.")

                        val isHotspot = name.contains("ap") || name.contains("softap") ||
                                name.contains("swlan") || name.contains("tether") ||
                                host == "192.168.43.1" || host.startsWith("192.168.43.")

                        val isWifi = name.contains("wlan") || name.contains("eth") ||
                                name.contains("en") || name.contains("wifi")

                        val isCellular = !isUsb && (name.contains("rmnet") || name.contains("ccmni") ||
                                name.contains("pdp") || name.contains("dummy") ||
                                name.contains("tun") || name.contains("sit"))

                        val connType = when {
                            isUsb -> ConnectionType.USB_TETHERING
                            isHotspot -> ConnectionType.WIFI_HOTSPOT
                            isWifi -> ConnectionType.LOCAL_WIFI
                            isCellular -> ConnectionType.UNKNOWN
                            else -> ConnectionType.UNKNOWN
                        }

                        // Only include non-cellular or local routable networks
                        if (!isCellular) {
                            list.add(
                                NetworkInfo(
                                    ipAddress = host,
                                    interfaceName = iface.name,
                                    connectionType = connType,
                                    isUsbTethering = isUsb,
                                    isHotspot = isHotspot
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network interfaces", e)
        }

        // Prioritize USB tethering, then Hotspot, then normal Wi-Fi
        val sorted = list.sortedBy {
            when (it.connectionType) {
                ConnectionType.USB_TETHERING -> 0
                ConnectionType.WIFI_HOTSPOT -> 1
                ConnectionType.LOCAL_WIFI -> 2
                else -> 3
            }
        }
        _activeNetworks.value = sorted
        return sorted
    }

    fun getPrimaryIp(): String {
        val current = refreshNetworkInterfaces()
        return current.firstOrNull()?.ipAddress ?: "127.0.0.1"
    }

    fun getPrimaryNetwork(): NetworkInfo? {
        return refreshNetworkInterfaces().firstOrNull()
    }

    fun startBroadcasting(scope: CoroutineScope, deviceName: String, tcpPort: Int, webPort: Int) {
        stopBroadcasting()
        broadcastJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true

                while (isActive) {
                    val networks = refreshNetworkInterfaces()
                    val primaryIp = networks.firstOrNull()?.ipAddress ?: "127.0.0.1"

                    val json = JSONObject().apply {
                        put("type", "castdrive_beacon")
                        put("legacyType", "automirror_beacon")
                        put("role", "sender")
                        put("name", deviceName)
                        put("ip", primaryIp)
                        put("tcpPort", tcpPort)
                        put("webPort", webPort)
                        put("timestamp", System.currentTimeMillis())
                    }.toString()

                    val bytes = json.toByteArray()

                    // Broadcast to 255.255.255.255
                    try {
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")
                        val packet = DatagramPacket(bytes, bytes.size, broadcastAddr, UDP_DISCOVERY_PORT)
                        socket.send(packet)
                    } catch (e: Exception) {
                        // ignore network drop
                    }

                    // Also broadcast to each active interface's broadcast subnet
                    for (net in networks) {
                        try {
                            val parts = net.ipAddress.split(".")
                            if (parts.size == 4) {
                                val subnetBroadcast = "${parts[0]}.${parts[1]}.${parts[2]}.255"
                                val addr = InetAddress.getByName(subnetBroadcast)
                                val packet = DatagramPacket(bytes, bytes.size, addr, UDP_DISCOVERY_PORT)
                                socket.send(packet)
                            }
                        } catch (e: Exception) {
                            // ignore
                        }
                    }

                    delay(1500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Broadcast error", e)
            } finally {
                socket?.close()
            }
        }
    }

    fun stopBroadcasting() {
        broadcastJob?.cancel()
        broadcastJob = null
    }

    fun startListeningForDevices(scope: CoroutineScope) {
        stopListening()

        // Acquire multicast lock if Wi-Fi manager available
        try {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("AutoMirrorLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire multicast lock", e)
        }

        listenJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(UDP_DISCOVERY_PORT).apply {
                    broadcast = true
                    reuseAddress = true
                }
                val buffer = ByteArray(2048)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val text = String(packet.data, 0, packet.length)
                    try {
                        val json = JSONObject(text)
                        val bType = json.optString("type")
                        val legacyType = json.optString("legacyType")
                        val isBeacon = bType == "castdrive_beacon" || bType == "automirror_beacon" || legacyType == "automirror_beacon"
                        if (isBeacon && json.optString("role") == "sender") {
                            val name = json.optString("name", "Phone")
                            val packetSenderIp = packet.address?.hostAddress ?: ""
                            val jsonIp = json.optString("ip", "")
                            val ip = if (jsonIp.isNotEmpty() && jsonIp != "127.0.0.1") jsonIp else packetSenderIp
                            if (ip.isEmpty() || ip == "127.0.0.1") continue

                            val tcpPort = json.optInt("tcpPort", STREAM_TCP_PORT)
                            val webPort = json.optInt("webPort", STREAM_HTTP_PORT)

                            val isUsb = ip.startsWith("192.168.42.")
                            val isHotspot = ip.startsWith("192.168.43.")
                            val connType = when {
                                isUsb -> ConnectionType.USB_TETHERING
                                isHotspot -> ConnectionType.WIFI_HOTSPOT
                                else -> ConnectionType.LOCAL_WIFI
                            }

                            val device = DiscoveredDevice(
                                name = name,
                                ip = ip,
                                port = tcpPort,
                                webPort = webPort,
                                connectionType = connType,
                                lastSeenMs = System.currentTimeMillis()
                            )

                            // Update discovered devices list
                            val current = _discoveredDevices.value.toMutableList()
                            val index = current.indexOfFirst { it.ip == ip }
                            if (index >= 0) {
                                current[index] = device
                            } else {
                                current.add(device)
                            }
                            _discoveredDevices.value = current
                        }
                    } catch (e: Exception) {
                        // ignore malformed packets
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Listen error", e)
            } finally {
                socket?.close()
            }
        }
    }

    fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
            multicastLock = null
        } catch (e: Exception) {
            // ignore
        }
    }
}
