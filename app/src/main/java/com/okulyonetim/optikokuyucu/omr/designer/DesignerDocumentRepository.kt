package com.okulyonetim.optikokuyucu.omr.designer

import android.content.Context
import java.io.File
import java.util.Base64

interface DesignerDocumentRepository {
    fun save(document: DesignerDocument)
    fun load(id: String, version: Int): DesignerDocument?
    fun list(): List<DesignerDocument>
    fun delete(id: String, version: Int): Boolean
}

/**
 * Fully offline repository backed by app-private files. A Room implementation can replace this
 * later without changing designer UI or compiler contracts.
 */
class FileDesignerDocumentRepository(context: Context) : DesignerDocumentRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override fun save(document: DesignerDocument) {
        val target = fileFor(document.id, document.version)
        val temp = File(directory, target.name + ".tmp")
        val bytes = DesignerDocumentCodec.encode(document)

        temp.outputStream().buffered().use { it.write(bytes) }
        if (target.exists() && !target.delete()) {
            temp.delete()
            error("Eski şablon dosyası değiştirilemedi.")
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            error("Şablon dosyası kaydedilemedi.")
        }
    }

    override fun load(id: String, version: Int): DesignerDocument? {
        val file = fileFor(id, version)
        if (!file.isFile) return null
        return DesignerDocumentCodec.decode(file.readBytes())
    }

    override fun list(): List<DesignerDocument> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension == EXTENSION }
        .mapNotNull { file -> runCatching { DesignerDocumentCodec.decode(file.readBytes()) }.getOrNull() }
        .sortedWith(compareBy<DesignerDocument> { it.name }.thenByDescending { it.version })
        .toList()

    override fun delete(id: String, version: Int): Boolean {
        val file = fileFor(id, version)
        return !file.exists() || file.delete()
    }

    private fun fileFor(id: String, version: Int): File {
        require(id.isNotBlank())
        require(version > 0)
        val encodedId = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(id.toByteArray(Charsets.UTF_8))
        return File(directory, "$encodedId-v$version.$EXTENSION")
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-designer-templates"
        const val EXTENSION = "omrd"
    }
}
