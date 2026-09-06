package com.okulyonetim.optikokuyucu.student

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object StudentRosterCodec {
    fun encode(entry: StudentRosterEntry): ByteArray {
        val normalized = entry.normalized()
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(MAGIC)
            data.writeInt(SCHEMA_VERSION)
            data.writeUTF(normalized.studentNumber)
            data.writeUTF(normalized.fullName)
            data.writeUTF(normalized.gender.name)
            data.writeInt(normalized.gradeLevel)
            data.writeUTF(normalized.branch)
            data.writeUTF(normalized.guardianName)
            data.writeUTF(normalized.guardianPhone)
            data.writeLong(normalized.updatedAtEpochMs)
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): StudentRosterEntry {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz öğrenci kayıt dosyası." }
            val schema = input.readInt()
            require(schema == SCHEMA_VERSION) { "Desteklenmeyen öğrenci kayıt sürümü: $schema" }
            val entry = StudentRosterEntry(
                studentNumber = input.readUTF(),
                fullName = input.readUTF(),
                gender = runCatching { StudentGender.valueOf(input.readUTF()) }.getOrDefault(StudentGender.UNKNOWN),
                gradeLevel = input.readInt(),
                branch = input.readUTF(),
                guardianName = input.readUTF(),
                guardianPhone = input.readUTF(),
                updatedAtEpochMs = input.readLong()
            ).normalized()
            require(input.available() == 0) { "Öğrenci kayıt dosyasında beklenmeyen ek veri var." }
            return entry
        }
    }

    private const val MAGIC = 0x4F4D5354 // OMST
    private const val SCHEMA_VERSION = 1
}
