package com.okulyonetim.optikokuyucu.omr.results

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Properties

internal object ScanRecordImmutabilityPolicy {
    fun validateExisting(stored: ScanRecord, incoming: ScanRecord) {
        require(stored.id == incoming.id) { "Ham OMR kaydı kimliği değiştirilemez." }
        require(stored == incoming) {
            "Ham OMR kaydı immutable olduğu için aynı kayıt kimliği farklı içerikle değiştirilemez."
        }
    }
}

interface ScanRecordRepository {
    fun save(record: ScanRecord)
    fun load(id: String): ScanRecord?
    fun list(): List<ScanRecord>
    fun delete(id: String): Boolean
}

internal object ManualAnswerOverrideApplier {
    const val BLANK_TOKEN = "__BLANK__"

    fun apply(record: ScanRecord, overrides: Map<String, String>): ScanRecord {
        if (overrides.isEmpty()) return record
        var changed = false
        val adjusted = record.answers.map { answer ->
            if (!overrides.containsKey(answer.questionId)) {
                answer
            } else {
                changed = true
                val value = overrides.getValue(answer.questionId)
                if (value == BLANK_TOKEN) {
                    answer.copy(
                        state = RecordedAnswerState.BLANK,
                        selectedChoice = null,
                        confidence = 1.0
                    )
                } else {
                    answer.copy(
                        state = RecordedAnswerState.MARKED,
                        selectedChoice = value,
                        confidence = 1.0
                    )
                }
            }
        }
        return if (changed) record.copy(answers = adjusted) else record
    }
}

/** App-private, atomic-ish file repository; no network or storage permission is required. */
class FileScanRecordRepository(
    context: Context
) : ScanRecordRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    fun setManualAnswer(recordId: String, questionId: String, selectedChoice: String?) {
        require(loadRaw(recordId) != null) { "Düzeltilecek OMR kaydı bulunamadı." }
        require(questionId.isNotBlank()) { "Soru kimliği boş olamaz." }
        val overrides = loadManualOverrides(recordId).toMutableMap()
        overrides[questionId] = selectedChoice?.trim()?.takeIf { it.isNotEmpty() }
            ?: ManualAnswerOverrideApplier.BLANK_TOKEN
        saveManualOverrides(recordId, overrides)
    }

    fun clearManualAnswer(recordId: String, questionId: String) {
        val overrides = loadManualOverrides(recordId).toMutableMap()
        if (overrides.remove(questionId) != null) saveManualOverrides(recordId, overrides)
    }

    fun isManuallyCorrected(recordId: String, questionId: String): Boolean =
        loadManualOverrides(recordId).containsKey(questionId)

    override fun save(record: ScanRecord) {
        val destination = fileFor(record.id)
        if (destination.isFile) {
            val stored = runCatching { ScanRecordCodec.decode(destination.readBytes()) }
                .getOrElse { error ->
                    throw IllegalStateException("Mevcut ham OMR kaydı okunamadı.", error)
                }
            ScanRecordImmutabilityPolicy.validateExisting(stored, record)
            return
        }

        val temporary = File(directory, destination.name + ".tmp")
        temporary.writeBytes(ScanRecordCodec.encode(record))
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("OMR kaydı kalıcı depoya taşınamadı.")
        }
    }

    override fun load(id: String): ScanRecord? = loadRaw(id)?.let { record ->
        ManualAnswerOverrideApplier.apply(record, loadManualOverrides(id))
    }

    override fun list(): List<ScanRecord> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .mapNotNull { file -> runCatching { ScanRecordCodec.decode(file.readBytes()) }.getOrNull() }
        .map { record -> ManualAnswerOverrideApplier.apply(record, loadManualOverrides(record.id)) }
        .sortedWith(
            compareByDescending<ScanRecord> { it.capturedAtEpochMs }
                .thenByDescending { it.id }
        )

    override fun delete(id: String): Boolean {
        val file = fileFor(id)
        val deleted = !file.exists() || file.delete()
        if (deleted) correctionFileFor(id).delete()
        return deleted
    }

    private fun loadRaw(id: String): ScanRecord? {
        val file = fileFor(id)
        if (!file.isFile) return null
        return runCatching { ScanRecordCodec.decode(file.readBytes()) }
            .getOrNull()
            ?.takeIf { it.id == id }
    }

    private fun loadManualOverrides(id: String): Map<String, String> {
        val file = correctionFileFor(id)
        if (!file.isFile) return emptyMap()
        return runCatching {
            val properties = Properties()
            file.inputStream().use(properties::load)
            properties.stringPropertyNames().associateWith { key -> properties.getProperty(key) }
        }.getOrDefault(emptyMap())
    }

    private fun saveManualOverrides(id: String, overrides: Map<String, String>) {
        val destination = correctionFileFor(id)
        if (overrides.isEmpty()) {
            destination.delete()
            return
        }
        val properties = Properties().apply {
            overrides.forEach { (key, value) -> setProperty(key, value) }
        }
        val temporary = File(directory, destination.name + ".tmp")
        temporary.outputStream().use { properties.store(it, "Manual OMR answer corrections") }
        if (destination.exists()) destination.delete()
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Manuel cevap düzeltmesi kalıcı depoya taşınamadı.")
        }
    }

    private fun fileFor(id: String): File = File(directory, keyFor(id) + FILE_SUFFIX)
    private fun correctionFileFor(id: String): File = File(directory, keyFor(id) + CORRECTION_SUFFIX)

    private fun keyFor(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-scan-records"
        const val FILE_SUFFIX = ".omrr"
        const val CORRECTION_SUFFIX = ".manual.properties"
    }
}
