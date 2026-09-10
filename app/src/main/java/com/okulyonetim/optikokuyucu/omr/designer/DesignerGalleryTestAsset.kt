package com.okulyonetim.optikokuyucu.omr.designer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.okulyonetim.optikokuyucu.omr.diagnostics.SyntheticOmrRenderer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** Produces a phone-editable PNG from the same designer document that recognition compiles. */
object DesignerGalleryTestAsset {
    fun render(
        document: DesignerDocument,
        markedChoicesByRow: Map<String, Set<String>> = emptyMap(),
        markedGridChoices: Map<String, Map<String, Set<String>>> = emptyMap(),
        numericHeaderValues: Map<String, String> = emptyMap()
    ): Bitmap {
        val renderPlan = DesignerPrintRenderer.render(document)
        val template = renderPlan.template
        val readability = TemplateReadabilityAnalyzer.analyze(document, template)
        require(readability.canSave) { "Template cannot be rendered while readability errors exist." }

        val bitmap = SyntheticOmrRenderer.render(
            template = template,
            markedChoicesByRow = markedChoicesByRow,
            markedGridChoices = markedGridChoices
        )
        val canvas = Canvas(bitmap)
        drawVisualLayer(canvas, document)
        drawComponentDecorations(canvas, document, numericHeaderValues)
        drawPrintTexts(canvas, renderPlan)
        return bitmap
    }

    fun saveToGallery(context: Context, document: DesignerDocument): Uri {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Galeriye doğrudan kaydetme Android 10 ve üstünde destekleniyor."
        }
        val resolver = context.contentResolver
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val safeName = document.name
            .replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")
            .trim('_')
            .ifBlank { "optik-form" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safeName-test-$timestamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OptikOkuyucu")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)) {
            "Galeri dosyası oluşturulamadı."
        }
        try {
            val bitmap = render(document)
            try {
                resolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Galeri çıktı akışı açılamadı." }
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG kaydedilemedi." }
                }
            } finally {
                bitmap.recycle()
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun drawVisualLayer(canvas: Canvas, document: DesignerDocument) {
        document.visualElements.forEach { element ->
            when (element) {
                is DesignerTextElement -> drawText(canvas, element)
                is DesignerImageElement -> drawImage(canvas, element)
                is DesignerBoxElement -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = element.strokeWidth.toFloat()
                    }
                    canvas.drawRect(
                        element.bounds.left.toFloat(), element.bounds.top.toFloat(),
                        element.bounds.right.toFloat(), element.bounds.bottom.toFloat(), paint
                    )
                }
                is DesignerLineElement -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = element.strokeWidth.toFloat()
                    }
                    canvas.drawLine(
                        element.start.x.toFloat(), element.start.y.toFloat(),
                        element.end.x.toFloat(), element.end.y.toFloat(), paint
                    )
                }
            }
        }
    }

    private fun drawText(canvas: Canvas, element: DesignerTextElement) {
        val physicalLeft = element.bounds.left.toFloat()
        val physicalTop = element.bounds.top.toFloat()
        val physicalRight = element.bounds.right.toFloat()
        val physicalBottom = element.bounds.bottom.toFloat()
        val centerX = (physicalLeft + physicalRight) / 2f
        val centerY = (physicalTop + physicalBottom) / 2f
        val quarterTurn = element.rotationDegrees == 90 || element.rotationDegrees == 270
        val logicalWidth = if (quarterTurn) physicalBottom - physicalTop else physicalRight - physicalLeft
        val logicalHeight = if (quarterTurn) physicalRight - physicalLeft else physicalBottom - physicalTop
        val left = centerX - logicalWidth / 2f
        val right = centerX + logicalWidth / 2f
        val top = centerY - logicalHeight / 2f
        val bottom = centerY + logicalHeight / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textSize = element.fontSize.toFloat()
            DesignerTypography.configurePaint(this, element.bold)
            textAlign = when (element.alignment) {
                DesignerTextAlignment.START -> Paint.Align.LEFT
                DesignerTextAlignment.CENTER -> Paint.Align.CENTER
                DesignerTextAlignment.END -> Paint.Align.RIGHT
            }
        }
        val x = when (element.alignment) {
            DesignerTextAlignment.START -> left
            DesignerTextAlignment.CENTER -> (left + right) / 2f
            DesignerTextAlignment.END -> right
        }
        val lineHeight = paint.textSize * 1.22f
        var baseline = top + paint.textSize
        canvas.save()
        if (element.rotationDegrees != 0) {
            canvas.rotate(element.rotationDegrees.toFloat(), centerX, centerY)
        }
        canvas.clipRect(left, top, right, bottom)
        element.text.split('\n').forEach { line ->
            if (baseline <= bottom + paint.textSize * 0.2f) {
                canvas.drawText(line, x, baseline, paint)
                baseline += lineHeight
            }
        }
        canvas.restore()
    }

    private fun drawImage(canvas: Canvas, element: DesignerImageElement) {
        val bytes = element.image.copyBytes()
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
            "Embedded designer image could not be decoded."
        }
        try {
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    element.bounds.left.toFloat(), element.bounds.top.toFloat(),
                    element.bounds.right.toFloat(), element.bounds.bottom.toFloat()
                ),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawComponentDecorations(
        canvas: Canvas,
        document: DesignerDocument,
        numericHeaderValues: Map<String, String>
    ) {
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        document.components.forEach { component ->
            if (DesignerEditorLayout.componentShowsLabel(component)) {
                val text = DesignerEditorLayout.componentLabel(component)
                if (text.isNotBlank()) {
                    val anchor = DesignerEditorLayout.labelAnchor(component)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.FILL
                        DesignerTypography.configurePaint(this, bold = true)
                        textSize = max(6.5, DesignerEditorLayout.componentBubbleRadius(component) * 1.15).toFloat()
                        textAlign = when (DesignerEditorLayout.componentLabelAlignment(component)) {
                            DesignerTextAlignment.START -> Paint.Align.LEFT
                            DesignerTextAlignment.CENTER -> Paint.Align.CENTER
                            DesignerTextAlignment.END -> Paint.Align.RIGHT
                        }
                    }
                    canvas.drawText(text, anchor.x.toFloat(), anchor.y.toFloat(), paint)
                }
            }
            if (component is NumericGridComponent) {
                val boxes = DesignerEditorLayout.numericHeaderBoxes(component)
                boxes.forEach { box ->
                    canvas.drawRect(
                        box.left.toFloat(), box.top.toFloat(),
                        box.right.toFloat(), box.bottom.toFloat(), boxPaint
                    )
                }
                val headerValue = numericHeaderValues[component.id]
                if (!headerValue.isNullOrBlank()) {
                    val normalized = headerValue.filter(Char::isDigit)
                        .takeLast(component.digits)
                        .padStart(component.digits, '0')
                    val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.FILL
                        textAlign = Paint.Align.CENTER
                        textSize = max(6.5, component.bubbleRadius * 1.18).toFloat()
                        DesignerTypography.configurePaint(this, bold = true)
                    }
                    val metrics = digitPaint.fontMetrics
                    boxes.forEachIndexed { index, box ->
                        normalized.getOrNull(index)?.let { digit ->
                            val baseline = box.center.y.toFloat() - (metrics.ascent + metrics.descent) / 2f
                            canvas.drawText(digit.toString(), box.center.x.toFloat(), baseline, digitPaint)
                        }
                    }
                }
            }
        }
    }

    private fun drawPrintTexts(canvas: Canvas, renderPlan: DesignerPrintRenderPlan) {
        renderPlan.texts.forEach { text ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
                DesignerTypography.configurePaint(this)
                textSize = max(6.5, text.textSize).toFloat()
                textAlign = when (text.alignment) {
                    DesignerTextAlignment.START -> Paint.Align.LEFT
                    DesignerTextAlignment.CENTER -> Paint.Align.CENTER
                    DesignerTextAlignment.END -> Paint.Align.RIGHT
                }
            }
            val metrics = paint.fontMetrics
            val baseline = text.anchor.y.toFloat() - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(text.text, text.anchor.x.toFloat(), baseline, paint)
        }
    }
}