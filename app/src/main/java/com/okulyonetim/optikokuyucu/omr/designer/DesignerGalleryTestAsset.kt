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
        markedGridChoices: Map<String, Map<String, Set<String>>> = emptyMap()
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
        drawComponentDecorations(canvas, document)
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
            DesignerTextAlignment.START -> element.bounds.left.toFloat()
            DesignerTextAlignment.CENTER -> element.bounds.center.x.toFloat()
            DesignerTextAlignment.END -> element.bounds.right.toFloat()
        }
        val lineHeight = paint.textSize * 1.22f
        var baseline = element.bounds.top.toFloat() + paint.textSize
        canvas.save()
        canvas.clipRect(
            element.bounds.left.toFloat(), element.bounds.top.toFloat(),
            element.bounds.right.toFloat(), element.bounds.bottom.toFloat()
        )
        element.text.split('\n').forEach { line ->
            if (baseline <= element.bounds.bottom.toFloat() + paint.textSize * 0.2f) {
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

    private fun drawComponentDecorations(canvas: Canvas, document: DesignerDocument) {
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
                DesignerEditorLayout.numericHeaderBoxes(component).forEach { box ->
                    canvas.drawRect(
                        box.left.toFloat(), box.top.toFloat(),
                        box.right.toFloat(), box.bottom.toFloat(), boxPaint
                    )
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
