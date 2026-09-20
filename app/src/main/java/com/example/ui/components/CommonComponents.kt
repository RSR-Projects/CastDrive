package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppRole
import com.example.model.ConnectionType
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
fun AppHeader(
    primaryNetwork: NetworkInfo?,
    onRefreshNetworks: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AutoSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AutoSurfaceElevated)
                        .border(1.dp, AutoCyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = "CastDrive Logo",
                        tint = AutoCyanPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CastDrive",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AutoTextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AutoCyanPrimary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LOW LATENCY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = AutoCyanPrimary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    val connText = when {
                        primaryNetwork?.isUsbTethering == true -> "⚡ USB Tethering Active (${primaryNetwork.ipAddress})"
                        primaryNetwork?.isHotspot == true -> "📡 Wi-Fi Hotspot Active (${primaryNetwork.ipAddress})"
                        primaryNetwork != null -> "📶 Wi-Fi (${primaryNetwork.ipAddress})"
                        else -> "No Network Detected"
                    }

                    Text(
                        text = connText,
                        fontSize = 12.sp,
                        color = if (primaryNetwork?.isUsbTethering == true) AutoCyanPrimary else AutoTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onRefreshNetworks,
                modifier = Modifier
                    .testTag("refresh_network_button")
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Networks",
                    tint = AutoSkySecondary
                )
            }
        }
    }
}

@Composable
fun RoleSelectorTabs(
    selectedRole: AppRole,
    onRoleSelected: (AppRole) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AutoSurfaceElevated)
            .border(1.dp, AutoBorder, RoundedCornerShape(14.dp))
            .padding(4.dp)
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RoleTabItem(
            title = "Cast Phone",
            subtitle = "Transmitter",
            icon = Icons.Default.Cast,
            isSelected = selectedRole == AppRole.SENDER,
            testTag = "tab_sender",
            onClick = { onRoleSelected(AppRole.SENDER) },
            modifier = Modifier.widthIn(min = 82.dp)
        )

        RoleTabItem(
            title = "Car Stereo",
            subtitle = "Receiver",
            icon = Icons.Default.DirectionsCar,
            isSelected = selectedRole == AppRole.RECEIVER,
            testTag = "tab_receiver",
            onClick = { onRoleSelected(AppRole.RECEIVER) },
            modifier = Modifier.widthIn(min = 82.dp)
        )

        RoleTabItem(
            title = "Browser",
            subtitle = "Zero Install",
            icon = Icons.Default.Language,
            isSelected = selectedRole == AppRole.WEB_GUIDE,
            testTag = "tab_web_guide",
            onClick = { onRoleSelected(AppRole.WEB_GUIDE) },
            modifier = Modifier.widthIn(min = 82.dp)
        )

        RoleTabItem(
            title = "Setup",
            subtitle = "How-To",
            icon = Icons.Default.HelpOutline,
            isSelected = selectedRole == AppRole.GUIDE,
            testTag = "tab_guide",
            onClick = { onRoleSelected(AppRole.GUIDE) },
            modifier = Modifier.widthIn(min = 82.dp)
        )
    }
}

@Composable
private fun RoleTabItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isSelected) AutoCyanPrimary.copy(alpha = 0.15f) else Color.Transparent
    val border = if (isSelected) AutoCyanPrimary else Color.Transparent
    val textColor = if (isSelected) AutoCyanPrimary else AutoTextSecondary

    Column(
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            fontSize = 9.sp,
            color = if (isSelected) AutoCyanPrimary.copy(alpha = 0.8f) else AutoTextMuted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MetricBadge(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = AutoCyanPrimary,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AutoSurfaceElevated,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AutoBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = label,
                    fontSize = 9.sp,
                    color = AutoTextMuted,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = value,
                    fontSize = 13.sp,
                    color = AutoTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
