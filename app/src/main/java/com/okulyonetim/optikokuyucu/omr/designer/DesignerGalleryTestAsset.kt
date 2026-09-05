package com.okulyonetim.optikokuyucu.omr.designer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.okulyonetim.optikokuyucu.omr.diagnostics.SyntheticOmrRenderer
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

/**
 * Produces a phone-editable PNG from the same designer document that recognition compiles.
 *
 * The bitmap is canonical/unitless. Saving it to Gallery does not assign A4/A5 semantics; if the
 * phone editor later scales or adds margins, the four fiducials recover the canonical geometry.
 */
object DesignerGalleryTestAsset {
    fun render(
        document: DesignerDocument,
        markedChoicesByRow: Map<String, Set<String>> = emptyMap(),
        markedGridChoices: Map<String, Map<String, Set<String>>> = emptyMap()
    ): Bitmap {
        val template = DesignerTemplateCompiler.compile(document)
        val readability = TemplateReadabilityAnalyzer.analyze(document, template)
        require(readability.canSave) {
            "Template cannot be rendered while readability errors exist."
        }

        val bitmap = SyntheticOmrRenderer.render(
            template = template,
            markedChoicesByRow = markedChoicesByRow,
            markedGridChoices = markedGridChoices
        )
        val canvas = Canvas(bitmap)
        drawVisualLayer(canvas, document)
        drawSemanticLabels(canvas, document, template)
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

        val uri = requireNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ) { "Galeri dosyası oluşturulamadı." }

        try {
            val bitmap = render(document)
            try {
                resolver.openOutputStream(uri, "w").use { output ->
                    requireNotNull(output) { "Galeri çıktı akışı açılamadı." }
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "PNG kaydedilemedi."
                    }
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
                is DesignerTextElement -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.FILL
                        textSize = element.fontSize.toFloat()
                        typeface = Typeface.create(
                            Typeface.DEFAULT,
                            if (element.bold) Typeface.BOLD else Typeface.NORMAL
                        )
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
                    val baseline = element.bounds.top.toFloat() + paint.textSize
                    canvas.drawText(element.text, x, baseline, paint)
                }

                is DesignerBoxElement -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = element.strokeWidth.toFloat()
                    }
                    canvas.drawRect(
                        element.bounds.left.toFloat(),
                        element.bounds.top.toFloat(),
                        element.bounds.right.toFloat(),
                        element.bounds.bottom.toFloat(),
                        paint
                    )
                }

                is DesignerLineElement -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        style = Paint.Style.STROKE
                        strokeWidth = element.strokeWidth.toFloat()
                    }
                    canvas.drawLine(
                        element.start.x.toFloat(),
                        element.start.y.toFloat(),
                        element.end.x.toFloat(),
                        element.end.y.toFloat(),
                        paint
                    )
                }
            }
        }
    }

    private fun drawSemanticLabels(
        canvas: Canvas,
        document: DesignerDocument,
        template: OmrTemplate
    ) {
        val byQuestionId = template.bubbleRows.associateBy { it.id }
        val byGridId = template.markGrids.associateBy { it.id }

        document.components.forEach { component ->
            when (component) {
                is QuestionGroupComponent -> repeat(component.questionCount) { index ->
                    val questionNumber = component.startQuestion + index
                    val questionId = DesignerTemplateCompiler.questionReadId(component, questionNumber)
                    val row = byQuestionId[questionId] ?: return@repeat
                    val first = row.bubbles.firstOrNull() ?: return@repeat
                    drawLabel(
                        canvas,
                        questionNumber.toString(),
                        first.center.x - first.radius * QUESTION_NUMBER_DISTANCE,
                        first.center.y + first.radius * 0.40,
                        Paint.Align.RIGHT,
                        first.radius * 1.02
                    )
                    row.bubbles.forEach { bubble ->
                        drawCenteredBubbleLabel(
                            canvas = canvas,
                            text = bubble.id,
                            x = bubble.center.x,
                            y = bubble.center.y,
                            radius = bubble.radius
                        )
                    }
                }

                is NumericGridComponent -> {
                    val grid = byGridId[component.id] ?: return@forEach
                    grid.columns.forEach { column ->
                        column.marks.forEach { mark ->
                            drawCenteredBubbleLabel(
                                canvas = canvas,
                                text = mark.id,
                                x = mark.center.x,
                                y = mark.center.y,
                                radius = mark.radius
                            )
                        }
                    }
                }

                is SingleChoiceComponent -> {
                    val grid = byGridId[component.id] ?: return@forEach
                    grid.columns.firstOrNull()?.marks.orEmpty().forEach { mark ->
                        drawCenteredBubbleLabel(
                            canvas = canvas,
                            text = mark.id,
                            x = mark.center.x,
                            y = mark.center.y,
                            radius = mark.radius
                        )
                    }
                }
            }
        }
    }

    private fun drawCenteredBubbleLabel(
        canvas: Canvas,
        text: String,
        x: Double,
        y: Double,
        radius: Double
    ) {
        drawLabel(
            canvas = canvas,
            text = text,
            x = x,
            y = y + radius * 0.34,
            align = Paint.Align.CENTER,
            textSize = radius * 0.78
        )
    }

    private fun drawLabel(
        canvas: Canvas,
        text: String,
        x: Double,
        y: Double,
        align: Paint.Align,
        textSize: Double
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            textAlign = align
            this.textSize = max(6.5, textSize).toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(text, x.toFloat(), y.toFloat(), paint)
    }

    private const val QUESTION_NUMBER_DISTANCE = 2.15
}
