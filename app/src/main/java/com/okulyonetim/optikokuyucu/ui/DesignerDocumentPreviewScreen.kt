package com.okulyonetim.optikokuyucu.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerGalleryTestAsset

/** Print-like read-only view backed by the same DesignerDocument/DesignerPrintRenderer path as PDF. */
@Composable
internal fun DesignerDocumentPreviewScreen(
    document: DesignerDocument,
    openCvReady: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val bitmapResult = remember(document, openCvReady) {
        if (!openCvReady) {
            Result.failure<Bitmap>(IllegalStateException("OpenCV hazır değil."))
        } else {
            runCatching { DesignerGalleryTestAsset.render(document) }
        }
    }
    val bitmap = bitmapResult.getOrNull()
    DisposableEffect(bitmap) {
        onDispose { bitmap?.takeIf { !it.isRecycled }?.recycle() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ProductTopBar(
            title = "Form Önizleme",
            leadingText = "‹",
            onLeadingClick = onBack,
            actionText = "Düzenle",
            onActionClick = onEdit
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(document.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${document.id} · v${document.version} · baskı görünümü",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "${document.name} önizlemesi",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        contentScale = ContentScale.FillWidth
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(
                            bitmapResult.exceptionOrNull()?.message ?: "Önizleme oluşturulamadı.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onEdit
            ) {
                Text("Bu Formu Düzenle")
            }

            DesignerPdfExportCard(document = document, openCvReady = openCvReady)
        }
    }
}
