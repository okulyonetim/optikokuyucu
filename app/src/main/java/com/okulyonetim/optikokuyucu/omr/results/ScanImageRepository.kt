package com.okulyonetim.optikokuyucu.omr.results

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Canonically rectified grayscale image captured for one accepted [ScanRecord].
 *
 * The raw recognition record intentionally remains image-free and immutable. This companion image
 * is optional and can be deleted/rebuilt independently without changing answers or scores.
 */
data class StoredScanImage(
    val scanRecordId: String,
    val width: Int,
    val height: Int,
    val luma: ByteArray
) {
    init {
        require(scanRecordId.isNotBlank())
        require(width > 0 && height > 0)
        val pixelCount = width.toLong() * height.toLong()
        require(pixelCount in 1..MAX_SCAN_IMAGE_PIXELS.toLong()) {
            "Geçersiz canonical görüntü boyutu: ${width}x$height"
        }
        require(luma.size.toLong() == pixelCount) {
            "Canonical görüntü piksel sayısı boyutlarla eşleşmiyor."
        }
    }
}

interface ScanImageRepository {
    fun save(image: StoredScanImage)
    fun load(scanRecordId: String): StoredScanImage?
    fun delete(scanRecordId: String): Boolean
}

/** App-private image store; no storage permission or network is required. */
class FileScanImageRepository(context: Context) : ScanImageRepository {
    private val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }

    override fun save(image: StoredScanImage) {
        val destination = fileFor(image.scanRecordId)
        val temporary = File(directory, destination.name + ".tmp")
        temporary.writeBytes(ScanImageCodec.encode(image))
        if (destination.exists() && !destination.delete()) {
            temporary.delete()
            error("Eski tarama görüntüsü değiştirilemedi.")
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            error("Tarama görüntüsü kalıcı depoya taşınamadı.")
        }
    }

    override fun load(scanRecordId: String): StoredScanImage? {
        val file = fileFor(scanRecordId)
        if (!file.isFile || file.length() !in 1..MAX_STORED_IMAGE_BYTES) return null
        return runCatching { ScanImageCodec.decode(file.readBytes()) }
            .getOrNull()
            ?.takeIf { it.scanRecordId == scanRecordId }
    }

    override fun delete(scanRecordId: String): Boolean {
        val file = fileFor(scanRecordId)
        return !file.exists() || file.delete()
    }

    private fun fileFor(id: String): File = File(directory, keyFor(id) + FILE_SUFFIX)

    private fun keyFor(id: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    }

    private companion object {
        const val DIRECTORY_NAME = "omr-scan-images"
        const val FILE_SUFFIX = ".omri"
        const val MAX_STORED_IMAGE_BYTES = 16_000_000L
    }
}

/** Versioned + gzip-compressed binary format for canonical grayscale scan images. */
object ScanImageCodec {
    fun encode(image: StoredScanImage): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { gzip ->
            DataOutputStream(gzip).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(SCHEMA_VERSION)
                out.writeUTF(image.scanRecordId)
                out.writeInt(image.width)
                out.writeInt(image.height)
                out.write(image.luma)
            }
        }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): StoredScanImage {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ENCODED_BYTES) {
            "Geçersiz tarama görüntüsü dosya boyutu."
        }
        DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { input ->
            require(input.readInt() == MAGIC) { "Geçersiz tarama görüntüsü dosyası." }
            val schema = input.readInt()
            require(schema == SCHEMA_VERSION) { "Desteklenmeyen tarama görüntüsü sürümü: $schema" }
            val id = input.readUTF()
            val width = input.readInt()
            val height = input.readInt()
            val pixelCount = width.toLong() * height.toLong()
            require(width > 0 && height > 0 && pixelCount in 1..MAX_SCAN_IMAGE_PIXELS.toLong()) {
                "Geçersiz canonical görüntü boyutu: ${width}x$height"
            }
            val luma = ByteArray(pixelCount.toInt())
            input.readFully(luma)
            require(input.read() == -1) { "Tarama görüntüsü dosyasında beklenmeyen ek veri var." }
            return StoredScanImage(
                scanRecordId = id,
                width = width,
                height = height,
                luma = luma
            )
        }
    }

    private const val MAGIC = 0x4F4D5249 // OMRI
    private const val SCHEMA_VERSION = 1
    private const val MAX_ENCODED_BYTES = 16_000_000
}

const val MAX_SCAN_IMAGE_PIXELS = 8_000_000
