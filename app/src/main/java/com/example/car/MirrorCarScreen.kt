package com.example.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.R
import com.example.service.StreamServiceHub
import com.example.stream.StreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Main Android Auto display screen.
 * Renders the phone's mirrored screen directly to the vehicle's hardware Surface.
 */
class MirrorCarScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    companion object {
        private const val TAG = "MirrorCarScreen"
    }

    private val screenScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var clientJob: Job? = null
    private var streamClient: StreamClient? = null

    @Volatile
    private var currentSurface: Surface? = null

    @Volatile
    private var lastReceivedBitmap: Bitmap? = null

    private var isFillMode: Boolean = false
    private var rotationAngle: Int = 0

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                try {
                    carContext.getCarService(AppManager::class.java).setSurfaceCallback(this@MirrorCarScreen)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register Car SurfaceCallback", e)
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                try {
                    carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unregister Car SurfaceCallback", e)
                }
                stopClientFallback()
                screenScope.cancel()
            }
        })
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface
        if (surface == null) {
            Log.w(TAG, "SurfaceContainer provided null surface")
            return
        }
        Log.d(TAG, "Car hardware surface available: $surface")
        currentSurface = surface

        // 1. Direct zero-latency callback for local screen mirroring
        StreamServiceHub.carFrameCallback = { bitmap ->
            lastReceivedBitmap = bitmap
            currentSurface?.let { surf ->
                renderBitmapToSurface(surf, bitmap)
            }
        }

        // 2. If already streaming, render last frame immediately; otherwise show standby dashboard
        val lastBmp = lastReceivedBitmap
        if (StreamServiceHub.isServiceRunning && lastBmp != null) {
            renderBitmapToSurface(surface, lastBmp)
        } else {
            drawStandbyDashboard(surface)
            startClientFallback()
        }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Log.d(TAG, "Car hardware surface destroyed")
        currentSurface = null
        StreamServiceHub.carFrameCallback = null
        stopClientFallback()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.d(TAG, "Visible area changed: $visibleArea")
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        Log.d(TAG, "Stable area changed: $stableArea")
    }

    private fun startClientFallback() {
        stopClientFallback()
        clientJob = screenScope.launch(Dispatchers.IO) {
            // Check periodically if local server is active, connect via TCP client if needed
            while (isActive) {
                if (!StreamServiceHub.isServiceRunning) {
                    try {
                        if (streamClient == null) {
                            streamClient = StreamClient()
                            streamClient?.connect(this, "127.0.0.1", 8088)
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Local client fallback standby: ${e.message}")
                    }
                }
                delay(2000)
            }
        }

        // Collect frames from fallback client
        screenScope.launch {
            streamClient?.latestFrame?.collect { bitmap ->
                if (bitmap != null && currentSurface != null) {
                    lastReceivedBitmap = bitmap
                    currentSurface?.let { renderBitmapToSurface(it, bitmap) }
                }
            }
        }
    }

    private fun stopClientFallback() {
        clientJob?.cancel()
        clientJob = null
        try {
            streamClient?.disconnect()
        } catch (ignored: Exception) {}
        streamClient = null
    }

    private fun refreshConnection() {
        val surf = currentSurface ?: return
        if (StreamServiceHub.isServiceRunning && lastReceivedBitmap != null) {
            lastReceivedBitmap?.let { renderBitmapToSurface(surf, it) }
        } else {
            drawStandbyDashboard(surf)
        }
        startClientFallback()
    }

    private fun renderBitmapToSurface(surface: Surface, bitmap: Bitmap) {
        if (!surface.isValid || bitmap.isRecycled) return
        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            if (canvas == null) return

            val canvasW = canvas.width.toFloat()
            val canvasH = canvas.height.toFloat()

            // Draw deep black background
            canvas.drawColor(android.graphics.Color.BLACK)

            canvas.save()
            if (rotationAngle != 0) {
                canvas.rotate(rotationAngle.toFloat(), canvasW / 2f, canvasH / 2f)
            }

            val matrix = android.graphics.Matrix()
            val bmpW = if (rotationAngle % 180 == 0) bitmap.width.toFloat() else bitmap.height.toFloat()
            val bmpH = if (rotationAngle % 180 == 0) bitmap.height.toFloat() else bitmap.width.toFloat()

            if (isFillMode) {
                // Stretch to fill display
                val scaleX = canvasW / bitmap.width.toFloat()
                val scaleY = canvasH / bitmap.height.toFloat()
                matrix.setScale(scaleX, scaleY)
            } else {
                // Maintain aspect ratio with clean letterboxing
                val scale = minOf(canvasW / bmpW, canvasH / bmpH)
                val destW = bitmap.width.toFloat() * scale
                val destH = bitmap.height.toFloat() * scale
                val left = (canvasW - destW) / 2f
                val top = (canvasH - destH) / 2f
                matrix.setScale(scale, scale)
                matrix.postTranslate(left, top)
            }

            canvas.drawBitmap(bitmap, matrix, null)
            canvas.restore()

        } catch (e: Exception) {
            Log.w(TAG, "Car surface render error", e)
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun drawStandbyDashboard(surface: Surface) {
        if (!surface.isValid) return
        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(null)
            if (canvas == null) return

            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val density = carContext.resources.displayMetrics.density

            // Vehicle cockpit dark backdrop
            canvas.drawColor(android.graphics.Color.rgb(15, 18, 24))

            // Rounded dashboard card
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(26, 31, 44)
                style = Paint.Style.FILL
            }
            val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(56, 189, 248)
                style = Paint.Style.STROKE
                strokeWidth = 2f * density
            }

            val cardW = minOf(w * 0.88f, 680f * density)
            val cardH = minOf(h * 0.78f, 320f * density)
            val cardL = (w - cardW) / 2f
            val cardT = (h - cardH) / 2f
            val cardR = cardL + cardW
            val cardB = cardT + cardH
            val corner = 16f * density

            val rectF = RectF(cardL, cardT, cardR, cardB)
            canvas.drawRoundRect(rectF, corner, corner, cardPaint)
            canvas.drawRoundRect(rectF, corner, corner, cardBorder)

            // Text paints
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(56, 189, 248)
                textSize = 14f * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 23f * density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(226, 232, 240)
                textSize = 15f * density
                textAlign = Paint.Align.CENTER
            }
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(148, 163, 184)
                textSize = 12.5f * density
                textAlign = Paint.Align.CENTER
            }

            val centerX = w / 2f
            var currentY = cardT + 46f * density
            canvas.drawText("CASTDRIVE • ANDROID AUTO", centerX, currentY, badgePaint)
            currentY += 40f * density
            canvas.drawText("Phone Screen Mirroring Standby", centerX, currentY, titlePaint)
            currentY += 34f * density
            canvas.drawText("Open CastDrive on your phone and tap 'START MIRRORING'", centerX, currentY, subtitlePaint)
            currentY += 38f * density
            canvas.drawText("Your phone screen projects directly to this car display with ultra-low latency", centerX, currentY, hintPaint)
            currentY += 26f * density
            canvas.drawText("Action Buttons: [Fit / Fill] Scale Mode  •  [Rotate] Orientation  •  [Info] URLs", centerX, currentY, hintPaint)

        } catch (e: Exception) {
            Log.w(TAG, "Standby draw error", e)
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (ignored: Exception) {}
            }
        }
    }

    override fun onGetTemplate(): Template {
        val backIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_back)).build()
        val homeIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_home)).build()
        val fitIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_fit)).build()
        val refreshIcon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_refresh)).build()

        val backAction = Action.Builder()
            .setIcon(backIcon)
            .setOnClickListener {
                com.example.touch.TouchController.handleKey("back")
            }
            .build()

        val homeAction = Action.Builder()
            .setIcon(homeIcon)
            .setOnClickListener {
                com.example.touch.TouchController.handleKey("home")
            }
            .build()

        val fitAction = Action.Builder()
            .setIcon(fitIcon)
            .setOnClickListener {
                isFillMode = !isFillMode
                invalidate()
                lastReceivedBitmap?.let { bmp ->
                    currentSurface?.let { renderBitmapToSurface(it, bmp) }
                }
            }
            .build()

        val refreshAction = Action.Builder()
            .setIcon(refreshIcon)
            .setOnClickListener {
                refreshConnection()
            }
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(backAction)
            .addAction(homeAction)
            .addAction(fitAction)
            .addAction(refreshAction)
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }
}
