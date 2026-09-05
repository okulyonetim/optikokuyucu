package com.okulyonetim.optikokuyucu.omr.diagnostics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import java.io.ByteArrayOutputStream

/**
 * Deterministic, printer-free phone benchmark.
 *
 * It generates a known 20-question answer pattern, produces transformations that mimic common
 * print/camera/marking variations, runs the real gallery OMR pipeline, and compares every answer
 * against ground truth. This is not a replacement for real paper tests; it is a fast regression
 * gate before live-camera tuning.
 */
object OmrStressBenchmark {
    private val template = StandardOmrTemplate.SAMPLE_20_ABCD

    private val groundTruth: Map<String, ExpectedAnswer> = mapOf(
        "1" to ExpectedAnswer.marked("A"),
        "2" to ExpectedAnswer.blank(),
        "3" to ExpectedAnswer.marked("D"),
        "4" to ExpectedAnswer.double(),
        "5" to ExpectedAnswer.marked("B"),
        "6" to ExpectedAnswer.blank(),
        "7" to ExpectedAnswer.marked("A"),
        "8" to ExpectedAnswer.double(),
        "9" to ExpectedAnswer.marked("C"),
        "10" to ExpectedAnswer.blank(),
        "11" to ExpectedAnswer.marked("B"),
        "12" to ExpectedAnswer.marked("A"),
        "13" to ExpectedAnswer.marked("B"),
        "14" to ExpectedAnswer.blank(),
        "15" to ExpectedAnswer.marked("A"),
        "16" to ExpectedAnswer.marked("C"),
        "17" to ExpectedAnswer.blank(),
        "18" to ExpectedAnswer.double(),
        "19" to ExpectedAnswer.marked("D"),
        "20" to ExpectedAnswer.marked("B")
    )

    fun run(): OmrStressBenchmarkResult {
        val startedAt = System.nanoTime()
        val clean = createGroundTruthBitmap(MarkStyle.SOLID)
        val faint = createGroundTruthBitmap(MarkStyle.FAINT_PENCIL)
        val partial = createGroundTruthBitmap(MarkStyle.PARTIAL)
        val erased = createGroundTruthBitmap(MarkStyle.SOLID, addEraseResidue = true)

        return try {
            val scenarios = listOf(
                "Temel" to clean.copy(Bitmap.Config.ARGB_8888, false),
                "A5 benzeri küçültme + marj" to scaledWithMargin(clean, 0.64f),
                "Farklı X/Y yazıcı ölçeği" to nonUniformScale(clean, 0.78f, 0.90f),
                "Perspektif" to perspectiveWarp(clean),
                "JPEG %65" to jpegRoundTrip(clean, 65),
                "Soluk kurşun kalem" to faint.copy(Bitmap.Config.ARGB_8888, false),
                "Kısmi doldurma" to partial.copy(Bitmap.Config.ARGB_8888, false),
                "Silgi izi / kirli boşlar" to erased.copy(Bitmap.Config.ARGB_8888, false),
                "Düşük kontrast" to lowContrast(clean),
                "Gölge" to shadow(clean),
                "Hafif parlama" to glare(clean),
                "Bulanıklık" to blurLike(clean),
                "180° ters form" to rotate180(clean),
                "Agresif perspektif" to aggressivePerspective(clean),
                "Soluk kalem + JPEG %55" to jpegRoundTrip(faint, 55)
            )

            val results = scenarios.map { (name, bitmap) ->
                try {
                    evaluate(name, bitmap)
                } finally {
                    bitmap.recycle()
                }
            }
            OmrStressBenchmarkResult(
                scenarios = results,
                elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0
            )
        } finally {
            clean.recycle()
            faint.recycle()
            partial.recycle()
            erased.recycle()
        }
    }

    private fun createGroundTruthBitmap(
        style: MarkStyle,
        addEraseResidue: Boolean = false
    ): Bitmap {
        val bitmap = GalleryTestFormGenerator.generateBitmap()
        val canvas = Canvas(bitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (style) {
                MarkStyle.SOLID -> Color.BLACK
                MarkStyle.FAINT_PENCIL -> Color.rgb(145, 145, 145)
                MarkStyle.PARTIAL -> Color.BLACK
            }
            this.style = Paint.Style.FILL
        }

        template.bubbleRows.forEach { row ->
            when (val expected = groundTruth.getValue(row.id)) {
                is ExpectedAnswer.Marked -> fillChoice(canvas, row.id, expected.choice, fillPaint, style)
                ExpectedAnswer.Blank -> Unit
                ExpectedAnswer.Double -> {
                    fillChoice(canvas, row.id, "A", fillPaint, style)
                    fillChoice(canvas, row.id, "C", fillPaint, style)
                }
            }
        }

        if (addEraseResidue) {
            addEraseResidue(canvas)
        }
        return bitmap
    }

    private fun fillChoice(
        canvas: Canvas,
        rowId: String,
        choice: String,
        paint: Paint,
        style: MarkStyle
    ) {
        val row = template.bubbleRows.first { it.id == rowId }
        val bubble = row.bubbles.first { it.id == choice }
        val cx = bubble.center.x.toFloat()
        val cy = bubble.center.y.toFloat()
        val radius = (bubble.radius * 0.78).toFloat()

        when (style) {
            MarkStyle.SOLID,
            MarkStyle.FAINT_PENCIL -> canvas.drawCircle(cx, cy, radius, paint)

            MarkStyle.PARTIAL -> {
                val save = canvas.save()
                canvas.clipRect(cx - radius, cy - radius, cx + radius * 0.20f, cy + radius)
                canvas.drawCircle(cx, cy, radius, paint)
                canvas.restoreToCount(save)
            }
        }
    }

    /** Adds weak grey traces inside otherwise blank answers. These must remain BLANK. */
    private fun addEraseResidue(canvas: Canvas) {
        val residuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(238, 238, 238)
            style = Paint.Style.FILL
        }
        val residueChoices = mapOf(
            "2" to "B",
            "6" to "D",
            "10" to "A",
            "14" to "C",
            "17" to "B"
        )
        residueChoices.forEach { (rowId, choice) ->
            val row = template.bubbleRows.first { it.id == rowId }
            val bubble = row.bubbles.first { it.id == choice }
            canvas.drawCircle(
                bubble.center.x.toFloat(),
                bubble.center.y.toFloat(),
                (bubble.radius * 0.62).toFloat(),
                residuePaint
            )
        }
    }

    private fun scaledWithMargin(source: Bitmap, scale: Float): Bitmap {
        val out = Bitmap.createBitmap(1080, 1540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(220f, 130f)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun nonUniformScale(source: Bitmap, scaleX: Float, scaleY: Float): Bitmap {
        val out = Bitmap.createBitmap(1080, 1540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            postScale(scaleX, scaleY)
            postTranslate(145f, 105f)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun perspectiveWarp(source: Bitmap): Bitmap = projectiveWarp(
        source = source,
        width = 1200,
        height = 1700,
        destination = floatArrayOf(
            150f, 115f,
            1035f, 205f,
            1100f, 1540f,
            95f, 1465f
        )
    )

    private fun aggressivePerspective(source: Bitmap): Bitmap = projectiveWarp(
        source = source,
        width = 1280,
        height = 1780,
        destination = floatArrayOf(
            265f, 90f,
            1035f, 285f,
            1170f, 1580f,
            80f, 1450f
        )
    )

    private fun projectiveWarp(
        source: Bitmap,
        width: Int,
        height: Int,
        destination: FloatArray
    ): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val src = floatArrayOf(
            0f, 0f,
            source.width.toFloat(), 0f,
            source.width.toFloat(), source.height.toFloat(),
            0f, source.height.toFloat()
        )
        val matrix = Matrix().apply {
            check(setPolyToPoly(src, 0, destination, 0, 4))
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun jpegRoundTrip(source: Bitmap, quality: Int): Bitmap {
        val bytes = ByteArrayOutputStream().use { output ->
            check(source.compress(Bitmap.CompressFormat.JPEG, quality, output))
            output.toByteArray()
        }
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    private fun lowContrast(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val veil = Paint().apply { color = Color.argb(82, 255, 255, 255) }
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), veil)
        return out
    }

    private fun shadow(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f,
                0f,
                out.width.toFloat(),
                out.height.toFloat(),
                Color.argb(8, 0, 0, 0),
                Color.argb(105, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), paint)
        return out
    }

    private fun glare(source: Bitmap): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.save()
        canvas.rotate(-12f, out.width * 0.52f, out.height * 0.54f)
        canvas.drawOval(
            out.width * 0.34f,
            out.height * 0.22f,
            out.width * 0.68f,
            out.height * 0.86f,
            paint
        )
        canvas.restore()
        return out
    }

    private fun blurLike(source: Bitmap): Bitmap {
        val downWidth = (source.width * 0.56f).toInt().coerceAtLeast(1)
        val downHeight = (source.height * 0.56f).toInt().coerceAtLeast(1)
        val down = Bitmap.createScaledBitmap(source, downWidth, downHeight, true)
        return try {
            Bitmap.createScaledBitmap(down, source.width, source.height, true)
        } finally {
            down.recycle()
        }
    }

    private fun rotate180(source: Bitmap): Bitmap {
        val matrix = Matrix().apply { postRotate(180f) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun evaluate(name: String, bitmap: Bitmap): BenchmarkScenarioResult {
        val result = GalleryOmrReader.readBitmap(bitmap)
        return try {
            var correct = 0
            val mismatches = mutableListOf<String>()
            result.bubbleResult.questions.forEach { question ->
                val expected = groundTruth[question.questionId] ?: return@forEach
                val matches = when (expected) {
                    is ExpectedAnswer.Marked ->
                        question.state == QuestionState.MARKED && question.selectedChoice == expected.choice
                    ExpectedAnswer.Blank -> question.state == QuestionState.BLANK
                    ExpectedAnswer.Double -> question.state == QuestionState.DOUBLE_MARK
                }
                if (matches) {
                    correct++
                } else if (mismatches.size < 5) {
                    mismatches += question.questionId
                }
            }

            val total = groundTruth.size
            BenchmarkScenarioResult(
                name = name,
                passed = result.markerCount == 4 && result.rectificationReady && correct == total,
                markerCount = result.markerCount,
                correctAnswers = correct,
                totalAnswers = total,
                elapsedMs = result.elapsedMs,
                mismatchQuestionIds = mismatches
            )
        } finally {
            result.bitmap.recycle()
        }
    }
}

private enum class MarkStyle {
    SOLID,
    FAINT_PENCIL,
    PARTIAL
}

sealed interface ExpectedAnswer {
    data class Marked(val choice: String) : ExpectedAnswer
    data object Blank : ExpectedAnswer
    data object Double : ExpectedAnswer

    companion object {
        fun marked(choice: String): ExpectedAnswer = Marked(choice)
        fun blank(): ExpectedAnswer = Blank
        fun double(): ExpectedAnswer = Double
    }
}

data class BenchmarkScenarioResult(
    val name: String,
    val passed: Boolean,
    val markerCount: Int,
    val correctAnswers: Int,
    val totalAnswers: Int,
    val elapsedMs: Double,
    val mismatchQuestionIds: List<String>
)

data class OmrStressBenchmarkResult(
    val scenarios: List<BenchmarkScenarioResult>,
    val elapsedMs: Double
) {
    val passedCount: Int get() = scenarios.count { it.passed }
    val allPassed: Boolean get() = scenarios.isNotEmpty() && scenarios.all { it.passed }
}
