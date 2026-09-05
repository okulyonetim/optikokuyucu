package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerHistoryTest {
    @Test
    fun `undo and redo traverse immutable designer states`() {
        val history = DesignerHistory("A")

        history.commit("B")
        history.commit("C")

        assertEquals("C", history.current())
        assertEquals("B", history.undo())
        assertEquals("A", history.undo())
        assertTrue(history.canRedo())
        assertEquals("B", history.redo())
    }

    @Test
    fun `new edit after undo clears redo branch`() {
        val history = DesignerHistory("A")
        history.commit("B")
        history.commit("C")
        history.undo()

        history.commit("D")

        assertEquals("D", history.current())
        assertFalse(history.canRedo())
        assertTrue(history.canUndo())
    }
}
