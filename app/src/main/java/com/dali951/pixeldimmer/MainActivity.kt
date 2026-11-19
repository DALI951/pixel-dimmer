package com.dali951.pixeldimmer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()

        setContent {
            PixelDimmerApp(activity = this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun PixelDimmerApp(activity: ComponentActivity) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf(MaskPreferences.load(context)) }
    var canDrawOverlays by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var a11yRunning by remember { mutableStateOf(PixelMaskAccessibilityService.isRunning) }
    var serviceDrawing by remember { mutableStateOf(OverlayController.isOverlayActivelyDrawing(context)) }
    var pendingEnableAfterPermission by remember { mutableStateOf(false) }

    var controlsAtTop by remember { mutableStateOf(false) }
    var controlsHidden by remember { mutableStateOf(false) }
    var whiteBackground by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var rootScreenOffset by remember { mutableStateOf(IntOffset.Zero) }

    var isDragging by remember { mutableStateOf(false) }
    var isNudgeActive by remember { mutableStateOf(false) }
    var nudgePulse by remember { mutableIntStateOf(0) }
    var fingerLocalPos by remember { mutableStateOf<Offset?>(null) }

    // GitHub update state
    val installedVersion = remember { GitHubUpdater.getInstalledVersionName(context) }
    var updateInfo by remember { mutableStateOf<ReleaseUpdateInfo?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf("Installed: v$installedVersion") }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }

    val (portraitW, portraitH) = remember(boxSize) {
        DisplayMetricsHelper.getPortraitSize(context)
    }

    fun commitConfig(updated: MaskConfig) {
        val normalized = updated.copy(
            dotX = updated.dotX.coerceIn(0, portraitW),
            dotY = updated.dotY.coerceIn(0, portraitH),
            dotSize = updated.dotSize.coerceIn(MaskConfig.MIN_DOT_SIZE, MaskConfig.MAX_DOT_SIZE),
            lineY = updated.lineY.coerceIn(0, portraitH),
            lineHeight = updated.lineHeight.coerceIn(MaskConfig.MIN_LINE_HEIGHT, MaskConfig.MAX_LINE_HEIGHT),
            calibrationWidth = portraitW,
            calibrationHeight = portraitH
        )
        config = normalized
        MaskPreferences.save(context, normalized)
        OverlayController.sync(context)
        serviceDrawing = OverlayController.isOverlayActivelyDrawing(context)
    }

    fun openOverlayPermissionSettings() {
        pendingEnableAfterPermission = true
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    fun toggleMaskEnabled(requested: Boolean) {
        if (requested && !Settings.canDrawOverlays(context) && !PixelMaskAccessibilityService.isRunning) {
            openOverlayPermissionSettings()
            return
        }
        commitConfig(config.copy(enabled = requested))
    }

    fun checkUpdates() {
        if (checkingUpdate) return
        checkingUpdate = true
        updateStatusText = "Checking GitHub Releases..."
        GitHubUpdater.checkLatestRelease(context) { result ->
            checkingUpdate = false
            result.onSuccess { info ->
                updateInfo = info
                updateStatusText = if (info.isNewerAvailable) {
                    "Update ready: ${info.latestTag} (you have v${info.currentVersion})"
                } else {
                    "Up to date (v${info.currentVersion})"
                }
            }.onFailure { err ->
                updateStatusText = "Check failed: ${err.message ?: "network error"}"
            }
        }
    }

    fun startUpdateDownload(apkUrl: String) {
        downloadProgress = 0
        updateStatusText = "Downloading update..."
        GitHubUpdater.downloadAndInstallApk(
            context = context,
            apkUrl = apkUrl,
            onProgress = { pct ->
                downloadProgress = pct
                updateStatusText = "Downloading update... $pct%"
            },
            onError = { err ->
                downloadProgress = null
                updateStatusText = err
            }
        )
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowCanDraw = Settings.canDrawOverlays(context)
                val nowA11y = PixelMaskAccessibilityService.isRunning
                canDrawOverlays = nowCanDraw
                a11yRunning = nowA11y
                if (pendingEnableAfterPermission && (nowCanDraw || nowA11y)) {
                    pendingEnableAfterPermission = false
                    val next = MaskPreferences.load(context).copy(enabled = true)
                    MaskPreferences.save(context, next)
                    config = next
                } else {
                    config = MaskPreferences.load(context)
                }
                OverlayController.sync(context)
                serviceDrawing = OverlayController.isOverlayActivelyDrawing(context)
            }
        }
        activity.lifecycle.addObserver(observer)
        OverlayController.setStateListener {
            serviceDrawing = OverlayController.isOverlayActivelyDrawing(context)
            a11yRunning = PixelMaskAccessibilityService.isRunning
        }
        onDispose {
            activity.lifecycle.removeObserver(observer)
            OverlayController.setStateListener(null)
        }
    }

    LaunchedEffect(Unit) {
        OverlayController.sync(context)
        serviceDrawing = OverlayController.isOverlayActivelyDrawing(context)
        checkUpdates()
    }

    LaunchedEffect(nudgePulse) {
        if (nudgePulse > 0) {
            isNudgeActive = true
            delay(1100L)
            isNudgeActive = false
        }
    }

    val latestConfig by rememberUpdatedState(config)
    val latestPortraitW by rememberUpdatedState(portraitW)
    val latestPortraitH by rememberUpdatedState(portraitH)

    val loupeVisible = isDragging || isNudgeActive

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (whiteBackground) Color.White else Color.Black)
            .onSizeChanged { boxSize = it }
            .onGloballyPositioned {
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                rootScreenOffset = IntOffset(loc[0], loc[1])
            }
            .pointerInput(Unit) {
                var accumX = 0f
                var accumY = 0f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isDragging = true
                    fingerLocalPos = down.position
                    accumX = 0f
                    accumY = 0f

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                            ?: break

                        if (!change.pressed) {
                            break
                        }

                        val delta = change.position - change.previousPosition
                        change.consume()
                        fingerLocalPos = change.position

                        val cfg = latestConfig
                        val scale = if (cfg.fineMode) 0.25f else 1.0f
                        accumX += delta.x * scale
                        accumY += delta.y * scale

                        val stepX = accumX.toInt()
                        val stepY = accumY.toInt()
                        if (stepX != 0 || stepY != 0) {
                            accumX -= stepX
                            accumY -= stepY

                            val maxW = latestPortraitW
                            val maxH = latestPortraitH
                            val updated = when (cfg.mode) {
                                MaskMode.DOT -> cfg.copy(
                                    dotX = (cfg.dotX + stepX).coerceIn(0, maxW),
                                    dotY = (cfg.dotY + stepY).coerceIn(0, maxH)
                                )
                                MaskMode.LINE -> cfg.copy(
                                    lineY = (cfg.lineY + stepY).coerceIn(0, maxH)
                                )
                                MaskMode.BOTH -> cfg.copy(
                                    dotX = (cfg.dotX + stepX).coerceIn(0, maxW),
                                    dotY = (cfg.dotY + stepY).coerceIn(0, maxH),
                                    lineY = (cfg.lineY + stepY).coerceIn(0, maxH)
                                )
                            }
                            commitConfig(updated)
                        }
                    }

                    isDragging = false
                    fingerLocalPos = null
                }
            }
    ) {
        // Preview mask inside the app when the real system overlay is not already on top
        if (!serviceDrawing) {
            val layout = remember(config, portraitW, portraitH) {
                OverlayGeometry.computeOverlayLayout(
                    config = config,
                    currentPortraitW = portraitW,
                    currentPortraitH = portraitH,
                    rotation = OverlayGeometry.ROT_0
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                layout.lineRect?.let { rect ->
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(
                            (rect.x - rootScreenOffset.x).toFloat(),
                            (rect.y - rootScreenOffset.y).toFloat()
                        ),
                        size = Size(rect.width.toFloat(), rect.height.toFloat())
                    )
                }
                layout.dotRect?.let { rect ->
                    drawOval(
                        color = Color.Black,
                        topLeft = Offset(
                            (rect.x - rootScreenOffset.x).toFloat(),
                            (rect.y - rootScreenOffset.y).toFloat()
                        ),
                        size = Size(rect.width.toFloat(), rect.height.toFloat())
                    )
                }
            }
        }

        // 6x Zoom Loupe next to the active pixel / line while holding or nudging
        if (loupeVisible && boxSize.width > 0 && boxSize.height > 0) {
            val focusX = if (config.mode == MaskMode.LINE) {
                ((fingerLocalPos?.x ?: (boxSize.width * 0.5f)) + rootScreenOffset.x)
                    .coerceIn(0f, portraitW.toFloat())
            } else {
                config.dotX.toFloat()
            }
            val focusY = if (config.mode == MaskMode.LINE) {
                config.lineY.toFloat()
            } else {
                config.dotY.toFloat()
            }

            ZoomLoupeOverlay(
                config = config,
                portraitW = portraitW,
                portraitH = portraitH,
                focusScreenX = focusX,
                focusScreenY = focusY,
                rootScreenOffset = rootScreenOffset,
                containerSize = boxSize,
                fingerLocalPos = fingerLocalPos,
                controlsAtTop = controlsAtTop,
                whiteBackground = whiteBackground
            )
        }

        // Subtle helper hint at top/bottom when controls are visible
        if (!controlsHidden && !loupeVisible) {
            val hintAlign = if (controlsAtTop) Alignment.BottomCenter else Alignment.TopCenter
            Box(
                modifier = Modifier
                    .align(hintAlign)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xB8141416))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val hintText = if (config.mode == MaskMode.LINE) {
                    "Hold & drag anywhere to move line with 6× zoom"
                } else {
                    "Hold & drag anywhere to move dot with 6× zoom"
                }
                Text(
                    text = hintText,
                    color = Color(0xFFE5E7EB),
                    fontSize = 12.sp
                )
            }
        }

        // Floating Control Card or Collapsed Pill
        val cardAlignment = if (controlsAtTop) Alignment.TopCenter else Alignment.BottomCenter
        if (controlsHidden) {
            Box(
                modifier = Modifier
                    .align(cardAlignment)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xD9141416))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(999.dp))
                    .clickable { controlsHidden = false }
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "Show Controls",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            ControlCard(
                modifier = Modifier
                    .align(cardAlignment)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                config = config,
                canDrawOverlays = canDrawOverlays,
                a11yRunning = a11yRunning,
                controlsAtTop = controlsAtTop,
                hasUpdateAvailable = updateInfo?.isNewerAvailable == true,
                onToggleEnabled = ::toggleMaskEnabled,
                onSelectMode = { mode -> commitConfig(config.copy(mode = mode)) },
                onDotSizeChange = { size -> commitConfig(config.copy(dotSize = size)) },
                onLineHeightChange = { h -> commitConfig(config.copy(lineHeight = h)) },
                onNudge = { dx, dy ->
                    nudgePulse++
                    val next = when (config.mode) {
                        MaskMode.DOT -> config.copy(
                            dotX = config.dotX + dx,
                            dotY = config.dotY + dy
                        )
                        MaskMode.LINE -> config.copy(
                            lineY = config.lineY + dy
                        )
                        MaskMode.BOTH -> config.copy(
                            dotX = config.dotX + dx,
                            dotY = config.dotY + dy,
                            lineY = config.lineY + dy
                        )
                    }
                    commitConfig(next)
                },
                onToggleFineMode = { commitConfig(config.copy(fineMode = !config.fineMode)) },
                onFlipPosition = { controlsAtTop = !controlsAtTop },
                onHideControls = { controlsHidden = true },
                onOpenSettings = { showSettingsDialog = true }
            )
        }
    }

    if (showSettingsDialog) {
        SettingsUpdateDialog(
            whiteBackground = whiteBackground,
            a11yRunning = a11yRunning,
            canDrawOverlays = canDrawOverlays,
            installedVersion = installedVersion,
            updateInfo = updateInfo,
            checkingUpdate = checkingUpdate,
            updateStatusText = updateStatusText,
            downloadProgress = downloadProgress,
            onToggleWhiteBackground = { whiteBackground = it },
            onOpenAccessibilitySettings = {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                } catch (_: Exception) {
                }
            },
            onOpenOverlaySettings = { openOverlayPermissionSettings() },
            onResetPosition = {
                val defaultY = (portraitH * 0.35f).toInt().coerceIn(0, portraitH)
                commitConfig(
                    config.copy(
                        dotX = 18.coerceIn(0, portraitW),
                        dotY = defaultY,
                        lineY = defaultY
                    )
                )
            },
            onCheckUpdates = ::checkUpdates,
            onInstallUpdate = { url -> startUpdateDownload(url) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
private fun ZoomLoupeOverlay(
    config: MaskConfig,
    portraitW: Int,
    portraitH: Int,
    focusScreenX: Float,
    focusScreenY: Float,
    rootScreenOffset: IntOffset,
    containerSize: IntSize,
    fingerLocalPos: Offset?,
    controlsAtTop: Boolean,
    whiteBackground: Boolean
) {
    val density = LocalDensity.current
    val loupeDiameterDp = 152.dp
    val loupeDiameterPx = with(density) { loupeDiameterDp.toPx() }
    val loupeRadiusPx = loupeDiameterPx / 2f
    val marginPx = with(density) { 14.dp.toPx() }
    val sideGapPx = with(density) { 36.dp.toPx() }
    val pillExtraHeightPx = with(density) { 34.dp.toPx() }

    val focusLocalX = focusScreenX - rootScreenOffset.x
    val focusLocalY = focusScreenY - rootScreenOffset.y

    val placeRight = focusLocalX < containerSize.width * 0.5f
    val desiredCenterX = if (placeRight) {
        focusLocalX + loupeRadiusPx + sideGapPx
    } else {
        focusLocalX - loupeRadiusPx - sideGapPx
    }
    val minCenterX = marginPx + loupeRadiusPx
    val maxCenterX = (containerSize.width - marginPx - loupeRadiusPx).coerceAtLeast(minCenterX)
    val loupeCenterX = desiredCenterX.coerceIn(minCenterX, maxCenterX)

    val minTopY = if (controlsAtTop) containerSize.height * 0.30f else marginPx
    val maxTopY = if (controlsAtTop) {
        (containerSize.height - marginPx - loupeDiameterPx - pillExtraHeightPx)
    } else {
        (containerSize.height * 0.66f - loupeDiameterPx - pillExtraHeightPx)
    }.coerceAtLeast(minTopY)

    var desiredTopY = (focusLocalY - loupeDiameterPx - sideGapPx * 0.7f)
    if (desiredTopY < minTopY) {
        desiredTopY = focusLocalY + sideGapPx * 0.7f
    }
    if (fingerLocalPos != null) {
        val fingerDistY = kotlin.math.abs((desiredTopY + loupeRadiusPx) - fingerLocalPos.y)
        val fingerDistX = kotlin.math.abs(loupeCenterX - fingerLocalPos.x)
        if (fingerDistX < loupeRadiusPx + 40f && fingerDistY < loupeRadiusPx + 40f) {
            val altTopY = if (fingerLocalPos.y > containerSize.height * 0.45f) {
                minTopY
            } else {
                maxTopY
            }
            desiredTopY = altTopY
        }
    }
    val loupeTopY = desiredTopY.coerceIn(minTopY, maxTopY)
    val loupeLeftX = (loupeCenterX - loupeRadiusPx).roundToInt()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.offset { IntOffset(loupeLeftX, loupeTopY.roundToInt()) }
    ) {
        Box(
            modifier = Modifier
                .size(loupeDiameterDp)
                .clip(CircleShape)
                .border(2.5.dp, Color(0xFF1E1E22), CircleShape)
                .padding(1.dp)
                .border(1.dp, Color(0xCCFFFFFF), CircleShape)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val zoom = 6f
                val cx = size.width / 2f
                val cy = size.height / 2f

                // Outer bezel background
                drawRect(color = Color(0xFFD0D5DD))

                // Screen bounds mapped into zoomed canvas space
                val screenLeft = cx + (0f - focusScreenX) * zoom
                val screenTop = cy + (0f - focusScreenY) * zoom
                val screenRight = cx + (portraitW.toFloat() - focusScreenX) * zoom
                val screenBottom = cy + (portraitH.toFloat() - focusScreenY) * zoom

                drawRect(
                    color = if (whiteBackground) Color.White else Color(0xFF0A0A0C),
                    topLeft = Offset(screenLeft, screenTop),
                    size = Size(screenRight - screenLeft, screenBottom - screenTop)
                )

                // Draw pixel grid lines inside screen bounds
                val startPxX = floor(focusScreenX - cx / zoom).toInt().coerceAtLeast(0)
                val endPxX = ceil(focusScreenX + cx / zoom).toInt().coerceAtMost(portraitW)
                val startPxY = floor(focusScreenY - cy / zoom).toInt().coerceAtLeast(0)
                val endPxY = ceil(focusScreenY + cy / zoom).toInt().coerceAtMost(portraitH)

                val gridColorFine = if (whiteBackground) Color(0x16000000) else Color(0x1EFFFFFF)
                val gridColorMajor = if (whiteBackground) Color(0x2E000000) else Color(0x38FFFFFF)

                for (px in startPxX..endPxX) {
                    val xPos = cx + (px - focusScreenX) * zoom
                    val isMajor = px % 5 == 0
                    drawLine(
                        color = if (isMajor) gridColorMajor else gridColorFine,
                        start = Offset(xPos, maxOf(0f, screenTop)),
                        end = Offset(xPos, minOf(size.height, screenBottom)),
                        strokeWidth = if (isMajor) 1.2f else 0.7f
                    )
                }
                for (py in startPxY..endPxY) {
                    val yPos = cy + (py - focusScreenY) * zoom
                    val isMajor = py % 5 == 0
                    drawLine(
                        color = if (isMajor) gridColorMajor else gridColorFine,
                        start = Offset(maxOf(0f, screenLeft), yPos),
                        end = Offset(minOf(size.width, screenRight), yPos),
                        strokeWidth = if (isMajor) 1.2f else 0.7f
                    )
                }

                // Draw pure-black line mask
                if (config.mode == MaskMode.LINE || config.mode == MaskMode.BOTH) {
                    val halfH = config.lineHeight / 2f
                    val lineTop = cy + ((config.lineY - halfH) - focusScreenY) * zoom
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(screenLeft, lineTop),
                        size = Size(screenRight - screenLeft, config.lineHeight * zoom)
                    )
                }

                // Draw pure-black dot mask
                if (config.mode == MaskMode.DOT || config.mode == MaskMode.BOTH) {
                    val r = (config.dotSize / 2f) * zoom
                    val dotCenterX = cx + (config.dotX - focusScreenX) * zoom
                    val dotCenterY = cy + (config.dotY - focusScreenY) * zoom
                    drawCircle(
                        color = Color.Black,
                        radius = r,
                        center = Offset(dotCenterX, dotCenterY)
                    )
                }

                // Highlight physical screen edge in red so bezel alignment is unmistakable
                val edgeColor = Color(0xFFE53935)
                if (screenLeft in 0f..size.width) {
                    drawLine(edgeColor, Offset(screenLeft, 0f), Offset(screenLeft, size.height), 2.5f)
                }
                if (screenRight in 0f..size.width) {
                    drawLine(edgeColor, Offset(screenRight, 0f), Offset(screenRight, size.height), 2.5f)
                }
                if (screenTop in 0f..size.height) {
                    drawLine(edgeColor, Offset(0f, screenTop), Offset(size.width, screenTop), 2.5f)
                }
                if (screenBottom in 0f..size.height) {
                    drawLine(edgeColor, Offset(0f, screenBottom), Offset(size.width, screenBottom), 2.5f)
                }

                // Target center crosshair
                val crossColor = Color(0xCC00C853)
                val arm = 12f
                val gap = 3f
                drawLine(crossColor, Offset(cx - arm, cy), Offset(cx - gap, cy), 1.8f)
                drawLine(crossColor, Offset(cx + gap, cy), Offset(cx + arm, cy), 1.8f)
                drawLine(crossColor, Offset(cx, cy - arm), Offset(cx, cy - gap), 1.8f)
                drawLine(crossColor, Offset(cx, cy + gap), Offset(cx, cy + arm), 1.8f)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val readout = when (config.mode) {
            MaskMode.DOT -> "X ${config.dotX} • Y ${config.dotY} • ${config.dotSize}px"
            MaskMode.LINE -> "Y ${config.lineY} • H ${config.lineHeight}px"
            MaskMode.BOTH -> "X ${config.dotX} • Y ${config.dotY} • ${config.dotSize}/${config.lineHeight}px"
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xE6141416))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = readout,
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ControlCard(
    modifier: Modifier = Modifier,
    config: MaskConfig,
    canDrawOverlays: Boolean,
    a11yRunning: Boolean,
    controlsAtTop: Boolean,
    hasUpdateAvailable: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectMode: (MaskMode) -> Unit,
    onDotSizeChange: (Int) -> Unit,
    onLineHeightChange: (Int) -> Unit,
    onNudge: (Int, Int) -> Unit,
    onToggleFineMode: () -> Unit,
    onFlipPosition: () -> Unit,
    onHideControls: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val hasOverlayPerm = canDrawOverlays || a11yRunning

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xEB151518))
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(20.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {}
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Master switch, Mode selector, and Quick icon buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = config.enabled && hasOverlayPerm,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF22C55E),
                        uncheckedThumbColor = Color(0xFFD1D5DB),
                        uncheckedTrackColor = Color(0xFF374151)
                    )
                )
                Column {
                    Text(
                        text = if (config.enabled && hasOverlayPerm) "Mask ON" else "Mask OFF",
                        color = if (config.enabled && hasOverlayPerm) Color(0xFF4ADE80) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!hasOverlayPerm) {
                        Text(
                            text = "Tap to allow",
                            color = Color(0xFFFBBF24),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF232329))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                MaskMode.entries.forEach { mode ->
                    val selected = config.mode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color.White else Color.Transparent)
                            .clickable { onSelectMode(mode) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            color = if (selected) Color.Black else Color(0xFFD1D5DB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Row 2: Size Sliders (Dot Size and/or Line Height)
        if (config.mode == MaskMode.DOT || config.mode == MaskMode.BOTH) {
            SizeSliderRow(
                label = "Dot",
                value = config.dotSize,
                valueRange = MaskConfig.MIN_DOT_SIZE..MaskConfig.MAX_DOT_SIZE,
                onValueChange = onDotSizeChange
            )
        }
        if (config.mode == MaskMode.LINE || config.mode == MaskMode.BOTH) {
            SizeSliderRow(
                label = "Line",
                value = config.lineHeight,
                valueRange = MaskConfig.MIN_LINE_HEIGHT..MaskConfig.MAX_LINE_HEIGHT,
                onValueChange = onLineHeightChange
            )
        }

        // Row 3: 1px Nudge controls, Drag speed, Flip, Hide, Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val xAllowed = config.mode != MaskMode.LINE
                NudgeButton(label = "◀", enabled = xAllowed) { onNudge(-1, 0) }
                NudgeButton(label = "▲", enabled = true) { onNudge(0, -1) }
                NudgeButton(label = "▼", enabled = true) { onNudge(0, 1) }
                NudgeButton(label = "▶", enabled = xAllowed) { onNudge(1, 0) }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactPillButton(
                    text = if (config.fineMode) "0.25×" else "1×",
                    highlighted = config.fineMode,
                    onClick = onToggleFineMode
                )
                CompactPillButton(
                    text = if (controlsAtTop) "↓" else "↑",
                    highlighted = false,
                    onClick = onFlipPosition
                )
                CompactPillButton(
                    text = "Hide",
                    highlighted = false,
                    onClick = onHideControls
                )
                Box {
                    CompactPillButton(
                        text = "⚙",
                        highlighted = hasUpdateAvailable,
                        onClick = onOpenSettings
                    )
                    if (hasUpdateAvailable) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeSliderRow(
    label: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFD1D5DB),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(34.dp)
        )
        NudgeButton(label = "−", enabled = value > valueRange.first) {
            onValueChange((value - 1).coerceIn(valueRange.first, valueRange.last))
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(valueRange.first, valueRange.last)) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color(0xFF22C55E),
                inactiveTrackColor = Color(0xFF374151)
            )
        )
        NudgeButton(label = "+", enabled = value < valueRange.last) {
            onValueChange((value + 1).coerceIn(valueRange.first, valueRange.last))
        }
        Text(
            text = "${value}px",
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(42.dp)
        )
    }
}

@Composable
private fun NudgeButton(
    label: String,
    enabled: Boolean,
    onStep: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val latestStep by rememberUpdatedState(onStep)

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Color(0xFF27272E) else Color(0xFF1B1B20))
            .border(
                1.dp,
                if (enabled) Color(0x33FFFFFF) else Color(0x14FFFFFF),
                RoundedCornerShape(8.dp)
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    latestStep()
                    val repeatJob = scope.launch {
                        delay(340L)
                        while (true) {
                            latestStep()
                            delay(65L)
                        }
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            break
                        }
                    }
                    repeatJob.cancel()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color(0xFF52525B),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun CompactPillButton(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) Color(0x3322C55E) else Color(0xFF27272E))
            .border(
                1.dp,
                if (highlighted) Color(0xFF22C55E) else Color(0x33FFFFFF),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (highlighted) Color(0xFF86EFAC) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
    }
}

@Composable
private fun SettingsUpdateDialog(
    whiteBackground: Boolean,
    a11yRunning: Boolean,
    canDrawOverlays: Boolean,
    installedVersion: String,
    updateInfo: ReleaseUpdateInfo?,
    checkingUpdate: Boolean,
    updateStatusText: String,
    downloadProgress: Int?,
    onToggleWhiteBackground: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onResetPosition: () -> Unit,
    onCheckUpdates: () -> Unit,
    onInstallUpdate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF18181B),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD4D4D8),
        title = {
            Text(
                text = "Pixel Dimmer Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Calibration screen background
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("White Test Screen", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Switch off to test how dim the line looks on black.", fontSize = 12.sp, color = Color(0xFFA1A1AA))
                    }
                    Switch(
                        checked = whiteBackground,
                        onCheckedChange = onToggleWhiteBackground
                    )
                }

                // Overlay permission status
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Overlay Mode",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = when {
                            a11yRunning -> "Full-Cover Mode (Accessibility) active — covers all apps & status bar."
                            canDrawOverlays -> "Standard Mode active — covers all apps."
                            else -> "Permission needed: tap below to allow Appear on top."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFFA1A1AA)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenOverlaySettings,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Appear on Top", fontSize = 12.sp, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onOpenAccessibilitySettings,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Full-Cover (A11y)", fontSize = 12.sp, color = Color.White)
                        }
                    }
                    Text(
                        text = "Optional Full-Cover tip: on Android 13+, if Accessibility is greyed out, open App Info → ⋮ → Allow restricted settings.",
                        fontSize = 11.sp,
                        color = Color(0xFF71717A)
                    )
                }

                // Position reset
                OutlinedButton(
                    onClick = {
                        onResetPosition()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Dot & Line Position", color = Color.White, fontSize = 13.sp)
                }

                // GitHub Releases Auto-Updater
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF232329))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "GitHub Updates (v$installedVersion)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = updateStatusText,
                        fontSize = 12.sp,
                        color = if (updateInfo?.isNewerAvailable == true) Color(0xFF4ADE80) else Color(0xFFA1A1AA)
                    )
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { (downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF22C55E),
                            trackColor = Color(0xFF3F3F46)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            enabled = !checkingUpdate && downloadProgress == null,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(if (checkingUpdate) "Checking..." else "Check", fontSize = 12.sp, color = Color.White)
                        }
                        if (updateInfo?.isNewerAvailable == true && downloadProgress == null) {
                            Button(
                                onClick = { onInstallUpdate(updateInfo.apkDownloadUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Update ${updateInfo.latestTag}", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF4ADE80), fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
