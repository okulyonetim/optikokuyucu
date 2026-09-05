package com.okulyonetim.optikokuyucu.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.okulyonetim.optikokuyucu.camera.CameraFrameAnalyzer
import com.okulyonetim.optikokuyucu.camera.CameraFrameStats
import com.okulyonetim.optikokuyucu.omr.tracking.PageTrackingPhase
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun OmrCameraScreen(openCvReady: Boolean) {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }

    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    MaterialTheme {
        if (cameraGranted) {
            CameraPreviewContent(openCvReady = openCvReady)
        } else {
            CameraPermissionContent(
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }
    }
}

@Composable
private fun CameraPermissionContent(onRequestPermission: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Kamera izni gerekli",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                text = "Optik formları canlı okuyabilmek için kameraya erişim vermelisiniz.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRequestPermission) {
                Text("Kamera izni ver")
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(openCvReady: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) {
        requireNotNull(context.findActivity() as? LifecycleOwner) {
            "Kamera için LifecycleOwner bulunamadı."
        }
    }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var stats by remember { mutableStateOf(CameraFrameStats.Empty) }
    var cameraMessage by remember { mutableStateOf("Kamera başlatılıyor…") }

    val analyzer = remember(openCvReady) {
        CameraFrameAnalyzer(openCvReady = openCvReady) { newStats ->
            mainExecutor.execute {
                stats = newStats
                cameraMessage = "Canlı analiz aktif"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        val listener = Runnable {
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

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
                    .also { useCase ->
                        useCase.setAnalyzer(analysisExecutor, analyzer)
                    }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
                cameraMessage = "Kamera hazır"
            } catch (error: Exception) {
                cameraMessage = "Kamera başlatılamadı: ${error.message ?: error.javaClass.simpleName}"
            }
        }

        cameraProviderFuture.addListener(listener, mainExecutor)

        onDispose {
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )

        OmrGuideOverlay(modifier = Modifier.fillMaxSize())

        CameraTelemetryCard(
            modifier = Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(12.dp),
            message = cameraMessage,
            stats = stats,
            initialOpenCvReady = openCvReady
        )

        Text(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(20.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            text = when (stats.trackingPhase) {
                PageTrackingPhase.LOCKED -> "Form kilitlendi"
                PageTrackingPhase.TRACKING -> "Form takip ediliyor"
                PageTrackingPhase.SEARCHING -> "Optik formu A4 çerçevesinin içine getirin"
            },
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CameraTelemetryCard(
    modifier: Modifier,
    message: String,
    stats: CameraFrameStats,
    initialOpenCvReady: Boolean
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.68f),
            contentColor = Color.White
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Optik Okuyucu · OMR Motor Testi",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                modifier = Modifier.padding(top = 3.dp),
                text = message,
                style = MaterialTheme.typography.bodySmall
            )

            if (stats.width > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${stats.width}×${stats.height}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        String.format(Locale.US, "%.1f FPS", stats.fps),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text("Y ${stats.averageLuma}", style = MaterialTheme.typography.labelMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val cvReady = stats.openCvReady || (stats.width == 0 && initialOpenCvReady)
                    Text(
                        text = if (cvReady) "OpenCV 5 ✓" else "OpenCV !",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = "Marker ${stats.markerCount}/4",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = String.format(Locale.US, "Güven %.0f%%", stats.pageConfidence * 100.0),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = if (initialOpenCvReady) "OpenCV 5 hazır" else "OpenCV başlatılamadı",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun OmrGuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val a4Aspect = 210f / 297f
        val maxWidth = size.width * 0.84f
        val maxHeight = size.height * 0.70f

        val width: Float
        val height: Float
        if (maxWidth / maxHeight > a4Aspect) {
            height = maxHeight
            width = height * a4Aspect
        } else {
            width = maxWidth
            height = width / a4Aspect
        }

        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f

        drawRoundRect(
            color = Color.White.copy(alpha = 0.82f),
            topLeft = Offset(left, top),
            size = ComposeSize(width, height),
            cornerRadius = CornerRadius(22f, 22f),
            style = Stroke(width = 3f)
        )

        val cornerLength = size.minDimension * 0.055f
        val cornerStroke = 8f
        val accent = Color(0xFF55E6A5)

        fun drawCorner(x: Float, y: Float, horizontalDirection: Float, verticalDirection: Float) {
            drawLine(
                color = accent,
                start = Offset(x, y),
                end = Offset(x + cornerLength * horizontalDirection, y),
                strokeWidth = cornerStroke
            )
            drawLine(
                color = accent,
                start = Offset(x, y),
                end = Offset(x, y + cornerLength * verticalDirection),
                strokeWidth = cornerStroke
            )
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
