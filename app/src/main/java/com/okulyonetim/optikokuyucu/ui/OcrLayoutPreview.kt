package com.okulyonetim.optikokuyucu.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.okulyonetim.optikokuyucu.ocr.OcrPageLayout
import com.okulyonetim.optikokuyucu.ocr.OcrRecognitionPostProcessor
import com.okulyonetim.optikokuyucu.ocr.OcrRecognitionResult
import java.util.concurrent.Executors

/**
 * Shows the corrected page itself and paints OCR geometry on top of the original positions.
 * The table/document is never rebuilt as a flat text list unless the user explicitly taps
 * "Metni Çıkar".
 */
@Composable
internal fun OcrLayoutPreview(
    recognition: OcrRecognitionResult,
    modifier: Modifier = Modifier
) {
    val pages = recognition.pages
    val clipboard = LocalClipboardManager.current
    var showExtractedText by remember(recognition) { mutableStateOf(false) }
    var extractedText by remember(recognition) { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = recognition.tokens.isNotEmpty(),
            onClick = {
                if (!showExtractedText) {
                    extractedText = OcrRecognitionPostProcessor.extractPlainText(recognition)
                }
                showExtractedText = !showExtractedText
            }
        ) {
            Text(if (showExtractedText) "Metni Gizle" else "Metni Çıkar")
        }

        if (showExtractedText) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = extractedText,
                onValueChange = { extractedText = it },
                minLines = 6,
                maxLines = 18,
                label = { Text("Düzenlenebilir metin") }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = extractedText.isNotBlank(),
                    onClick = { clipboard.setText(AnnotatedString(extractedText)) }
                ) {
                    Text("Kopyala")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { extractedText = OcrRecognitionPostProcessor.extractPlainText(recognition) }
                ) {
                    Text("Yeniden Oluştur")
                }
            }
        }

        if (pages.isEmpty()) {
            Text(
                "Belge görüntüsü kullanılamıyor; OCR konumları yine de cevap anahtarı analizinde korunuyor.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        pages.forEachIndexed { index, page ->
            if (pages.size > 1) {
                Text("Sayfa ${index + 1}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context -> OcrPageLayoutView(context) },
                update = { it.setPage(page) }
            )
        }
    }
}

private class OcrPageLayoutView(context: Context) : View(context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 0.8f
        color = 0x9932A873.toInt()
    }
    private val lowConfidencePaint = Paint(boxPaint).apply { color = 0x99E29A2D.toInt() }
    private var bitmap: Bitmap? = null
    private var page: OcrPageLayout? = null
    private var loadingUri: String? = null

    fun setPage(value: OcrPageLayout) {
        page = value
        if (loadingUri == value.sourceUri && bitmap != null) {
            requestLayout()
            invalidate()
            return
        }
        loadingUri = value.sourceUri
        worker.execute {
            val decoded = runCatching { decodeScaledBitmap(context, Uri.parse(value.sourceUri)) }.getOrNull()
            post {
                if (loadingUri == value.sourceUri) {
                    bitmap?.takeIf { it !== decoded }?.recycle()
                    bitmap = decoded
                    requestLayout()
                    invalidate()
                } else {
                    decoded?.recycle()
                }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        val current = page
        val height = if (current != null && current.imageWidth > 0) {
            (width * current.imageHeight.toFloat() / current.imageWidth.toFloat()).toInt().coerceAtLeast(120)
        } else {
            220
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentPage = page ?: return
        val currentBitmap = bitmap ?: return
        val sx = width.toFloat() / currentPage.imageWidth.coerceAtLeast(1)
        val sy = height.toFloat() / currentPage.imageHeight.coerceAtLeast(1)
        canvas.drawBitmap(currentBitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null)
        currentPage.tokens.forEach { token ->
            val paint = if ((token.confidence ?: 1f) < 0.55f) lowConfidencePaint else boxPaint
            canvas.drawRect(token.left * sx, token.top * sy, token.right * sx, token.bottom * sy, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        worker.shutdownNow()
        bitmap?.recycle()
        bitmap = null
    }

    @Suppress("DEPRECATION")
    private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val largest = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                if (largest > MAX_PREVIEW_EDGE) {
                    val scale = MAX_PREVIEW_EDGE.toFloat() / largest
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
            }
        } else {
            requireNotNull(MediaStore.Images.Media.getBitmap(context.contentResolver, uri))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P && maxOf(bitmap.width, bitmap.height) > MAX_PREVIEW_EDGE) {
            val scale = MAX_PREVIEW_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
            return Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== bitmap) bitmap.recycle() }
        }
        return bitmap
    }

    companion object {
        private const val MAX_PREVIEW_EDGE = 1800
    }
}
