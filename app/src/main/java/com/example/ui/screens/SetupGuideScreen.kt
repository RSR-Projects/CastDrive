package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AutoAmber
import com.example.ui.theme.AutoBorder
import com.example.ui.theme.AutoCyanPrimary
import com.example.ui.theme.AutoGreenOk
import com.example.ui.theme.AutoSkySecondary
import com.example.ui.theme.AutoSurface
import com.example.ui.theme.AutoSurfaceElevated
import com.example.ui.theme.AutoTextMuted
import com.example.ui.theme.AutoTextPrimary
import com.example.ui.theme.AutoTextSecondary

@Composable
fun SetupGuideScreen(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Option 1: USB Tethering (Ranked #1)
        MethodCard(
            title = "Method 1: USB Tethering (RECOMMENDED)",
            subtitle = "★ #1 Best for Minimal Latency & 60 FPS",
            icon = Icons.Default.Usb,
            accentColor = AutoCyanPrimary,
            pros = listOf(
                "Sub-30ms ultra low latency (wired direct connection)",
                "Zero Wi-Fi interference or lag spikes in traffic",
                "Simultaneously charges your phone while driving",
                "100% compatible with Android 7 & 8 Chinese head units"
            ),
            steps = listOf(
                "Plug your phone's USB charging cable into the Car Stereo USB port.",
                "On your phone, go to Settings → Network / Hotspot → Enable 'USB Tethering'.",
                "Open CastDrive on stereo and tap 'Connect USB Mirror (192.168.42.129)'. Done!"
            )
        )

        // Option 2: Wi-Fi Hotspot (Ranked #2)
        MethodCard(
            title = "Method 2: Wi-Fi Hotspot (Best Wireless)",
            subtitle = "No USB cable required • Direct wireless link",
            icon = Icons.Default.WifiTethering,
            accentColor = AutoSkySecondary,
            pros = listOf(
                "Completely wireless phone screen mirroring",
                "No car router needed (phone creates the network)",
                "Low latency (typically 40 - 60ms)"
            ),
            steps = listOf(
                "Turn on 'Personal Hotspot' or 'Portable Wi-Fi Hotspot' on your phone.",
                "On your car stereo, connect to your phone's Wi-Fi hotspot in Wi-Fi settings.",
                "Open CastDrive on stereo and tap the detected phone, or open the browser URL."
            )
        )

        // Audio & Android 7/8 Compatibility FAQ
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Audio",
                        tint = AutoSkySecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Direct In-Car Audio Streaming",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "CastDrive captures internal device audio directly (Android 10+). Music, media, and navigation sounds stream synchronously with video frames to the CastDrive receiver and in-car web player without requiring external audio cabling.",
                    fontSize = 12.sp,
                    color = AutoTextSecondary,
                    lineHeight = 17.sp
                )
            }
        }

        // Android Auto Launcher Troubleshooting
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, AutoCyanPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Android Auto Alert",
                        tint = AutoAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Not Showing in Android Auto?",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoTextPrimary
                        )
                        Text(
                            text = "Google's Installer Check & How to Bypass",
                            fontSize = 11.sp,
                            color = AutoCyanPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Why it happens:\nModern Android Auto enforces a security check that verifies the app's installer source. If an APK was installed directly via file manager or browser, Google flags it as an unverified installer and silently hides it from the car's screen and 'Customize launcher', even with Developer settings & 'Unknown sources' turned on.",
                    fontSize = 12.sp,
                    color = AutoTextSecondary,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = AutoSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "3 Proven Ways to Solve This:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoGreenOk
                        )

                        Text(
                            text = "1. KingInstaller (Recommended / No PC):\nDownload KingInstaller (open-source tool), open it, choose CastDrive.apk, and tap 'Install as KingInstaller'. It registers the package as installed by Google Play (com.android.vending), making it appear on the car immediately!",
                            fontSize = 11.sp,
                            color = AutoTextPrimary,
                            lineHeight = 16.sp
                        )

                        Text(
                            text = "2. ADB Command (via PC / Mac / Bugjaeger):\nRun: adb install -r -i com.android.vending CastDrive.apk\nThe '-i' flag sets the installer source to Google Play.",
                            fontSize = 11.sp,
                            color = AutoTextPrimary,
                            lineHeight = 16.sp
                        )

                        Text(
                            text = "3. Zero-Install In-Car Browser Mirroring:\nNo Android Auto bypass needed! Tap 'Start Mirroring' on this phone, then open http://192.168.42.129:8080 in the car stereo's browser for instant video streaming without app installation!",
                            fontSize = 11.sp,
                            color = AutoTextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Android 7 & 8 Car Stereo Installation Guide
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = AutoAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Installing on Android 7 & 8 Head Units",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "• Sideloading APK: Copy this app's APK to a USB flash drive, plug into car stereo, open 'ApkInstaller' or 'File Manager' on head unit, and tap Install.\n\n• Alternatively: If you don't want to install an APK on the car stereo, use Method 3 (Browser Mirroring) which runs directly inside the car stereo's Chrome browser!",
                    fontSize = 12.sp,
                    color = AutoTextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun MethodCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    pros: List<String>,
    steps: List<String>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AutoSurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextPrimary
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pros
            pros.forEach { pro ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AutoGreenOk,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pro,
                        fontSize = 11.sp,
                        color = AutoTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Steps
            Surface(
                color = AutoSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "QUICK SETUP STEPS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AutoTextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    steps.forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            fontSize = 11.sp,
                            color = AutoTextPrimary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
