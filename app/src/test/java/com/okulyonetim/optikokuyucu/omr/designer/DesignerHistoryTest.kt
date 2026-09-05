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

    @Test
    fun `many transaction previews collapse into one undo step`() {
        val history = DesignerHistory("A")

        history.beginTransaction()
        history.updateTransaction("B")
        history.updateTransaction("C")
        history.updateTransaction("D")
        history.endTransaction()

        assertEquals("D", history.current())
        assertTrue(history.canUndo())
        assertEquals("A", history.undo())
        assertFalse(history.canUndo())
        assertEquals("D", history.redo())
    }

    @Test
    fun `cancel transaction restores gesture start without history entry`() {
        val history = DesignerHistory("A")

        history.beginTransaction()
        history.updateTransaction("B")
        history.updateTransaction("C")
        history.cancelTransaction()

        assertEquals("A", history.current())
        assertFalse(history.canUndo())
        assertFalse(history.canRedo())
    }
}
