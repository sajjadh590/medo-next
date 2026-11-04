package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.qr.MedoQrAndLanEngine
import com.example.ui.components.GlassCard
import com.example.ui.components.MedoPill
import com.example.ui.components.MedoPrimaryButton
import com.example.ui.components.MedoSecondaryButton
import com.example.ui.theme.LocalMedoTheme
import com.example.util.DiagnosticsLogger
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * Full-Screen CameraX + ZXing QR Scanner Screen (Section A.1, A.2, A.3):
 * - CameraX Preview + ImageAnalysis (STRATEGY_KEEP_ONLY_LATEST, ~1280x720, ~10 fps throttle)
 * - Torch toggle, continuous autofocus + tap-to-focus + pinch zoom
 * - Visible square viewfinder frame
 * - 10-second no-detection hint («گوشی را کمی نزدیک‌تر یا دورتر ببرید و نور صفحه رایانه را بیشتر کنید»)
 * - On detection: vibrates, freezes frame, validates private LAN URL, displays decoded address + «اتصال» button
 * - Permission rationale + denied state + button to open system app settings
 * - Fallbacks: Manual IP:Port + PIN input, Pick QR image from gallery (Android Photo Picker), Paste from clipboard
 */
@Composable
fun QrScannerModalScreen(
    forcePermissionDeniedForTest: Boolean = false,
    onConnectValidatedUrl: (String) -> Unit,
    onClose: () -> Unit
) {
    val tokens = LocalMedoTheme.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var hasCameraPermission by remember(forcePermissionDeniedForTest) {
        mutableStateOf(
            if (forcePermissionDeniedForTest) {
                false
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
            }
        )
    }
    var permissionDeniedPermanently by remember { mutableStateOf(forcePermissionDeniedForTest) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionDeniedPermanently = !granted
        DiagnosticsLogger.logEvent("QR_PERM", "نتیجه مجوز دوربین: granted=$granted")
    }

    var frozenDetectedRaw by remember { mutableStateOf<String?>(null) }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    var validatedUrlReady by remember { mutableStateOf<String?>(null) }
    var showTenSecondHint by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var manualAddressInput by remember { mutableStateOf("192.168.1.100:8765") }
    var manualPinInput by remember { mutableStateOf("") }
    var torchEnabled by remember { mutableStateOf(false) }

    // Zero-permission Android Photo Picker for decoding a QR image from the gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val decoded = MedoQrAndLanEngine.decodeFromBitmap(bitmap)
                        if (decoded != null) {
                            handleCandidateQrPayload(
                                context = context,
                                rawPayload = decoded,
                                optionalPin = manualPinInput,
                                onFrozen = { raw, validUrl, errMsg ->
                                    frozenDetectedRaw = raw
                                    validatedUrlReady = validUrl
                                    validationMessage = errMsg
                                }
                            )
                        } else {
                            validationMessage = "کد QR در تصویر انتخاب‌شده تشخیص داده نشد. تصویر واضح‌تری انتخاب کنید."
                        }
                    }
                }
            } catch (e: Exception) {
                validationMessage = "خطا در خواندن تصویر از گالری: ${e.localizedMessage}"
            }
        }
    }

    LaunchedEffect(Unit) {
        MedoQrAndLanEngine.resetScanSessionStats()
    }

    // 10-second hint timer when no QR has been detected yet
    LaunchedEffect(hasCameraPermission, frozenDetectedRaw) {
        if (hasCameraPermission && frozenDetectedRaw == null) {
            showTenSecondHint = false
            delay(10_000L)
            if (frozenDetectedRaw == null) {
                showTenSecondHint = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.gradientStart)
            .padding(16.dp)
            .testTag("qr_scanner_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Header Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("close_qr_scanner_btn")) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = tokens.textPrimary
                    )
                }
                Text(
                    text = "اسکن کد QR و دریافت بسته‌ها",
                    style = MaterialTheme.typography.titleLarge,
                    color = tokens.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            if (hasCameraPermission) {
                IconButton(
                    onClick = { torchEnabled = !torchEnabled },
                    modifier = Modifier.testTag("qr_torch_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "چراغ قوه (فلش)",
                        tint = if (torchEnabled) tokens.warning else tokens.textPrimary
                    )
                }
            }
        }

        // Camera Viewfinder OR Permission Rationale / Denied State
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, tokens.accent.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission && frozenDetectedRaw == null) {
                CameraXQrPreview(
                    torchEnabled = torchEnabled,
                    onQrDetected = { rawText ->
                        if (frozenDetectedRaw == null) {
                            handleCandidateQrPayload(
                                context = context,
                                rawPayload = rawText,
                                optionalPin = manualPinInput,
                                onFrozen = { raw, validUrl, errMsg ->
                                    frozenDetectedRaw = raw
                                    validatedUrlReady = validUrl
                                    validationMessage = errMsg
                                }
                            )
                        }
                    }
                )

                // Visible square viewfinder frame overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val boxSide = (size.minDimension * 0.68f).coerceAtMost(640f)
                    val left = (size.width - boxSide) / 2f
                    val top = (size.height - boxSide) / 2f
                    drawRoundRect(
                        color = tokens.accent,
                        topLeft = Offset(left, top),
                        size = ComposeSize(boxSide, boxSide),
                        cornerRadius = CornerRadius(28f, 28f),
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

                if (showTenSecondHint) {
                    Surface(
                        color = tokens.readingSurfaceTint.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .testTag("qr_ten_second_hint")
                    ) {
                        Text(
                            text = "💡 راهنما: گوشی را کمی نزدیک‌تر یا دورتر ببرید و روشنایی صفحه رایانه را افزایش دهید، یا از دکمه «وارد کردن آدرس دستی» استفاده کنید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.warning,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            } else if (!hasCameraPermission) {
                // Permission Rationale + Denied State + Open App Settings Button
                GlassCard(
                    isReadingSurface = true,
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .testTag("qr_permission_rationale_card")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = tokens.accent,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "مجوز دوربین برای اسکن کد QR شبکه محلی",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "برنامه فقط برای خواندن کد QR نمایش‌داده‌شده در صفحه رایانه شما از دوربین استفاده می‌کند. هیچ تصویری ذخیره یا ارسال نمی‌شود.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.textSecondary
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MedoPrimaryButton(
                                text = "اعطای مجوز دوربین",
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("request_camera_permission_btn")
                            )
                            if (permissionDeniedPermanently) {
                                MedoSecondaryButton(
                                    text = "تنظیمات برنامه",
                                    onClick = {
                                        try {
                                            val intent = Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.fromParts("package", context.packageName, null)
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("open_app_settings_permission_btn")
                                )
                            }
                        }
                    }
                }
            } else {
                // Frozen Frame State on Detection: shows decoded address and clear «اتصال» button
                GlassCard(
                    isReadingSurface = true,
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .testTag("qr_detected_frozen_card")
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "✓ کد QR دریافت و تصویر ثابت شد",
                            style = MaterialTheme.typography.titleLarge,
                            color = tokens.correct,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "آدرس استخراج‌شده: ${frozenDetectedRaw.orEmpty()}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = tokens.textPrimary
                        )
                        if (validationMessage != null) {
                            Text(
                                text = validationMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (validatedUrlReady != null) tokens.correct else tokens.wrong
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (validatedUrlReady != null) {
                                MedoPrimaryButton(
                                    text = "اتصال",
                                    onClick = { onConnectValidatedUrl(validatedUrlReady!!) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("qr_connect_btn")
                                )
                            }
                            MedoSecondaryButton(
                                text = "اسکن مجدد",
                                onClick = {
                                    frozenDetectedRaw = null
                                    validatedUrlReady = null
                                    validationMessage = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("qr_rescan_btn")
                            )
                        }
                    }
                }
            }
        }

        // Fallback Actions Row: Manual Address Entry, Gallery QR Image Picker, Clipboard Paste
        GlassCard(
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MedoPill(
                        text = "وارد کردن آدرس دستی",
                        onClick = { showManualEntry = !showManualEntry },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fallback_manual_address_btn")
                    )
                    MedoPill(
                        text = "انتخاب عکس QR از گالری",
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("fallback_gallery_qr_btn")
                    )
                    MedoPill(
                        text = "چسباندن از کلیپ‌بورد",
                        onClick = {
                            val clipText = clipboard.getText()?.text.orEmpty()
                            handleCandidateQrPayload(
                                context = context,
                                rawPayload = clipText,
                                optionalPin = manualPinInput,
                                onFrozen = { raw, validUrl, errMsg ->
                                    frozenDetectedRaw = raw.ifBlank { "کلیپ‌بورد خالی" }
                                    validatedUrlReady = validUrl
                                    validationMessage = errMsg
                                }
                            )
                        },
                        modifier = Modifier.testTag("fallback_paste_clipboard_btn")
                    )
                }

                if (showManualEntry) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = manualAddressInput,
                            onValueChange = { manualAddressInput = it },
                            label = { Text("IP:Port (مثلاً 192.168.1.100:8765)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.68f)
                                .testTag("manual_ip_port_input")
                        )
                        OutlinedTextField(
                            value = manualPinInput,
                            onValueChange = { manualPinInput = it },
                            label = { Text("PIN (اختیاری)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.32f)
                                .testTag("manual_pin_input")
                        )
                    }
                    MedoPrimaryButton(
                        text = "بررسی آدرس و اتصال به سرور دسکتاپ",
                        onClick = {
                            handleCandidateQrPayload(
                                context = context,
                                rawPayload = manualAddressInput,
                                optionalPin = manualPinInput,
                                onFrozen = { raw, validUrl, errMsg ->
                                    frozenDetectedRaw = raw
                                    validatedUrlReady = validUrl
                                    validationMessage = errMsg
                                    if (validUrl != null) {
                                        onConnectValidatedUrl(validUrl)
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_connect_submit_btn")
                    )
                }

                if (validationMessage != null && frozenDetectedRaw == null) {
                    Text(
                        text = validationMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.wrong
                    )
                }
            }
        }
    }
}

private fun handleCandidateQrPayload(
    context: Context,
    rawPayload: String,
    optionalPin: String?,
    onFrozen: (raw: String, validatedUrl: String?, message: String) -> Unit
) {
    vibrateOnDetection(context)
    when (val res = MedoQrAndLanEngine.validateLanTransferUrl(rawPayload, optionalPin)) {
        is MedoQrAndLanEngine.LanValidationResult.Valid -> {
            MedoQrAndLanEngine.bindProcessToWifiIfAvailable(context)
            onFrozen(
                rawPayload,
                res.normalizedUrl,
                "آدرس شبکه محلی معتبر است (${res.host}:${res.port}). آماده دریافت بسته‌ها."
            )
        }
        is MedoQrAndLanEngine.LanValidationResult.Invalid -> {
            onFrozen(rawPayload, null, res.errorFa)
        }
    }
}

private fun vibrateOnDetection(context: Context) {
    try {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(70L)
        }
    } catch (_: Exception) {
    }
}

@Composable
private fun CameraXQrPreview(
    torchEnabled: Boolean,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var lastAnalyzedTimestampMs by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(activeCamera, torchEnabled) {
        try {
            activeCamera?.cameraControl?.enableTorch(torchEnabled)
        } catch (_: Exception) {
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    @Suppress("DEPRECATION")
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val now = System.currentTimeMillis()
                        // Throttle to ~10 frames per second (100ms interval) so UI never lags
                        if (now - lastAnalyzedTimestampMs >= 95L) {
                            lastAnalyzedTimestampMs = now
                            try {
                                val yPlane = imageProxy.planes.firstOrNull()
                                if (yPlane != null) {
                                    val yBytes = MedoQrAndLanEngine.extractContiguousYPlane(
                                        yBuffer = yPlane.buffer,
                                        width = imageProxy.width,
                                        height = imageProxy.height,
                                        rowStride = yPlane.rowStride,
                                        pixelStride = yPlane.pixelStride
                                    )
                                    val decoded = MedoQrAndLanEngine.decodeYuvFrame(
                                        yBytesWithStride = yBytes,
                                        width = imageProxy.width,
                                        height = imageProxy.height,
                                        rowStride = imageProxy.width,
                                        pixelStride = 1,
                                        rotationDegrees = imageProxy.imageInfo.rotationDegrees
                                    )
                                    if (decoded != null) {
                                        previewView.post { onQrDetected(decoded) }
                                    }
                                }
                            } catch (_: Exception) {
                            }
                        }
                        imageProxy.close()
                    }

                    cameraProvider.unbindAll()
                    activeCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    DiagnosticsLogger.logEvent("CAM_BIND_ERR", "خطای اتصال دوربین: ${e.javaClass.simpleName}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    // Tap-to-focus
                    try {
                        val cam = activeCamera ?: return@detectTapGestures
                        val factory = androidx.camera.core.SurfaceOrientedMeteringPointFactory(
                            size.width.toFloat(),
                            size.height.toFloat()
                        )
                        val point = factory.createPoint(tapOffset.x, tapOffset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        cam.cameraControl.startFocusAndMetering(action)
                    } catch (_: Exception) {
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    // Pinch zoom
                    try {
                        val cam = activeCamera ?: return@detectTransformGestures
                        val maxZoom = cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 4f
                        currentZoomRatio = (currentZoomRatio * zoom).coerceIn(1f, maxZoom)
                        cam.cameraControl.setZoomRatio(currentZoomRatio)
                    } catch (_: Exception) {
                    }
                }
            }
    )
}
