package com.okulyonetim.optikokuyucu.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionComparatorTest {
    @Test
    fun `newer patch version is detected`() {
        assertTrue(AppVersionComparator.isNewer("0.19.69", "0.19.70"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(AppVersionComparator.isNewer("0.19.70", "v0.19.70"))
    }

    @Test
    fun `older remote version is not newer`() {
        assertFalse(AppVersionComparator.isNewer("0.20.0", "0.19.99"))
    }

    @Test
    fun `missing version segments are normalized`() {
        assertFalse(AppVersionComparator.isNewer("1.2.0", "1.2"))
        assertTrue(AppVersionComparator.isNewer("1.2", "1.2.1"))
    }
}
