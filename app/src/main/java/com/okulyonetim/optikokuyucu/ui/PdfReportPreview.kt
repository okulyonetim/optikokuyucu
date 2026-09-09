package com.okulyonetim.optikokuyucu.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

private data class RenderedPdfPage(
    val bitmap: Bitmap,
    val pageCount: Int
)

@Composable
fun PdfReportPreview(
    pdfBytes: ByteArray,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hash = remember(pdfBytes) { pdfBytes.contentHashCode() }
    var pageIndex by remember(hash) { mutableStateOf(0) }
    val rendered = remember(hash, pageIndex) {
        runCatching { renderPdfPage(context.applicationContext, pdfBytes, pageIndex) }.getOrNull()
    }

    DisposableEffect(rendered?.bitmap) {
        onDispose {
            val bitmap = rendered?.bitmap
            if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (rendered == null) {
            ProductEmptyState(
                title = "PDF önizlenemedi",
                body = "Rapor PDF'i oluşturuldu ancak sayfa görüntüsü hazırlanamadı."
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    enabled = pageIndex > 0,
                    onClick = { pageIndex-- },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("‹ Önceki", fontSize = 10.sp) }
                Text(
                    "Sayfa ${pageIndex + 1} / ${rendered.pageCount}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
                OutlinedButton(
                    enabled = pageIndex + 1 < rendered.pageCount,
                    onClick = { pageIndex++ },
                    shape = RoundedCornerShape(10.dp)
                ) { Text("Sonraki ›", fontSize = 10.sp) }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp,
                shadowElevation = 1.dp
            ) {
                Image(
                    bitmap = rendered.bitmap.asImageBitmap(),
                    contentDescription = "Rapor PDF önizleme sayfası ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}

private fun renderPdfPage(context: Context, pdfBytes: ByteArray, pageIndex: Int): RenderedPdfPage {
    val cacheFile = File.createTempFile("report-preview-", ".pdf", context.cacheDir)
    try {
        cacheFile.writeBytes(pdfBytes)
        val descriptor = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount > 0) { "PDF sayfası bulunamadı." }
                val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safeIndex).use { page ->
                    val scale = (1400f / page.width.toFloat()).coerceIn(1f, 2.2f)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return RenderedPdfPage(bitmap = bitmap, pageCount = renderer.pageCount)
                }
            }
        } finally {
            runCatching { descriptor.close() }
        }
    } finally {
        cacheFile.delete()
    }
}
