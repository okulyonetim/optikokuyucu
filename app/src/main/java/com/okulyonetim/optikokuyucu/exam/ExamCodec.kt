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
            out.writeUTF(exam.subjectName)
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
            out.writeUTF(exam.ownerUid)
            out.writeUTF(exam.ownerDisplayName)
            out.writeBoolean(exam.isPublic)
            writeScoringConfiguration(out, exam.scoringConfiguration)
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
            val subjectName = if (schema >= 4) input.readUTF() else ""
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
            val ownerUid: String
            val ownerDisplayName: String
            val isPublic: Boolean
            if (schema >= 3) {
                ownerUid = input.readUTF()
                ownerDisplayName = input.readUTF()
                isPublic = input.readBoolean()
            } else {
                ownerUid = ""
                ownerDisplayName = ""
                isPublic = false
            }
            val scoringConfiguration = if (schema >= 5) {
                readScoringConfiguration(input)
            } else {
                ExamScoringConfiguration.forType(
                    if (subjectName.isNotBlank()) ExamScoringType.SINGLE_SUBJECT else ExamScoringType.NORMAL
                )
            }
            val exam = Exam(
                id = id,
                name = name,
                schoolName = schoolName,
                templateSelection = templateSelection,
                subjectName = subjectName,
                wrongAnswerPolicy = wrongAnswerPolicy,
                scoringConfiguration = scoringConfiguration,
                folderName = folderName,
                examDateEpochDay = examDateEpochDay,
                createdAtEpochMs = createdAtEpochMs,
                papers = papers,
                participants = participants,
                bookletCount = bookletCount,
                personalizedFormsEnabled = personalizedFormsEnabled,
                ownerUid = ownerUid,
                ownerDisplayName = ownerDisplayName,
                isPublic = isPublic
            )
            require(input.available() == 0) { "Sınav dosyasında beklenmeyen ek veri var." }
            return exam
        }
    }

    private fun writeScoringConfiguration(out: DataOutputStream, configuration: ExamScoringConfiguration) {
        out.writeUTF(configuration.type.name)
        out.writeUTF(configuration.scoreMode.name)
        out.writeDouble(configuration.minimumScore)
        out.writeDouble(configuration.maximumScore)
        out.writeBoolean(configuration.customWrongAnswerDivisor != null)
        configuration.customWrongAnswerDivisor?.let(out::writeDouble)
        require(configuration.lessonWeights.size <= MAX_SCORING_WEIGHTS)
        out.writeInt(configuration.lessonWeights.size)
        configuration.lessonWeights.toSortedMap().forEach { (lessonId, weight) ->
            out.writeUTF(lessonId)
            out.writeDouble(weight)
        }
    }

    private fun readScoringConfiguration(input: DataInputStream): ExamScoringConfiguration {
        val type = ExamScoringType.valueOf(input.readUTF())
        val scoreMode = ExamScoreMode.valueOf(input.readUTF())
        val minimumScore = input.readDouble()
        val maximumScore = input.readDouble()
        val customWrongAnswerDivisor = if (input.readBoolean()) input.readDouble() else null
        val weights = linkedMapOf<String, Double>()
        repeat(readSafeCount(input, MAX_SCORING_WEIGHTS, "ders ağırlığı")) {
            val lessonId = input.readUTF()
            require(lessonId !in weights) { "Sınav dosyasında yinelenen ders ağırlığı: $lessonId" }
            weights[lessonId] = input.readDouble()
        }
        return ExamScoringConfiguration(
            type = type,
            scoreMode = scoreMode,
            minimumScore = minimumScore,
            maximumScore = maximumScore,
            customWrongAnswerDivisor = customWrongAnswerDivisor,
            lessonWeights = weights
        )
    }

    private fun readSafeCount(input: DataInputStream, maximum: Int, label: String): Int {
        val count = input.readInt()
        require(count in 0..maximum) { "Sınav dosyasında geçersiz $label sayısı: $count" }
        return count
    }

    private const val MAGIC = 0x4F4D4558 // OMEX
    private const val MIN_SUPPORTED_SCHEMA = 1
    private const val SCHEMA_VERSION = 5
    private const val MAX_PAPERS = 10000
    private const val MAX_PARTICIPANTS = 10000
    private const val MAX_SCORING_WEIGHTS = 64
}
