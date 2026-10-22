package com.okulyonetim.optikokuyucu.omr.bubble

import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Plain byte-array view of a single-channel 8-bit image.
 *
 * Reading one pixel through Mat.get() crosses the JNI boundary and allocates, which made scoring a
 * sheet cost hundreds of thousands of native calls per frame. Copying the canonical frame once and
 * scoring from this array removes that overhead and lets the scoring math run in plain JVM tests.
 */
class GrayImage(
    val width: Int,
    val height: Int,
    val pixels: ByteArray
) {
    init {
        require(width > 0 && height > 0) { "Geçersiz görüntü boyutu: ${width}x$height" }
        require(pixels.size.toLong() == width.toLong() * height.toLong()) {
            "Piksel sayısı boyutlarla eşleşmiyor."
        }
    }

    fun valueAt(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF
}

/** Copies a single-channel [Mat] into a [GrayImage]; null when the Mat is empty or multi-channel. */
fun Mat.toGrayImageOrNull(): GrayImage? {
    val bytes = toByteArray8U() ?: return null
    return GrayImage(cols(), rows(), bytes)
}

/** Row-major copy of an 8-bit single-channel Mat, converting depth/continuity when required. */
internal fun Mat.toByteArray8U(): ByteArray? {
    if (empty() || channels() != 1) return null
    val temporaries = ArrayList<Mat>(2)
    try {
        var source = this
        if (source.type() != CvType.CV_8UC1) {
            val converted = Mat()
            temporaries += converted
            source.convertTo(converted, CvType.CV_8U)
            source = converted
        }
        if (!source.isContinuous) {
            val continuous = source.clone()
            temporaries += continuous
            source = continuous
        }
        val count = source.rows().toLong() * source.cols().toLong()
        if (count <= 0L || count > Int.MAX_VALUE) return null
        val bytes = ByteArray(count.toInt())
        return if (source.get(0, 0, bytes) == bytes.size) bytes else null
    } finally {
        temporaries.forEach { it.release() }
    }
}
