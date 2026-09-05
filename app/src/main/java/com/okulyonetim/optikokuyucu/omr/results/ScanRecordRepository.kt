package com.okulyonetim.optikokuyucu.omr.results

import android.content.Context
import java.io.File
import java.security.MessageDigest

interface ScanRecordRepository {
    fun save(record: ScanRecord)
    fun load(id: String): ScanRecord?
    fun list(): List<ScanRecord>
    fun delete(id: String): Boolean
}

/** App-private, atomic-ish file repository; no network or storage permission is required. */
class FileScanRecordRepository(
    context: Context
) : ScanRecordRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override fun save(record: ScanRecord) {
        val destination = fileFor(record.id)
        val temporary = File(directory, destination.name + ".tmp")
        temporary.writeBytes(ScanRecordCodec.encode(record))
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("Eski OMR kaydı değiştirilemedi.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("OMR kaydı kalıcı depoya taşınamadı.")
        }
    }

    override fun load(id: String): ScanRecord? {
        val file = fileFor(id)
        if (!file.isFile) return null
        return runCatching { ScanRecordCodec.decode(file.readBytes()) }
            .getOrNull()
            ?.takeIf { it.id == id }
    }

    override fun list(): List<ScanRecord> = directory
        .listFiles { file -> file.isFile && file.name.endsWith(FILE_SUFFIX) }
        .orEmpty()
        .mapNotNull { file -> runCatching { ScanRecordCodec.decode(file.readBytes()) }.getOrNull() }
        .sortedWith(
            compareByDescending<ScanRecord> { it.capturedAtEpochMs }
                .thenByDescending { it.id }
        )

    override fun delete(id: String): Boolean {
        val file = fileFor(id)
        return !file.exists() || file.delete()
    }

    private fun fileFor(id: String): File = File(directory, keyFor(id) + FILE_SUFFIX)

    private fun keyFor(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-scan-records"
        const val FILE_SUFFIX = ".omrr"
    }
}
