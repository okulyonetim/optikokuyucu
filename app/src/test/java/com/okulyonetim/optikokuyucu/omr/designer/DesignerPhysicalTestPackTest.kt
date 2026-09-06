package com.okulyonetim.optikokuyucu.omr.designer

import com.okulyonetim.optikokuyucu.omr.template.OmrRecognitionBindingsResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignerPhysicalTestPackTest {
    @Test
    fun `physical Turkish test form is one safe canonical designer document`() {
        val document = DesignerPhysicalTestPack.document()
        val template = DesignerTemplateCompiler.compile(document)
        val readability = TemplateReadabilityAnalyzer.analyze(document, template)
        val bindings = OmrRecognitionBindingsResolver.fromTemplate(template)

        assertEquals(DesignerPhysicalTestPack.TEMPLATE_ID, document.id)
        assertEquals(DesignerPhysicalTestPack.TEMPLATE_VERSION, document.version)
        assertEquals(document.id, template.id)
        assertEquals(document.version, template.version)
        assertEquals(20, template.bubbleRows.size)
        assertTrue(readability.issues.joinToString { it.type.name }, readability.canSave)
        assertEquals(DesignerPhysicalTestPack.NUMBER_COMPONENT_ID, bindings.studentNumberGridId)
        assertEquals(DesignerPhysicalTestPack.BOOKLET_COMPONENT_ID, bindings.bookletGridId)

        val number = template.markGrids.single { it.id == DesignerPhysicalTestPack.NUMBER_COMPONENT_ID }
        val booklet = template.markGrids.single { it.id == DesignerPhysicalTestPack.BOOKLET_COMPONENT_ID }
        assertEquals(6, number.columns.size)
        assertTrue(number.columns.all { column -> column.marks.map { it.id } == (0..9).map(Int::toString) })
        assertEquals(listOf("A", "B"), booklet.columns.single().marks.map { it.id })
    }

    @Test
    fun `Turkish text and glyph probe survive designer codec exactly`() {
        val document = DesignerPhysicalTestPack.document()
        val decoded = DesignerDocumentCodec.decode(DesignerDocumentCodec.encode(document))
        val probe = decoded.visualElements.filterIsInstance<DesignerTextElement>().single { it.id == "turkish-font-probe" }

        assertEquals(document, decoded)
        assertEquals("Noto Sans", DesignerTypography.FONT_FAMILY_NAME)
        assertEquals("5e35378e6bda803962ee6fd257e444a7d459660d", DesignerTypography.FONT_SOURCE_COMMIT)
        assertEquals("75575046c015ff623a848096a15779867ba71453", DesignerTypography.FONT_GIT_BLOB_SHA)
        assertTrue(probe.text.contains(DesignerTypography.TURKISH_GLYPH_SAMPLE))
        assertEquals("İ ı Ş ş Ğ ğ Ç ç Ö ö Ü ü", DesignerTypography.TURKISH_GLYPH_SAMPLE)
    }

    @Test
    fun `physical test answer pattern is deterministic`() {
        assertEquals(
            listOf("A", "B", "C", "D", "A", "B", "C", "D"),
            (1..8).map(DesignerPhysicalTestPack::answerFor)
        )
        assertEquals("A", DesignerPhysicalTestPack.answerFor(1))
        assertEquals("D", DesignerPhysicalTestPack.answerFor(20))
    }
}
