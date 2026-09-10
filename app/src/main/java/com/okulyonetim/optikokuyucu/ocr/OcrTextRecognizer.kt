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
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * On-device Latin OCR. Turkish is handled by the Latin model. Every corrected page is read twice:
 * once as-is and once with a high-resolution contrast pass. The passes are merged geometrically so
 * characters found by only one pass are retained without flattening the document layout.
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
                                enhancedForHandwriting = false,
                                sourceUri = uri.toString()
                            )

                            val enhancedBitmapResult = runCatching {
                                when {
                                    answerKeyMode -> enhanceForAnswerKey(appContext, uri)
                                    handwritingMode -> enhanceForHandwriting(appContext, uri)
                                    else -> enhanceForPrintedText(appContext, uri)
                                }
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
                                                enhancedForHandwriting = handwritingMode,
                                                sourceUri = uri.toString()
                                            )
                                            enhancedBitmap.recycle()
                                            finish(
                                                Result.success(
                                                    OcrRecognitionPostProcessor.mergePasses(original, enhanced)
                                                )
                                            )
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

    /** Recognizes scanner pages in order while keeping every page as an independent layout surface. */
    fun recognizePages(
        context: Context,
        uris: List<Uri>,
        handwritingMode: Boolean,
        answerKeyMode: Boolean = false,
        onResult: (Result<OcrRecognitionResult>) -> Unit
    ) {
        val pages = uris.distinct()
        if (pages.isEmpty()) {
            onResult(Result.failure(IllegalArgumentException("Taranan belge sayfası bulunamadı.")))
            return
        }
        if (pages.size == 1) {
            recognize(context, pages.first(), handwritingMode, answerKeyMode, onResult)
            return
        }

        val results = mutableListOf<OcrRecognitionResult>()
        fun readPage(index: Int) {
            if (index >= pages.size) {
                onResult(Result.success(mergePages(results)))
                return
            }
            recognize(
                context = context,
                uri = pages[index],
                handwritingMode = handwritingMode,
                answerKeyMode = answerKeyMode
            ) { result ->
                result.onSuccess {
                    results += it
                    readPage(index + 1)
                }.onFailure { onResult(Result.failure(it)) }
            }
        }
        readPage(0)
    }

    private fun mergePages(results: List<OcrRecognitionResult>): OcrRecognitionResult {
        var yOffset = 0
        val mergedTokens = mutableListOf<OcrToken>()
        val pageLayouts = mutableListOf<OcrPageLayout>()
        results.forEach { page ->
            val ownPage = page.pages.firstOrNull()
            if (ownPage != null) pageLayouts += ownPage
            page.tokens.forEach { token ->
                mergedTokens += token.copy(
                    top = token.top + yOffset,
                    bottom = token.bottom + yOffset,
                    cornerPoints = token.cornerPoints.map { it.copy(y = it.y + yOffset) },
                    symbols = token.symbols.map { symbol ->
                        symbol.copy(
                            top = symbol.top + yOffset,
                            bottom = symbol.bottom + yOffset,
                            cornerPoints = symbol.cornerPoints.map { it.copy(y = it.y + yOffset) }
                        )
                    }
                )
            }
            yOffset += page.imageHeight + PAGE_GAP_PX
        }
        val provisional = OcrRecognitionResult(
            text = "",
            tokens = mergedTokens,
            imageWidth = results.maxOfOrNull { it.imageWidth } ?: 0,
            imageHeight = (yOffset - PAGE_GAP_PX).coerceAtLeast(0),
            enhancedForHandwriting = results.any { it.enhancedForHandwriting },
            pages = pageLayouts
        )
        return provisional.copy(text = OcrRecognitionPostProcessor.extractPlainText(provisional))
    }

    private fun toResult(
        text: Text,
        imageWidth: Int,
        imageHeight: Int,
        enhancedForHandwriting: Boolean,
        sourceUri: String
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
            enhancedForHandwriting = enhancedForHandwriting,
            pages = listOf(OcrPageLayout(sourceUri, imageWidth, imageHeight, tokens))
        )
    }

    @Suppress("DEPRECATION")
    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        } else {
            requireNotNull(MediaStore.Images.Media.getBitmap(context.contentResolver, uri)) { "Görsel açılamadı." }
        }
    }

    private fun enhanceForPrintedText(context: Context, uri: Uri): Bitmap = enhanceBitmap(
        context = context,
        uri = uri,
        targetLargest = 3600,
        maxScale = 3f,
        contrast = 1.30f,
        brightness = 10f
    )

    private fun enhanceForHandwriting(context: Context, uri: Uri): Bitmap = enhanceBitmap(
        context = context,
        uri = uri,
        targetLargest = 3000,
        maxScale = 2.4f,
        contrast = 1.26f,
        brightness = 8f
    )

    private fun enhanceForAnswerKey(context: Context, uri: Uri): Bitmap = enhanceBitmap(
        context = context,
        uri = uri,
        targetLargest = 3800,
        maxScale = 3f,
        contrast = 1.44f,
        brightness = 14f
    )

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
        } else source

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

    private const val PAGE_GAP_PX = 24
}
