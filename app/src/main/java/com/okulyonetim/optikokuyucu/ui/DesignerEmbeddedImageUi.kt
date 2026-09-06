package com.okulyonetim.optikokuyucu.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.okulyonetim.optikokuyucu.omr.designer.DesignerBoxElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDynamicText
import com.okulyonetim.optikokuyucu.omr.designer.DesignerImageData
import com.okulyonetim.optikokuyucu.omr.designer.DesignerImageElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerLineElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextAlignment
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTextElement
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTypography
import com.okulyonetim.optikokuyucu.omr.designer.DesignerVisualElement
import java.io.ByteArrayOutputStream
import kotlin.math.min

internal fun importDesignerImage(context: Context, uri: Uri): Result<DesignerImageData> = runCatching {
    val decoded = context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Resim dosyası açılamadı." }
        requireNotNull(BitmapFactory.decodeStream(input)) { "Seçilen dosya geçerli bir resim değil." }
    }
    try {
        require(decoded.width > 0 && decoded.height > 0) { "Resim boyutları geçersiz." }
        val maxEdge = maxOf(decoded.width, decoded.height)
        val scale = min(1.0, MAX_IMPORT_EDGE.toDouble() / maxEdge.toDouble())
        val scaled = if (scale < 1.0) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else decoded
        try {
            val flattened = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = AndroidCanvas(flattened)
                canvas.drawColor(AndroidColor.WHITE)
                canvas.drawBitmap(
                    scaled, 0f, 0f,
                    AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
                )
                val output = ByteArrayOutputStream()
                var quality = 90
                do {
                    output.reset()
                    check(flattened.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                        "Resim sıkıştırılamadı."
                    }
                    quality -= 10
                } while (output.size() > DesignerImageData.MAX_BYTES && quality >= 60)
                require(output.size() <= DesignerImageData.MAX_BYTES) {
                    "Resim forma eklemek için çok büyük. Daha küçük bir resim seçin."
                }
                DesignerImageData(
                    mimeType = "image/jpeg",
                    pixelWidth = flattened.width,
                    pixelHeight = flattened.height,
                    bytes = output.toByteArray()
                )
            } finally {
                flattened.recycle()
            }
        } finally {
            if (scaled !== decoded) scaled.recycle()
        }
    } finally {
        decoded.recycle()
    }
}

@Composable
internal fun rememberDesignerImageBitmaps(elements: List<DesignerVisualElement>): Map<String, Bitmap> {
    val images = remember(elements) {
        elements.filterIsInstance<DesignerImageElement>().mapNotNull { element ->
            val bytes = element.image.copyBytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { element.id to it }
        }.toMap()
    }
    DisposableEffect(images) {
        onDispose {
            images.values.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }
    return images
}

internal fun DrawScope.drawDesignerVisualElements(
    elements: List<DesignerVisualElement>,
    imageBitmaps: Map<String, Bitmap>,
    scaleX: Float,
    scaleY: Float
) {
    val averageScale = (scaleX + scaleY) / 2f
    elements.forEach { element ->
        when (element) {
            is DesignerTextElement -> drawDesignerTextElement(element, scaleX, scaleY)
            is DesignerImageElement -> imageBitmaps[element.id]?.let {
                drawDesignerImageElement(element, it, scaleX, scaleY)
            }
            is DesignerBoxElement -> drawRect(
                color = Color.Black,
                topLeft = Offset(element.bounds.left.toFloat() * scaleX, element.bounds.top.toFloat() * scaleY),
                size = Size(element.bounds.width.toFloat() * scaleX, element.bounds.height.toFloat() * scaleY),
                style = Stroke(width = (element.strokeWidth.toFloat() * averageScale).coerceAtLeast(1f))
            )
            is DesignerLineElement -> drawLine(
                color = Color.Black,
                start = Offset(element.start.x.toFloat() * scaleX, element.start.y.toFloat() * scaleY),
                end = Offset(element.end.x.toFloat() * scaleX, element.end.y.toFloat() * scaleY),
                strokeWidth = (element.strokeWidth.toFloat() * averageScale).coerceAtLeast(1f)
            )
        }
    }
}

internal fun DrawScope.drawDesignerTextElement(
    element: DesignerTextElement,
    scaleX: Float,
    scaleY: Float
) {
    val averageScale = (scaleX + scaleY) / 2f
    val renderedText = DesignerDynamicText.render(element)
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val left = element.bounds.left.toFloat() * scaleX
        val top = element.bounds.top.toFloat() * scaleY
        val right = element.bounds.right.toFloat() * scaleX
        val bottom = element.bounds.bottom.toFloat() * scaleY
        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.BLACK
            textSize = (element.fontSize.toFloat() * averageScale).coerceAtLeast(7f)
            DesignerTypography.configurePaint(this, element.bold)
            textAlign = when (element.alignment) {
                DesignerTextAlignment.START -> AndroidPaint.Align.LEFT
                DesignerTextAlignment.CENTER -> AndroidPaint.Align.CENTER
                DesignerTextAlignment.END -> AndroidPaint.Align.RIGHT
            }
        }
        val x = when (element.alignment) {
            DesignerTextAlignment.START -> left
            DesignerTextAlignment.CENTER -> (left + right) / 2f
            DesignerTextAlignment.END -> right
        }
        val lineHeight = paint.textSize * 1.22f
        var baseline = top + paint.textSize
        native.save()
        native.clipRect(left, top, right, bottom)
        renderedText.split('\n').forEach { line ->
            if (baseline <= bottom + paint.textSize * 0.2f) {
                native.drawText(line, x, baseline, paint)
                baseline += lineHeight
            }
        }
        native.restore()
    }
}

internal fun DrawScope.drawDesignerImageElement(
    element: DesignerImageElement,
    bitmap: Bitmap,
    scaleX: Float,
    scaleY: Float
) {
    drawIntoCanvas { canvas ->
        val rect = RectF(
            element.bounds.left.toFloat() * scaleX,
            element.bounds.top.toFloat() * scaleY,
            element.bounds.right.toFloat() * scaleX,
            element.bounds.bottom.toFloat() * scaleY
        )
        canvas.nativeCanvas.drawBitmap(
            bitmap,
            null,
            rect,
            AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.FILTER_BITMAP_FLAG)
        )
    }
}

private const val MAX_IMPORT_EDGE = 1600
