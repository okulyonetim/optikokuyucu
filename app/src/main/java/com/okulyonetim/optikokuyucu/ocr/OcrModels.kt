package com.okulyonetim.optikokuyucu.ocr

data class OcrToken(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

data class OcrRecognitionResult(
    val text: String,
    val tokens: List<OcrToken>,
    val imageWidth: Int,
    val imageHeight: Int,
    val enhancedForHandwriting: Boolean = false
)

data class OcrAnswerRow(
    val sectionId: String,
    val subject: String,
    val questionId: String,
    val questionNumber: Int,
    val answer: String?
)

data class OcrAnswerKeyExtraction(
    val rows: List<OcrAnswerRow>,
    val answers: Map<String, String>,
    val detectedBooklet: String?,
    val warnings: List<String>
)
