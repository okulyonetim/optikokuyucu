package com.okulyonetim.optikokuyucu.omr.scoring

import com.okulyonetim.optikokuyucu.omr.designer.DesignerAreaCatalog
import com.okulyonetim.optikokuyucu.omr.designer.DesignerDocument
import com.okulyonetim.optikokuyucu.omr.designer.DesignerTemplateCompiler
import jxl.Workbook
import jxl.write.Label
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerKeyImportTest {
    private fun documentAndTemplate(): Pair<DesignerDocument, com.okulyonetim.optikokuyucu.omr.template.OmrTemplate> {
        var document = DesignerDocument(id = "synthetic-key-form", version = 1, name = "Sentetik Form")
        val answers = DesignerAreaCatalog.createAnswerArea(document)
        document = document.copy(components = document.components + answers)
        return document to DesignerTemplateCompiler.compile(document)
    }

    @Test
    fun xlsxExportCanBeImportedBackLosslessly() {
        val (_, template) = documentAndTemplate()
        val answerMap = template.bubbleRows.associate { row -> row.id to row.bubbles.first().id }
        val stored = StoredAnswerKey(
            answerKey = AnswerKey(template.id, template.version, answerMap),
            source = AnswerKeySource.MANUAL,
            createdAtEpochMs = 1L
        )
        val bytes = AnswerKeyXlsxExporter.export(stored)
        val imported = AnswerKeySpreadsheetImporter.import(
            ByteArrayInputStream(bytes),
            "cevap.xlsx",
            template
        )
        assertEquals(answerMap, imported.answerKey.answers)
    }

    @Test
    fun legacyXlsTwoColumnSheetIsImported() {
        val (_, template) = documentAndTemplate()
        val output = ByteArrayOutputStream()
        val workbook = Workbook.createWorkbook(output)
        val sheet = workbook.createSheet("Cevap Anahtarı", 0)
        sheet.addCell(Label(0, 0, "Soru"))
        sheet.addCell(Label(1, 0, "Doğru Cevap"))
        template.bubbleRows.forEachIndexed { index, row ->
            sheet.addCell(Label(0, index + 1, row.id))
            sheet.addCell(Label(1, index + 1, row.bubbles.last().id))
        }
        workbook.write()
        workbook.close()

        val imported = AnswerKeySpreadsheetImporter.import(
            ByteArrayInputStream(output.toByteArray()),
            "cevap.xls",
            template
        )
        assertEquals(template.bubbleRows.size, imported.answerKey.answers.size)
        assertEquals(template.bubbleRows.first().bubbles.last().id, imported.answerKey.answers[template.bubbleRows.first().id])
    }

    @Test
    fun manualBuilderAcceptsLessonSequence() {
        val (document, template) = documentAndTemplate()
        val sections = ManualAnswerKeyBuilder.sections(document, template)
        val entered = sections.associate { section ->
            section.id to section.questionIds.joinToString("") { questionId ->
                template.bubbleRows.first { it.id == questionId }.bubbles.first().id
            }
        }
        val key = ManualAnswerKeyBuilder.build(template, sections, entered)
        assertEquals(template.bubbleRows.size, key.answers.size)
    }
}
