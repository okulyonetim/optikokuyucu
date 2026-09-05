package com.okulyonetim.optikokuyucu.exam

import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSelection
import com.okulyonetim.optikokuyucu.omr.template.ActiveTemplateSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Dependency-free, versioned offline persistence format for exam containers. */
object ExamCodec {
    fun encode(exam: Exam): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(SCHEMA_VERSION)
            out.writeUTF(exam.id)
            out.writeUTF(exam.name)
            out.writeUTF(exam.schoolName)
            out.writeUTF(exam.templateSelection.source.name)
            out.writeUTF(exam.templateSelection.templateId)
            out.writeInt(exam.templateSelection.templateVersion)
            out.writeUTF(exam.wrongAnswerPolicy.name)
            out.writeUTF(exam.folderName)
            out.writeLong(exam.examDateEpochDay)
            out.writeLong(exam.createdAtEpochMs)
            require(exam.papers.size <= MAX_PAPERS)
            out.writeInt(exam.papers.size)
            exam.papers.forEach { paper ->
                out.writeUTF(paper.scanRecordId)
                out.writeUTF(paper.studentName)
                out.writeUTF(paper.className)
                out.writeUTF(paper.studentNumber)
                out.writeUTF(paper.bookletCode)
                out.writeLong(paper.linkedAtEpochMs)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Exam {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz sınav dosyası." }
            val schema = input.readInt()
            require(schema == SCHEMA_VERSION) { "Desteklenmeyen sınav dosyası sürümü: $schema" }
            val exam = Exam(
                id = input.readUTF(),
                name = input.readUTF(),
                schoolName = input.readUTF(),
                templateSelection = ActiveTemplateSelection(
                    source = ActiveTemplateSource.valueOf(input.readUTF()),
                    templateId = input.readUTF(),
                    templateVersion = input.readInt()
                ),
                wrongAnswerPolicy = WrongAnswerPolicy.valueOf(input.readUTF()),
                folderName = input.readUTF(),
                examDateEpochDay = input.readLong(),
                createdAtEpochMs = input.readLong(),
                papers = List(readSafeCount(input, MAX_PAPERS)) {
                    ExamPaperLink(
                        scanRecordId = input.readUTF(),
                        studentName = input.readUTF(),
                        className = input.readUTF(),
                        studentNumber = input.readUTF(),
                        bookletCode = input.readUTF(),
                        linkedAtEpochMs = input.readLong()
                    )
                }
            )
            require(input.available() == 0) { "Sınav dosyasında beklenmeyen ek veri var." }
            return exam
        }
    }

    private fun readSafeCount(input: DataInputStream, maximum: Int): Int {
        val count = input.readInt()
        require(count in 0..maximum) { "Sınav dosyasında geçersiz kağıt sayısı: $count" }
        return count
    }

    private const val MAGIC = 0x4F4D4558 // OMEX
    private const val SCHEMA_VERSION = 1
    private const val MAX_PAPERS = 10000
}
