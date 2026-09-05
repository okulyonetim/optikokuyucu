package com.okulyonetim.optikokuyucu.omr.results

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanImageCodecTest {
    @Test
    fun roundTripsCanonicalLumaWithoutChangingPixels() {
        val luma = ByteArray(80 * 120) { index -> (index % 251).toByte() }
        val original = StoredScanImage(
            scanRecordId = "scan-42",
            width = 80,
            height = 120,
            luma = luma
        )

        val encoded = ScanImageCodec.encode(original)
        val decoded = ScanImageCodec.decode(encoded)

        assertEquals(original.scanRecordId, decoded.scanRecordId)
        assertEquals(original.width, decoded.width)
        assertEquals(original.height, decoded.height)
        assertArrayEquals(original.luma, decoded.luma)
        assertTrue(encoded.size < original.luma.size + 512)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPixelCountThatDoesNotMatchDimensions() {
        StoredScanImage(
            scanRecordId = "scan-bad",
            width = 20,
            height = 30,
            luma = ByteArray(599)
        )
    }

    @Test
    fun rejectsTruncatedCompressedData() {
        val encoded = ScanImageCodec.encode(
            StoredScanImage(
                scanRecordId = "scan-truncated",
                width = 32,
                height = 24,
                luma = ByteArray(32 * 24) { 127.toByte() }
            )
        )
        val truncated = encoded.copyOf(encoded.size / 2)

        assertTrue(runCatching { ScanImageCodec.decode(truncated) }.isFailure)
    }
}
