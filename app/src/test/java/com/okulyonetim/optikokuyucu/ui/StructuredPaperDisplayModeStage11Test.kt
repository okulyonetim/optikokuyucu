package com.okulyonetim.optikokuyucu.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredPaperDisplayModeStage11Test {
    @Test
    fun `edit mode keeps editor chrome and direct manipulation`() {
        val mode = StructuredPaperDisplayMode.EDIT

        assertTrue(mode.editorChromeVisible)
        assertTrue(mode.directEditingEnabled)
        assertFalse(mode.usesPrintInk)
        assertFalse(mode.viewportNavigationAlwaysEnabled)
    }

    @Test
    fun `preview mode is print only and cannot edit geometry`() {
        val mode = StructuredPaperDisplayMode.PREVIEW

        assertFalse(mode.editorChromeVisible)
        assertFalse(mode.directEditingEnabled)
        assertTrue(mode.usesPrintInk)
        assertTrue(mode.viewportNavigationAlwaysEnabled)
    }
}
