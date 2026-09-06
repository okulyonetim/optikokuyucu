package com.okulyonetim.optikokuyucu.omr.scoring

import android.content.Context
import com.okulyonetim.optikokuyucu.omr.results.ScanRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

enum class AnswerKeySource {
    GALLERY,
    CAMERA,
    MANUAL,
    SPREADSHEET,
    SCAN_RECORD
}

data class StoredAnswerKey(
    val answerKey: AnswerKey,
    val variantGridId: String? = null,
    val variantValue: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val source: AnswerKeySource,
    val sourceRecordId: String? = null
) {
    init {
        require((variantGridId == null) == (variantValue == null)) {
            "Variant grid and value must either both be set or both be null."
        }
        require(variantGridId?.isNotBlank() != false)
        require(variantValue?.isNotBlank() != false)
        require(createdAtEpochMs >= 0L)
        require(sourceRecordId?.isNotBlank() != false)
    }

    val templateId: String get() = answerKey.templateId
    val templateVersion: Int get() = answerKey.templateVersion
}

interface AnswerKeyRepository {
    fun save(key: StoredAnswerKey)
    fun load(
        templateId: String,
        templateVersion: Int,
        variantGridId: String? = null,
        variantValue: String? = null
    ): StoredAnswerKey?
    fun list(): List<StoredAnswerKey>
    fun delete(
        templateId: String,
        templateVersion: Int,
        variantGridId: String? = null,
        variantValue: String? = null
    ): Boolean
}

class FileAnswerKeyRepository(context: Context) : AnswerKeyRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override fun save(key: StoredAnswerKey) {
        val destination = fileFor(
            key.templateId,
            key.templateVersion,
            key.variantGridId,
            key.variantValue
        )
        val temporary = File(directory, destination.name + ".tmp")
        temporary.writeBytes(AnswerKeyCodec.encode(key))
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("Eski cevap anahtarı değiştirilemedi.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Cevap anahtarı kalıcı depoya taşınamadı.")
        }
    }

    override fun load(
        templateId: String,
        templateVersion: Int,
        variantGridId: String?,
        variantValue: String?
    ): StoredAnswerKey? {
        val file = fileFor(templateId, templateVersion, variantGridId, variantValue)
        if (!file.isFile) return null
        return runCatching { AnswerKeyCodec.decode(file.readBytes()) }
            .getOrNull()
            ?.takeIf {
                it.templateId == templateId &&
                    it.templateVersion == templateVersion &&
                    it.variantGridId == variantGridId &&
                    it.variantValue == variantValue
            }
    }

    override fun list(): List<StoredAnswerKey> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .mapNotNull { file -> runCatching { AnswerKeyCodec.decode(file.readBytes()) }.getOrNull() }
        .sortedWith(
            compareByDescending<StoredAnswerKey> { it.createdAtEpochMs }
                .thenBy { it.templateId }
                .thenBy { it.templateVersion }
                .thenBy { it.variantGridId ?: "" }
                .thenBy { it.variantValue ?: "" }
        )

    override fun delete(
        templateId: String,
        templateVersion: Int,
        variantGridId: String?,
        variantValue: String?
    ): Boolean {
        val file = fileFor(templateId, templateVersion, variantGridId, variantValue)
        return !file.exists() || file.delete()
    }

    private fun fileFor(
        templateId: String,
        templateVersion: Int,
        variantGridId: String?,
        variantValue: String?
    ): File {
        require(templateId.isNotBlank())
        require(templateVersion > 0)
        require((variantGridId == null) == (variantValue == null))
        val identity = listOf(
            templateId,
            templateVersion.toString(),
            variantGridId ?: "",
            variantValue ?: ""
        ).joinToString("\u0000")
        return File(directory, sha256(identity) + FILE_SUFFIX)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-answer-keys"
        const val FILE_SUFFIX = ".omrak"
    }
}

object AnswerKeyResolver {
    fun resolve(record: ScanRecord, keys: List<StoredAnswerKey>): StoredAnswerKey? {
        val compatible = keys.filter {
            it.templateId == record.templateId && it.templateVersion == record.templateVersion
        }
        if (compatible.isEmpty()) return null

        compatible
            .filter { it.variantGridId != null }
            .firstOrNull { key ->
                val recordValue = record.grid(requireNotNull(key.variantGridId))?.value
                recordValue != null && recordValue == key.variantValue
            }
            ?.let { return it }

        return compatible.firstOrNull { it.variantGridId == null }
    }
}

object AnswerKeyCodec {
    fun encode(key: StoredAnswerKey): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(SCHEMA_VERSION)
            out.writeUTF(key.answerKey.templateId)
            out.writeInt(key.answerKey.templateVersion)
            writeNullableString(out, key.variantGridId)
            writeNullableString(out, key.variantValue)
            out.writeLong(key.createdAtEpochMs)
            out.writeUTF(key.source.name)
            writeNullableString(out, key.sourceRecordId)
            require(key.answerKey.answers.size in 1..MAX_ANSWERS)
            out.writeInt(key.answerKey.answers.size)
            key.answerKey.answers.forEach { (questionId, choice) ->
                out.writeUTF(questionId)
                out.writeUTF(choice)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): StoredAnswerKey {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz cevap anahtarı dosyası." }
            val schema = input.readInt()
            require(schema == SCHEMA_VERSION) { "Desteklenmeyen cevap anahtarı sürümü: $schema" }
            val templateId = input.readUTF()
            val templateVersion = input.readInt()
            val variantGridId = readNullableString(input)
            val variantValue = readNullableString(input)
            val createdAtEpochMs = input.readLong()
            val source = AnswerKeySource.valueOf(input.readUTF())
            val sourceRecordId = readNullableString(input)
            val count = input.readInt()
            require(count in 1..MAX_ANSWERS) { "Geçersiz cevap anahtarı soru sayısı: $count" }
            val answers = linkedMapOf<String, String>()
            repeat(count) {
                val questionId = input.readUTF()
                val choice = input.readUTF()
                require(questionId !in answers) { "Cevap anahtarında yinelenen soru: $questionId" }
                answers[questionId] = choice
            }
            require(input.available() == 0) { "Cevap anahtarı dosyasında beklenmeyen ek veri var." }
            return StoredAnswerKey(
                answerKey = AnswerKey(
                    templateId = templateId,
                    templateVersion = templateVersion,
                    answers = answers
                ),
                variantGridId = variantGridId,
                variantValue = variantValue,
                createdAtEpochMs = createdAtEpochMs,
                source = source,
                sourceRecordId = sourceRecordId
            )
        }
    }

    private fun writeNullableString(out: DataOutputStream, value: String?) {
        out.writeBoolean(value != null)
        if (value != null) out.writeUTF(value)
    }

    private fun readNullableString(input: DataInputStream): String? =
        if (input.readBoolean()) input.readUTF() else null

    private const val MAGIC = 0x4F4D414B // OMAK
    private const val SCHEMA_VERSION = 1
    private const val MAX_ANSWERS = 1000
}
