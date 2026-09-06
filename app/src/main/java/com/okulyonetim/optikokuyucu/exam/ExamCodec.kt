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
            out.writeInt(exam.bookletCount)
            out.writeBoolean(exam.personalizedFormsEnabled)
            require(exam.participants.size <= MAX_PARTICIPANTS)
            out.writeInt(exam.participants.size)
            exam.participants.forEach { participant ->
                out.writeUTF(participant.studentNumber)
                out.writeUTF(participant.studentName)
                out.writeUTF(participant.className)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): Exam {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz sınav dosyası." }
            val schema = input.readInt()
            require(schema in MIN_SUPPORTED_SCHEMA..SCHEMA_VERSION) {
                "Desteklenmeyen sınav dosyası sürümü: $schema"
            }
            val id = input.readUTF()
            val name = input.readUTF()
            val schoolName = input.readUTF()
            val templateSelection = ActiveTemplateSelection(
                source = ActiveTemplateSource.valueOf(input.readUTF()),
                templateId = input.readUTF(),
                templateVersion = input.readInt()
            )
            val wrongAnswerPolicy = WrongAnswerPolicy.valueOf(input.readUTF())
            val folderName = input.readUTF()
            val examDateEpochDay = input.readLong()
            val createdAtEpochMs = input.readLong()
            val papers = List(readSafeCount(input, MAX_PAPERS, "kağıt")) {
                ExamPaperLink(
                    scanRecordId = input.readUTF(),
                    studentName = input.readUTF(),
                    className = input.readUTF(),
                    studentNumber = input.readUTF(),
                    bookletCode = input.readUTF(),
                    linkedAtEpochMs = input.readLong()
                )
            }
            val bookletCount: Int
            val personalizedFormsEnabled: Boolean
            val participants: List<ExamParticipant>
            if (schema >= 2) {
                bookletCount = input.readInt()
                personalizedFormsEnabled = input.readBoolean()
                participants = List(readSafeCount(input, MAX_PARTICIPANTS, "katılımcı")) {
                    ExamParticipant(
                        studentNumber = input.readUTF(),
                        studentName = input.readUTF(),
                        className = input.readUTF()
                    )
                }
            } else {
                bookletCount = 1
                personalizedFormsEnabled = false
                participants = emptyList()
            }
            val exam = Exam(
                id = id,
                name = name,
                schoolName = schoolName,
                templateSelection = templateSelection,
                wrongAnswerPolicy = wrongAnswerPolicy,
                folderName = folderName,
                examDateEpochDay = examDateEpochDay,
                createdAtEpochMs = createdAtEpochMs,
                papers = papers,
                participants = participants,
                bookletCount = bookletCount,
                personalizedFormsEnabled = personalizedFormsEnabled
            )
            require(input.available() == 0) { "Sınav dosyasında beklenmeyen ek veri var." }
            return exam
        }
    }

    private fun readSafeCount(input: DataInputStream, maximum: Int, label: String): Int {
        val count = input.readInt()
        require(count in 0..maximum) { "Sınav dosyasında geçersiz $label sayısı: $count" }
        return count
    }

    private const val MAGIC = 0x4F4D4558 // OMEX
    private const val MIN_SUPPORTED_SCHEMA = 1
    private const val SCHEMA_VERSION = 2
    private const val MAX_PAPERS = 10000
    private const val MAX_PARTICIPANTS = 10000
}
