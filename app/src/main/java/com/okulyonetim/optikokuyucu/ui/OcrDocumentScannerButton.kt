package com.okulyonetim.optikokuyucu.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/** Google Play services document scanner with crop, perspective correction and filters. */
@Composable
internal fun OcrDocumentScannerButton(
    enabled: Boolean,
    pageLimit: Int,
    modifier: Modifier = Modifier,
    onPagesReady: (List<Uri>) -> Unit,
    onStatus: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context.findActivity()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        when {
            activityResult.resultCode == Activity.RESULT_OK && result != null -> {
                val pages = result.pages.orEmpty().map { it.imageUri }
                if (pages.isEmpty()) onStatus("Taranan belgede görüntü bulunamadı.")
                else onPagesReady(pages)
            }
            activityResult.resultCode == Activity.RESULT_CANCELED -> onStatus("Belge tarama iptal edildi.")
            else -> onStatus("Belge tarama tamamlanamadı.")
        }
    }

    Button(
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && activity != null,
        shape = RoundedCornerShape(13.dp),
        onClick = {
            val host = activity
            if (host == null) {
                onStatus("Belge tarayıcı bu ekranda başlatılamadı.")
                return@Button
            }
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(pageLimit.coerceAtLeast(1))
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(host)
                .addOnSuccessListener { sender ->
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener { error ->
                    onStatus("Akıllı belge tarayıcı açılamadı: ${error.message ?: error.javaClass.simpleName}")
                }
        }
    ) {
        Text("Akıllı Belge Tara")
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
