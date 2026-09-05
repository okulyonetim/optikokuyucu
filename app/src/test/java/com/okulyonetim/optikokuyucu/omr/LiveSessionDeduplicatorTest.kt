package com.okulyonetim.optikokuyucu.omr

import com.okulyonetim.optikokuyucu.omr.live.LiveScanFingerprint
import com.okulyonetim.optikokuyucu.omr.live.LiveSessionDeduplicator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSessionDeduplicatorTest {
    @Test
    fun `same student and same answers are rejected on second registration`() {
        val deduplicator = LiveSessionDeduplicator()
        val fingerprint = requireNotNull(
            LiveScanFingerprint.build("form", 1, "407215", "1:A|2:B")
        )

        assertTrue(deduplicator.registerIfNew(fingerprint))
        assertFalse(deduplicator.registerIfNew(fingerprint))
    }

    @Test
    fun `same student with changed answers is accepted as a new fingerprint`() {
        val deduplicator = LiveSessionDeduplicator()
        val first = requireNotNull(LiveScanFingerprint.build("form", 1, "407215", "1:A|2:B"))
        val corrected = requireNotNull(LiveScanFingerprint.build("form", 1, "407215", "1:A|2:C"))

        assertTrue(deduplicator.registerIfNew(first))
        assertTrue(deduplicator.registerIfNew(corrected))
    }

    @Test
    fun `no student identity does not create unsafe answer-only fingerprint`() {
        assertNull(LiveScanFingerprint.build("form", 1, null, "1:A|2:B"))
        assertNull(LiveScanFingerprint.build("form", 1, "   ", "1:A|2:B"))
    }
}
