package com.okulyonetim.optikokuyucu.omr.diagnostics

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.okulyonetim.optikokuyucu.omr.bubble.QuestionState
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrReader
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrResult
import com.okulyonetim.optikokuyucu.omr.template.StandardOmrTemplate
import java.io.ByteArrayOutputStream

/** Phase-3 phone benchmark for a dense 100-question / 400-bubble form. */
object Omr100Benchmark {
    private val template = StandardOmrTemplate.SAMPLE_100_ABCD

    private val groundTruth: Map<String, Set<String>> = (1..100).associate { question ->
        val answers = when {
            question % 13 == 0 -> setOf("A", "C")
            question % 10 == 0 -> emptySet()
            else -> setOf(listOf("A", "B", "C", "D")[(question * 7) % 4])
        }
        question.toString() to answers
    }

    fun run(): Omr100BenchmarkResult {
        val source = SyntheticOmrRenderer.render(template, groundTruth)
        return try {
            // One warm-up removes first-use OpenCV/JIT noise from the measured median.
            GalleryOmrReader.readBitmap(source, template).also { it.bitmap.recycle() }

            val cleanRuns = (1..3).map {
                evaluate("Temel-$it", source)
            }

            val scaled = scaledWithMargin(source, 0.66f)
            val perspective = perspectiveWarp(source)
            val jpeg = jpegRoundTrip(source, 62)
            val stressRuns = try {
                listOf(
                    evaluate("A5 benzeri küçültme", scaled),
                    evaluate("Perspektif", perspective),
                    evaluate("JPEG %62", jpeg)
                )
            } finally {
                scaled.recycle()
                perspective.recycle()
                jpeg.recycle()
            }

            val allRuns = cleanRuns + stressRuns
            val cleanTiming = cleanRuns.map { it.timing }
            Omr100BenchmarkResult(
                cleanRuns = cleanRuns,
                stressRuns = stressRuns,
                medianTiming = OmrStageTiming(
                    preprocessingMs = median(cleanTiming.map { it.preprocessingMs }),
                    markerMs = median(cleanTiming.map { it.markerMs }),
                    rectificationMs = median(cleanTiming.map { it.rectificationMs }),
                    bubbleMs = median(cleanTiming.map { it.bubbleMs }),
                    totalMs = median(cleanTiming.map { it.totalMs })
                ),
                allPassed = allRuns.all { it.passed }
            )
        } finally {
            source.recycle()
        }
    }

    private fun evaluate(name: String, bitmap: Bitmap): Omr100RunResult {
        val result = GalleryOmrReader.readBitmap(bitmap, template)
        return try {
            var correct = 0
            val mismatches = mutableListOf<String>()
            result.bubbleResult.questions.forEach { question ->
                val expected = groundTruth[question.questionId] ?: return@forEach
                val matches = when (expected.size) {
                    0 -> question.state == QuestionState.BLANK
                    1 -> question.state == QuestionState.MARKED &&
                        question.selectedChoice == expected.first()
                    else -> question.state == QuestionState.DOUBLE_MARK
                }
                if (matches) {
                    correct += 1
                } else if (mismatches.size < 8) {
                    mismatches += question.questionId
                }
            }

            Omr100RunResult(
                name = name,
                correctAnswers = correct,
                totalAnswers = groundTruth.size,
                markerCount = result.markerCount,
                passed = result.markerCount == 4 &&
                    result.rectificationReady &&
                    correct == groundTruth.size,
                mismatchQuestionIds = mismatches,
                timing = result.toStageTiming()
            )
        } finally {
            result.bitmap.recycle()
        }
    }

    private fun scaledWithMargin(source: Bitmap, scale: Float): Bitmap {
        val out = Bitmap.createBitmap(1080, 1540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(205f, 155f)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun perspectiveWarp(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(1220, 1720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val src = floatArrayOf(
            0f, 0f,
            source.width.toFloat(), 0f,
            source.width.toFloat(), source.height.toFloat(),
            0f, source.height.toFloat()
        )
        val dst = floatArrayOf(
            175f, 110f,
            1045f, 215f,
            1125f, 1570f,
            90f, 1470f
        )
        val matrix = Matrix().apply { check(setPolyToPoly(src, 0, dst, 0, 4)) }
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

    private fun GalleryOmrResult.toStageTiming(): OmrStageTiming = OmrStageTiming(
        preprocessingMs = preprocessingMs,
        markerMs = markerMs,
        rectificationMs = rectificationMs,
        bubbleMs = bubbleMs,
        totalMs = elapsedMs
    )

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }
}

data class OmrStageTiming(
    val preprocessingMs: Double,
    val markerMs: Double,
    val rectificationMs: Double,
    val bubbleMs: Double,
    val totalMs: Double
)

data class Omr100RunResult(
    val name: String,
    val correctAnswers: Int,
    val totalAnswers: Int,
    val markerCount: Int,
    val passed: Boolean,
    val mismatchQuestionIds: List<String>,
    val timing: OmrStageTiming
)

data class Omr100BenchmarkResult(
    val cleanRuns: List<Omr100RunResult>,
    val stressRuns: List<Omr100RunResult>,
    val medianTiming: OmrStageTiming,
    val allPassed: Boolean
) {
    val passedRuns: Int get() = (cleanRuns + stressRuns).count { it.passed }
    val totalRuns: Int get() = cleanRuns.size + stressRuns.size
}
