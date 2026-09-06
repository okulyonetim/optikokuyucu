package com.okulyonetim.optikokuyucu.omr.results

import org.junit.Assert.assertThrows
import org.junit.Test

class ScanRecordImmutabilityStage12Test {
    private fun record(
        version: Int = 1,
        decisionConfidence: Double = 0.80
    ): ScanRecord = ScanRecord(
        id = "scan-stage12",
        templateId = "form-stage12",
        templateVersion = version,
        capturedAtEpochMs = 1L,
        source = ScanSource.LIVE_CAMERA,
        sourceWidth = 100,
        sourceHeight = 200,
        pageConfidence = 0.90,
        decisionConfidence = decisionConfidence,
        elapsedMs = 10.0,
        answers = emptyList(),
        markGrids = emptyList()
    )

    @Test
    fun `identical raw scan is idempotent`() {
        val original = record()

        ScanRecordImmutabilityPolicy.validateExisting(original, original.copy())
    }

    @Test
    fun `same scan id with changed raw content is rejected`() {
        val original = record()
        val changed = record(decisionConfidence = 0.70)

        assertThrows(IllegalArgumentException::class.java) {
            ScanRecordImmutabilityPolicy.validateExisting(original, changed)
        }
    }

    @Test
    fun `same scan id cannot move to another template version`() {
        val original = record(version = 1)
        val changed = record(version = 2)

        assertThrows(IllegalArgumentException::class.java) {
            ScanRecordImmutabilityPolicy.validateExisting(original, changed)
        }
    }
}
