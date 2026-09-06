package com.okulyonetim.optikokuyucu.student

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

data class EschoolPdfImportPreview(
    val students: List<StudentRosterEntry>
) {
    init {
        require(students.isNotEmpty()) { "Önizleme için öğrenci listesi boş olamaz." }
    }

    val classCounts: List<Pair<String, Int>>
        get() = students
            .groupingBy { it.className }
            .eachCount()
            .toList()
            .sortedBy { it.first }
}

/** Reads embedded text from an e-Okul PDF locally on-device; no OCR or network upload is used. */
object EschoolPdfImporter {
    fun read(
        context: Context,
        uri: Uri,
        importedAtEpochMs: Long = System.currentTimeMillis()
    ): EschoolPdfImportPreview {
        require(importedAtEpochMs >= 0L)
        val appContext = context.applicationContext
        PDFBoxResourceLoader.init(appContext)

        val text = requireNotNull(appContext.contentResolver.openInputStream(uri)) {
            "Seçilen PDF açılamadı."
        }.use { input ->
            PDDocument.load(input).use { document ->
                require(document.numberOfPages > 0) { "PDF içinde sayfa bulunamadı." }
                PDFTextStripper().apply {
                    sortByPosition = true
                }.getText(document)
            }
        }

        val students = EschoolClassListParser.parse(
            text = text,
            importedAtEpochMs = importedAtEpochMs
        )
        return EschoolPdfImportPreview(students)
    }
}
