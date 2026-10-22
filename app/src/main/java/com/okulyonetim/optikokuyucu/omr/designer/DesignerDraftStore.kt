package com.okulyonetim.optikokuyucu.omr.designer

import android.content.Context
import com.okulyonetim.optikokuyucu.storage.AtomicFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Unsaved editor state. The form editor keeps its document in memory only, so a process kill
 * (low memory, swipe-away, crash) used to discard every edit made since the last explicit save.
 */
data class DesignerDraft(
    /** Account the draft belongs to ("" when signed out) so a shared phone never leaks drafts. */
    val ownerKey: String,
    /** Text of the name field, which can differ from the document name before the first save. */
    val formName: String,
    val document: DesignerDocument,
    val savedAtEpochMs: Long
)

object DesignerDraftCodec {
    fun encode(draft: DesignerDraft): ByteArray {
        val body = DesignerDocumentCodec.encode(draft.document)
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(SCHEMA_VERSION)
            out.writeUTF(draft.ownerKey.take(MAX_TEXT_CHARS))
            out.writeUTF(draft.formName.take(MAX_TEXT_CHARS))
            out.writeLong(draft.savedAtEpochMs)
            out.writeInt(body.size)
            out.write(body)
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): DesignerDraft {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz form taslağı dosyası." }
            val schema = input.readInt()
            require(schema == SCHEMA_VERSION) { "Desteklenmeyen form taslağı sürümü: $schema" }
            val ownerKey = input.readUTF()
            val formName = input.readUTF()
            val savedAt = input.readLong()
            val size = input.readInt()
            require(size in 1..MAX_DOCUMENT_BYTES) { "Geçersiz form taslağı boyutu: $size" }
            val body = ByteArray(size)
            input.readFully(body)
            require(input.available() == 0) { "Form taslağı dosyasında beklenmeyen ek veri var." }
            return DesignerDraft(
                ownerKey = ownerKey,
                formName = formName,
                document = DesignerDocumentCodec.decode(body),
                savedAtEpochMs = savedAt
            )
        }
    }

    private const val MAGIC = 0x4F4D4444 // OMDD
    private const val SCHEMA_VERSION = 1
    private const val MAX_TEXT_CHARS = 200
    private const val MAX_DOCUMENT_BYTES = 40_000_000
}

/** Single-slot autosave for the form editor; written atomically so a kill mid-write is harmless. */
class DesignerDraftStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME).apply { mkdirs() }
    private val file = File(directory, FILE_NAME)

    init {
        AtomicFiles.cleanStaleTemporaries(directory)
    }

    fun save(draft: DesignerDraft) {
        synchronized(AtomicFiles.lockFor(directory)) {
            AtomicFiles.writeBytes(file, DesignerDraftCodec.encode(draft))
        }
    }

    fun load(ownerKey: String): DesignerDraft? {
        if (!file.isFile) return null
        return AtomicFiles.readOrNull(file, "Form taslağı") { DesignerDraftCodec.decode(it) }
            ?.takeIf { it.ownerKey == ownerKey }
    }

    fun clear() {
        synchronized(AtomicFiles.lockFor(directory)) {
            file.delete()
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-designer-draft"
        const val FILE_NAME = "draft.omrdd"
    }
}
