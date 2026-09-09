package com.okulyonetim.optikokuyucu.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * On-device Latin OCR. Turkish is handled by the Latin model and all coordinates are preserved
 * so table-like answer keys can be reconstructed instead of flattening everything into plain text.
 *
 * Handwriting mode runs a second, contrast-enhanced pass and keeps the stronger recognition result.
 * It intentionally remains a best-effort image OCR path; no document is uploaded to a server.
 */
object OcrTextRecognizer {
    fun recognize(
        context: Context,
        uri: Uri,
        handwritingMode: Boolean,
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
                                enhanced = false
                            )
                            if (!handwritingMode) {
                                finish(Result.success(original))
                                return@addOnSuccessListener
                            }

                            runCatching { enhanceForHandwriting(appContext, uri) }
                                .onFailure { finish(Result.success(original)) }
                                .onSuccess { enhancedBitmap ->
                                    val enhancedImage = InputImage.fromBitmap(enhancedBitmap, 0)
                                    recognizer.process(enhancedImage)
                                        .addOnSuccessListener(worker) { enhancedText ->
                                            val enhanced = toResult(
                                                text = enhancedText,
                                                imageWidth = enhancedBitmap.width,
                                                imageHeight = enhancedBitmap.height,
                                                enhanced = true
                                            )
                                            enhancedBitmap.recycle()
                                            val best = if (qualityScore(enhanced) > qualityScore(original)) enhanced else original
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
        enhanced: Boolean
    ): OcrRecognitionResult {
        val tokens = buildList {
            text.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    line.elements.forEach { element ->
                        val box = element.boundingBox ?: return@forEach
                        val value = element.text.trim()
                        if (value.isNotEmpty()) {
                            add(
                                OcrToken(
                                    text = value,
                                    left = box.left,
                                    top = box.top,
                                    right = box.right,
                                    bottom = box.bottom
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
            enhancedForHandwriting = enhanced
        )
    }

    private fun qualityScore(result: OcrRecognitionResult): Int {
        val usefulChars = result.text.count { it.isLetterOrDigit() }
        val longTokens = result.tokens.count { token -> token.text.count(Char::isLetterOrDigit) >= 2 }
        val suspicious = result.text.count { it == '\uFFFD' }
        return usefulChars + (longTokens * 3) - (suspicious * 12)
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
        val source = decodeBitmap(context, uri)
        val largest = max(source.width, source.height).coerceAtLeast(1)
        val targetLargest = min(2600, max(1800, largest))
        val scale = (targetLargest.toFloat() / largest.toFloat()).coerceIn(1f, 2f)
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
        val contrast = 1.28f
        val translate = (-0.5f * 255f * (contrast - 1f)) + 10f
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
}
