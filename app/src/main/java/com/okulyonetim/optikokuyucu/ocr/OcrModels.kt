package com.okulyonetim.optikokuyucu.ocr

data class OcrPoint(
    val x: Int,
    val y: Int
)

data class OcrSymbol(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float? = null,
    val angle: Float = 0f,
    val cornerPoints: List<OcrPoint> = emptyList()
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

data class OcrToken(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float? = null,
    val angle: Float = 0f,
    val cornerPoints: List<OcrPoint> = emptyList(),
    val symbols: List<OcrSymbol> = emptyList()
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Int get() = (right - left).coerceAtLeast(1)
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/** One corrected/scanned page together with OCR coordinates that belong to that exact page. */
data class OcrPageLayout(
    val sourceUri: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val tokens: List<OcrToken>
)

data class OcrRecognitionResult(
    val text: String,
    val tokens: List<OcrToken>,
    val imageWidth: Int,
    val imageHeight: Int,
    val enhancedForHandwriting: Boolean = false,
    val pages: List<OcrPageLayout> = emptyList()
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
