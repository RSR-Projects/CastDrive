package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NetworkInfo
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
fun WebGuideScreen(
    primaryNetwork: NetworkInfo?,
    isMirroring: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val hostIp = primaryNetwork?.ipAddress ?: "192.168.42.129"
    val webUrl = "http://$hostIp:8080"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurfaceElevated),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoCyanPrimary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AutoCyanPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Browser",
                            tint = AutoCyanPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Zero-Install Web Mirroring",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoTextPrimary
                        )
                        Text(
                            text = "No app needed on the car stereo! Just open any browser",
                            fontSize = 11.sp,
                            color = AutoCyanPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "If your Android 7 or 8 car stereo cannot install new apps or lacks Google Play, you can simply open Google Chrome or the stock Browser on your stereo and navigate to the address below:",
                    fontSize = 12.sp,
                    color = AutoTextSecondary,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Big URL Box
                Surface(
                    color = AutoSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, AutoCyanPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "OPEN THIS IN STEREO BROWSER:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AutoTextMuted
                            )
                            Text(
                                text = webUrl,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AutoCyanPrimary
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("CastDrive Web", webUrl))
                                Toast.makeText(context, "URL Copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = AutoSkySecondary
                            )
                        }
                    }
                }

                if (!isMirroring) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚠️ Start screen mirror on 'Cast Phone' tab first before opening the URL.",
                        fontSize = 11.sp,
                        color = AutoAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Steps Card
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurface),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "3 Easy Steps in Car",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AutoTextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                StepItem(
                    number = "1",
                    title = "Connect Network",
                    desc = "Turn on Wi-Fi Hotspot on your phone and connect your car stereo to it, or connect via USB Cable with USB Tethering."
                )

                Spacer(modifier = Modifier.height(10.dp))

                StepItem(
                    number = "2",
                    title = "Start Mirroring on Phone",
                    desc = "Go to the 'Cast Phone' tab in this app and tap START SCREEN MIRROR."
                )

                Spacer(modifier = Modifier.height(10.dp))

                StepItem(
                    number = "3",
                    title = "Open Stereo Browser",
                    desc = "On your Android 7/8 car head unit, open Chrome or the built-in Car Browser and type $webUrl. Tap 'Fullscreen' on the stereo screen!"
                )
            }
        }

        // Features included in Web Player
        Card(
            colors = CardDefaults.cardColors(containerColor = AutoSurfaceElevated),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Built-in Web Player Features:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AutoSkySecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FeatureBullet("True Fullscreen mode (hides stereo URL bar)")
                FeatureBullet("Aspect ratio toggle (Fit / Full Screen Fill)")
                FeatureBullet("Screen rotation 90° for horizontal car dashboards")
                FeatureBullet("Auto Wake-Lock to keep car stereo screen illuminated")
                FeatureBullet("Zero-install video streaming (audio & touch require Native Receiver mode)")
            }
        }
    }
}

@Composable
private fun StepItem(
    number: String,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(AutoCyanPrimary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF003830)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AutoTextPrimary
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = AutoTextSecondary,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun FeatureBullet(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
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
            text = text,
            fontSize = 11.sp,
            color = AutoTextSecondary
        )
    }
}
