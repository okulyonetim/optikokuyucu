package com.okulyonetim.optikokuyucu.omr.diagnostics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Deterministic, printer-free phone benchmark.
 *
 * It generates a known 20-question answer pattern, produces transformations that mimic common
 * print/camera variations, runs the real gallery OMR pipeline, and compares every answer against
 * ground truth. This is not a replacement for real paper tests; it is a fast regression gate.
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
        val clean = createGroundTruthBitmap()
        return try {
            val scenarios = listOf(
                "Temel" to clean.copy(Bitmap.Config.ARGB_8888, false),
                "A5 benzeri küçültme + marj" to scaledWithMargin(clean, 0.64f),
                "Farklı X/Y yazıcı ölçeği" to nonUniformScale(clean, 0.78f, 0.90f),
                "Perspektif" to perspectiveWarp(clean),
                "JPEG %65" to jpegRoundTrip(clean, 65)
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
        }
    }

    private fun createGroundTruthBitmap(): Bitmap {
        val bitmap = GalleryTestFormGenerator.generateBitmap()
        val canvas = Canvas(bitmap)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        template.bubbleRows.forEach { row ->
            when (val expected = groundTruth.getValue(row.id)) {
                is ExpectedAnswer.Marked -> fillChoice(canvas, row.id, expected.choice, fillPaint)
                ExpectedAnswer.Blank -> Unit
                ExpectedAnswer.Double -> {
                    fillChoice(canvas, row.id, "A", fillPaint)
                    fillChoice(canvas, row.id, "C", fillPaint)
                }
            }
        }
        return bitmap
    }

    private fun fillChoice(canvas: Canvas, rowId: String, choice: String, paint: Paint) {
        val row = template.bubbleRows.first { it.id == rowId }
        val bubble = row.bubbles.first { it.id == choice }
        canvas.drawCircle(
            bubble.center.x.toFloat(),
            bubble.center.y.toFloat(),
            (bubble.radius * 0.78).toFloat(),
            paint
        )
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

    private fun perspectiveWarp(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(1200, 1700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)

        val src = floatArrayOf(
            0f, 0f,
            source.width.toFloat(), 0f,
            source.width.toFloat(), source.height.toFloat(),
            0f, source.height.toFloat()
        )
        val dst = floatArrayOf(
            150f, 115f,
            1035f, 205f,
            1100f, 1540f,
            95f, 1465f
        )
        val matrix = Matrix().apply {
            check(setPolyToPoly(src, 0, dst, 0, 4))
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
                } else if (mismatches.size < 4) {
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
