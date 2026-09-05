package com.okulyonetim.optikokuyucu.exam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamGalleryBatchProgressTest {
    @Test
    fun tracksImportedFailedAndRemainingItems() {
        var progress = ExamGalleryBatchProgress.start(4)

        progress = progress.onImported()
        progress = progress.onFailed()
        progress = progress.onImported()

        assertEquals(4, progress.total)
        assertEquals(3, progress.processed)
        assertEquals(2, progress.imported)
        assertEquals(1, progress.failed)
        assertEquals(1, progress.remaining)

        progress = progress.onImported()
        assertTrue(progress.completed)
        assertEquals(0, progress.remaining)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyBatch() {
        ExamGalleryBatchProgress.start(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsProgressWhoseCountersDoNotMatchProcessedCount() {
        ExamGalleryBatchProgress(total = 3, processed = 2, imported = 2, failed = 1)
    }
}
