package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppRole
import com.example.stream.ClientState
import com.example.ui.components.AppHeader
import com.example.ui.components.RoleSelectorTabs
import com.example.ui.screens.ReceiverScreen
import com.example.ui.screens.SenderScreen
import com.example.ui.screens.SetupGuideScreen
import com.example.ui.screens.WebGuideScreen
import com.example.ui.theme.AutoCarbon
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MirrorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MirrorViewModel by viewModels()

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startScreenMirroring(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            requestMediaProjection()
        } else {
            // Still proceed on older versions or if denied, though notification may not appear
            requestMediaProjection()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep car stereo / phone screen awake while app is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    viewModel = viewModel,
                    onRequestStartMirroring = { checkPermissionsAndStartMirroring() }
                )
            }
        }
    }

    private fun checkPermissionsAndStartMirroring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPerm = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasNotificationPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestMediaProjection()
    }

    private fun requestMediaProjection() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mediaProjectionManager != null) {
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        } else {
            Toast.makeText(this, "MediaProjection not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainAppScreen(
    viewModel: MirrorViewModel,
    onRequestStartMirroring: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val isMirroring by viewModel.isMirroring.collectAsStateWithLifecycle()
    val activeNetworks by viewModel.activeNetworks.collectAsStateWithLifecycle()
    val qualityPreset by viewModel.qualityPreset.collectAsStateWithLifecycle()
    val senderStats by viewModel.senderStats.collectAsStateWithLifecycle()

    val receiverState by viewModel.receiverState.collectAsStateWithLifecycle()
    val receiverFrame by viewModel.receiverFrame.collectAsStateWithLifecycle()
    val receiverStats by viewModel.receiverStats.collectAsStateWithLifecycle()
    val scaleMode by viewModel.scaleMode.collectAsStateWithLifecycle()
    val rotationAngle by viewModel.rotationAngle.collectAsStateWithLifecycle()
    val showHud by viewModel.showReceiverHud.collectAsStateWithLifecycle()
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val manualIpInput by viewModel.manualIpInput.collectAsStateWithLifecycle()

    val primaryNetwork = activeNetworks.firstOrNull()

    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    // Exit fullscreen if receiver disconnects
    LaunchedEffect(receiverState) {
        if (receiverState !is ClientState.Connected) {
            isFullscreen = false
        }
    }

    val context = LocalContext.current
    DisposableEffect(isFullscreen) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val win = (context as? Activity)?.window
            if (win != null) {
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AutoCarbon,
        contentWindowInsets = if (isFullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullscreen) PaddingValues(0.dp) else innerPadding)
                .background(AutoCarbon)
        ) {
            if (!isFullscreen) {
                // Header with active connection indicator
                AppHeader(
                    primaryNetwork = primaryNetwork,
                    onRefreshNetworks = { viewModel.refreshNetworks() }
                )

                // Segmented mode switcher tabs
                RoleSelectorTabs(
                    selectedRole = currentRole,
                    onRoleSelected = { viewModel.setRole(it) }
                )
            }

            // Active Viewport
            Box(modifier = Modifier.weight(1f)) {
                when (currentRole) {
                    AppRole.SENDER -> {
                        SenderScreen(
                            isMirroring = isMirroring,
                            primaryNetwork = primaryNetwork,
                            activeNetworks = activeNetworks,
                            qualityPreset = qualityPreset,
                            stats = senderStats,
                            onStartMirroring = onRequestStartMirroring,
                            onStopMirroring = { viewModel.stopScreenMirroring() },
                            onSelectQuality = { viewModel.setQualityPreset(it) },
                            onLaunchApp = { pkg, fallback -> viewModel.launchAppShortcut(pkg, fallback) }
                        )
                    }
                    AppRole.RECEIVER -> {
                        ReceiverScreen(
                            clientState = receiverState,
                            latestFrame = receiverFrame,
                            stats = receiverStats,
                            scaleMode = scaleMode,
                            rotationAngle = rotationAngle,
                            showHud = showHud,
                            discoveredDevices = discoveredDevices,
                            manualIpInput = manualIpInput,
                            isFullscreen = isFullscreen,
                            onToggleFullscreen = { isFullscreen = !isFullscreen },
                            onManualIpChange = { viewModel.setManualIp(it) },
                            onConnectToDevice = { viewModel.connectToDevice(it) },
                            onConnectManualIp = { viewModel.connectToManualIp(it) },
                            onConnectUsbTethering = { viewModel.connectToUsbTetheringDefault() },
                            onDisconnect = { viewModel.disconnectReceiver() },
                            onCycleScaleMode = {
                                val nextMode = when (scaleMode) {
                                    com.example.model.ScaleMode.FIT -> com.example.model.ScaleMode.FILL
                                    com.example.model.ScaleMode.FILL -> com.example.model.ScaleMode.ORIGINAL
                                    com.example.model.ScaleMode.ORIGINAL -> com.example.model.ScaleMode.FIT
                                }
                                viewModel.setScaleMode(nextMode)
                            },
                            onCycleRotation = { viewModel.cycleRotation() },
                            onToggleHud = { viewModel.toggleHud() },
                            onSendTouch = { action, x, y -> viewModel.sendTouch(action, x, y) },
                            onSendKey = { key -> viewModel.sendKey(key) }
                        )
                    }
                    AppRole.WEB_GUIDE -> {
                        WebGuideScreen(
                            primaryNetwork = primaryNetwork,
                            isMirroring = isMirroring
                        )
                    }
                    AppRole.GUIDE -> {
                        SetupGuideScreen()
                    }
                }
            }
        }
    }
}
