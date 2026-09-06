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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.okulyonetim.optikokuyucu.camera.CameraFrameAnalyzer
import com.okulyonetim.optikokuyucu.camera.CameraFrameStats
import com.okulyonetim.optikokuyucu.camera.LiveOmrReadResult
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.diagnostics.OmrSelfTestResult
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import com.okulyonetim.optikokuyucu.omr.tracking.PageTrackingPhase
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun OmrCameraScreen(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    template: OmrTemplate,
    onAcceptedRead: (LiveOmrReadResult) -> Unit = {}
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
            onAcceptedRead = onAcceptedRead
        )
    } else {
        CameraPermissionContent(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
    }
}

@Composable
private fun CameraPermissionContent(onRequestPermission: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ProductTopBar(title = "Kamera")
        Box(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("▣", fontSize = 42.sp, color = MaterialTheme.colorScheme.primary)
                    Text("Kamera izni gerekli", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Optik formları canlı okuyabilmek için kameraya erişim vermelisiniz.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onRequestPermission, shape = RoundedCornerShape(18.dp)) {
                        Text("Kamera İzni Ver")
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    template: OmrTemplate,
    onAcceptedRead: (LiveOmrReadResult) -> Unit
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
    val currentOnAcceptedRead by rememberUpdatedState(onAcceptedRead)

    var stats by remember { mutableStateOf(CameraFrameStats.Empty) }
    var liveRead by remember { mutableStateOf<LiveOmrReadResult?>(null) }
    var cameraMessage by remember { mutableStateOf("Kamera hazırlanıyor…") }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var torchEnabled by remember { mutableStateOf(false) }

    val analyzer = remember(openCvReady, template) {
        CameraFrameAnalyzer(
            openCvReady = openCvReady,
            onStats = { newStats ->
                mainExecutor.execute {
                    stats = newStats
                    cameraMessage = when {
                        liveRead != null && !newStats.readArmed -> "Okuma tamamlandı"
                        newStats.trackingPhase == PageTrackingPhase.LOCKED -> "Form algılandı · okunuyor"
                        newStats.trackingPhase == PageTrackingPhase.TRACKING -> "Form algılandı · sabit tutun"
                        else -> "Formu çerçeve içine alın"
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
            template = template
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
                cameraMessage = "Formu çerçeve içine alın"
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
        else -> "Dört köşe işaretini çerçeve içine alın"
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

        OmrGuideOverlay(
            modifier = Modifier.fillMaxSize(),
            templateAspect = template.space.aspectRatio.toFloat(),
            phase = stats.trackingPhase,
            confidence = stats.pageConfidence
        )

        CameraProductHeader(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            template = template,
            stats = stats,
            openCvReady = openCvReady,
            selfTest = selfTest,
            torchEnabled = torchEnabled,
            torchAvailable = boundCamera?.cameraInfo?.hasFlashUnit() == true,
            onToggleTorch = {
                val camera = boundCamera ?: return@CameraProductHeader
                if (!camera.cameraInfo.hasFlashUnit()) return@CameraProductHeader
                val next = !torchEnabled
                camera.cameraControl.enableTorch(next)
                torchEnabled = next
            }
        )

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            color = Color.Black.copy(alpha = 0.62f),
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                text = cameraMessage,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            liveRead?.let { result -> LiveReadResultCard(result = result) }
            CameraStatusPanel(
                stateText = scanStateText,
                stats = stats,
                openCvReady = openCvReady
            )
        }
    }
}

@Composable
private fun CameraProductHeader(
    modifier: Modifier,
    template: OmrTemplate,
    stats: CameraFrameStats,
    openCvReady: Boolean,
    selfTest: OmrSelfTestResult,
    torchEnabled: Boolean,
    torchAvailable: Boolean,
    onToggleTorch: () -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Optik Tarama", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${template.id} · v${template.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                )
                Text(
                    when {
                        !openCvReady -> "Okuma motoru hazır değil"
                        !selfTest.passed -> "Okuma motoru kontrol gerekli"
                        stats.markerCount > 0 -> "${stats.markerCount}/4 köşe · güven %${(stats.pageConfidence * 100).toInt()}"
                        else -> "Otomatik okuma açık"
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }
            OutlinedButton(
                onClick = onToggleTorch,
                enabled = torchAvailable,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (torchEnabled) "☀ Açık" else "☀ Flaş", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun CameraStatusPanel(
    stateText: String,
    stats: CameraFrameStats,
    openCvReady: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.78f),
            contentColor = Color.White
        )
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stateText, fontWeight = FontWeight.SemiBold)
                Text("${stats.markerCount}/4", fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (openCvReady) "OMR hazır ✓" else "OMR hazır değil", style = MaterialTheme.typography.bodySmall)
                Text("Okunan ${stats.liveReadCount}", style = MaterialTheme.typography.bodySmall)
                Text("Güven %${(stats.pageConfidence * 100).toInt()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LiveReadResultCard(result: LiveOmrReadResult) {
    val bubbles = result.bubbleResult
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓ Form Okundu", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(String.format(Locale.US, "%.0f%%", result.decisionConfidence * 100.0))
            }
            val student = result.markGridResult.grids.firstOrNull { it.gridId == "studentNumber" }?.value
            val booklet = result.markGridResult.grids.firstOrNull { it.gridId == "booklet" }?.value
            Text(
                "No: ${student ?: "—"} · Kitapçık: ${booklet ?: "—"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "İşaretli ${bubbles.markedCount} · Boş ${bubbles.blankCount} · Çift ${bubbles.doubleMarkCount} · Şüpheli ${bubbles.suspiciousCount}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun OmrGuideOverlay(
    modifier: Modifier = Modifier,
    templateAspect: Float = StandardOmrTemplate.DEFAULT.space.aspectRatio.toFloat(),
    phase: PageTrackingPhase = PageTrackingPhase.SEARCHING,
    confidence: Double = 0.0
) {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
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
        val accent = when (phase) {
            PageTrackingPhase.LOCKED -> Color(0xFF55E6A5)
            PageTrackingPhase.TRACKING -> Color(0xFFFFC45B)
            else -> primary.copy(alpha = if (confidence > 0.0) 0.95f else 0.78f)
        }

        drawRoundRect(
            color = Color.White.copy(alpha = 0.62f),
            topLeft = Offset(left, top),
            size = ComposeSize(width, height),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 2.5f)
        )

        val cornerLength = size.minDimension * 0.06f
        val cornerStroke = 9f
        fun drawCorner(x: Float, y: Float, horizontalDirection: Float, verticalDirection: Float) {
            drawLine(accent, Offset(x, y), Offset(x + cornerLength * horizontalDirection, y), cornerStroke)
            drawLine(accent, Offset(x, y), Offset(x, y + cornerLength * verticalDirection), cornerStroke)
        }
        drawCorner(left, top, 1f, 1f)
        drawCorner(left + width, top, -1f, 1f)
        drawCorner(left, top + height, 1f, -1f)
        drawCorner(left + width, top + height, -1f, -1f)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
