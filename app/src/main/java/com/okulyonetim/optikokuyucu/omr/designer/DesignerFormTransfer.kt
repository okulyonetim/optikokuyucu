package com.okulyonetim.optikokuyucu.omr.designer

/** Portable, lossless and editable form package backed by the canonical DesignerDocument codec. */
object DesignerFormTransfer {
    const val FILE_EXTENSION = "omrd"
    const val MIME_TYPE = "application/octet-stream"

    fun export(document: DesignerDocument): ByteArray = DesignerDocumentCodec.encode(document)

    fun import(bytes: ByteArray): DesignerDocument {
        require(bytes.isNotEmpty()) { "Optik form dosyası boş." }
        return DesignerDocumentCodec.decode(bytes)
    }

    fun fileName(document: DesignerDocument): String {
        val safe = document.name
            .trim()
            .replace(Regex("[^A-Za-z0-9ÇĞİÖŞÜçğıöşü._-]+"), "-")
            .trim('-')
            .ifBlank { "optik-form" }
        return "$safe-v${document.version}.$FILE_EXTENSION"
    }
}
