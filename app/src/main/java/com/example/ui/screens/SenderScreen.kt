package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetworkInfo
import com.example.model.QualityPreset
import com.example.model.StreamStats
import com.example.touch.TouchController
import com.example.ui.components.MetricBadge
import com.example.util.AndroidAutoHelper
import com.example.util.TetheringHelper
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
fun SenderScreen(
    isMirroring: Boolean,
    primaryNetwork: NetworkInfo?,
    activeNetworks: List<NetworkInfo>,
    qualityPreset: QualityPreset,
    stats: StreamStats,
    onStartMirroring: () -> Unit,
    onStopMirroring: () -> Unit,
    onSelectQuality: (QualityPreset) -> Unit,
    onLaunchApp: (packageName: String, fallbackUrl: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedNetworkIndex by remember { mutableStateOf(0) }
    var showAndroidAutoHelp by remember { mutableStateOf(false) }
    val currentNetwork = if (activeNetworks.isNotEmpty()) {
        activeNetworks.getOrNull(selectedNetworkIndex) ?: activeNetworks.first()
    } else {
        primaryNetwork
    }
    val primaryIp = currentNetwork?.ipAddress ?: (primaryNetwork?.ipAddress ?: "192.168.43.1")
    val carAppUrl = "$primaryIp:8088"
    val carWebUrl = "http://$primaryIp:8080"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Primary Cast Control Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurfaceElevated),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isMirroring) AutoCyanPrimary else AutoBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isMirroring) AutoGreenOk else AutoTextMuted)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMirroring) "TRANSMITTING LIVE" else "READY TO CAST",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMirroring) AutoGreenOk else AutoTextSecondary,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (isMirroring) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AutoCyanPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${stats.clientCount} Stereo Connected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AutoCyanPrimary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Big Action Button (Minimum 56dp height for driving touch standard)
                Button(
                    onClick = {
                        if (isMirroring) onStopMirroring() else onStartMirroring()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMirroring) AutoRedAlert else AutoCyanPrimary,
                        contentColor = if (isMirroring) Color.White else Color(0xFF003830)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .testTag("toggle_mirror_button")
                        .fillMaxWidth()
                        .height(58.dp)
                ) {
                    Icon(
                        imageVector = if (isMirroring) Icons.Default.Stop else Icons.Default.Cast,
                        contentDescription = if (isMirroring) "Stop Mirroring" else "Start Mirroring",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isMirroring) "STOP SCREEN MIRROR" else "START SCREEN MIRROR",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (isMirroring) {
                    Surface(
                        color = AutoGreenOk.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AutoGreenOk.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "Audio Active",
                                tint = AutoGreenOk,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Direct Audio Streaming Active (No Bluetooth needed)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AutoGreenOk
                            )
                        }
                    }
                }

                if (!isMirroring) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Projects phone screen to Android 7 & 8 Car Stereos with minimal lag",
                        fontSize = 11.sp,
                        color = AutoTextMuted
                    )
                }
            }
        }

        // 2. USB Tethering Quick Helper (0ms Lag)
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (activeNetworks.any { it.isUsbTethering }) AutoGreenOk else AutoBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "USB",
                            tint = if (activeNetworks.any { it.isUsbTethering }) AutoGreenOk else AutoCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "USB Tethering (0ms Lag)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoTextPrimary
                        )
                    }

                    val isUsbActive = activeNetworks.any { it.isUsbTethering }
                    Surface(
                        color = if (isUsbActive) AutoGreenOk.copy(alpha = 0.15f) else AutoTextMuted.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isUsbActive) "● CONNECTED" else "DISCONNECTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUsbActive) AutoGreenOk else AutoTextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Plug USB cable to car stereo, then tap below to toggle ON 'USB Tethering' in Android Settings. No Wi-Fi required!",
                    fontSize = 11.sp,
                    color = AutoTextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        val opened = TetheringHelper.openTetheringSettings(context)
                        if (!opened) {
                            Toast.makeText(context, "Open Settings > Hotspot & Tethering > USB Tethering", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AutoSurfaceElevated,
                        contentColor = AutoCyanPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Android USB Tethering Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 3. Touchscreen Reverse Control Card (Control Phone from Car Display)
        val isTouchActive = TouchController.isAccessibilityEnabled(context)
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isTouchActive) AutoGreenOk.copy(alpha = 0.6f) else AutoBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Reverse Touch",
                            tint = if (isTouchActive) AutoGreenOk else AutoCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reverse Touchscreen Control",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = if (isTouchActive) AutoGreenOk.copy(alpha = 0.15f) else AutoCyanPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isTouchActive) "● ACTIVE" else "SETUP NEEDED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isTouchActive) AutoGreenOk else AutoCyanPrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isTouchActive) {
                        "Touches, taps, drags, and back/home gestures from the native CastDrive Receiver app on your car stereo are directly injected into this phone in real-time."
                    } else {
                        "Control this phone directly from your car touchscreen without root. Enable CastDrive in Android Accessibility Settings to allow remote gestures."
                    },
                    fontSize = 12.sp,
                    color = AutoTextSecondary
                )

                if (!isTouchActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { TouchController.openAccessibilitySettings(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AutoCyanPrimary,
                            contentColor = Color(0xFF003830)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Reverse Touch Service", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Active Streaming Endpoints (For Car Head Unit)
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
                        text = "Car Stereo Connection URLs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = AutoGreenOk.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "● SERVER ONLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoGreenOk,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enter in Car Stereo browser or CastDrive Receiver app:",
                    fontSize = 11.sp,
                    color = AutoTextSecondary
                )

                // Network Interface Switcher if multiple are available
                if (activeNetworks.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Available Network Interfaces:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AutoSkySecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeNetworks.forEachIndexed { idx, net ->
                            val isSelected = idx == selectedNetworkIndex
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) AutoCyanPrimary.copy(alpha = 0.2f) else AutoSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) AutoCyanPrimary else AutoBorder
                                ),
                                modifier = Modifier.clickable { selectedNetworkIndex = idx }
                            ) {
                                Text(
                                    text = "${net.interfaceName} (${net.ipAddress})",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AutoCyanPrimary else AutoTextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Browser address
                UrlCopyRow(
                    label = "Car Browser",
                    badge = "HTTP :8080",
                    url = carWebUrl,
                    onCopy = {
                        copyToClipboard(context, carWebUrl)
                        Toast.makeText(context, "Copied Browser URL", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Test in Browser Button
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(carWebUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AutoCyanPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Cast,
                        contentDescription = "Test in Browser",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Web Stream in Browser", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App Receiver address
                UrlCopyRow(
                    label = "Car Stereo App",
                    badge = "TCP :8088",
                    url = carAppUrl,
                    onCopy = {
                        copyToClipboard(context, carAppUrl)
                        Toast.makeText(context, "Copied Car App address", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // 3. Live Diagnostics / Stats Strip
        if (isMirroring) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AutoSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Live Stream Metrics",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoCyanPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBadge(
                            label = "FPS",
                            value = "${stats.fps} fps",
                            icon = Icons.Default.Speed,
                            color = AutoGreenOk
                        )
                        MetricBadge(
                            label = "Bitrate",
                            value = "${stats.bitrateKbps} kbps",
                            icon = Icons.Default.NetworkCheck,
                            color = AutoSkySecondary
                        )
                        MetricBadge(
                            label = "Lag",
                            value = "~${stats.latencyMs} ms",
                            icon = Icons.Default.Sensors,
                            color = AutoAmber
                        )
                    }
                }
            }
        }

        // 4. Quality Preset Selector
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
                        text = "Stream Quality Preset",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary
                    )
                    Text(
                        text = "Optimized for Android 7/8",
                        fontSize = 11.sp,
                        color = AutoCyanPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                QualityPreset.values().forEach { preset ->
                    val isSelected = preset == qualityPreset
                    Surface(
                        color = if (isSelected) AutoCyanPrimary.copy(alpha = 0.12f) else AutoSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) AutoCyanPrimary else AutoBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSelectQuality(preset) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, if (isSelected) AutoCyanPrimary else AutoTextMuted, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(AutoCyanPrimary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = preset.label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AutoCyanPrimary else AutoTextPrimary
                                )
                                Text(
                                    text = preset.description,
                                    fontSize = 11.sp,
                                    color = AutoTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Driver Quick Launch Shortcuts (Maps, Music, Dialer)
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Quick Driving Apps",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AutoTextPrimary
                )
                Text(
                    text = "Launch navigation or media while mirroring",
                    fontSize = 11.sp,
                    color = AutoTextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickAppButton(
                        title = "Maps",
                        icon = Icons.Default.Navigation,
                        color = AutoCyanPrimary,
                        onClick = { onLaunchApp("com.google.android.apps.maps", "https://maps.google.com") },
                        modifier = Modifier.weight(1f)
                    )

                    QuickAppButton(
                        title = "Waze",
                        icon = Icons.Default.Map,
                        color = AutoSkySecondary,
                        onClick = { onLaunchApp("com.waze", null) },
                        modifier = Modifier.weight(1f)
                    )

                    QuickAppButton(
                        title = "Spotify",
                        icon = Icons.Default.MusicNote,
                        color = AutoGreenOk,
                        onClick = { onLaunchApp("com.spotify.music", null) },
                        modifier = Modifier.weight(1f)
                    )

                    QuickAppButton(
                        title = "Phone",
                        icon = Icons.Default.Call,
                        color = AutoAmber,
                        onClick = { onLaunchApp("com.google.android.dialer", null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 6. Android Auto Support Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoCyanPrimary.copy(alpha = 0.08f)),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = AutoCyanPrimary.copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "Android Auto",
                                tint = AutoCyanPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Android Auto Support",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AutoCyanPrimary
                            )
                            Surface(
                                color = AutoCyanPrimary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "READY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Direct vehicle display mirroring with hardware acceleration.",
                            fontSize = 11.sp,
                            color = AutoTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Alert tip banner explaining why sideloaded apps need Unknown Sources
                Surface(
                    color = AutoAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AutoAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AutoAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Not showing on your car? Android Auto requires 'Unknown sources' enabled in its Developer settings for APK apps.",
                            fontSize = 11.sp,
                            color = AutoTextPrimary,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showAndroidAutoHelp = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AutoCyanPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Show on Car / Launcher Guide",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            AndroidAutoHelper.openAndroidAutoSettings(context)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AutoCyanPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Android Auto Settings",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // 7. Audio Sync Tip
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurfaceElevated),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = "Audio note",
                    tint = AutoSkySecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Audio Streaming for Car Speakers",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary
                    )
                    Text(
                        text = "Direct internal audio streaming is built-in. Music, navigation voices, and media audio stream synchronously with video directly to your car stereo receiver or browser.",
                        fontSize = 11.sp,
                        color = AutoTextSecondary
                    )
                }
            }
        }
    }

    if (showAndroidAutoHelp) {
        AlertDialog(
            onDismissRequest = { showAndroidAutoHelp = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = AutoCyanPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Show on Android Auto",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Why Android Auto hides sideloaded apps & How to show it:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoCyanPrimary
                    )

                    SetupStepRow(
                        step = "!",
                        title = "Why it's hidden (Google's Installer Check)",
                        description = "Google updated Android Auto so that sideloaded APKs are hidden from the launcher unless installed with the Play Store source (com.android.vending), even if 'Unknown sources' is turned on."
                    )

                    SetupStepRow(
                        step = "1",
                        title = "Solution 1: KingInstaller (Easiest / No PC)",
                        description = "Download KingInstaller (free open-source tool), open it, select CastDrive.apk, and tap 'Install as KingInstaller'. This spoofs the installer to Google Play Store and CastDrive will immediately appear!"
                    )

                    SetupStepRow(
                        step = "2",
                        title = "Solution 2: 1-Line ADB Command",
                        description = "From a PC/Mac/Bugjaeger run: adb install -r -i com.android.vending CastDrive.apk. The '-i' parameter tells Android it was installed by Google Play."
                    )

                    SetupStepRow(
                        step = "3",
                        title = "Solution 3: Zero-Install In-Car Browser",
                        description = "Tap 'Start Mirroring' on this phone, connect car stereo to phone USB/Hotspot, and open http://192.168.42.129:8080 in your car's web browser for instant video streaming without installing an app!"
                    )

                    SetupStepRow(
                        step = "4",
                        title = "Developer Settings & Unknown Sources",
                        description = "In AA Settings -> tap 'Version' 10 times -> 3 dots (⋮) -> Developer settings -> check 'Unknown sources' & verify in 'Customize launcher'."
                    )
                }
            },
            confirmButton = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Button(
                        onClick = {
                            AndroidAutoHelper.openAndroidAutoSettings(context)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AutoCyanPrimary,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open AA Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }

                    OutlinedButton(
                        onClick = {
                            AndroidAutoHelper.openAndroidAutoAppInfo(context)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AutoCyanPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary.copy(alpha = 0.6f))
                    ) {
                        Text("Reset AA Cache", fontSize = 12.sp, maxLines = 1, softWrap = false)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAndroidAutoHelp = false }) {
                    Text("Close", color = AutoTextSecondary)
                }
            },
            containerColor = AutoSurfaceElevated,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SetupStepRow(
    step: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = AutoCyanPrimary.copy(alpha = 0.2f),
            shape = CircleShape,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AutoCyanPrimary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AutoTextPrimary
            )
            Text(
                text = description,
                fontSize = 11.sp,
                color = AutoTextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun UrlCopyRow(
    label: String,
    badge: String,
    url: String,
    onCopy: () -> Unit
) {
    Surface(
        color = AutoSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AutoTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = AutoCyanPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoCyanPrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = url,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AutoCyanPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(40.dp)
                    .background(AutoCyanPrimary.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy URL",
                    tint = AutoCyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickAppButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AutoSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AutoTextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("CastDrive URL", text)
    clipboard.setPrimaryClip(clip)
}

