package com.okulyonetim.optikokuyucu.ocr

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Combines multiple OCR passes without losing the corrected document coordinate system.
 * It also reconstructs plain text from the final positioned tokens when the user explicitly
 * asks to extract text from the document preview.
 */
object OcrRecognitionPostProcessor {
    fun mergePasses(
        primary: OcrRecognitionResult,
        secondary: OcrRecognitionResult
    ): OcrRecognitionResult {
        if (primary.imageWidth <= 0 || primary.imageHeight <= 0) return secondary
        if (secondary.imageWidth <= 0 || secondary.imageHeight <= 0) return primary

        val normalizedSecondary = normalizeTo(
            result = secondary,
            targetWidth = primary.imageWidth,
            targetHeight = primary.imageHeight,
            sourceUri = primary.pages.firstOrNull()?.sourceUri
                ?: secondary.pages.firstOrNull()?.sourceUri
                ?: ""
        )
        val mergedTokens = mergeTokens(primary.tokens, normalizedSecondary.tokens)
        val sourceUri = primary.pages.firstOrNull()?.sourceUri
            ?: normalizedSecondary.pages.firstOrNull()?.sourceUri
            ?: ""
        val pages = if (sourceUri.isBlank()) {
            emptyList()
        } else {
            listOf(
                OcrPageLayout(
                    sourceUri = sourceUri,
                    imageWidth = primary.imageWidth,
                    imageHeight = primary.imageHeight,
                    tokens = mergedTokens
                )
            )
        }
        val provisional = OcrRecognitionResult(
            text = "",
            tokens = mergedTokens,
            imageWidth = primary.imageWidth,
            imageHeight = primary.imageHeight,
            enhancedForHandwriting = primary.enhancedForHandwriting || secondary.enhancedForHandwriting,
            pages = pages
        )
        return provisional.copy(text = extractPlainText(provisional))
    }

    fun extractPlainText(recognition: OcrRecognitionResult): String {
        val pageTexts = if (recognition.pages.isNotEmpty()) {
            recognition.pages.map { page -> positionedText(page.tokens) }
        } else {
            listOf(positionedText(recognition.tokens))
        }
        return pageTexts.filter(String::isNotBlank).joinToString("\n\n")
    }

    private fun positionedText(tokens: List<OcrToken>): String {
        val useful = tokens
            .filter { token -> token.text.isNotBlank() && token.width > 0 && token.height > 0 }
            .sortedWith(compareBy<OcrToken> { it.centerY }.thenBy { it.left })
        if (useful.isEmpty()) return ""

        data class Line(val tokens: MutableList<OcrToken>, var centerY: Float, var averageHeight: Float)

        val lines = mutableListOf<Line>()
        useful.forEach { token ->
            val line = lines
                .filter { candidate ->
                    val tolerance = max(5f, max(candidate.averageHeight, token.height.toFloat()) * 0.58f)
                    abs(candidate.centerY - token.centerY) <= tolerance
                }
                .minByOrNull { candidate -> abs(candidate.centerY - token.centerY) }

            if (line == null) {
                lines += Line(mutableListOf(token), token.centerY, token.height.toFloat())
            } else {
                line.tokens += token
                val count = line.tokens.size.toFloat()
                line.centerY = ((line.centerY * (count - 1f)) + token.centerY) / count
                line.averageHeight = ((line.averageHeight * (count - 1f)) + token.height) / count
            }
        }

        val ordered = lines.sortedBy { it.centerY }
        val globalHeight = useful.map { it.height }.average().toFloat().coerceAtLeast(1f)
        return buildString {
            ordered.forEachIndexed { index, line ->
                if (index > 0) {
                    val previous = ordered[index - 1]
                    val gap = line.centerY - previous.centerY
                    append(if (gap > globalHeight * 2.25f) "\n\n" else "\n")
                }
                append(
                    line.tokens
                        .sortedBy { it.left }
                        .joinToString(" ") { it.text.trim() }
                )
            }
        }.trim()
    }

    private fun normalizeTo(
        result: OcrRecognitionResult,
        targetWidth: Int,
        targetHeight: Int,
        sourceUri: String
    ): OcrRecognitionResult {
        if (result.imageWidth == targetWidth && result.imageHeight == targetHeight) {
            val page = OcrPageLayout(sourceUri, targetWidth, targetHeight, result.tokens)
            return result.copy(pages = if (sourceUri.isBlank()) emptyList() else listOf(page))
        }
        val sx = targetWidth.toFloat() / result.imageWidth.coerceAtLeast(1).toFloat()
        val sy = targetHeight.toFloat() / result.imageHeight.coerceAtLeast(1).toFloat()
        val tokens = result.tokens.map { token -> scaleToken(token, sx, sy) }
        return result.copy(
            tokens = tokens,
            imageWidth = targetWidth,
            imageHeight = targetHeight,
            pages = if (sourceUri.isBlank()) emptyList() else listOf(OcrPageLayout(sourceUri, targetWidth, targetHeight, tokens))
        )
    }

    private fun mergeTokens(primary: List<OcrToken>, secondary: List<OcrToken>): List<OcrToken> {
        val merged = primary.toMutableList()
        secondary
            .filter(::isUsefulSecondaryToken)
            .forEach { candidate ->
                val sameIndex = merged.indices
                    .filter { index -> sameRegion(merged[index], candidate) }
                    .maxByOrNull { index -> overlapRatio(merged[index], candidate) }

                if (sameIndex != null) {
                    if (tokenScore(candidate) > tokenScore(merged[sameIndex])) merged[sameIndex] = candidate
                    return@forEach
                }

                val coveredByLarger = merged.any { existing ->
                    contains(existing, candidate) &&
                        existing.text.trim().length >= candidate.text.trim().length * 2 &&
                        (existing.confidence ?: 0.55f) >= (candidate.confidence ?: 0.55f) - 0.12f
                }
                if (!coveredByLarger) merged += candidate
            }
        return merged.sortedWith(compareBy<OcrToken> { it.top }.thenBy { it.left })
    }

    private fun isUsefulSecondaryToken(token: OcrToken): Boolean {
        val text = token.text.trim()
        if (text.isBlank() || text.none { it.isLetterOrDigit() }) return false
        return token.confidence == null || token.confidence >= 0.28f
    }

    private fun sameRegion(a: OcrToken, b: OcrToken): Boolean {
        val overlap = overlapRatio(a, b)
        if (overlap < 0.52f) return false
        val widthSimilarity = min(a.width, b.width).toFloat() / max(a.width, b.width).toFloat()
        val heightSimilarity = min(a.height, b.height).toFloat() / max(a.height, b.height).toFloat()
        if (widthSimilarity < 0.42f || heightSimilarity < 0.48f) return false
        val xTolerance = max(5f, min(a.width, b.width) * 0.70f)
        val yTolerance = max(5f, max(a.height, b.height) * 0.62f)
        return abs(a.centerX - b.centerX) <= xTolerance && abs(a.centerY - b.centerY) <= yTolerance
    }

    private fun tokenScore(token: OcrToken): Float {
        val confidence = token.confidence ?: 0.52f
        val usefulChars = token.text.count { it.isLetterOrDigit() }.coerceAtMost(12)
        val symbolConfidence = token.symbols.mapNotNull { it.confidence }.averageOrNull()?.toFloat() ?: confidence
        return (confidence * 10f) + (symbolConfidence * 2f) + usefulChars * 0.12f
    }

    private fun overlapRatio(a: OcrToken, b: OcrToken): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val intersection = (right - left).toFloat() * (bottom - top).toFloat()
        val smaller = min(a.width.toFloat() * a.height, b.width.toFloat() * b.height).coerceAtLeast(1f)
        return intersection / smaller
    }

    private fun contains(outer: OcrToken, inner: OcrToken): Boolean {
        val left = max(outer.left, inner.left)
        val top = max(outer.top, inner.top)
        val right = min(outer.right, inner.right)
        val bottom = min(outer.bottom, inner.bottom)
        if (right <= left || bottom <= top) return false
        val intersection = (right - left).toFloat() * (bottom - top).toFloat()
        val innerArea = inner.width.toFloat() * inner.height.toFloat()
        return intersection / innerArea.coerceAtLeast(1f) >= 0.86f
    }

    private fun scaleToken(token: OcrToken, sx: Float, sy: Float): OcrToken = token.copy(
        left = (token.left * sx).roundToInt(),
        top = (token.top * sy).roundToInt(),
        right = (token.right * sx).roundToInt(),
        bottom = (token.bottom * sy).roundToInt(),
        cornerPoints = token.cornerPoints.map { point ->
            OcrPoint((point.x * sx).roundToInt(), (point.y * sy).roundToInt())
        },
        symbols = token.symbols.map { symbol ->
            symbol.copy(
                left = (symbol.left * sx).roundToInt(),
                top = (symbol.top * sy).roundToInt(),
                right = (symbol.right * sx).roundToInt(),
                bottom = (symbol.bottom * sy).roundToInt(),
                cornerPoints = symbol.cornerPoints.map { point ->
                    OcrPoint((point.x * sx).roundToInt(), (point.y * sy).roundToInt())
                }
            )
        }
    )

    private fun List<Float>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
