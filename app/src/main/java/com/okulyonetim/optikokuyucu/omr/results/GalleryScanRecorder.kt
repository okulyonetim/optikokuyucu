package com.okulyonetim.optikokuyucu.omr.results

import com.okulyonetim.optikokuyucu.omr.bubble.BubbleReadResult
import com.okulyonetim.optikokuyucu.omr.gallery.GalleryOmrResult
import com.okulyonetim.optikokuyucu.omr.markgrid.MarkGridReadResult
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate
import java.util.UUID

/**
 * Explicit persistence boundary for gallery recognition.
 *
 * Gallery diagnostics must not silently create real student records. Callers invoke this only
 * after the user explicitly chooses to save a successfully rectified recognition result.
 */
class GalleryScanRecorder(
    private val repository: ScanRecordRepository
) {
    fun record(
        template: OmrTemplate,
        result: GalleryOmrResult,
        id: String? = null,
        capturedAtEpochMs: Long? = null
    ): ScanRecord {
        require(result.rectificationReady) {
            "Galeri okuması kaydedilemedi: form canonical olarak hizalanmadı."
        }
        return recordRecognition(
            template = template,
            sourceWidth = result.width,
            sourceHeight = result.height,
            elapsedMs = result.elapsedMs,
            bubbleResult = result.bubbleResult,
            markGridResult = result.markGridResult,
            id = id,
            capturedAtEpochMs = capturedAtEpochMs
        )
    }

    internal fun recordRecognition(
        template: OmrTemplate,
        sourceWidth: Int,
        sourceHeight: Int,
        elapsedMs: Double,
        bubbleResult: BubbleReadResult,
        markGridResult: MarkGridReadResult,
        id: String? = null,
        capturedAtEpochMs: Long? = null
    ): ScanRecord {
        require(bubbleResult.questions.isNotEmpty()) {
            "Galeri okuması kaydedilemedi: soru sonucu bulunamadı."
        }
        val record = ScanRecordFactory.fromRecognition(
            templateId = template.id,
            templateVersion = template.version,
            source = ScanSource.GALLERY,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            elapsedMs = elapsedMs,
            bubbleResult = bubbleResult,
            markGridResult = markGridResult,
            pageConfidence = null,
            decisionConfidence = null,
            id = id ?: UUID.randomUUID().toString(),
            capturedAtEpochMs = capturedAtEpochMs ?: System.currentTimeMillis()
        )
        repository.save(record)
        return record
    }
}
