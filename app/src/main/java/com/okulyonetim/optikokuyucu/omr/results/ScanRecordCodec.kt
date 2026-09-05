package com.okulyonetim.optikokuyucu.omr.results

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Dependency-free, versioned persistence format for immutable raw OMR scan records. */
object ScanRecordCodec {
    fun encode(record: ScanRecord): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(SCHEMA_VERSION)
            out.writeUTF(record.id)
            out.writeUTF(record.templateId)
            out.writeInt(record.templateVersion)
            out.writeLong(record.capturedAtEpochMs)
            out.writeUTF(record.source.name)
            out.writeInt(record.sourceWidth)
            out.writeInt(record.sourceHeight)
            writeNullableDouble(out, record.pageConfidence)
            writeNullableDouble(out, record.decisionConfidence)
            out.writeDouble(record.elapsedMs)

            out.writeInt(record.answers.size)
            record.answers.forEach { answer ->
                out.writeUTF(answer.questionId)
                out.writeUTF(answer.state.name)
                writeNullableString(out, answer.selectedChoice)
                out.writeDouble(answer.confidence)
                writeScores(out, answer.choiceScores)
            }

            out.writeInt(record.markGrids.size)
            record.markGrids.forEach { grid ->
                out.writeUTF(grid.gridId)
                out.writeInt(grid.columns.size)
                grid.columns.forEach { column ->
                    out.writeUTF(column.columnId)
                    out.writeUTF(column.state.name)
                    writeNullableString(out, column.selectedValue)
                    out.writeDouble(column.confidence)
                    writeScores(out, column.scores)
                }
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): ScanRecord {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz OMR sonuç dosyası." }
            val schema = input.readInt()
            require(schema == SCHEMA_VERSION) { "Desteklenmeyen OMR sonuç sürümü: $schema" }

            val record = ScanRecord(
                id = input.readUTF(),
                templateId = input.readUTF(),
                templateVersion = input.readInt(),
                capturedAtEpochMs = input.readLong(),
                source = ScanSource.valueOf(input.readUTF()),
                sourceWidth = input.readInt(),
                sourceHeight = input.readInt(),
                pageConfidence = readNullableDouble(input),
                decisionConfidence = readNullableDouble(input),
                elapsedMs = input.readDouble(),
                answers = List(readSafeCount(input, MAX_ANSWERS)) {
                    RecordedAnswer(
                        questionId = input.readUTF(),
                        state = RecordedAnswerState.valueOf(input.readUTF()),
                        selectedChoice = readNullableString(input),
                        confidence = input.readDouble(),
                        choiceScores = readScores(input)
                    )
                },
                markGrids = List(readSafeCount(input, MAX_GRIDS)) {
                    RecordedMarkGrid(
                        gridId = input.readUTF(),
                        columns = List(readSafeCount(input, MAX_GRID_COLUMNS)) {
                            RecordedMarkColumn(
                                columnId = input.readUTF(),
                                state = RecordedMarkState.valueOf(input.readUTF()),
                                selectedValue = readNullableString(input),
                                confidence = input.readDouble(),
                                scores = readScores(input)
                            )
                        }
                    )
                }
            )
            require(input.available() == 0) { "OMR sonuç dosyasında beklenmeyen ek veri var." }
            return record
        }
    }

    private fun writeScores(out: DataOutputStream, scores: Map<String, Double>) {
        require(scores.size <= MAX_SCORES)
        out.writeInt(scores.size)
        scores.forEach { (key, value) ->
            out.writeUTF(key)
            out.writeDouble(value)
        }
    }

    private fun readScores(input: DataInputStream): Map<String, Double> = buildMap {
        repeat(readSafeCount(input, MAX_SCORES)) {
            put(input.readUTF(), input.readDouble())
        }
    }

    private fun writeNullableString(out: DataOutputStream, value: String?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeUTF(value)
    }

    private fun readNullableString(input: DataInputStream): String? =
        if (input.readBoolean()) input.readUTF() else null

    private fun writeNullableDouble(out: DataOutputStream, value: Double?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeDouble(value)
    }

    private fun readNullableDouble(input: DataInputStream): Double? =
        if (input.readBoolean()) input.readDouble() else null

    private fun readSafeCount(input: DataInputStream, maximum: Int): Int {
        val count = input.readInt()
        require(count in 0..maximum) { "OMR sonuç dosyasında geçersiz öğe sayısı: $count" }
        return count
    }

    private const val MAGIC = 0x4F4D5252 // OMRR
    private const val SCHEMA_VERSION = 1
    private const val MAX_ANSWERS = 1000
    private const val MAX_GRIDS = 100
    private const val MAX_GRID_COLUMNS = 100
    private const val MAX_SCORES = 100
}
