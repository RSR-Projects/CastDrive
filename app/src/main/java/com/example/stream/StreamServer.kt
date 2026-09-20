package com.example.stream

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.example.model.StreamStats
import com.example.touch.TouchController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class StreamServer {
    companion object {
        private const val TAG = "AutoMirrorServer"
        const val DEFAULT_TCP_PORT = 8088
        const val DEFAULT_HTTP_PORT = 8080
    }

    private var tcpServerSocket: ServerSocket? = null
    private var httpServerSocket: ServerSocket? = null

    private var tcpJob: Job? = null
    private var httpJob: Job? = null
    private var statsJob: Job? = null
    private var idleKeepAliveJob: Job? = null

    private val tcpClients = ConcurrentHashMap<Socket, OutputStream>()
    private val httpMjpegClients = ConcurrentHashMap<Socket, OutputStream>()
    private val httpAudioClients = ConcurrentHashMap<Socket, OutputStream>()

    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    private val frameCounter = AtomicLong(0L)
    private val byteCounter = AtomicLong(0L)
    private val activeClients = AtomicInteger(0)

    @Volatile
    var isServerListening = false
        private set

    @Volatile
    var isStreamingFrames = false
        private set

    // Alias for backward compatibility
    val isRunning: Boolean
        get() = isServerListening

    @Volatile
    private var lastResolution = "1280x720"

    @Volatile
    private var latestJpegBytes: ByteArray? = null

    private var cachedPlaceholderJpeg: ByteArray? = null

    /**
     * Starts the HTTP (8080) and TCP (8088) server listeners.
     * Can be called as soon as the app opens so the car head unit can connect immediately!
     */
    @Synchronized
    fun start(scope: CoroutineScope, tcpPort: Int = DEFAULT_TCP_PORT, httpPort: Int = DEFAULT_HTTP_PORT) {
        if (isServerListening) {
            Log.d(TAG, "StreamServer is already listening on ports TCP:$tcpPort, HTTP:$httpPort")
            return
        }
        isServerListening = true

        // Prepare initial placeholder JPEG
        latestJpegBytes = getPlaceholderJpeg("CastDrive Ready - Start Mirroring on Phone")

        // 1. Start TCP Stream Server (Port 8088) for CastDrive Receiver App
        tcpJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress("0.0.0.0", tcpPort), 50)
                tcpServerSocket = server
                Log.d(TAG, "TCP Streaming Server listening on 0.0.0.0:$tcpPort")

                while (isActive && isServerListening) {
                    val socket = try {
                        server.accept()
                    } catch (e: Exception) {
                        break
                    }
                    socket.tcpNoDelay = true
                    socket.sendBufferSize = 256 * 1024
                    val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)
                    tcpClients[socket] = out
                    activeClients.incrementAndGet()
                    Log.d(TAG, "New TCP Client connected: ${socket.inetAddress.hostAddress}")

                    // Send current frame immediately so receiver displays right away
                    val initialFrame = latestJpegBytes ?: getPlaceholderJpeg()
                    try {
                        val lengthBuffer = ByteBuffer.allocate(4).putInt(initialFrame.size).array()
                        out.write(lengthBuffer)
                        out.write(initialFrame)
                        out.flush()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed sending initial frame to TCP client", e)
                    }

                    // Launch reverse touch input listener for this connected receiver
                    scope.launch(Dispatchers.IO) {
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
                            while (isActive && isServerListening && !socket.isClosed) {
                                val line = reader.readLine() ?: break
                                val parts = line.trim().split(" ")
                                if (parts.size >= 4 && parts[0] == "TOUCH") {
                                    val action = parts[1]
                                    val x = parts[2].toFloatOrNull() ?: 0f
                                    val y = parts[3].toFloatOrNull() ?: 0f
                                    TouchController.handleTouch(action, x, y)
                                } else if (parts.size >= 2 && parts[0] == "KEY") {
                                    val key = parts[1]
                                    TouchController.handleKey(key)
                                }
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TCP Server error on port $tcpPort", e)
            }
        }

        // 2. Start HTTP / MJPEG Web Server (Port 8080) for Any Car Stereo Browser
        httpJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress("0.0.0.0", httpPort), 50)
                httpServerSocket = server
                Log.d(TAG, "HTTP Web Server listening on 0.0.0.0:$httpPort")

                while (isActive && isServerListening) {
                    val socket = try {
                        server.accept()
                    } catch (e: Exception) {
                        break
                    }
                    scope.launch(Dispatchers.IO) {
                        handleHttpRequest(socket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "HTTP Server error on port $httpPort", e)
            }
        }

        // 3. Periodic Stats Aggregator
        statsJob = scope.launch(Dispatchers.Default) {
            var lastFrames = 0L
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()

            while (isActive && isServerListening) {
                delay(1000)
                val now = System.currentTimeMillis()
                val deltaSec = (now - lastTime) / 1000.0
                if (deltaSec > 0) {
                    val currentFrames = frameCounter.get()
                    val currentBytes = byteCounter.get()

                    val fps = ((currentFrames - lastFrames) / deltaSec).toInt()
                    val kbps = (((currentBytes - lastBytes) * 8) / (deltaSec * 1000)).toLong()

                    _stats.value = StreamStats(
                        fps = if (isStreamingFrames) fps else 0,
                        bitrateKbps = if (isStreamingFrames) kbps else 0,
                        latencyMs = if (isStreamingFrames) 20L else 0L,
                        frameCount = currentFrames,
                        clientCount = activeClients.get(),
                        resolution = lastResolution
                    )

                    lastFrames = currentFrames
                    lastBytes = currentBytes
                    lastTime = now
                }
            }
        }

        // 4. Idle keep-alive: When not streaming, send placeholder to MJPEG clients every 2s
        idleKeepAliveJob = scope.launch(Dispatchers.IO) {
            while (isActive && isServerListening) {
                delay(2000)
                if (!isStreamingFrames && httpMjpegClients.isNotEmpty()) {
                    val placeholder = getPlaceholderJpeg("CastDrive Ready - Tap START to Stream")
                    broadcastToMjpegClients(placeholder)
                }
            }
        }
    }

    fun setStreamingActive(active: Boolean) {
        isStreamingFrames = active
        if (!active) {
            latestJpegBytes = getPlaceholderJpeg("Mirroring Paused - Tap START on Phone")
            val frame = latestJpegBytes ?: return
            broadcastToMjpegClients(frame)
        }
    }

    private fun handleHttpRequest(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val firstLine = reader.readLine() ?: run {
                socket.close()
                return
            }
            val parts = firstLine.split(" ")
            val rawPath = if (parts.size > 1) parts[1] else "/"
            val path = rawPath.substringBefore('?')

            // Consume remaining headers
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrEmpty()) {
                headerLine = reader.readLine()
            }

            val out = BufferedOutputStream(socket.getOutputStream(), 32 * 1024)

            when {
                path == "/stream" -> {
                    // Standard MJPEG Multipart Streaming
                    // Remove socket timeout for persistent video streaming
                    socket.soTimeout = 0

                    val header = ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: multipart/x-mixed-replace; boundary=automirror_frame\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Cache-Control: no-cache, no-store, must-revalidate, max-age=0\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Expires: 0\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                    out.write(header)
                    out.flush()

                    // Send immediate first frame so browser never hangs
                    val initialFrame = latestJpegBytes ?: getPlaceholderJpeg()
                    val partHeader = ("--automirror_frame\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${initialFrame.size}\r\n\r\n").toByteArray()
                    out.write(partHeader)
                    out.write(initialFrame)
                    out.write("\r\n".toByteArray())
                    out.flush()

                    httpMjpegClients[socket] = out
                    activeClients.incrementAndGet()
                    Log.d(TAG, "New HTTP MJPEG Web Client connected: ${socket.inetAddress.hostAddress}")
                    // Keep socket open for continuous streaming
                }
                path == "/audio" || path == "/audio.wav" -> {
                    // Continuous live PCM WAV streaming for car browser / stereo
                    socket.soTimeout = 0
                    socket.tcpNoDelay = true

                    val header = ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: audio/wav\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Expires: 0\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                    out.write(header)

                    // Write infinite streaming WAV header (44.1kHz, 16-bit, stereo)
                    val wavHeader = createWavHeader(44100, 2, 16)
                    out.write(wavHeader)
                    out.flush()

                    httpAudioClients[socket] = out
                    Log.d(TAG, "New Live Audio Client connected: ${socket.inetAddress.hostAddress}")
                    // Keep socket open for continuous live audio
                }
                path == "/frame.jpg" || path == "/snapshot.jpg" -> {
                    val frame = latestJpegBytes ?: getPlaceholderJpeg()
                    val resp = ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: image/jpeg\r\n" +
                            "Content-Length: ${frame.size}\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Cache-Control: no-cache, no-store, must-revalidate, max-age=0\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                    out.write(resp)
                    out.write(frame)
                    out.flush()
                    socket.close()
                }
                path == "/ping" || path == "/status" -> {
                    val json = "{\"status\":\"ok\",\"server\":true,\"streaming\":$isStreamingFrames,\"clients\":${activeClients.get()},\"resolution\":\"$lastResolution\",\"fps\":${_stats.value.fps},\"bitrate\":${_stats.value.bitrateKbps}}"
                    val resp = ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Cache-Control: no-cache, no-store\r\n" +
                            "Content-Length: ${json.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n" +
                            json).toByteArray()
                    out.write(resp)
                    out.flush()
                    socket.close()
                }
                path == "/touch" -> {
                    // Reverse touch event from in-car web browser
                    val query = rawPath.substringAfter('?', "")
                    val params = query.split('&').associate {
                        val kv = it.split('=', limit = 2)
                        if (kv.size == 2) kv[0] to kv[1] else kv[0] to ""
                    }
                    val action = params["action"] ?: ""
                    val x = params["x"]?.toFloatOrNull() ?: 0f
                    val y = params["y"]?.toFloatOrNull() ?: 0f

                    if (action.isNotEmpty()) {
                        if (action == "back" || action == "home" || action == "recents") {
                            TouchController.handleKey(action)
                        } else {
                            TouchController.handleTouch(action, x, y)
                        }
                    }

                    val json = "{\"status\":\"ok\"}"
                    val resp = ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Cache-Control: no-cache, no-store\r\n" +
                            "Content-Length: ${json.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n" +
                            json).toByteArray()
                    out.write(resp)
                    out.flush()
                    socket.close()
                }
                else -> {
                    // Serves HTML5 Car Stereo Web Player
                    val html = getCarWebPlayerHtml()
                    val htmlBytes = html.toByteArray(Charsets.UTF_8)
                    val resp = ("HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Cache-Control: no-cache, no-store\r\n" +
                            "Content-Length: ${htmlBytes.size}\r\n" +
                            "Connection: close\r\n\r\n").toByteArray()
                    out.write(resp)
                    out.write(htmlBytes)
                    out.flush()
                    socket.close()
                }
            }
        } catch (e: Exception) {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    fun broadcastFrame(jpegBytes: ByteArray, width: Int, height: Int) {
        if (!isServerListening) return
        isStreamingFrames = true
        latestJpegBytes = jpegBytes
        lastResolution = "${width}x${height}"
        frameCounter.incrementAndGet()
        byteCounter.addAndGet(jpegBytes.size.toLong())

        // 1. Send to Native TCP Clients (Header: 4-byte length + JPEG)
        if (tcpClients.isNotEmpty()) {
            val lengthBuffer = ByteBuffer.allocate(4).putInt(jpegBytes.size).array()
            val deadTcp = mutableListOf<Socket>()

            for ((socket, out) in tcpClients) {
                try {
                    out.write(lengthBuffer)
                    out.write(jpegBytes)
                    out.flush()
                } catch (e: Exception) {
                    deadTcp.add(socket)
                }
            }
            for (dead in deadTcp) {
                tcpClients.remove(dead)
                activeClients.decrementAndGet()
                try { dead.close() } catch (ignored: Exception) {}
            }
        }

        // 2. Send to HTTP MJPEG Web Clients
        if (httpMjpegClients.isNotEmpty()) {
            broadcastToMjpegClients(jpegBytes)
        }
    }

    private fun broadcastToMjpegClients(jpegBytes: ByteArray) {
        val partHeader = ("--automirror_frame\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: ${jpegBytes.size}\r\n\r\n").toByteArray()
        val deadHttp = mutableListOf<Socket>()

        for ((socket, out) in httpMjpegClients) {
            try {
                out.write(partHeader)
                out.write(jpegBytes)
                out.write("\r\n".toByteArray())
                out.flush()
            } catch (e: Exception) {
                deadHttp.add(socket)
            }
        }
        for (dead in deadHttp) {
            httpMjpegClients.remove(dead)
            activeClients.decrementAndGet()
            try { dead.close() } catch (ignored: Exception) {}
        }
    }

    @Synchronized
    fun stop() {
        stopServer()
    }

    @Synchronized
    fun stopServer() {
        isServerListening = false
        isStreamingFrames = false
        tcpJob?.cancel()
        httpJob?.cancel()
        statsJob?.cancel()
        idleKeepAliveJob?.cancel()

        for (socket in tcpClients.keys) {
            try { socket.close() } catch (ignored: Exception) {}
        }
        tcpClients.clear()

        for (socket in httpMjpegClients.keys) {
            try { socket.close() } catch (ignored: Exception) {}
        }
        httpMjpegClients.clear()

        for (socket in httpAudioClients.keys) {
            try { socket.close() } catch (ignored: Exception) {}
        }
        httpAudioClients.clear()

        try { tcpServerSocket?.close() } catch (ignored: Exception) {}
        try { httpServerSocket?.close() } catch (ignored: Exception) {}

        tcpServerSocket = null
        httpServerSocket = null
        activeClients.set(0)
        Log.d(TAG, "StreamServer stopped cleanly")
    }

    /**
     * Broadcasts live PCM audio chunk to all connected car web browser audio streams.
     */
    fun broadcastAudio(pcmBytes: ByteArray, length: Int) {
        if (!isServerListening || httpAudioClients.isEmpty() || length <= 0) return
        val deadAudio = mutableListOf<Socket>()
        for ((socket, out) in httpAudioClients) {
            try {
                out.write(pcmBytes, 0, length)
                out.flush()
            } catch (e: Exception) {
                deadAudio.add(socket)
            }
        }
        for (dead in deadAudio) {
            httpAudioClients.remove(dead)
            try { dead.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Generates a 44-byte standard RIFF/WAVE header for continuous streaming.
     */
    private fun createWavHeader(sampleRate: Int = 44100, channels: Int = 2, bitsPerSample: Int = 16): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = 0x7FFFFFFF - 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        // RIFF/WAVE header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalDataLen and 0xff).toByte()
        header[41] = ((totalDataLen shr 8) and 0xff).toByte()
        header[42] = ((totalDataLen shr 16) and 0xff).toByte()
        header[43] = ((totalDataLen shr 24) and 0xff).toByte()
        return header
    }

    private fun getPlaceholderJpeg(message: String = "CastDrive Server Ready"): ByteArray {
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(15, 23, 42)) // Modern dark navy

        val titlePaint = Paint().apply {
            color = Color.rgb(56, 189, 248) // Cyan
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🚗 CastDrive", 320f, 150f, titlePaint)

        val subPaint = Paint().apply {
            color = Color.rgb(203, 213, 225) // Light slate
            textSize = 20f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(message, 320f, 210f, subPaint)

        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        bmp.recycle()
        return stream.toByteArray()
    }

    private fun getCarWebPlayerHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>CastDrive Car Stereo Stream</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body {
    background-color: #000000;
    color: #FFFFFF;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    overflow: hidden;
    height: 100vh;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    user-select: none;
    -webkit-user-select: none;
  }
  #streamContainer {
    position: relative;
    width: 100vw;
    height: 100vh;
    display: flex;
    justify-content: center;
    align-items: center;
    background: #000000;
  }
  #videoStream, #canvasStream {
    max-width: 100%;
    max-height: 100%;
    object-fit: contain;
    transition: object-fit 0.2s, transform 0.2s;
  }
  #hud {
    position: absolute;
    top: 14px;
    left: 14px;
    display: flex;
    gap: 8px;
    z-index: 10;
    transition: opacity 0.4s ease, transform 0.4s ease;
  }
  .badge {
    background: rgba(15, 23, 42, 0.9);
    border: 1px solid rgba(56, 189, 248, 0.4);
    color: #38BDF8;
    padding: 6px 14px;
    border-radius: 8px;
    font-size: 13px;
    font-weight: 600;
    backdrop-filter: blur(6px);
  }
  #statusBadge {
    color: #34D399;
  }
  #controls {
    position: absolute;
    bottom: 16px;
    display: flex;
    gap: 10px;
    z-index: 10;
    opacity: 0.95;
    transition: opacity 0.4s ease, transform 0.4s ease;
    flex-wrap: wrap;
    justify-content: center;
    padding: 0 10px;
  }
  .ui-hidden {
    opacity: 0 !important;
    pointer-events: none !important;
    transform: translateY(16px);
  }
  #hud.ui-hidden {
    transform: translateY(-16px);
  }
  #controls:hover { opacity: 1; }
  .btn {
    background: #1E293B;
    color: #FFFFFF;
    border: 1px solid #334155;
    padding: 10px 18px;
    border-radius: 10px;
    font-size: 14px;
    font-weight: 600;
    cursor: pointer;
    box-shadow: 0 4px 12px rgba(0,0,0,0.6);
    touch-action: manipulation;
  }
  .btn:active { background: #38BDF8; color: #000000; }
</style>
</head>
<body>
<div id="streamContainer">
  <div id="hud">
    <div class="badge">🚗 CastDrive</div>
    <div class="badge" id="statusBadge">Connecting...</div>
  </div>
  <img id="videoStream" src="/stream" alt="Car Screen Mirror">
  <canvas id="canvasStream" style="display: none;"></canvas>
  <audio id="carAudio" preload="none"></audio>
  <div id="controls">
    <button class="btn" onclick="sendKey('back')" style="background: rgba(239, 68, 68, 0.2); border-color: #EF4444; color: #FCA5A5;">◀ Back</button>
    <button class="btn" onclick="sendKey('home')" style="background: rgba(56, 189, 248, 0.2); border-color: #38BDF8; color: #38BDF8;">● Home</button>
    <button class="btn" onclick="sendKey('recents')" style="background: rgba(168, 85, 247, 0.2); border-color: #A855F7; color: #D8B4FE;">▢ Recents</button>
    <button class="btn" id="btnAudio" onclick="toggleAudio()" style="background: rgba(14, 165, 233, 0.2); border-color: #38BDF8; color: #38BDF8;">🔊 Play Audio (No BT)</button>
    <button class="btn" id="btnFit" onclick="toggleFit()">Aspect: Fit</button>
    <button class="btn" onclick="toggleFullscreen()">⛶ Fullscreen</button>
    <button class="btn" onclick="rotateScreen()">🔄 Rotate</button>
    <button class="btn" id="btnMode" onclick="toggleMode()">Mode: Auto</button>
    <button class="btn" onclick="reloadStream()">⚡ Refresh</button>
  </div>
</div>
<script>
  var isCover = false;
  var rotation = 0;
  var streamMode = 'mjpeg';
  var img = document.getElementById('videoStream');
  var canvas = document.getElementById('canvasStream');
  var ctx = canvas.getContext('2d');
  var statusBadge = document.getElementById('statusBadge');
  var audioEl = document.getElementById('carAudio');
  var btnAudio = document.getElementById('btnAudio');
  var isAudioPlaying = false;
  var isPolling = false;

  function sendTouch(action, x, y) {
    try {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', '/touch?action=' + action + '&x=' + x.toFixed(4) + '&y=' + y.toFixed(4), true);
      xhr.timeout = 1000;
      xhr.send();
    } catch(e) {}
  }

  function sendKey(key) {
    try {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', '/touch?action=' + key, true);
      xhr.timeout = 1000;
      xhr.send();
    } catch(e) {}
  }

  var lastMoveTime = 0;
  function handleScreenTouch(e, action) {
    var el = streamMode === 'mjpeg' ? img : canvas;
    var rect = el.getBoundingClientRect();
    var t = e.touches && e.touches[0] ? e.touches[0] : (e.changedTouches && e.changedTouches[0] ? e.changedTouches[0] : e);
    if (!t || rect.width <= 0 || rect.height <= 0) return;
    var x = (t.clientX - rect.left) / rect.width;
    var y = (t.clientY - rect.top) / rect.height;
    if (x < 0 || x > 1 || y < 0 || y > 1) return;
    if (action === 'move') {
      var now = Date.now();
      if (now - lastMoveTime < 40) return;
      lastMoveTime = now;
    }
    sendTouch(action, x, y);
  }

  img.addEventListener('touchstart', function(e) { handleScreenTouch(e, 'down'); }, { passive: true });
  img.addEventListener('touchmove', function(e) { handleScreenTouch(e, 'move'); }, { passive: true });
  img.addEventListener('touchend', function(e) { handleScreenTouch(e, 'up'); }, { passive: true });
  canvas.addEventListener('touchstart', function(e) { handleScreenTouch(e, 'down'); }, { passive: true });
  canvas.addEventListener('touchmove', function(e) { handleScreenTouch(e, 'move'); }, { passive: true });
  canvas.addEventListener('touchend', function(e) { handleScreenTouch(e, 'up'); }, { passive: true });

  var isMouseDown = false;
  img.addEventListener('mousedown', function(e) { isMouseDown = true; handleScreenTouch(e, 'down'); });
  img.addEventListener('mousemove', function(e) { if (isMouseDown) handleScreenTouch(e, 'move'); });
  img.addEventListener('mouseup', function(e) { if (isMouseDown) { isMouseDown = false; handleScreenTouch(e, 'up'); } });
  canvas.addEventListener('mousedown', function(e) { isMouseDown = true; handleScreenTouch(e, 'down'); });
  canvas.addEventListener('mousemove', function(e) { if (isMouseDown) handleScreenTouch(e, 'move'); });
  canvas.addEventListener('mouseup', function(e) { if (isMouseDown) { isMouseDown = false; handleScreenTouch(e, 'up'); } });

  function toggleAudio() {
    if (!isAudioPlaying) {
      audioEl.src = '/audio?t=' + Date.now();
      audioEl.play().then(function() {
        isAudioPlaying = true;
        btnAudio.innerText = '🔊 Audio Live';
        btnAudio.style.background = '#059669';
        btnAudio.style.borderColor = '#10B981';
        btnAudio.style.color = '#FFFFFF';
      }).catch(function(err) {
        console.warn('Audio play request failed: ' + err);
        // Retry
        audioEl.play().catch(function(){});
      });
    } else {
      audioEl.pause();
      audioEl.removeAttribute('src');
      audioEl.load();
      isAudioPlaying = false;
      btnAudio.innerText = '🔇 Unmute Audio';
      btnAudio.style.background = 'rgba(14, 165, 233, 0.2)';
      btnAudio.style.borderColor = '#38BDF8';
      btnAudio.style.color = '#38BDF8';
    }
  }

  function toggleFit() {
    isCover = !isCover;
    var mode = isCover ? 'cover' : 'contain';
    img.style.objectFit = mode;
    canvas.style.objectFit = mode;
    document.getElementById('btnFit').innerText = isCover ? 'Aspect: Fill' : 'Aspect: Fit';
  }

  function toggleFullscreen() {
    try {
      if (!document.fullscreenElement && !document.webkitFullscreenElement) {
        if (document.documentElement.requestFullscreen) {
          document.documentElement.requestFullscreen();
        } else if (document.documentElement.webkitRequestFullscreen) {
          document.documentElement.webkitRequestFullscreen();
        }
      } else {
        if (document.exitFullscreen) {
          document.exitFullscreen();
        } else if (document.webkitExitFullscreen) {
          document.webkitExitFullscreen();
        }
      }
    } catch (e) {}
  }

  function rotateScreen() {
    rotation = (rotation + 90) % 360;
    var rotStr = 'rotate(' + rotation + 'deg)';
    img.style.transform = rotStr;
    canvas.style.transform = rotStr;
  }

  function toggleMode() {
    if (streamMode === 'mjpeg') {
      switchToCanvasPolling();
      document.getElementById('btnMode').innerText = 'Mode: Canvas';
    } else {
      switchToMjpeg();
      document.getElementById('btnMode').innerText = 'Mode: MJPEG';
    }
  }

  function switchToMjpeg() {
    streamMode = 'mjpeg';
    isPolling = false;
    canvas.style.display = 'none';
    img.style.display = 'block';
    img.src = '/stream?t=' + Date.now();
  }

  function switchToCanvasPolling() {
    if (streamMode === 'canvas' && isPolling) return;
    streamMode = 'canvas';
    img.style.display = 'none';
    canvas.style.display = 'block';
    if (!isPolling) {
      isPolling = true;
      pollNextFrame();
    }
  }

  function reloadStream() {
    if (streamMode === 'mjpeg') {
      img.src = '/stream?t=' + Date.now();
    } else {
      isPolling = false;
      setTimeout(function() { isPolling = true; pollNextFrame(); }, 100);
    }
  }

  img.onerror = function() {
    console.warn('MJPEG stream issue, switching to high-speed canvas polling');
    switchToCanvasPolling();
  };

  function pollNextFrame() {
    if (!isPolling || streamMode !== 'canvas') return;
    var nextImg = new Image();
    nextImg.onload = function() {
      canvas.width = nextImg.naturalWidth || 1280;
      canvas.height = nextImg.naturalHeight || 720;
      ctx.drawImage(nextImg, 0, 0);
      if (isPolling) {
        if (window.requestAnimationFrame) {
          window.requestAnimationFrame(pollNextFrame);
        } else {
          setTimeout(pollNextFrame, 33);
        }
      }
    };
    nextImg.onerror = function() {
      if (isPolling) {
        setTimeout(pollNextFrame, 400);
      }
    };
    nextImg.src = '/frame.jpg?t=' + Date.now();
  }

  // Cross-browser XHR ping to support older Android 7 & 8 car head unit browsers
  function checkStatus() {
    try {
      var xhr = new XMLHttpRequest();
      xhr.open('GET', '/status?t=' + Date.now(), true);
      xhr.timeout = 2500;
      xhr.onreadystatechange = function() {
        if (xhr.readyState === 4) {
          if (xhr.status === 200) {
            try {
              var data = JSON.parse(xhr.responseText);
              if (data.streaming) {
                statusBadge.innerText = '🟢 ' + (data.fps > 0 ? data.fps + ' FPS' : 'Live') + ' (' + data.resolution + ')';
                statusBadge.style.color = '#34D399';
              } else {
                statusBadge.innerText = '🟡 Server Online - Waiting for Phone...';
                statusBadge.style.color = '#FBBF24';
              }
            } catch (parseErr) {
              statusBadge.innerText = '🟢 Connected';
            }
          } else {
            statusBadge.innerText = '🔴 Reconnecting...';
            statusBadge.style.color = '#F87171';
          }
        }
      };
      xhr.onerror = function() {
        statusBadge.innerText = '🔴 Phone Disconnected';
        statusBadge.style.color = '#F87171';
      };
      xhr.send();
    } catch (e) {}
  }

  setInterval(checkStatus, 1500);
  checkStatus();

  // Auto-hide controls & HUD with touch/click wake-up
  var hudEl = document.getElementById('hud');
  var controlsEl = document.getElementById('controls');
  var hideTimer = null;

  function showControls() {
    if (hudEl) hudEl.classList.remove('ui-hidden');
    if (controlsEl) controlsEl.classList.remove('ui-hidden');
    resetHideTimer();
  }

  function hideControls() {
    if (hudEl) hudEl.classList.add('ui-hidden');
    if (controlsEl) controlsEl.classList.add('ui-hidden');
  }

  function resetHideTimer() {
    if (hideTimer) {
      clearTimeout(hideTimer);
    }
    // Automatically hide controls after 3.5 seconds of no touch/interaction
    hideTimer = setTimeout(function() {
      hideControls();
    }, 3500);
  }

  // Touch anywhere to reveal controls immediately
  document.addEventListener('touchstart', function() {
    showControls();
  }, { passive: true });

  document.addEventListener('click', function() {
    showControls();
  });

  document.addEventListener('mousemove', function() {
    showControls();
  });

  // Start auto-hide timer on initial load
  resetHideTimer();
</script>
</body>
</html>
        """.trimIndent()
    }
}
