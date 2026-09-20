package com.example.stream

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.example.model.StreamStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicLong

sealed class ClientState {
    object Disconnected : ClientState()
    data class Connecting(val ip: String, val port: Int) : ClientState()
    data class Connected(val ip: String, val port: Int) : ClientState()
    data class Error(val message: String) : ClientState()
}

class StreamClient {
    companion object {
        private const val TAG = "AutoMirrorClient"
    }

    private var connectionJob: Job? = null
    private var statsJob: Job? = null
    private var audioJob: Job? = null
    private var socket: Socket? = null
    private var outputStream: java.io.OutputStream? = null
    private var clientScope: CoroutineScope? = null
    private var audioTrack: AudioTrack? = null

    private val _clientState = MutableStateFlow<ClientState>(ClientState.Disconnected)
    val clientState: StateFlow<ClientState> = _clientState.asStateFlow()

    private val _latestFrame = MutableStateFlow<Bitmap?>(null)
    val latestFrame: StateFlow<Bitmap?> = _latestFrame.asStateFlow()

    private val _isAudioStreaming = MutableStateFlow(false)
    val isAudioStreaming: StateFlow<Boolean> = _isAudioStreaming.asStateFlow()

    private val _stats = MutableStateFlow(StreamStats())
    val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    private val frameCount = AtomicLong(0L)
    private val byteCount = AtomicLong(0L)
    private val lastLatencyMs = AtomicLong(0L)

    private var reusableBitmap: Bitmap? = null
    private val decodeOptions = BitmapFactory.Options().apply {
        inMutable = true
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    fun connect(scope: CoroutineScope, ip: String, port: Int = StreamServer.DEFAULT_TCP_PORT) {
        disconnect()

        _clientState.value = ClientState.Connecting(ip, port)

        connectionJob = scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Connecting to $ip:$port...")
                val s = Socket()
                s.tcpNoDelay = true
                s.receiveBufferSize = 256 * 1024
                s.connect(InetSocketAddress(ip, port), 5000)
                socket = s
                clientScope = scope
                outputStream = java.io.BufferedOutputStream(s.getOutputStream())

                _clientState.value = ClientState.Connected(ip, port)
                Log.d(TAG, "Connected to $ip:$port successfully!")

                val dis = DataInputStream(BufferedInputStream(s.getInputStream(), 64 * 1024))

                // Start stats tracking
                startStatsTracker(scope)

                // Start streaming audio directly through mirroring connection without Bluetooth
                startAudioReceiver(scope, ip)

                while (isActive && s.isConnected && !s.isClosed) {
                    val frameStartTime = System.currentTimeMillis()

                    // Read 4-byte frame length
                    val length = dis.readInt()
                    if (length <= 0 || length > 10 * 1024 * 1024) {
                        Log.w(TAG, "Invalid frame length: $length")
                        break
                    }

                    // Read exact frame bytes
                    val frameBuffer = ByteArray(length)
                    dis.readFully(frameBuffer)

                    byteCount.addAndGet((length + 4).toLong())

                    // Decode frame
                    val decoded = try {
                        BitmapFactory.decodeByteArray(frameBuffer, 0, length)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Frame decode failed", e)
                        null
                    }

                    if (decoded != null) {
                        val decodeDuration = System.currentTimeMillis() - frameStartTime
                        lastLatencyMs.set(decodeDuration)
                        frameCount.incrementAndGet()

                        // Push frame to UI
                        _latestFrame.value = decoded
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Connection error: ${e.message}")
                    _clientState.value = ClientState.Error(e.message ?: "Connection closed")
                }
            } finally {
                disconnectInternal()
            }
        }
    }

    private fun startStatsTracker(scope: CoroutineScope) {
        statsJob?.cancel()
        statsJob = scope.launch(Dispatchers.Default) {
            var lastFrames = 0L
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()

            while (isActive && _clientState.value is ClientState.Connected) {
                kotlinx.coroutines.delay(1000)
                val now = System.currentTimeMillis()
                val deltaSec = (now - lastTime) / 1000.0
                if (deltaSec > 0) {
                    val currentFrames = frameCount.get()
                    val currentBytes = byteCount.get()

                    val fps = ((currentFrames - lastFrames) / deltaSec).toInt()
                    val kbps = (((currentBytes - lastBytes) * 8) / (deltaSec * 1000)).toLong()

                    val bmp = _latestFrame.value
                    val res = if (bmp != null) "${bmp.width}x${bmp.height}" else "0x0"

                    _stats.value = StreamStats(
                        fps = fps,
                        bitrateKbps = kbps,
                        latencyMs = lastLatencyMs.get(),
                        frameCount = currentFrames,
                        clientCount = 1,
                        resolution = res
                    )

                    lastFrames = currentFrames
                    lastBytes = currentBytes
                    lastTime = now
                }
            }
        }
    }

    private fun startAudioReceiver(scope: CoroutineScope, ip: String, httpPort: Int = StreamServer.DEFAULT_HTTP_PORT) {
        audioJob?.cancel()
        audioJob = scope.launch(Dispatchers.IO) {
            var audioSocket: Socket? = null
            var track: AudioTrack? = null
            try {
                audioSocket = Socket()
                audioSocket.tcpNoDelay = true
                audioSocket.connect(InetSocketAddress(ip, httpPort), 4000)

                val out = audioSocket.getOutputStream()
                val req = "GET /audio HTTP/1.1\r\nHost: $ip:$httpPort\r\nConnection: close\r\n\r\n"
                out.write(req.toByteArray())
                out.flush()

                val inp = BufferedInputStream(audioSocket.getInputStream(), 16 * 1024)
                // Consume HTTP headers until empty line \r\n\r\n
                var matched = 0
                while (matched < 4) {
                    val b = inp.read()
                    if (b == -1) break
                    if ((matched == 0 || matched == 2) && b == '\r'.code) matched++
                    else if ((matched == 1 || matched == 3) && b == '\n'.code) matched++
                    else matched = if (b == '\r'.code) 1 else 0
                }

                // Skip 44-byte WAV header
                val wavHeader = ByteArray(44)
                var headerRead = 0
                while (headerRead < 44) {
                    val r = inp.read(wavHeader, headerRead, 44 - headerRead)
                    if (r <= 0) break
                    headerRead += r
                }

                val sampleRate = 44100
                val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                val bufferSize = maxOf(minBufSize * 2, 4096)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.play()
                _isAudioStreaming.value = true
                Log.d(TAG, "AudioTrack started playing mirror audio (No Bluetooth required)")

                val buffer = ByteArray(bufferSize)
                while (isActive && !audioSocket.isClosed) {
                    val read = inp.read(buffer)
                    if (read <= 0) break
                    track.write(buffer, 0, read)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Audio streaming receiver completed/stopped: ${e.message}")
            } finally {
                _isAudioStreaming.value = false
                try { track?.stop() } catch (ignored: Exception) {}
                try { track?.release() } catch (ignored: Exception) {}
                audioTrack = null
                try { audioSocket?.close() } catch (ignored: Exception) {}
            }
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        statsJob?.cancel()
        statsJob = null
        audioJob?.cancel()
        audioJob = null
        disconnectInternal()
        _clientState.value = ClientState.Disconnected
        _latestFrame.value = null
        _isAudioStreaming.value = false
    }

    fun sendTouch(action: String, xNorm: Float, yNorm: Float) {
        val out = outputStream ?: return
        clientScope?.launch(Dispatchers.IO) {
            try {
                val clampedX = xNorm.coerceIn(0f, 1f)
                val clampedY = yNorm.coerceIn(0f, 1f)
                val msg = "TOUCH $action $clampedX $clampedY\n"
                out.write(msg.toByteArray(Charsets.US_ASCII))
                out.flush()
            } catch (ignored: Exception) {}
        }
    }

    fun sendKey(key: String) {
        val out = outputStream ?: return
        clientScope?.launch(Dispatchers.IO) {
            try {
                val msg = "KEY $key\n"
                out.write(msg.toByteArray(Charsets.US_ASCII))
                out.flush()
            } catch (ignored: Exception) {}
        }
    }

    private fun disconnectInternal() {
        try {
            socket?.close()
        } catch (ignored: Exception) {}
        socket = null
        outputStream = null
        clientScope = null

        audioJob?.cancel()
        audioJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (ignored: Exception) {}
        audioTrack = null
        _isAudioStreaming.value = false
    }
}
