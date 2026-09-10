package com.okulyonetim.optikokuyucu.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * On-device Latin OCR. Turkish is handled by the Latin model. Element and symbol geometry,
 * confidence and angle are preserved so answer-key tables can be reconstructed reliably.
 *
 * Handwriting mode and answer-key mode may run an optimized second pass. No document is uploaded.
 */
object OcrTextRecognizer {
    fun recognize(
        context: Context,
        uri: Uri,
        handwritingMode: Boolean,
        answerKeyMode: Boolean = !handwritingMode,
        onResult: (Result<OcrRecognitionResult>) -> Unit
    ) {
        val appContext = context.applicationContext
        val worker = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        fun finish(result: Result<OcrRecognitionResult>) {
            runCatching { recognizer.close() }
            worker.shutdown()
            main.post { onResult(result) }
        }

        worker.execute {
            runCatching { InputImage.fromFilePath(appContext, uri) }
                .onFailure { finish(Result.failure(it)) }
                .onSuccess { originalImage ->
                    recognizer.process(originalImage)
                        .addOnSuccessListener(worker) { recognized ->
                            val original = toResult(
                                text = recognized,
                                imageWidth = originalImage.width,
                                imageHeight = originalImage.height,
                                enhancedForHandwriting = false
                            )
                            if (!handwritingMode && !answerKeyMode) {
                                finish(Result.success(original))
                                return@addOnSuccessListener
                            }

                            val enhancedBitmapResult = runCatching {
                                if (answerKeyMode) enhanceForAnswerKey(appContext, uri)
                                else enhanceForHandwriting(appContext, uri)
                            }
                            enhancedBitmapResult
                                .onFailure { finish(Result.success(original)) }
                                .onSuccess { enhancedBitmap ->
                                    val enhancedImage = InputImage.fromBitmap(enhancedBitmap, 0)
                                    recognizer.process(enhancedImage)
                                        .addOnSuccessListener(worker) { enhancedText ->
                                            val enhanced = toResult(
                                                text = enhancedText,
                                                imageWidth = enhancedBitmap.width,
                                                imageHeight = enhancedBitmap.height,
                                                enhancedForHandwriting = handwritingMode
                                            )
                                            enhancedBitmap.recycle()
                                            val originalScore = if (answerKeyMode) answerKeyQualityScore(original) else qualityScore(original)
                                            val enhancedScore = if (answerKeyMode) answerKeyQualityScore(enhanced) else qualityScore(enhanced)
                                            val best = if (enhancedScore > originalScore) enhanced else original
                                            finish(Result.success(best))
                                        }
                                        .addOnFailureListener(worker) {
                                            enhancedBitmap.recycle()
                                            finish(Result.success(original))
                                        }
                                }
                        }
                        .addOnFailureListener(worker) { finish(Result.failure(it)) }
                }
        }
    }

    private fun toResult(
        text: Text,
        imageWidth: Int,
        imageHeight: Int,
        enhancedForHandwriting: Boolean
    ): OcrRecognitionResult {
        val tokens = buildList {
            text.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    line.elements.forEach { element ->
                        val box = element.boundingBox ?: return@forEach
                        val value = element.text.trim()
                        if (value.isNotEmpty()) {
                            val symbols = element.symbols.mapNotNull { symbol ->
                                val symbolBox = symbol.boundingBox ?: return@mapNotNull null
                                OcrSymbol(
                                    text = symbol.text.trim(),
                                    left = symbolBox.left,
                                    top = symbolBox.top,
                                    right = symbolBox.right,
                                    bottom = symbolBox.bottom,
                                    confidence = symbol.confidence,
                                    angle = symbol.angle,
                                    cornerPoints = symbol.cornerPoints.orEmpty().map { point -> OcrPoint(point.x, point.y) }
                                )
                            }
                            add(
                                OcrToken(
                                    text = value,
                                    left = box.left,
                                    top = box.top,
                                    right = box.right,
                                    bottom = box.bottom,
                                    confidence = element.confidence,
                                    angle = element.angle,
                                    cornerPoints = element.cornerPoints.orEmpty().map { point -> OcrPoint(point.x, point.y) },
                                    symbols = symbols
                                )
                            )
                        }
                    }
                }
            }
        }
        return OcrRecognitionResult(
            text = text.text,
            tokens = tokens,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            enhancedForHandwriting = enhancedForHandwriting
        )
    }

    private fun qualityScore(result: OcrRecognitionResult): Int {
        val usefulChars = result.text.count { it.isLetterOrDigit() }
        val longTokens = result.tokens.count { token -> token.text.count(Char::isLetterOrDigit) >= 2 }
        val suspicious = result.text.count { it == '\uFFFD' }
        val confidentTokens = result.tokens.count { (it.confidence ?: 0f) >= 0.70f }
        return usefulChars + (longTokens * 3) + (confidentTokens * 2) - (suspicious * 12)
    }

    /** Prefer a pass that actually recovers tiny number/answer cells with usable confidence. */
    private fun answerKeyQualityScore(result: OcrRecognitionResult): Int {
        var score = qualityScore(result)
        result.tokens.forEach { token ->
            val cleaned = token.text.trim().replace(Regex("[^\\p{L}0-9]"), "")
            val upper = cleaned.uppercase(TURKISH)
            val confidenceBonus = ((token.confidence ?: 0.45f) * 10f).toInt()
            when {
                upper in ANSWER_CHOICES -> score += 24 + confidenceBonus
                cleaned.all(Char::isDigit) && cleaned.toIntOrNull() in 1..99 -> score += 12 + confidenceBonus
                upper.length >= 3 && SUBJECT_HINTS.any { upper.contains(it) } -> score += 8 + confidenceBonus
            }
            token.symbols.forEach { symbol ->
                val symbolText = symbol.text.trim().uppercase(TURKISH)
                if (symbolText in ANSWER_CHOICES && (symbol.confidence ?: 0f) >= 0.50f) {
                    score += 8 + (((symbol.confidence ?: 0f) * 10f).toInt())
                }
            }
        }
        return score
    }

    @Suppress("DEPRECATION")
    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            requireNotNull(MediaStore.Images.Media.getBitmap(context.contentResolver, uri)) {
                "Görsel açılamadı."
            }
        }
    }

    private fun enhanceForHandwriting(context: Context, uri: Uri): Bitmap {
        return enhanceBitmap(
            context = context,
            uri = uri,
            targetLargest = 2600,
            maxScale = 2f,
            contrast = 1.28f,
            brightness = 10f
        )
    }

    private fun enhanceForAnswerKey(context: Context, uri: Uri): Bitmap {
        return enhanceBitmap(
            context = context,
            uri = uri,
            targetLargest = 3400,
            maxScale = 2.8f,
            contrast = 1.48f,
            brightness = 18f
        )
    }

    private fun enhanceBitmap(
        context: Context,
        uri: Uri,
        targetLargest: Int,
        maxScale: Float,
        contrast: Float,
        brightness: Float
    ): Bitmap {
        val source = decodeBitmap(context, uri)
        val largest = max(source.width, source.height).coerceAtLeast(1)
        val scale = (targetLargest.toFloat() / largest.toFloat()).coerceIn(1f, maxScale)
        val scaled = if (scale > 1.02f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { if (it !== source) source.recycle() }
        } else {
            source
        }

        val output = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val translate = (-0.5f * 255f * (contrast - 1f)) + brightness
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        Canvas(output).drawBitmap(
            scaled,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        )
        scaled.recycle()
        return output
    }

    private val TURKISH = Locale("tr", "TR")
    private val ANSWER_CHOICES = setOf("A", "B", "C", "D", "E")
    private val SUBJECT_HINTS = setOf("TÜRK", "TURK", "MAT", "FEN", "DİN", "DIN", "İNG", "ING", "INK")
}
