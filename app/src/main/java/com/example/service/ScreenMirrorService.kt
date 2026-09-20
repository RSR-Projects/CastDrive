package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.QualityPreset
import com.example.stream.StreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

object StreamServiceHub {
    val server = StreamServer()
    var isServiceRunning = false
    var currentPreset = QualityPreset.BALANCED

    @Volatile
    var isAudioStreamingActive = false

    // Ultra-low latency callback for direct Android Auto hardware surface drawing
    @Volatile
    var carFrameCallback: ((Bitmap) -> Unit)? = null
}

class ScreenMirrorService : Service() {
    companion object {
        private const val TAG = "AutoMirrorService"
        const val CHANNEL_ID = "automirror_service_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_QUALITY_PRESET = "extra_quality_preset"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var displayManager: DisplayManager? = null
    private var windowManager: WindowManager? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var orientationEventListener: OrientationEventListener? = null

    @Volatile
    private var lastPhysicalWidth: Int = 0
    @Volatile
    private var lastPhysicalHeight: Int = 0
    @Volatile
    private var lastRotation: Int = -1
    @Volatile
    private var currentCaptureWidth: Int = 0
    @Volatile
    private var currentCaptureHeight: Int = 0

    private val captureLock = Any()
    private val isReconfiguring = AtomicBoolean(false)
    private val isProcessingFrame = AtomicBoolean(false)
    private var currentPreset = QualityPreset.BALANCED

    // Audio capture
    private var audioRecord: AudioRecord? = null
    private var audioThread: Thread? = null
    private val isAudioCapturing = AtomicBoolean(false)

    // Reusable buffers to eliminate garbage collection stutter
    private var cachedRawBitmap: Bitmap? = null
    private var cachedCroppedBitmap: Bitmap? = null
    private var cachedDirectBuffer: java.nio.ByteBuffer? = null
    private val frameBaos = ByteArrayOutputStream(128 * 1024)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        createNotificationChannel()

        handlerThread = HandlerThread("AutoMirrorCapture").apply { start() }
        backgroundHandler = Handler(handlerThread!!.looper)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "System configuration changed: orientation=${newConfig.orientation}")
        checkAndHandleOrientationChange()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopScreenCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
            }
            val presetName = intent.getStringExtra(EXTRA_QUALITY_PRESET) ?: QualityPreset.BALANCED.name
            currentPreset = try {
                QualityPreset.valueOf(presetName)
            } catch (e: Exception) {
                QualityPreset.BALANCED
            }
            StreamServiceHub.currentPreset = currentPreset

            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            if (resultData != null && mediaProjection == null) {
                startScreenCapture(resultCode, resultData)
            } else if (resultData == null) {
                Log.e(TAG, "Cannot start capture: resultData is null")
            }
        }

        return START_NOT_STICKY
    }

    private fun startScreenCapture(resultCode: Int, data: Intent) {
        try {
            StreamServiceHub.server.start(serviceScope)
            StreamServiceHub.server.setStreamingActive(true)
            StreamServiceHub.isServiceRunning = true

            val mp = mediaProjectionManager?.getMediaProjection(resultCode, data)
            if (mp == null) {
                Log.e(TAG, "mediaProjectionManager.getMediaProjection returned null")
                stopScreenCapture()
                return
            }
            mediaProjection = mp

            // Register MediaProjection.Callback (Mandatory in Android 14+ / API 34 before createVirtualDisplay)
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped by system")
                    stopScreenCapture()
                    stopSelf()
                }
            }, backgroundHandler)

            val wm = windowManager ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val screenDensity = metrics.densityDpi
            val rotation = wm.defaultDisplay.rotation

            lastPhysicalWidth = screenWidth
            lastPhysicalHeight = screenHeight
            lastRotation = rotation

            // Determine dimensions matching screen orientation to avoid any pillarboxing/letterboxing
            val isLandscape = screenWidth > screenHeight
            val targetLongEdge = maxOf(currentPreset.targetWidth, currentPreset.targetHeight)
            val targetShortEdge = minOf(currentPreset.targetWidth, currentPreset.targetHeight)

            val (maxTargetW, maxTargetH) = if (isLandscape) {
                Pair(targetLongEdge, targetShortEdge)
            } else {
                Pair(targetShortEdge, targetLongEdge)
            }

            val scale = minOf(
                maxTargetW.toFloat() / maxOf(screenWidth, 1),
                maxTargetH.toFloat() / maxOf(screenHeight, 1),
                1.0f
            )
            val captureWidth = maxOf(((screenWidth * scale).toInt() / 2) * 2, 320)
            val captureHeight = maxOf(((screenHeight * scale).toInt() / 2) * 2, 240)

            currentCaptureWidth = captureWidth
            currentCaptureHeight = captureHeight

            Log.d(TAG, "Initial Screen: ${screenWidth}x$screenHeight (Landscape=$isLandscape) -> Capture: ${captureWidth}x$captureHeight @ density $screenDensity")

            imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 3)
            imageReader?.setOnImageAvailableListener({ reader ->
                processLatestImage(reader, captureWidth, captureHeight)
            }, backgroundHandler)

            virtualDisplay = mp.createVirtualDisplay(
                "AutoMirrorCast",
                captureWidth,
                captureHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                backgroundHandler
            )
            Log.d(TAG, "VirtualDisplay created successfully (${captureWidth}x$captureHeight)")

            // 1. Register DisplayListener for instant rotation/display changes
            displayListener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {}
                override fun onDisplayRemoved(displayId: Int) {}
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        checkAndHandleOrientationChange()
                    }
                }
            }
            displayManager?.registerDisplayListener(displayListener, backgroundHandler)

            // 2. Register OrientationEventListener to catch hardware tilt events smoothly
            try {
                orientationEventListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
                    override fun onOrientationChanged(orientation: Int) {
                        if (orientation == ORIENTATION_UNKNOWN) return
                        checkAndHandleOrientationChange()
                    }
                }
                if (orientationEventListener?.canDetectOrientation() == true) {
                    orientationEventListener?.enable()
                }
            } catch (e: Exception) {
                Log.w(TAG, "OrientationEventListener init skipped: ${e.message}")
            }

            // Start internal system audio capture (Android 10+)
            startAudioCapture(mp)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize screen capture", e)
            stopScreenCapture()
        }
    }

    private fun startAudioCapture(mp: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Log.d(TAG, "Audio playback capture requires Android 10 (API 29)+")
            return
        }
        try {
            val audioConfig = AudioPlaybackCaptureConfiguration.Builder(mp)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val sampleRate = 44100
            val channelMask = AudioFormat.CHANNEL_IN_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
            val bufferSize = maxOf(minBufSize * 2, 4096)

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(audioConfig)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord failed to initialize")
                record.release()
                return
            }

            audioRecord = record
            record.startRecording()
            isAudioCapturing.set(true)
            StreamServiceHub.isAudioStreamingActive = true

            audioThread = Thread({
                val buffer = ByteArray(bufferSize)
                while (isAudioCapturing.get() && !Thread.currentThread().isInterrupted) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        StreamServiceHub.server.broadcastAudio(buffer, read)
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            }, "AutoMirrorAudioCapture").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            Log.d(TAG, "Internal Audio Playback Capture started successfully (44.1kHz Stereo PCM)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied for AudioPlaybackCapture: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start audio playback capture: ${e.message}")
        }
    }

    private fun stopAudioCapture() {
        isAudioCapturing.set(false)
        StreamServiceHub.isAudioStreamingActive = false
        audioThread?.interrupt()
        audioThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (ignored: Exception) {}
        audioRecord = null
    }

    private fun checkAndHandleOrientationChange() {
        val wm = windowManager ?: return
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val rotation = wm.defaultDisplay.rotation
        val physicalWidth = metrics.widthPixels
        val physicalHeight = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        if (physicalWidth == lastPhysicalWidth && physicalHeight == lastPhysicalHeight && rotation == lastRotation) {
            return
        }

        backgroundHandler?.post {
            updateCaptureResolution(physicalWidth, physicalHeight, densityDpi, rotation)
        }
    }

    private fun updateCaptureResolution(
        screenWidth: Int,
        screenHeight: Int,
        screenDensity: Int,
        rotation: Int
    ) {
        if (!isReconfiguring.compareAndSet(false, true)) {
            return
        }
        try {
            val mp = mediaProjection ?: return

            val isLandscape = screenWidth > screenHeight
            val targetLongEdge = maxOf(currentPreset.targetWidth, currentPreset.targetHeight)
            val targetShortEdge = minOf(currentPreset.targetWidth, currentPreset.targetHeight)

            val (maxTargetW, maxTargetH) = if (isLandscape) {
                Pair(targetLongEdge, targetShortEdge)
            } else {
                Pair(targetShortEdge, targetLongEdge)
            }

            val scale = minOf(
                maxTargetW.toFloat() / maxOf(screenWidth, 1),
                maxTargetH.toFloat() / maxOf(screenHeight, 1),
                1.0f
            )
            val newWidth = maxOf(((screenWidth * scale).toInt() / 2) * 2, 320)
            val newHeight = maxOf(((screenHeight * scale).toInt() / 2) * 2, 240)

            if (newWidth == currentCaptureWidth && newHeight == currentCaptureHeight && rotation == lastRotation) {
                return
            }

            Log.d(TAG, "Orientation switch: Screen ${screenWidth}x${screenHeight} (rot=$rotation) -> Capture ${newWidth}x${newHeight} (Landscape=$isLandscape)")

            lastPhysicalWidth = screenWidth
            lastPhysicalHeight = screenHeight
            lastRotation = rotation

            synchronized(captureLock) {
                currentCaptureWidth = newWidth
                currentCaptureHeight = newHeight

                cachedRawBitmap?.recycle()
                cachedRawBitmap = null
                cachedCroppedBitmap?.recycle()
                cachedCroppedBitmap = null
                cachedDirectBuffer = null

                val oldReader = imageReader
                val newReader = ImageReader.newInstance(newWidth, newHeight, PixelFormat.RGBA_8888, 3)
                newReader.setOnImageAvailableListener({ reader ->
                    processLatestImage(reader, newWidth, newHeight)
                }, backgroundHandler)
                imageReader = newReader

                val vd = virtualDisplay
                if (vd != null) {
                    try {
                        vd.setSurface(newReader.surface)
                        vd.resize(newWidth, newHeight, screenDensity)
                        Log.d(TAG, "VirtualDisplay resized cleanly to ${newWidth}x$newHeight")
                    } catch (e: Exception) {
                        Log.w(TAG, "VirtualDisplay resize failed, recreating", e)
                        try { vd.release() } catch (ignored: Exception) {}
                        virtualDisplay = mp.createVirtualDisplay(
                            "AutoMirrorCast",
                            newWidth,
                            newHeight,
                            screenDensity,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            newReader.surface,
                            null,
                            backgroundHandler
                        )
                    }
                } else {
                    virtualDisplay = mp.createVirtualDisplay(
                        "AutoMirrorCast",
                        newWidth,
                        newHeight,
                        screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        newReader.surface,
                        null,
                        backgroundHandler
                    )
                }

                try {
                    oldReader?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing previous ImageReader", e)
                }
            }
        } finally {
            isReconfiguring.set(false)
        }
    }

    private fun processLatestImage(reader: ImageReader, width: Int, height: Int) {
        if (reader != imageReader) {
            try { reader.acquireLatestImage()?.close() } catch (ignored: Exception) {}
            return
        }

        val image: Image = (try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        }) ?: return

        // Fast runtime rotation check in case listener had delayed delivery
        val wm = windowManager
        if (wm != null) {
            val currentRot = wm.defaultDisplay.rotation
            if (currentRot != lastRotation && !isReconfiguring.get()) {
                backgroundHandler?.post { checkAndHandleOrientationChange() }
            }
        }

        // If previous frame is still encoding, drop this frame immediately to avoid buffer queue lag!
        if (isProcessingFrame.compareAndSet(false, true)) {
            try {
                val planes = image.planes
                if (planes.isEmpty()) return
                val plane = planes[0]
                val buffer = plane.buffer ?: return
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val stridePixels = rowStride / maxOf(pixelStride, 1)

                // Ensure raw bitmap cache matches current dimensions
                if (cachedRawBitmap == null || cachedRawBitmap?.width != stridePixels || cachedRawBitmap?.height != height) {
                    cachedRawBitmap?.recycle()
                    cachedRawBitmap = Bitmap.createBitmap(stridePixels, height, Bitmap.Config.ARGB_8888)
                }
                val rawBitmap = cachedRawBitmap ?: return

                buffer.rewind()
                val requiredBytes = rawBitmap.byteCount
                if (buffer.remaining() >= requiredBytes) {
                    rawBitmap.copyPixelsFromBuffer(buffer)
                } else {
                    if (cachedDirectBuffer == null || cachedDirectBuffer?.capacity() != requiredBytes) {
                        cachedDirectBuffer = java.nio.ByteBuffer.allocateDirect(requiredBytes)
                    }
                    val temp = cachedDirectBuffer ?: return
                    temp.clear()
                    temp.put(buffer)
                    temp.rewind()
                    rawBitmap.copyPixelsFromBuffer(temp)
                }

                // If stride exceeds width, crop to exact display dimensions
                val finalBitmap = if (stridePixels != width) {
                    if (cachedCroppedBitmap == null || cachedCroppedBitmap?.width != width || cachedCroppedBitmap?.height != height) {
                        cachedCroppedBitmap?.recycle()
                        cachedCroppedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    }
                    val cropped = cachedCroppedBitmap ?: rawBitmap
                    val canvas = android.graphics.Canvas(cropped)
                    canvas.drawBitmap(rawBitmap, 0f, 0f, null)
                    cropped
                } else {
                    rawBitmap
                }

                // Compress to JPEG for ultra low-latency transport
                frameBaos.reset()
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, currentPreset.jpegQuality, frameBaos)
                val jpegBytes = frameBaos.toByteArray()

                // Broadcast to TCP and HTTP clients
                StreamServiceHub.server.broadcastFrame(jpegBytes, width, height)

                // Dispatch directly to Android Auto vehicle surface if connected
                try {
                    StreamServiceHub.carFrameCallback?.invoke(finalBitmap)
                } catch (e: Exception) {
                    Log.w(TAG, "Car frame dispatch error", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Frame processing error", e)
            } finally {
                try { image.close() } catch (ignored: Exception) {}
                isProcessingFrame.set(false)
            }
        } else {
            // Drop frame to preserve zero-latency
            try { image.close() } catch (ignored: Exception) {}
        }
    }

    private fun stopScreenCapture() {
        try {
            try {
                orientationEventListener?.disable()
            } catch (ignored: Exception) {}
            orientationEventListener = null

            displayListener?.let {
                try { displayManager?.unregisterDisplayListener(it) } catch (ignored: Exception) {}
            }
            displayListener = null

            synchronized(captureLock) {
                virtualDisplay?.release()
                virtualDisplay = null

                imageReader?.close()
                imageReader = null

                mediaProjection?.stop()
                mediaProjection = null

                cachedRawBitmap?.recycle()
                cachedRawBitmap = null
                cachedCroppedBitmap?.recycle()
                cachedCroppedBitmap = null
                cachedDirectBuffer = null
            }

            StreamServiceHub.carFrameCallback = null
            stopAudioCapture()
            StreamServiceHub.server.setStreamingActive(false)
            StreamServiceHub.isServiceRunning = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ScreenMirrorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CastDrive is Active")
            .setContentText("Mirroring phone screen to car stereo (${currentPreset.label})")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Mirroring", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CastDrive Screen Stream Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification while phone screen is being cast to car stereo"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopScreenCapture()
        handlerThread?.quitSafely()
        handlerThread = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
