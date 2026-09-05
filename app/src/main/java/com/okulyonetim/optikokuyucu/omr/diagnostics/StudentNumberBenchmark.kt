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

/** Phase-4 closed-loop benchmark for question answers + a generic six-digit student-number grid. */
object StudentNumberBenchmark {
    private val template = StandardOmrTemplate.SAMPLE_20_ABCD_STUDENT_6
    const val EXPECTED_STUDENT_NUMBER = "407215"

    private val answerTruth: Map<String, Set<String>> = (1..20).associate { question ->
        question.toString() to setOf(listOf("A", "B", "C", "D")[(question - 1) % 4])
    }

    private val studentTruth: Map<String, Map<String, Set<String>>> = mapOf(
        "studentNumber" to EXPECTED_STUDENT_NUMBER.mapIndexed { index, digit ->
            (index + 1).toString() to setOf(digit.toString())
        }.toMap()
    )

    fun run(): StudentNumberBenchmarkResult {
        val source = SyntheticOmrRenderer.render(
            template = template,
            markedChoicesByRow = answerTruth,
            markedGridChoices = studentTruth
        )
        return try {
            GalleryOmrReader.readBitmap(source, template).also { it.bitmap.recycle() }

            val scaled = scaledWithMargin(source, 0.72f)
            val perspective = perspectiveWarp(source)
            val jpeg = jpegRoundTrip(source, 64)
            try {
                val runs = listOf(
                    evaluate("Temel", source),
                    evaluate("Küçültme", scaled),
                    evaluate("Perspektif", perspective),
                    evaluate("JPEG %64", jpeg)
                )
                StudentNumberBenchmarkResult(
                    expectedStudentNumber = EXPECTED_STUDENT_NUMBER,
                    runs = runs,
                    allPassed = runs.all { it.passed }
                )
            } finally {
                scaled.recycle()
                perspective.recycle()
                jpeg.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun evaluate(name: String, bitmap: Bitmap): StudentNumberRunResult {
        val result = GalleryOmrReader.readBitmap(bitmap, template)
        return try {
            val studentGrid = result.markGridResult.grid("studentNumber")
            val readStudentNumber = studentGrid?.value
            val correctAnswers = result.bubbleResult.questions.count { question ->
                val expected = answerTruth[question.questionId]?.singleOrNull()
                question.state == QuestionState.MARKED && question.selectedChoice == expected
            }

            StudentNumberRunResult(
                name = name,
                markerCount = result.markerCount,
                correctAnswers = correctAnswers,
                totalAnswers = answerTruth.size,
                studentNumber = readStudentNumber,
                blankDigitCount = studentGrid?.blankCount ?: template.markGrids.single().columns.size,
                suspiciousDigitCount = studentGrid?.suspiciousCount ?: template.markGrids.single().columns.size,
                markGridMs = result.markGridMs,
                totalMs = result.elapsedMs,
                passed = result.markerCount == 4 &&
                    result.rectificationReady &&
                    correctAnswers == answerTruth.size &&
                    readStudentNumber == EXPECTED_STUDENT_NUMBER
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
            postTranslate(180f, 165f)
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
            170f, 120f,
            1040f, 210f,
            1120f, 1580f,
            95f, 1470f
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
}

data class StudentNumberRunResult(
    val name: String,
    val markerCount: Int,
    val correctAnswers: Int,
    val totalAnswers: Int,
    val studentNumber: String?,
    val blankDigitCount: Int,
    val suspiciousDigitCount: Int,
    val markGridMs: Double,
    val totalMs: Double,
    val passed: Boolean
)

data class StudentNumberBenchmarkResult(
    val expectedStudentNumber: String,
    val runs: List<StudentNumberRunResult>,
    val allPassed: Boolean
) {
    val passedRuns: Int get() = runs.count { it.passed }
}
