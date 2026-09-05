package com.okulyonetim.optikokuyucu.omr.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredFormDesignTest {
    @Test
    fun `default structured form builds a safe multi lesson template`() {
        val result = StructuredFormDocumentFactory.build(StructuredFormConfig())
        val template = DesignerTemplateCompiler.compile(result.document)
        val report = TemplateReadabilityAnalyzer.analyze(result.document, template)

        assertTrue(report.canSave)
        assertEquals(60, template.bubbleRows.size)
        assertEquals(2, template.markGrids.size)
        assertTrue(template.bubbleRows.any { it.id == "turkce:1" })
        assertTrue(template.bubbleRows.any { it.id == "matematik:1" })
        assertTrue(template.bubbleRows.any { it.id == "fen:20" })
        assertTrue(result.protectedZones.isNotEmpty())
    }

    @Test
    fun `student number can use twelve horizontal value bubbles per digit row`() {
        val result = StructuredFormDocumentFactory.build(
            StructuredFormConfig(
                studentNumberDigits = 8,
                lessons = listOf(StructuredLesson("test", "Test", 20))
            )
        )
        val template = DesignerTemplateCompiler.compile(result.document)
        val grid = template.markGrids.first { it.id == "studentNumber" }

        assertEquals(8, grid.columns.size)
        assertEquals(10, grid.columns.first().marks.size)
        assertTrue(grid.columns.first().marks[1].center.x > grid.columns.first().marks[0].center.x)
        assertTrue(grid.columns[1].marks[0].center.y > grid.columns[0].marks[0].center.y)
    }

    @Test
    fun `landscape lgs style form keeps lesson question numbers locally starting at one`() {
        val lessons = listOf(
            StructuredLesson("turkce", "Türkçe", 20),
            StructuredLesson("inkilap", "İnkılap Tarihi", 10),
            StructuredLesson("din", "Din Kültürü", 10),
            StructuredLesson("yabanci", "Yabancı Dil", 10),
            StructuredLesson("matematik", "Matematik", 20),
            StructuredLesson("fen", "Fen Bilimleri", 20)
        )
        val result = StructuredFormDocumentFactory.build(
            StructuredFormConfig(
                name = "LGS Mini",
                title = "LGS Mini",
                orientation = StructuredOrientation.LANDSCAPE,
                bookletTypeCount = 4,
                studentNumberDigits = 4,
                lessons = lessons
            )
        )
        val template = DesignerTemplateCompiler.compile(result.document)

        assertTrue(result.document.space.width > result.document.space.height)
        assertEquals(90, template.bubbleRows.size)
        assertTrue(template.bubbleRows.any { it.id == "turkce:1" })
        assertTrue(template.bubbleRows.any { it.id == "fen:1" })
        assertTrue(template.bubbleRows.any { it.id == "fen:20" })
        assertTrue(TemplateReadabilityAnalyzer.analyze(result.document, template).canSave)
    }
}
