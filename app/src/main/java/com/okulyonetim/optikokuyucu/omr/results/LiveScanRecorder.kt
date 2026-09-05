package com.okulyonetim.optikokuyucu.omr.results

import com.okulyonetim.optikokuyucu.camera.LiveOmrReadResult
import com.okulyonetim.optikokuyucu.omr.template.OmrTemplate

/** Small boundary adapter used only after live consensus has accepted a sheet. */
class LiveScanRecorder(
    private val repository: ScanRecordRepository
) {
    fun record(
        template: OmrTemplate,
        result: LiveOmrReadResult,
        id: String? = null,
        capturedAtEpochMs: Long? = null
    ): ScanRecord {
        val record = ScanRecordFactory.fromRecognition(
            templateId = template.id,
            templateVersion = template.version,
            source = ScanSource.LIVE_CAMERA,
            sourceWidth = result.sourceWidth,
            sourceHeight = result.sourceHeight,
            elapsedMs = result.elapsedMs,
            bubbleResult = result.bubbleResult,
            markGridResult = result.markGridResult,
            pageConfidence = result.pageConfidence,
            decisionConfidence = result.decisionConfidence,
            id = id ?: java.util.UUID.randomUUID().toString(),
            capturedAtEpochMs = capturedAtEpochMs ?: System.currentTimeMillis()
        )
        repository.save(record)
        return record
    }
}
