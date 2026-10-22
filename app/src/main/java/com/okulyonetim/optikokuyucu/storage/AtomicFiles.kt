package com.okulyonetim.optikokuyucu.storage

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Crash-safe file replacement shared by every app-private repository.
 *
 * Old pattern: write ".tmp", delete the destination, rename. A power loss or process kill between
 * the delete and the rename destroyed the only copy of an exam, answer key or form version.
 * New pattern: write a uniquely named temp file, fsync it, then replace the destination with a
 * single atomic rename, so a reader always sees either the complete old or the complete new file.
 */
object AtomicFiles {
    private val locks = ConcurrentHashMap<String, Any>()

    /**
     * One lock object per directory, shared by all repository instances. Repositories are created
     * freely by screens, so an instance-level lock would not protect read-modify-write sequences.
     */
    fun lockFor(directory: File): Any = locks.getOrPut(directory.absolutePath) { Any() }

    fun writeBytes(target: File, bytes: ByteArray) {
        val directory = target.absoluteFile.parentFile ?: error("Kayıt klasörü bulunamadı.")
        directory.mkdirs()
        val temporary = File(directory, "${target.name}.${UUID.randomUUID().toString().take(8)}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            replace(temporary, target)
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    fun writeText(target: File, text: String) = writeBytes(target, text.toByteArray(Charsets.UTF_8))

    private fun replace(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Removes temp files left behind by a process that died mid-write. */
    fun cleanStaleTemporaries(directory: File, olderThanMs: Long = STALE_TEMP_AGE_MS) {
        val threshold = System.currentTimeMillis() - olderThanMs
        directory.listFiles { file -> file.isFile && file.name.endsWith(".tmp") }
            .orEmpty()
            .filter { it.lastModified() < threshold }
            .forEach { it.delete() }
    }

    /**
     * Decodes [file] and reports corruption instead of hiding it. The file is left in place so a
     * later app version (or a manual recovery) can still read it.
     */
    fun <T> readOrNull(file: File, label: String, decode: (ByteArray) -> T): T? =
        try {
            decode(file.readBytes())
        } catch (error: Throwable) {
            AppLog.warn("Storage", "$label okunamadı: ${file.name}", error)
            null
        }

    private const val STALE_TEMP_AGE_MS = 60L * 60L * 1000L
}

/** Logging that stays harmless in plain JVM unit tests, where android.util.Log is not mocked. */
object AppLog {
    fun warn(tag: String, message: String, error: Throwable? = null) {
        try {
            if (error == null) Log.w(tag, message) else Log.w(tag, message, error)
        } catch (_: Throwable) {
            // android.util.Log is unavailable in local JVM tests.
        }
    }
}
