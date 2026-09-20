package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.model.ConnectionType
import com.example.model.DiscoveredDevice
import com.example.model.ScaleMode
import com.example.model.StreamStats
import com.example.stream.ClientState
import com.example.ui.components.MetricBadge
import com.example.ui.theme.AutoAmber
import com.example.ui.theme.AutoBorder
import com.example.ui.theme.AutoCarbon
import com.example.ui.theme.AutoCyanPrimary
import com.example.ui.theme.AutoGreenOk
import com.example.ui.theme.AutoRedAlert
import com.example.ui.theme.AutoSkySecondary
import com.example.ui.theme.AutoSurface
import com.example.ui.theme.AutoSurfaceElevated
import com.example.ui.theme.AutoTextMuted
import com.example.ui.theme.AutoTextPrimary
import com.example.ui.theme.AutoTextSecondary

@Composable
fun ReceiverScreen(
    clientState: ClientState,
    latestFrame: Bitmap?,
    stats: StreamStats,
    scaleMode: ScaleMode,
    rotationAngle: Int,
    showHud: Boolean,
    discoveredDevices: List<DiscoveredDevice>,
    manualIpInput: String,
    onManualIpChange: (String) -> Unit,
    onConnectToDevice: (DiscoveredDevice) -> Unit,
    onConnectManualIp: (String) -> Unit,
    onConnectUsbTethering: () -> Unit,
    onDisconnect: () -> Unit,
    onCycleScaleMode: () -> Unit,
    onCycleRotation: () -> Unit,
    onToggleHud: () -> Unit,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onSendTouch: (action: String, xNorm: Float, yNorm: Float) -> Unit = { _, _, _ -> },
    onSendKey: (key: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AutoCarbon)
    ) {
        when (clientState) {
            is ClientState.Connected -> {
                // Connected: Fullscreen Live Video Stream Viewport
                LiveMirrorViewport(
                    bitmap = latestFrame,
                    scaleMode = scaleMode,
                    rotationAngle = rotationAngle,
                    stats = stats,
                    showHud = showHud,
                    isFullscreen = isFullscreen,
                    onDisconnect = onDisconnect,
                    onCycleScaleMode = onCycleScaleMode,
                    onCycleRotation = onCycleRotation,
                    onToggleHud = onToggleHud,
                    onToggleFullscreen = onToggleFullscreen,
                    onSendTouch = onSendTouch,
                    onSendKey = onSendKey
                )
            }
            else -> {
                // Disconnected or Connecting: Setup & Discovery View
                ReceiverSetupView(
                    clientState = clientState,
                    discoveredDevices = discoveredDevices,
                    manualIpInput = manualIpInput,
                    onManualIpChange = onManualIpChange,
                    onConnectToDevice = onConnectToDevice,
                    onConnectManualIp = onConnectManualIp,
                    onConnectUsbTethering = onConnectUsbTethering
                )
            }
        }
    }
}

@Composable
private fun ReceiverSetupView(
    clientState: ClientState,
    discoveredDevices: List<DiscoveredDevice>,
    manualIpInput: String,
    onManualIpChange: (String) -> Unit,
    onConnectToDevice: (DiscoveredDevice) -> Unit,
    onConnectManualIp: (String) -> Unit,
    onConnectUsbTethering: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Mode Header
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurfaceElevated),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AutoSkySecondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "Car Stereo",
                        tint = AutoSkySecondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Car Stereo Display Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary
                    )
                    Text(
                        text = "Connects to phone via USB cable or Wi-Fi with sub-50ms latency",
                        fontSize = 11.sp,
                        color = AutoTextSecondary
                    )
                }
            }
        }

        // Connecting indicator
        if (clientState is ClientState.Connecting) {
            Surface(
                color = AutoSurfaceElevated,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AutoCyanPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "Connecting to ${clientState.ip}:${clientState.port}...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AutoCyanPrimary
                    )
                }
            }
        }

        if (clientState is ClientState.Error) {
            Surface(
                color = AutoRedAlert.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AutoRedAlert),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Connection Error: ${clientState.message}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AutoRedAlert,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // 1. One-Tap USB Tethering Connect Button (Best method)
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AutoCyanPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "USB",
                            tint = AutoCyanPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1-Tap USB Tethering",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = AutoGreenOk.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "FASTEST • 0ms LAG",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AutoGreenOk,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Plug USB cable from phone to stereo and tap below. Uses standard 192.168.42.129 address with ultra-low latency.",
                    fontSize = 12.sp,
                    color = AutoTextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onConnectUsbTethering,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AutoCyanPrimary,
                        contentColor = Color(0xFF003830)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .testTag("connect_usb_tethering_button")
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = "Connect",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONNECT USB MIRROR (192.168.42.129)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 2. Discovered Sender Phones (via Wi-Fi / Hotspot / USB)
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detected Phones Nearby",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary
                    )

                    Text(
                        text = "Scanning UDP...",
                        fontSize = 11.sp,
                        color = AutoSkySecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (discoveredDevices.isEmpty()) {
                    Surface(
                        color = AutoSurfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = "Waiting",
                                tint = AutoTextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Waiting for phone beacon...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = AutoTextSecondary
                            )
                            Text(
                                text = "Make sure 'Start Screen Mirror' is active on your phone",
                                fontSize = 10.sp,
                                color = AutoTextMuted
                            )
                        }
                    }
                } else {
                    discoveredDevices.forEach { device ->
                        Surface(
                            color = AutoSurfaceElevated,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = "Device",
                                        tint = AutoCyanPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = device.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AutoTextPrimary
                                        )
                                        Text(
                                            text = "${device.ip} • ${device.connectionType.title}",
                                            fontSize = 11.sp,
                                            color = AutoCyanPrimary
                                        )
                                    }
                                }

                                Button(
                                    onClick = { onConnectToDevice(device) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AutoCyanPrimary,
                                        contentColor = Color(0xFF003830)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text(
                                        text = "CONNECT",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Manual IP Connect
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Manual Connection",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AutoTextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = onManualIpChange,
                        placeholder = { Text("e.g. 192.168.43.1", color = AutoTextMuted, fontSize = 13.sp) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onConnectManualIp(manualIpInput) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AutoCyanPrimary,
                            unfocusedBorderColor = AutoBorder,
                            focusedTextColor = AutoTextPrimary,
                            unfocusedTextColor = AutoTextPrimary
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_ip_input")
                    )

                    Button(
                        onClick = { onConnectManualIp(manualIpInput) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AutoSkySecondary,
                            contentColor = Color(0xFF002238)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .testTag("connect_manual_button")
                            .height(54.dp)
                    ) {
                        Text(
                            text = "CONNECT",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveMirrorViewport(
    bitmap: Bitmap?,
    scaleMode: ScaleMode,
    rotationAngle: Int,
    stats: StreamStats,
    showHud: Boolean,
    isFullscreen: Boolean,
    onDisconnect: () -> Unit,
    onCycleScaleMode: () -> Unit,
    onCycleRotation: () -> Unit,
    onToggleHud: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSendTouch: (action: String, xNorm: Float, yNorm: Float) -> Unit,
    onSendKey: (key: String) -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }

    // Auto-hide controls after 5 seconds of inactivity
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(5000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(bitmap, scaleMode, rotationAngle) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val (normX, normY) = calculateNormCoords(
                        down.position,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        bitmap,
                        scaleMode,
                        rotationAngle
                    )
                    onSendTouch("down", normX, normY)

                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (change.pressed) {
                            val (currX, currY) = calculateNormCoords(
                                change.position,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                bitmap,
                                scaleMode,
                                rotationAngle
                            )
                            onSendTouch("move", currX, currY)
                        } else {
                            val (upX, upY) = calculateNormCoords(
                                change.position,
                                size.width.toFloat(),
                                size.height.toFloat(),
                                bitmap,
                                scaleMode,
                                rotationAngle
                            )
                            onSendTouch("up", upX, upY)
                            break
                        }
                    }
                }
            }
    ) {
        // 1. Hardware Accelerated Frame Canvas
        if (bitmap != null && !bitmap.isRecycled) {
            val imageBitmap = bitmap.asImageBitmap()

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val bmpW = if (rotationAngle % 180 == 0) bitmap.width.toFloat() else bitmap.height.toFloat()
                val bmpH = if (rotationAngle % 180 == 0) bitmap.height.toFloat() else bitmap.width.toFloat()

                rotate(rotationAngle.toFloat(), pivot = center) {
                    when (scaleMode) {
                        ScaleMode.FILL -> {
                            // Stretch to fill entire screen
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(0, 0),
                                dstSize = IntSize(canvasWidth.toInt(), canvasHeight.toInt())
                            )
                        }
                        ScaleMode.FIT -> {
                            // Maintain aspect ratio with letterbox
                            val scale = minOf(canvasWidth / bmpW, canvasHeight / bmpH)
                            val destW = (bitmap.width * scale).toInt()
                            val destH = (bitmap.height * scale).toInt()
                            val offsetX = ((canvasWidth - destW) / 2).toInt()
                            val offsetY = ((canvasHeight - destH) / 2).toInt()

                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(offsetX, offsetY),
                                dstSize = IntSize(destW, destH)
                            )
                        }
                        ScaleMode.ORIGINAL -> {
                            val offsetX = ((canvasWidth - bitmap.width) / 2).toInt()
                            val offsetY = ((canvasHeight - bitmap.height) / 2).toInt()
                            drawImage(
                                image = imageBitmap,
                                dstOffset = IntOffset(offsetX, offsetY),
                                dstSize = IntSize(bitmap.width, bitmap.height)
                            )
                        }
                    }
                }
            }
        } else {
            // Waiting for first frame
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AutoCyanPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Connected! Receiving phone screen...",
                        color = AutoCyanPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Top-Left Floating HUD Stats (Auto-hides with controls)
        AnimatedVisibility(
            visible = showHud && controlsVisible,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Surface(
                color = AutoCarbon.copy(alpha = 0.85f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "⚡ ${stats.latencyMs}ms",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoCyanPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "🎬 ${stats.fps} fps",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoGreenOk,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "📶 ${stats.bitrateKbps} kbps",
                        fontSize = 12.sp,
                        color = AutoSkySecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = stats.resolution,
                        fontSize = 11.sp,
                        color = AutoTextMuted,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 3. Top-Right Floating Fullscreen Exit Pill (Visible in Fullscreen when touched)
        AnimatedVisibility(
            visible = isFullscreen && controlsVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                color = AutoCarbon.copy(alpha = 0.88f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary),
                modifier = Modifier.clickable { onToggleFullscreen() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = AutoCyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Exit Fullscreen",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoCyanPrimary
                    )
                }
            }
        }

        // 4. Bottom Floating Automotive Quick Controls Bar (Auto-hides with touch to show)
        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Surface(
                color = AutoCarbon.copy(alpha = 0.88f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder)
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // In-Car Reverse Touch Navigation Keys
                    HudActionButton(
                        label = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        color = Color(0xFFEF4444),
                        onClick = { onSendKey("back") }
                    )

                    HudActionButton(
                        label = "Home",
                        icon = Icons.Default.Home,
                        color = AutoCyanPrimary,
                        onClick = { onSendKey("home") }
                    )

                    HudActionButton(
                        label = "Apps",
                        icon = Icons.Default.Menu,
                        color = AutoSkySecondary,
                        onClick = { onSendKey("recents") }
                    )

                    // Fullscreen toggle button
                    HudActionButton(
                        label = if (isFullscreen) "Exit Full" else "Fullscreen",
                        icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        color = AutoGreenOk,
                        onClick = onToggleFullscreen
                    )

                    // Scale mode button
                    HudActionButton(
                        label = scaleMode.label.substringBefore(" "),
                        icon = Icons.Default.AspectRatio,
                        color = AutoCyanPrimary,
                        onClick = onCycleScaleMode
                    )

                    // Rotation button
                    HudActionButton(
                        label = "$rotationAngle°",
                        icon = Icons.Default.RotateRight,
                        color = AutoSkySecondary,
                        onClick = onCycleRotation
                    )

                    // HUD toggle
                    HudActionButton(
                        label = if (showHud) "Stats" else "Stats Off",
                        icon = if (showHud) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        color = AutoAmber,
                        onClick = onToggleHud
                    )

                    // Disconnect
                    HudActionButton(
                        label = "Exit",
                        icon = Icons.Default.Close,
                        color = AutoRedAlert,
                        onClick = onDisconnect
                    )
                }
            }
        }

        // 5. Floating Quick Controls Pill (Always visible to toggle controls or open menu)
        IconButton(
            onClick = { controlsVisible = !controlsVisible },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(42.dp)
                .background(AutoCarbon.copy(alpha = 0.75f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Controls Menu",
                tint = if (controlsVisible) AutoCyanPrimary else AutoTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun calculateNormCoords(
    touchPos: androidx.compose.ui.geometry.Offset,
    canvasW: Float,
    canvasH: Float,
    bitmap: Bitmap?,
    scaleMode: ScaleMode,
    rotationAngle: Int
): Pair<Float, Float> {
    if (bitmap == null || bitmap.width == 0 || bitmap.height == 0 || canvasW <= 0 || canvasH <= 0) {
        return Pair(
            (touchPos.x / maxOf(canvasW, 1f)).coerceIn(0f, 1f),
            (touchPos.y / maxOf(canvasH, 1f)).coerceIn(0f, 1f)
        )
    }

    val bmpW = if (rotationAngle % 180 == 0) bitmap.width.toFloat() else bitmap.height.toFloat()
    val bmpH = if (rotationAngle % 180 == 0) bitmap.height.toFloat() else bitmap.width.toFloat()

    var destW = canvasW
    var destH = canvasH
    var offsetX = 0f
    var offsetY = 0f

    when (scaleMode) {
        ScaleMode.FILL -> {
            destW = canvasW
            destH = canvasH
        }
        ScaleMode.FIT -> {
            val scale = minOf(canvasW / bmpW, canvasH / bmpH)
            destW = bmpW * scale
            destH = bmpH * scale
            offsetX = (canvasW - destW) / 2f
            offsetY = (canvasH - destH) / 2f
        }
        ScaleMode.ORIGINAL -> {
            destW = bmpW
            destH = bmpH
            offsetX = (canvasW - bmpW) / 2f
            offsetY = (canvasH - bmpH) / 2f
        }
    }

    val xInFrame = (touchPos.x - offsetX).coerceIn(0f, destW)
    val yInFrame = (touchPos.y - offsetY).coerceIn(0f, destH)

    val normX = if (destW > 0) (xInFrame / destW).coerceIn(0f, 1f) else 0f
    val normY = if (destH > 0) (yInFrame / destH).coerceIn(0f, 1f) else 0f

    return Pair(normX, normY)
}

@Composable
private fun HudActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        color = AutoSurfaceElevated.copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AutoTextPrimary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
