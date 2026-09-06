package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.TemplatePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerFreeEditingStage8Test {
    @Test
    fun `schema seven preserves component label alignment and booklet label`() {
        val document = DesignerPageGeometry.apply(
            DesignerDocument(
                id = "alignment",
                version = 1,
                name = "Alignment",
                components = listOf(
                    NumericGridComponent(
                        id = "number",
                        digits = 6,
                        startX = 150.0,
                        topY = 300.0,
                        bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
                        columnGap = 24.0,
                        rowGap = 24.0,
                        label = "Öğrenci No",
                        labelAlignment = DesignerTextAlignment.CENTER
                    ),
                    SingleChoiceComponent(
                        id = "booklet",
                        choices = listOf("A", "B", "C", "D"),
                        start = TemplatePoint(200.0, 650.0),
                        bubbleRadius = DesignerEditorLayout.STANDARD_BUBBLE_RADIUS,
                        gap = DesignerEditorLayout.BOOKLET_GAP,
                        label = "Kitapçık Türü",
                        labelAlignment = DesignerTextAlignment.END
                    )
                )
            )
        )
        val decoded = DesignerDocumentCodec.decode(DesignerDocumentCodec.encode(document))
        assertEquals(document, decoded)
        val number = decoded.components[0] as NumericGridComponent
        val booklet = decoded.components[1] as SingleChoiceComponent
        assertEquals(DesignerTextAlignment.CENTER, number.labelAlignment)
        assertEquals("Kitapçık Türü", booklet.label)
        assertEquals(DesignerTextAlignment.END, booklet.labelAlignment)
    }

    @Test
    fun `duplicated answer gets a unique recognition prefix`() {
        val base = DesignerPageGeometry.apply(DesignerDocument("duplicate", 1, "Duplicate"))
        val answer = DesignerAreaCatalog.createAnswerArea(base)
        val document = base.copy(components = listOf(answer))
        val duplicated = DesignerDocumentEditor.duplicateComponent(document, answer.id, "answers-copy", 5.0, 5.0)
        val groups = duplicated.components.filterIsInstance<QuestionGroupComponent>()
        assertEquals(2, groups.size)
        assertNotEquals(groups[0].questionIdPrefix, groups[1].questionIdPrefix)
        val compiled = DesignerTemplateCompiler.compile(duplicated)
        assertEquals(compiled.bubbleRows.size, compiled.bubbleRows.map { it.id }.toSet().size)
    }

    @Test
    fun `millimeter conversion produces finer grid than legacy 50 canonical units`() {
        val document = DesignerPageGeometry.apply(DesignerDocument("grid", 1, "Grid"))
        val minor = DesignerEditorLayout.canonicalForMillimeters(document, DesignerEditorLayout.GRID_MINOR_MM)
        val snap = DesignerEditorLayout.canonicalForMillimeters(document, DesignerEditorLayout.DRAG_SNAP_MM)
        assertTrue(minor < 50.0)
        assertTrue(snap < minor)
    }
}
