package com.okulyonetim.optikokuyucu.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.okulyonetim.optikokuyucu.camera.CameraFrameAnalyzer
import com.okulyonetim.optikokuyucu.camera.CameraFrameStats
import com.okulyonetim.optikokuyucu.camera.LiveOmrReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.OmrSensitivity
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.geometry.ImagePoint
import com.okulyonetim.optikokuyucu.omr.geometry.ImageQuadrilateral
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.tracking.PageTrackingPhase
import com.okulyonetim.optikokuyucu.settings.CameraScanSettings
import com.okulyonetim.optikokuyucu.settings.CameraScanSettingsRepository
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun OmrCameraScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    template: OmrTemplate,
    onAcceptedRead: (LiveOmrReadResult) -> Unit = {},
    title: String = "Optik Tarama",
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenGallery: (() -> Unit)? = null,
    showRawReadCard: Boolean = true,
    onCameraSettingsChanged: (CameraScanSettings) -> Unit = {}
) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (cameraGranted) {
        CameraPreviewContent(
            openCvReady = openCvReady,
            selfTest = selfTest,
            template = template,
            onAcceptedRead = onAcceptedRead,
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            onOpenGallery = onOpenGallery,
            showRawReadCard = showRawReadCard,
            onCameraSettingsChanged = onCameraSettingsChanged
        )
    } else {
        CameraPermissionContent(
            title = title,
            onBack = onBack,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
    }
}

@Composable
private fun CameraPermissionContent(
    title: String,
    onBack: (() -> Unit)?,
    onRequestPermission: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(
            title = title,
            leadingText = if (onBack != null) "‹" else null,
            onLeadingClick = onBack
        )
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            ProductCompactCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProductInitialBadge("▣")
                    Text("Kamera izni gerekli", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Optik formları canlı okuyabilmek için kameraya erişim vermelisiniz.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Button(onClick = onRequestPermission, shape = RoundedCornerShape(12.dp)) {
                        Text("Kamera İzni Ver", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CameraPreviewContent(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    template: OmrTemplate,
    onAcceptedRead: (LiveOmrReadResult) -> Unit,
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    onOpenGallery: (() -> Unit)?,
    showRawReadCard: Boolean,
    onCameraSettingsChanged: (CameraScanSettings) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) {
        requireNotNull(context.findActivity() as? LifecycleOwner) {
            "Kamera için LifecycleOwner bulunamadı."
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 78) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val settingsRepository = remember(context) {
        CameraScanSettingsRepository(context.applicationContext)
    }
    val currentOnAcceptedRead by rememberUpdatedState(onAcceptedRead)
    val currentOnCameraSettingsChanged by rememberUpdatedState(onCameraSettingsChanged)

    var cameraSettings by remember { mutableStateOf(settingsRepository.load()) }
    var showOptions by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(CameraFrameStats.Empty) }
    var liveRead by remember { mutableStateOf<LiveOmrReadResult?>(null) }
    var cameraMessage by remember { mutableStateOf("Kamera hazırlanıyor…") }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }

    fun saveCameraSettings(next: CameraScanSettings) {
        settingsRepository.save(next)
        cameraSettings = next
        currentOnCameraSettingsChanged(next)
    }

    LaunchedEffect(Unit) {
        currentOnCameraSettingsChanged(cameraSettings)
    }

    LaunchedEffect(liveRead?.sequence) {
        val sequence = liveRead?.sequence ?: return@LaunchedEffect
        delay(3_000)
        if (liveRead?.sequence == sequence) liveRead = null
    }

    val analyzer = remember(openCvReady, template, cameraSettings.sensitivity) {
        CameraFrameAnalyzer(
            openCvReady = openCvReady,
            onStats = { newStats ->
                mainExecutor.execute {
                    stats = newStats
                    cameraMessage = when {
                        liveRead != null && !newStats.readArmed -> "Okuma tamamlandı"
                        newStats.trackingPhase == PageTrackingPhase.LOCKED -> "Form algılandı · okunuyor"
                        newStats.trackingPhase == PageTrackingPhase.TRACKING -> "Form algılandı · sabit tutun"
                        else -> "Dört köşe markerını gösterin"
                    }
                }
            },
            onLiveRead = { result ->
                currentOnAcceptedRead(result)
                mainExecutor.execute {
                    liveRead = result
                    cameraMessage = "Form başarıyla okundu"
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                }
            },
            template = template,
            sensitivity = cameraSettings.sensitivity
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            toneGenerator.release()
        }
    }

    DisposableEffect(lifecycleOwner, previewView, analyzer) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val resolutionSelector = ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(960, 540),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { useCase -> useCase.setAnalyzer(analysisExecutor, analyzer) }

                cameraProvider.unbindAll()
                boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                cameraMessage = "Dört köşe markerını gösterin"
            } catch (error: Exception) {
                cameraMessage = "Kamera başlatılamadı: ${error.message ?: error.javaClass.simpleName}"
            }
        }
        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            torchEnabled = false
            boundCamera = null
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    val scanStateText = when {
        liveRead != null && !stats.readArmed -> "Okundu · yeni formu gösterin"
        stats.trackingPhase == PageTrackingPhase.LOCKED -> "Form kilitlendi · otomatik okunuyor"
        stats.trackingPhase == PageTrackingPhase.TRACKING -> "Form algılandı · sabit tutun"
        else -> "Dört köşe markerını kamera içinde tutun"
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        OmrGuideOverlay(
            modifier = Modifier.fillMaxSize(),
            templateAspect = template.space.aspectRatio.toFloat(),
            detectedPage = stats.pageQuadrilateral,
            sourceWidth = stats.width,
            sourceHeight = stats.height,
            rotationDegrees = stats.rotationDegrees,
            phase = stats.trackingPhase,
            confidence = stats.pageConfidence
        )

        CameraProductHeader(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            title = title,
            subtitle = subtitle ?: "${template.id} · v${template.version}",
            stats = stats,
            openCvReady = openCvReady,
            selfTest = selfTest,
            torchEnabled = torchEnabled,
            torchAvailable = boundCamera?.cameraInfo?.hasFlashUnit() == true,
            onBack = onBack,
            onOpenGallery = onOpenGallery,
            onOpenOptions = { showOptions = true },
            onToggleTorch = {
                val camera = boundCamera ?: return@CameraProductHeader
                if (!camera.cameraInfo.hasFlashUnit()) return@CameraProductHeader
                val next = !torchEnabled
                camera.cameraControl.enableTorch(next)
                torchEnabled = next
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (showRawReadCard) {
                liveRead?.let { result -> LiveReadResultCard(result = result) }
            }
            CameraStatusPanel(
                message = cameraMessage,
                stateText = scanStateText,
                stats = stats,
                openCvReady = openCvReady
            )
        }
    }

    if (showOptions) {
        ModalBottomSheet(onDismissRequest = { showOptions = false }) {
            CameraOptionsSheet(
                settings = cameraSettings,
                onSensitivityChanged = { sensitivity ->
                    saveCameraSettings(cameraSettings.copy(sensitivity = sensitivity))
                },
                onShowStudentSummaryChanged = { enabled ->
                    saveCameraSettings(cameraSettings.copy(showStudentSummary = enabled))
                }
            )
        }
    }
}

@Composable
private fun CameraOptionsSheet(
    settings: CameraScanSettings,
    onSensitivityChanged: (OmrSensitivity) -> Unit,
    onShowStudentSummaryChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Kamera Seçenekleri", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Hassasiyet", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Normal standart okumadır. Yüksek soluk işaretlerde, Düşük çok koyu baskılarda kullanılabilir.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SensitivityButton(
                modifier = Modifier.weight(1f),
                text = "Düşük",
                selected = settings.sensitivity == OmrSensitivity.LOW,
                onClick = { onSensitivityChanged(OmrSensitivity.LOW) }
            )
            SensitivityButton(
                modifier = Modifier.weight(1f),
                text = "Normal",
                selected = settings.sensitivity == OmrSensitivity.NORMAL,
                onClick = { onSensitivityChanged(OmrSensitivity.NORMAL) }
            )
            SensitivityButton(
                modifier = Modifier.weight(1f),
                text = "Yüksek",
                selected = settings.sensitivity == OmrSensitivity.HIGH,
                onClick = { onSensitivityChanged(OmrSensitivity.HIGH) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = settings.showStudentSummary,
                onCheckedChange = onShowStudentSummaryChanged
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Öğrenci bilgisini göster", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Okuma sonrası öğrenci ve sonuç özetini kısa süre gösterir.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SensitivityButton(
    modifier: Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(12.dp)) {
            Text(text, fontSize = 11.sp)
        }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick, shape = RoundedCornerShape(12.dp)) {
            Text(text, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CameraProductHeader(
    modifier: Modifier,
    title: String,
    subtitle: String,
    stats: CameraFrameStats,
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onBack: (() -> Unit)?,
    onOpenGallery: (() -> Unit)?,
    onOpenOptions: () -> Unit,
    onToggleTorch: () -> Unit
) {
    val stateText = when {
        !openCvReady -> "Okuma motoru hazır değil"
        !selfTest.passed -> "Okuma motoru kontrol gerekli"
        stats.markerCount > 0 -> "${stats.markerCount}/4 marker · %${(stats.pageConfidence * 100).toInt()} güven"
        else -> "Otomatik okuma"
    }

    Surface(
        modifier = modifier,
        color = Color(0xFF151623).copy(alpha = 0.90f),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.82f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    TextButton(onClick = onBack) {
                        Text("‹", color = Color.White, fontSize = 20.sp)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onOpenGallery != null) {
                    OutlinedButton(
                        onClick = onOpenGallery,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
                    ) {
                        Text("Galeri", fontSize = 10.sp)
                    }
                }
                TextButton(onClick = onOpenOptions) {
                    Text("⋮", color = Color.White, fontSize = 20.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stateText,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.78f)
                )
                OutlinedButton(
                    onClick = onToggleTorch,
                    enabled = torchAvailable,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
                ) {
                    Text(if (torchEnabled) "☀ Açık" else "☀ Flaş", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun CameraStatusPanel(
    message: String,
    stateText: String,
    stats: CameraFrameStats,
    openCvReady: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF151623).copy(alpha = 0.90f),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.58f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(message, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stateText,
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text("${stats.markerCount}/4", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(if (openCvReady) "OMR ✓" else "OMR —", fontSize = 9.sp)
                Text("Okunan ${stats.liveReadCount}", fontSize = 9.sp)
                Text("Güven %${(stats.pageConfidence * 100).toInt()}", fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun LiveReadResultCard(result: LiveOmrReadResult) {
    val bubbles = result.bubbleResult
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF151623).copy(alpha = 0.92f),
        contentColor = Color.White,
        border = BorderStroke(2.dp, Color(0xFF69D49F).copy(alpha = 0.78f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓ Form Okundu", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(String.format(Locale.US, "%.0f%%", result.decisionConfidence * 100.0), fontSize = 10.sp)
            }
            val student = result.markGridResult.grids.firstOrNull { it.gridId == "studentNumber" }?.value
            val booklet = result.markGridResult.grids.firstOrNull { it.gridId == "booklet" }?.value
            Text(
                "No ${student ?: "—"} · Kitapçık ${booklet ?: "—"} · İşaretli ${bubbles.markedCount} · Boş ${bubbles.blankCount}",
                fontSize = 9.sp,
                color = Color.White.copy(alpha = 0.76f)
            )
            if (bubbles.doubleMarkCount > 0 || bubbles.suspiciousCount > 0) {
                Text(
                    "Çift ${bubbles.doubleMarkCount} · Şüpheli ${bubbles.suspiciousCount}",
                    fontSize = 9.sp,
                    color = Color(0xFFFFC75A)
                )
            }
        }
    }
}

@Composable
private fun OmrGuideOverlay(
    modifier: Modifier = Modifier,
    templateAspect: Float = StandardOmrTemplate.DEFAULT.space.aspectRatio.toFloat(),
    detectedPage: ImageQuadrilateral? = null,
    sourceWidth: Int = 0,
    sourceHeight: Int = 0,
    rotationDegrees: Int = 0,
    phase: PageTrackingPhase = PageTrackingPhase.SEARCHING,
    confidence: Double = 0.0
) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val accent = when (phase) {
            PageTrackingPhase.LOCKED -> Color(0xFF4ADE80)
            PageTrackingPhase.TRACKING -> Color(0xFFFBBF24)
            else -> primary.copy(alpha = if (confidence > 0.0) 0.95f else 0.70f)
        }

        if (detectedPage != null && sourceWidth > 0 && sourceHeight > 0) {
            val rotation = ((rotationDegrees % 360) + 360) % 360
            val rotatedWidth = if (rotation == 90 || rotation == 270) sourceHeight.toFloat() else sourceWidth.toFloat()
            val rotatedHeight = if (rotation == 90 || rotation == 270) sourceWidth.toFloat() else sourceHeight.toFloat()
            val scale = max(size.width / rotatedWidth, size.height / rotatedHeight)
            val offsetX = (size.width - rotatedWidth * scale) / 2f
            val offsetY = (size.height - rotatedHeight * scale) / 2f

            fun mapPoint(point: ImagePoint): Offset {
                val x: Float
                val y: Float
                when (rotation) {
                    90 -> {
                        x = (sourceHeight - point.y).toFloat()
                        y = point.x.toFloat()
                    }
                    180 -> {
                        x = (sourceWidth - point.x).toFloat()
                        y = (sourceHeight - point.y).toFloat()
                    }
                    270 -> {
                        x = point.y.toFloat()
                        y = (sourceWidth - point.x).toFloat()
                    }
                    else -> {
                        x = point.x.toFloat()
                        y = point.y.toFloat()
                    }
                }
                return Offset(offsetX + x * scale, offsetY + y * scale)
            }

            val points = listOf(
                mapPoint(detectedPage.topLeft),
                mapPoint(detectedPage.topRight),
                mapPoint(detectedPage.bottomRight),
                mapPoint(detectedPage.bottomLeft)
            )
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                lineTo(points[1].x, points[1].y)
                lineTo(points[2].x, points[2].y)
                lineTo(points[3].x, points[3].y)
                close()
            }
            drawPath(path, color = Color.Black.copy(alpha = 0.24f), style = Stroke(width = 9f))
            drawPath(path, color = accent, style = Stroke(width = 5f))
            points.forEach { point ->
                drawCircle(color = Color.Black.copy(alpha = 0.45f), radius = 10f, center = point)
                drawCircle(color = accent, radius = 6f, center = point)
            }
        } else {
            val maxWidth = size.width * 0.84f
            val maxHeight = size.height * 0.66f
            val width: Float
            val height: Float
            if (maxWidth / maxHeight > templateAspect) {
                height = maxHeight
                width = height * templateAspect
            } else {
                width = maxWidth
                height = width / templateAspect
            }
            val left = (size.width - width) / 2f
            val top = (size.height - height) / 2f

            drawRoundRect(
                color = Color.White.copy(alpha = 0.34f),
                topLeft = Offset(left, top),
                size = ComposeSize(width, height),
                cornerRadius = CornerRadius(20f, 20f),
                style = Stroke(width = 2f)
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
